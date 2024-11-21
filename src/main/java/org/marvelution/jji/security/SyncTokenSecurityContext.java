package org.marvelution.jji.security;

import jakarta.servlet.http.*;

import org.marvelution.jji.configuration.*;

import com.nimbusds.jwt.*;
import hudson.security.*;
import org.springframework.security.access.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;

public class SyncTokenSecurityContext
		implements SecurityContext
{

	private static final String REQUEST_ATTRIBUTE = SyncTokenSecurityContext.class.getName();
	private final JWTClaimsSet claimsSet;
	private final JiraSite site;
	private Authentication authentication;

	public SyncTokenSecurityContext(
			JWTClaimsSet claimsSet,
			JiraSite site)
	{
		this.claimsSet = claimsSet;
		this.site = site;
		authentication = ACL.SYSTEM2;
	}

	public static SyncTokenSecurityContext checkSyncTokenAuthentication(HttpServletRequest request)
	{
		SecurityContext context = SecurityContextHolder.getContext();
		// JEP-227 was implemented in 2.266 and this brakes this check.
		if (context instanceof SyncTokenSecurityContext)
		{
			return (SyncTokenSecurityContext) context;
		}
		Object syncTokenSecurityContext = request.getAttribute(REQUEST_ATTRIBUTE);
		if (syncTokenSecurityContext instanceof SyncTokenSecurityContext)
		{
			return (SyncTokenSecurityContext) syncTokenSecurityContext;
		}
		throw new AccessDeniedException("Sync Token authentication required");
	}

	@Override
	public Authentication getAuthentication()
	{
		return authentication;
	}

	@Override
	public void setAuthentication(Authentication authentication)
	{
		this.authentication = authentication;
	}

	public JWTClaimsSet getClaimsSet()
	{
		return claimsSet;
	}

	public JiraSite getSite()
	{
		return site;
	}

	void attachToRequest(HttpServletRequest request)
	{
		request.setAttribute(REQUEST_ATTRIBUTE, this);
	}

	void detachFromRequest(HttpServletRequest request)
	{
		request.removeAttribute(REQUEST_ATTRIBUTE);
	}
}
