package org.marvelution.jji.security.csrf;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.marvelution.jji.management.*;

import hudson.*;
import hudson.security.csrf.*;
import org.apache.commons.lang.*;

@Extension
public class JiraIntegrationCrumbExclusion
		extends CrumbExclusion
{

	@Override
	public boolean process(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain chain)
			throws IOException, ServletException
	{
		String pathInfo = request.getPathInfo();
		if (StringUtils.isBlank(pathInfo))
		{
			return false;
		}

		pathInfo = pathInfo.endsWith("/") ? pathInfo : (pathInfo + '/');

		if (pathInfo.matches(".*/" + JiraSiteManagement.URL_NAME + "/(register|unregister|build)/"))
		{
			chain.doFilter(request, response);
			return true;
		}

		return false;
	}
}
