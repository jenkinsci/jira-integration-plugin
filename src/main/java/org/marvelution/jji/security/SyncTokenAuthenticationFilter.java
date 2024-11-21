package org.marvelution.jji.security;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.marvelution.jji.configuration.JiraSite;
import org.marvelution.jji.configuration.JiraSitesConfiguration;
import org.marvelution.jji.management.JiraSiteManagement;
import org.marvelution.jji.synctoken.SyncTokenAuthenticator;
import org.marvelution.jji.synctoken.SyncTokenRequest;
import org.marvelution.jji.synctoken.exceptions.SyncTokenRequiredException;
import org.marvelution.jji.synctoken.exceptions.UnknownSyncTokenIssuerException;

import com.nimbusds.jwt.JWTClaimsSet;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.PluginServletFilter;
import hudson.util.Secret;
import io.jenkins.servlet.http.HttpServletRequestWrapper;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SyncTokenAuthenticationFilter
        extends OncePerRequestFilter
{

    private static final Logger LOGGER = Logger.getLogger(SyncTokenAuthenticationFilter.class.getName());
    private static final String REGISTER_PATH = "/" + JiraSiteManagement.URL_NAME + "/register/";
    private final JiraSitesConfiguration sitesConfiguration;
    private final SyncTokenAuthenticator tokenAuthenticator;

    public SyncTokenAuthenticationFilter()
    {
        this(JiraSitesConfiguration.get());
    }

    public SyncTokenAuthenticationFilter(JiraSitesConfiguration sitesConfiguration)
    {
        this.sitesConfiguration = sitesConfiguration;
        tokenAuthenticator = new SyncTokenAuthenticator(issuer -> this.sitesConfiguration.findSite(issuer)
                .map(site -> site.getSharedSecretCredentials()
                        .map(StringCredentials::getSecret)
                        .map(Secret::getPlainText)
                        .orElseGet(site::getSharedSecret)));
    }

    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void registerFilter()
    {
        SyncTokenAuthenticationFilter filter = new SyncTokenAuthenticationFilter();
        if (!PluginServletFilter.hasFilter(filter))
        {
            try
            {
                PluginServletFilter.addFilter(filter);
            }
            catch (ServletException e)
            {
                LOGGER.log(Level.WARNING, "Failed to set up sync-token authentication servlet filter", e);
            }
        }
    }

    @Override
    protected String getFilterName()
    {
        return getClass().getName();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException
    {
        try
        {
            JWTClaimsSet claimsSet = tokenAuthenticator.authenticate(new SyncTokenRequest(HttpServletRequestWrapper.fromJakartaHttpServletRequest(request)));
            LOGGER.log(Level.FINE, "Authenticated {0} through Sync Token.", claimsSet.getIssuer());
            JiraSite site = sitesConfiguration.findSite(claimsSet.getIssuer())
                    .orElseThrow(() -> new IllegalStateException("Authenticated by sync-token but unable to find a Jira site for it."));

            LOGGER.log(Level.FINE, "Forwarding request with SYSTEM authentication for {0}.", site.getName());

            doFilter(new SyncTokenSecurityContext(claimsSet, site), request, response, chain);
        }
        catch (UnknownSyncTokenIssuerException e)
        {
            String pathInfo = request.getPathInfo();

            int lastSlashIndex = pathInfo.lastIndexOf('/');
            int lastDotIndex = pathInfo.lastIndexOf('.');

            pathInfo = pathInfo.endsWith("/") || lastDotIndex > lastSlashIndex ? pathInfo : (pathInfo + '/');

            if (REGISTER_PATH.equals(pathInfo))
            {
                LOGGER.log(Level.FINE,
                        "Allowing request to {0} for unverified site {1}",
                        new Object[]{pathInfo,
                                e.getUnverifiedClaims().getIssuer()});
                SyncTokenSecurityContext tokenSecurityContext = new SyncTokenSecurityContext(e.getUnverifiedClaims(), null);
                tokenSecurityContext.setAuthentication(SecurityContextHolder.getContext()
                        .getAuthentication());
                doFilter(tokenSecurityContext, request, response, chain);
            }
            else
            {
                LOGGER.log(Level.FINE,
                        "Unknown sync token issuer, forwarding request through the chain for others to handle authentication.");
                chain.doFilter(request, response);
            }
        }
        catch (SyncTokenRequiredException e)
        {
            LOGGER.log(Level.FINE, "No sync token found, forwarding request through the chain for others to handle authentication.");
            chain.doFilter(request, response);
        }
        catch (SecurityException e)
        {
            throw new AccessDeniedException("invalid sync token", e);
        }
    }

    private void doFilter(
            SyncTokenSecurityContext securityContext,
            HttpServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException
    {
        SecurityContext oldContext = SecurityContextHolder.getContext();
        try
        {
            SecurityContextHolder.setContext(securityContext);
            securityContext.attachToRequest(request);

            chain.doFilter(request, response);
        }
        finally
        {
            SecurityContextHolder.setContext(oldContext);
            securityContext.detachFromRequest(request);
        }
    }
}
