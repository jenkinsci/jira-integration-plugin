package org.marvelution.jji.tunnel;

import java.io.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.marvelution.jji.*;
import org.marvelution.jji.configuration.*;

import com.ngrok.*;
import jenkins.model.*;
import org.springframework.security.access.*;

public class NgrokConnector
{
    private static final Logger LOGGER = Logger.getLogger(NgrokConnector.class.getName());
    public static final String X_TUNNEL_ID = "X-Tunnel-Id";
    public static final String X_TUNNEL_TOKEN = "X-Tunnel-Token";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TunnelManager tunnelManager;
    private final JiraSite site;
    private final TunnelDetails details;
    private Session session;
    private Tunnel tunnel;
    private Future<?> runner;

    public NgrokConnector(
            TunnelManager tunnelManager,
            JiraSite site,
            TunnelDetails details)
    {
        this.tunnelManager = tunnelManager;
        this.site = site;
        this.details = details;
    }

    public void verifyTunnelToken(
            String tunnelId,
            String tunnelToken)
    {
        if (site.getIdentifier()
                .equals(tunnelId))
        {
            if (!details.token.equals(tunnelToken))
            {
                throw new AccessDeniedException("missing or invalid tunnel token");
            }
        }
    }

    public void connect()
    {
        if (runner == null)
        {
            runner = executor.submit(this::doConnect);
        }
    }

    private void doConnect()
    {
        LOGGER.info("Connecting tunnel for " + site);
        try
        {
            session = Session.connect(Session.newBuilder(details.authtoken)
                    .addUserAgent("Jenkins", Jenkins.VERSION)
                    .addUserAgent("jira-integration", JiraIntegrationPlugin.getVersion())
                    .serverAddr(details.serverAddr)
                    .stopCallback(() -> LOGGER.info(String.format("Stopping tunnel %s", details.domain)))
                    .restartCallback(() -> LOGGER.info(String.format("Restarting tunnel %s", details.domain)))
                    .updateCallback(() -> LOGGER.info(String.format("Updated tunnel %s", details.domain)))
                    .heartbeatHandler(latency -> LOGGER.fine(String.format("Tunnel %s heartbeat %d ms", details.domain, latency))));
            String forwardTo = tunnelManager.getForwardTo();
            tunnel = session.httpTunnel(new HttpTunnel.Builder().domain(details.domain)
                    .scheme(HttpTunnel.Scheme.HTTPS)
                    .addRequestHeader(X_TUNNEL_ID, site.getIdentifier())
                    .forwardsTo(forwardTo));
            tunnel.forwardTcp(forwardTo);
        }
        catch (IOException e)
        {
            LOGGER.log(Level.SEVERE, "Failed to setup the tunnel session", e);
        }
    }

    public void disconnect()
    {
        if (runner != null)
        {
            LOGGER.info("Disconnecting tunnel for " + site);
            try
            {
                runner.cancel(true);
                runner = null;
                tunnel.close();
                tunnel = null;
                session.close();
                session = null;
            }
            catch (Exception e)
            {
                LOGGER.log(Level.SEVERE, "Failed to stop tunnel session", e);
            }
        }
    }

    public void reconnect()
    {
        LOGGER.info("Reconnecting tunnel for " + site);
        disconnect();
        connect();
    }

    public void close()
    {
        disconnect();
        LOGGER.info("Closing tunnel for " + site);
        executor.shutdown();
    }
}
