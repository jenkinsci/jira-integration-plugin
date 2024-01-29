package org.marvelution.jji.tunnel;

import javax.annotation.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.logging.*;

import org.marvelution.jji.*;
import org.marvelution.jji.configuration.*;

import com.ngrok.*;
import jakarta.json.*;
import jenkins.model.*;
import org.springframework.security.access.*;

public class NgrokConnector
{
    public static final String X_TUNNEL_ID = "X-Tunnel-Id";
    public static final String X_TUNNEL_TOKEN = "X-Tunnel-Token";
    private static final Logger LOGGER = Logger.getLogger(NgrokConnector.class.getName());
    private final TunnelManager tunnelManager;
    private final JiraSite site;
    private final TunnelDetails details;
    private Thread runner;
    private Throwable connectException;
    private Session session;
    private Forwarder.Endpoint endpoint;

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
            runner = new Thread(this::doConnect);
            runner.start();
        }
    }

    private void doConnect()
    {
        LOGGER.info("Connecting tunnel for " + site);
        String metadata = Json.createObjectBuilder()
                .add("site", site.getIdentifier())
                .build()
                .toString();
        String forwardTo = tunnelManager.getForwardTo();
        try
        {
            session = sessionConnect(Session.withAuthtoken(details.authtoken)
                    .metadata(metadata)
                    .addClientInfo("Jenkins", Jenkins.VERSION)
                    .addClientInfo("jira-integration", JiraIntegrationPlugin.getVersion())
                    .serverAddr(details.serverAddr)
                    .stopCallback(() -> {
                        LOGGER.info(String.format("Stop callback received for tunnel %s", details.domain));
                        tunnelManager.disconnectTunnelForSite(site);
                    })
                    .restartCallback(() -> {
                        LOGGER.info(String.format("Restart callback received for tunnel%s", details.domain));
                        reconnect();
                    })
                    .updateCallback(() -> {
                        LOGGER.info(String.format("Update callback received for tunnel %s", details.domain));
                        reconnect();
                    })
                    .heartbeatHandler(latency -> LOGGER.fine(String.format("Tunnel %s heartbeat %d ms", details.domain, latency))));
            endpoint = session.httpEndpoint()
                    .scheme(Http.Scheme.HTTPS)
                    .addRequestHeader(X_TUNNEL_ID, site.getIdentifier())
                    .metadata(metadata)
                    .forwardsTo(forwardTo)
                    .forward(URI.create(forwardTo)
                            .toURL());
        }
        catch (Throwable e)
        {
            LOGGER.log(Level.SEVERE, "Failed to setup the tunnel session", e);
            connectException = e;
        }
    }

    private Session sessionConnect(Session.Builder builder)
            throws IOException
    {
        try
        {
            Class<?> clazz = tunnelManager.getTunnelClassLoader()
                    .loadClass("com.ngrok.NativeSession");
            Method method = clazz.getMethod("connect", Session.Builder.class);
            return (Session) method.invoke(null, builder);
        }
        catch (InvocationTargetException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof IOException)
            {
                throw (IOException) cause;
            }
            else
            {
                throw new RuntimeException(cause);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public void disconnect()
    {
        if (runner != null)
        {
            LOGGER.info("Disconnecting tunnel for " + site);
            try
            {
                session.closeForwarder(endpoint.getId());
                session.close();
                connectException = null;
                runner = null;
                endpoint = null;
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
    }

    @Nullable
    public Throwable getConnectException()
    {
        return connectException;
    }
}
