package org.marvelution.jji.management;

import javax.annotation.*;
import javax.inject.*;
import javax.servlet.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

import org.marvelution.jji.*;
import org.marvelution.jji.Headers;
import org.marvelution.jji.configuration.*;
import org.marvelution.jji.security.*;
import org.marvelution.jji.tunnel.*;

import com.fasterxml.jackson.databind.*;
import hudson.*;
import hudson.model.*;
import jenkins.model.*;
import net.sf.json.*;
import okhttp3.*;
import okhttp3.Request;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.bind.*;
import org.kohsuke.stapler.interceptor.*;
import org.springframework.security.access.*;

import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;
import static org.marvelution.jji.JiraUtils.*;
import static org.marvelution.jji.Messages.*;
import static org.marvelution.jji.synctoken.SyncTokenAuthenticator.*;

@Extension(ordinal = Integer.MAX_VALUE - 500)
public class JiraSiteManagement
        extends ManagementLink
{

    public static final String URL_NAME = "jji";
    private static final Logger LOGGER = Logger.getLogger(JiraSiteManagement.class.getName());
    private JiraSitesConfiguration sitesConfiguration;
    private TunnelManager tunnelManager;
    private OkHttpClient httpClient;
    private ObjectMapper objectMapper;
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

    @Inject
    public void setObjectMapper(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
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
        sitesConfiguration.unregisterSite(URI.create(uri));
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
                        throw HttpResponses.errorWithoutStack(500, site_get_url_failed());
                    }
                })
                .orElseThrow(() -> HttpResponses.errorWithoutStack(404, site_not_found()));
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
            StaplerRequest req,
            StaplerResponse rsp)
            throws IOException, ServletException
    {
        Jenkins.get()
                .getACL()
                .checkPermission(Jenkins.ADMINISTER);

        if (req.hasParameter("url") && req.hasParameter("token"))
        {
            String url = req.getParameter("url");
            String token = req.getParameter("token");

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
                            JsonNode details = objectMapper.readTree(body.bytes());

                            site = new JiraSite(URI.create(details.get("url")
                                    .asText())).withIdentifier(details.get("identifier")
                                            .asText())
                                    .withName(details.get("name")
                                            .asText())
                                    .withSharedSecret(details.get("sharedSecret")
                                            .asText())
                                    .withPostJson(details.get("firewalled")
                                            .asBoolean())
                                    .withTunneled(details.get("tunneled")
                                            .asBoolean());

                            req.getView(this, "add")
                                    .forward(req, rsp);
                        }
                    }
                }
                else
                {
                    req.setAttribute("error", "Unable to get registration details; " + response.code() + "[" + response.message() + "]");
                }
            }
        }
        else
        {
            req.getView(this, "index")
                    .forward(req, rsp);
        }
    }

    public JiraSite getSite()
    {
        return site;
    }

    @RequirePOST
    public synchronized void doSubmit(
            StaplerRequest req,
            StaplerResponse rsp)
            throws IOException, ServletException
    {
        Jenkins.get()
                .getACL()
                .checkPermission(Jenkins.ADMINISTER);
        JSONObject form = req.getSubmittedForm();
        try
        {
            JiraSite site = new JiraSite(URI.create(form.getString("url"))).withIdentifier(form.getString("identifier"))
                    .withName(form.getString("name"))
                    .withSharedSecret(form.getString("sharedSecret"))
                    .withPostJson(form.getBoolean("firewalled"))
                    .withTunneled(form.getBoolean("tunneled"));

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
    public void doRegister(StaplerRequest request)
            throws IOException
    {
        SyncTokenSecurityContext securityContext = SyncTokenSecurityContext.checkSyncTokenAuthentication(request);
        Jenkins.get()
                .getACL()
                .checkPermission(Jenkins.ADMINISTER);

        JSONObject data = getJsonFromRequest(request);
        JiraSite jiraSite = new JiraSite(URI.create(data.getString("url"))).withName(data.getString("name"))
                .withIdentifier(data.getString("identifier"))
                .withSharedSecret(data.getString("sharedSecret"))
                .withPostJson(data.optBoolean("firewalled", false));

        JiraSite existingSite = securityContext.getSite();
        if (existingSite != null && Objects.equals(jiraSite.getIdentifier(), existingSite.getIdentifier()) &&
            Objects.equals(jiraSite.getSharedSecret(), existingSite.getSharedSecret()))
        {
            JiraSitesConfiguration.get()
                    .registerSite(jiraSite);
        }
        else if (Objects.equals(jiraSite.getIdentifier(),
                securityContext.getClaimsSet()
                        .getIssuer()))
        {
            JiraSitesConfiguration.get()
                    .registerSite(jiraSite);
        }
        else
        {
            throw new AccessDeniedException("Unauthorized Jira site registration attempt.");
        }
    }

    @RequirePOST
    @SuppressWarnings("lgtm[jenkins/no-permission-check]")
    public void doUnregister(StaplerRequest request)
            throws IOException
    {
        SyncTokenSecurityContext.checkSyncTokenAuthentication(request);
        JiraSitesConfiguration.get()
                .unregisterSite(URI.create(getJsonFromRequest(request).getString("url")));
    }
}
