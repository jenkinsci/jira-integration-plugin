package org.marvelution.jji;

import jakarta.servlet.Filter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.logging.*;

import hudson.init.*;
import hudson.util.*;

import static org.marvelution.jji.JiraIntegrationPlugin.*;

public class VersionHeaderFilter
        implements Filter
{

    private static final Logger LOGGER = Logger.getLogger(VersionHeaderFilter.class.getName());

    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void registerFilter()
    {
        VersionHeaderFilter filter = new VersionHeaderFilter();
        if (!PluginServletFilter.hasFilter(filter))
        {
            try
            {
                PluginServletFilter.addFilter(filter);
            }
            catch (ServletException e)
            {
                LOGGER.log(Level.WARNING, "Failed to set up version header servlet filter", e);
            }
        }
    }

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException
    {}

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException
    {
        if (response instanceof HttpServletResponse)
        {
            ((HttpServletResponse) response).setHeader(VERSION_HEADER, JiraIntegrationPlugin.getVersion());
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy()
    {}
}
