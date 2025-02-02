package org.marvelution.jji.management;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.inject.Inject;
import jakarta.servlet.ServletException;

import org.marvelution.jji.Headers;
import org.marvelution.jji.JiraIntegrationPlugin;
import org.marvelution.jji.Messages;
import org.marvelution.jji.configuration.JiraSite;
import org.marvelution.jji.configuration.JiraSitesConfiguration;
import org.marvelution.jji.security.SyncTokenSecurityContext;
import org.marvelution.jji.tunnel.TunnelManager;

import hudson.Extension;
import hudson.model.ManagementLink;
import jenkins.model.Jenkins;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import okhttp3.*;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.security.access.AccessDeniedException;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toCollection;
import static org.marvelution.jji.JiraUtils.getJsonFromRequest;
import static org.marvelution.jji.Messages.*;
import static org.marvelution.jji.synctoken.SyncTokenAuthenticator.OLD_SYNC_TOKEN_HEADER_NAME;

@Extension(ordinal = Integer.MAX_VALUE - 500)
public class JiraSiteManagement
        extends ManagementLink
{

    public static final String URL_NAME = "jji";
    private static final Logger LOGGER = Logger.getLogger(JiraSiteManagement.class.getName());
    private JiraSitesConfiguration sitesConfiguration;
    private TunnelManager tunnelManager;
    private OkHttpClient httpClient;
    private JiraSite site;

    @Inject
    public void setSitesConfiguration(JiraSitesConfiguration sitesConfiguration)
    {
        this.sitesConfiguration = sitesConfiguration;
    }

    @Inject
    public void setTunnelManager(TunnelManager tunnelManager)
    {
        this.tunnelManager = tunnelManager;
    }

    @Inject
    public void setHttpClient(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    @Override
    public String getIconFileName()
    {
        return "/plugin/" + JiraIntegrationPlugin.SHORT_NAME + "/images/48x48/jji.png";
    }

    @Override
    public String getDescription()
    {
        return manage_description();
    }

    @Override
    public String getUrlName()
    {
        return URL_NAME;
    }

    @Override
    public Category getCategory()
    {
        return Category.CONFIGURATION;
    }

    @Override
    public String getDisplayName()
    {
        return manage_display_name();
    }

    public Set<JiraSite> getSites()
    {
        return sitesConfiguration.stream()
                .sorted(comparing(JiraSite::getName))
                .collect(toCollection(LinkedHashSet::new));
    }

    @Nullable
    public String getSiteConnectionError(String siteId)
    {
        return sitesConfiguration.findSite(siteId)
                .filter(JiraSite::isTunneled)
                .map(site -> tunnelManager.getSiteConnectionError(site))
                .orElse(null);
    }

    @JavaScriptMethod
    public void deleteSite(String uri)
    {
        sitesConfiguration.findSite(URI.create(uri))
                .ifPresent(site -> {
                    try (Response response = httpClient.newCall(site.createUnregisterRequest())
                            .execute())
                    {
                        if (response.isSuccessful())
                        {
                            LOGGER.info("Successfully unregistered site: " + site);
                        }
                        else
                        {
                            LOGGER.warning("Unable to unregistered site: " + site + ": Site responded with status " + response.code());
                        }
                    }
                    catch (Exception e)
                    {
                        LOGGER.log(Level.SEVERE, "Failed to unregistered site: " + site, e);
                    }
                    sitesConfiguration.unregisterSite(site);
                });
    }

    @JavaScriptMethod
    public String getSiteUrl(String url)
    {
        return sitesConfiguration.findSite(URI.create(url))
                .map(site -> {
                    try (Response response = httpClient.newCall(site.createGetBaseUrlRequest())
                            .execute();
                         ResponseBody body = response.body())
                    {
                        return body.string();
                    }
                    catch (Exception e)
                    {
                        return site_get_url_failed();
                    }
                })
                .orElseGet(Messages::site_not_found);
    }

    @JavaScriptMethod
    public void refreshTunnel(String url)
    {
        sitesConfiguration.findSite(URI.create(url))
                .filter(JiraSite::isTunneled)
                .ifPresent(tunnelManager::refreshTunnel);
    }

    public String getBaseHelpUrl()
    {
        return "/plugin/" + JiraIntegrationPlugin.SHORT_NAME + "/help/";
    }

    public void doIndex(
            StaplerRequest2 req,
            StaplerResponse2 rsp)
            throws IOException, ServletException
    {
        Jenkins.get()
                .getACL()
                .checkPermission(Jenkins.ADMINISTER);

        if (req.hasParameter("url") && req.hasParameter("token"))
        {
            String url = req.getParameter("url");
            String token = req.getParameter("token");

            try
            {
                registerSiteForm(req, rsp, token, url);
            }
            catch (Exception e)
            {
                req.setAttribute("error", e.getMessage());
                req.getView(this, "index")
                        .forward(req, rsp);
            }
        }
        else
        {
            req.getView(this, "index")
                    .forward(req, rsp);
        }
    }

    @RequirePOST
    public synchronized void doAdd(
            StaplerRequest2 req,
            StaplerResponse2 rsp)
            throws IOException, ServletException
    {
        Jenkins.get()
                .getACL()
                .checkPermission(Jenkins.ADMINISTER);
        JSONObject form = req.getSubmittedForm();

        try
        {
            if (!form.containsKey("url"))
            {
                throw new IllegalArgumentException("Missing url");
            }
            String urlParam = form.getString("url");
            HttpUrl httpUrl = HttpUrl.parse(urlParam);
            if (httpUrl == null)
            {
                throw new IllegalArgumentException("Invalid URL " + urlParam);
            }
            String url = Objects.requireNonNull(httpUrl.queryParameter("url"), "Missing url parameter");
            String token = Objects.requireNonNull(httpUrl.queryParameter("token"), "Missing token parameter");

            registerSiteForm(req, rsp, token, url);
        }
        catch (Exception e)
        {
            req.setAttribute("error", e.getMessage());
            req.getView(this, "manual")
                    .forward(req, rsp);
        }
    }

    private void registerSiteForm(
            StaplerRequest2 req,
            StaplerResponse2 rsp,
            String token,
            String url)
            throws IOException, ServletException
    {
        try (Response response = httpClient.newCall(new Request.Builder().get()
                        .addHeader(Headers.SYNC_TOKEN, token)
                        // Keep setting the old header for the time being
                        .addHeader(OLD_SYNC_TOKEN_HEADER_NAME, token)
                        .url(url)
                        .build())
                .execute())
        {
            if (response.isSuccessful())
            {
                try (ResponseBody body = response.body())
                {
                    if (body != null)
                    {
                        JSONObject details = JSONObject.fromObject(body.string());

                        site = JiraSite.getSite(details);

                        req.getView(this, "add")
                                .forward(req, rsp);
                    }
                }
                catch (JSONException e)
                {
                    throw new IllegalArgumentException("Invalid JSON response: " + e.getMessage());
                }
            }
            else
            {
                throw new IllegalArgumentException(
                        "Unable to get registration details; " + response.code() + "[" + response.message() + "]");
            }
        }
    }

    public JiraSite getSite()
    {
        return site;
    }

    @RequirePOST
    public synchronized void doSubmit(
            StaplerRequest2 req,
            StaplerResponse2 rsp)
            throws IOException, ServletException
    {
        Jenkins.get()
                .getACL()
                .checkPermission(Jenkins.ADMINISTER);
        JSONObject form = req.getSubmittedForm();
        try
        {
            JiraSite site = JiraSite.getSite(form);

            try (Response response = httpClient.newCall(site.createRegisterRequest())
                    .execute())
            {
                if (response.code() != 202)
                {
                    throw new IllegalStateException(
                            "Failed to complete the registration with " + site + "; " + response.code() + "[" + response.message() + "]");
                }
            }

            sitesConfiguration.registerSite(site);

            rsp.sendRedirect(".");
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Registration of Jira site failed", e);
            req.setAttribute("error", e.getMessage());
            req.getView(this, "index")
                    .forward(req, rsp);
        }
    }

    @RequirePOST
    public synchronized void doRefresh(
            StaplerRequest2 req,
            StaplerResponse2 rsp)
            throws IOException, ServletException
    {
        Jenkins.get()
                .getACL()
                .checkPermission(Jenkins.ADMINISTER);

        sitesConfiguration.updateSiteRegistrations(httpClient);

        rsp.sendRedirect(".");
    }

    @RequirePOST
    public void doRegister(StaplerRequest2 request)
            throws IOException
    {
        SyncTokenSecurityContext securityContext = SyncTokenSecurityContext.checkSyncTokenAuthentication(request);
        Jenkins.get()
                .getACL()
                .checkPermission(Jenkins.ADMINISTER);

        JSONObject data = getJsonFromRequest(request);
        JiraSite jiraSite = JiraSite.getSite(data);

        JiraSite existingSite = securityContext.getSite();
        if (existingSite != null && Objects.equals(jiraSite.getIdentifier(), existingSite.getIdentifier()) &&
            Objects.equals(jiraSite.getSharedSecret(), existingSite.getSharedSecret()))
        {
            sitesConfiguration.registerSite(jiraSite);
        }
        else if (Objects.equals(jiraSite.getIdentifier(),
                securityContext.getClaimsSet()
                        .getIssuer()))
        {
            sitesConfiguration.registerSite(jiraSite);
        }
        else
        {
            throw new AccessDeniedException("Unauthorized Jira site registration attempt.");
        }
    }

    @RequirePOST
    @SuppressWarnings("lgtm[jenkins/no-permission-check]")
    public void doUnregister(StaplerRequest2 request)
            throws IOException
    {
        SyncTokenSecurityContext.checkSyncTokenAuthentication(request);
        URI url = URI.create(getJsonFromRequest(request).getString("url"));
        sitesConfiguration.findSite(url)
                .ifPresent(sitesConfiguration::unregisterSite);
    }
}
