package org.marvelution.jji.tunnel;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;

import org.springframework.security.access.*;
import org.springframework.web.filter.*;

import static org.marvelution.jji.tunnel.NgrokConnector.*;

public class TunnelAuthenticationFilter
        extends OncePerRequestFilter
{

    private final TunnelManager tunnelManager;

    public TunnelAuthenticationFilter(TunnelManager tunnelManager)
    {
        this.tunnelManager = tunnelManager;
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
        String tunnelId = request.getHeader(X_TUNNEL_ID);
        if (tunnelId != null)
        {
            String tunnelToken = request.getHeader(X_TUNNEL_TOKEN);
            try
            {
                tunnelManager.verifyTunnelToken(tunnelId, tunnelToken);
            }
            catch (AccessDeniedException e)
            {
                response.sendError(401, e.getMessage());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
