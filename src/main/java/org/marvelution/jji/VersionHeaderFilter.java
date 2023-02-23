package org.marvelution.jji;

import java.io.*;
import java.util.logging.*;
import javax.servlet.Filter;
import javax.servlet.*;
import javax.servlet.http.*;

import hudson.*;
import hudson.init.*;
import hudson.util.*;
import jenkins.model.*;

import static org.marvelution.jji.JiraPlugin.*;

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
			Plugin plugin = Jenkins.get().getPlugin(SHORT_NAME);
			if (plugin == null)
			{
				LOGGER.log(Level.WARNING, "Unable to locate plugin " + SHORT_NAME);
			}
			else
			{
				((HttpServletResponse) response).setHeader(VERSION_HEADER, plugin.getWrapper().getVersion());
			}
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy()
	{}
}
