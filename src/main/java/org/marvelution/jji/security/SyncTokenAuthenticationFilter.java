package org.marvelution.jji.security;

import javax.servlet.Filter;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;

import org.marvelution.jji.configuration.*;
import org.marvelution.jji.management.*;
import org.marvelution.jji.synctoken.*;
import org.marvelution.jji.synctoken.exceptions.*;

import com.nimbusds.jwt.*;
import hudson.init.*;
import hudson.util.*;
import org.jenkinsci.plugins.plaincredentials.*;
import org.springframework.security.access.*;
import org.springframework.security.core.context.*;

public class SyncTokenAuthenticationFilter
        implements Filter
{

    private static final Logger LOGGER = Logger.getLogger(SyncTokenAuthenticationFilter.class.getName());
    private static final String FILTER_APPLIED = SyncTokenAuthenticationFilter.class.getName();
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
        tokenAuthenticator = new SyncTokenAuthenticator(issuer -> this.sitesConfiguration.stream()
                                                                                         .filter(site -> Objects.equals(issuer,
                                                                                                 site.getIdentifier()))
                                                                                         .findFirst()
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
    public void init(FilterConfig filterConfig)
    {}

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException
    {
        if (!(request instanceof HttpServletRequest))
        {
            chain.doFilter(request, response);
            return;
        }

        // ensure that filter is only applied once per request
        if (request.getAttribute(FILTER_APPLIED) != null)
        {
            chain.doFilter(request, response);
            return;
        }

        request.setAttribute(FILTER_APPLIED, Boolean.TRUE);

        try
        {
            JWTClaimsSet claimsSet = tokenAuthenticator.authenticate((HttpServletRequest) request);
            LOGGER.log(Level.FINE, "Authenticated {0} through Sync Token.", claimsSet.getIssuer());
            JiraSite site = sitesConfiguration.stream()
                                              .filter(s -> Objects.equals(s.getIdentifier(), claimsSet.getIssuer()))
                                              .findFirst()
                                              .orElseThrow(() -> new IllegalStateException(
                                                      "Authenticated by sync-token but unable to find a Jira site for it."));

            LOGGER.log(Level.FINE, "Forwarding request with SYSTEM authentication for {0}.", site.getName());

            doFilter(new SyncTokenSecurityContext(claimsSet, site), (HttpServletRequest) request, response, chain);
        }
        catch (UnknownSyncTokenIssuerException e)
        {
            String pathInfo = ((HttpServletRequest) request).getPathInfo();

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
                doFilter(tokenSecurityContext, (HttpServletRequest) request, response, chain);
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
        finally
        {
            request.removeAttribute(FILTER_APPLIED);
        }
    }

    @Override
    public void destroy()
    {}

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
