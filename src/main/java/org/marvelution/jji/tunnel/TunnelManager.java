package org.marvelution.jji.tunnel;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.servlet.ServletException;

import org.marvelution.jji.JiraIntegrationPlugin;
import org.marvelution.jji.Messages;
import org.marvelution.jji.configuration.JiraSite;
import org.marvelution.jji.configuration.JiraSitesConfiguration;
import org.marvelution.jji.rest.HttpClientProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.PluginServletFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.security.access.AccessDeniedException;

@Extension
public class TunnelManager
        extends SaveableListener
{
    private static final Logger LOGGER = Logger.getLogger(TunnelManager.class.getName());
    private static final String UNKNOWN = "unknown";
    private final Map<String, NgrokConnector> tunnels = new ConcurrentHashMap<>();
    private ClassLoader tunnelClassLoader;
    private boolean loadedTunnelFilter = false;
    private String forwardTo;
    private ObjectMapper objectMapper;

    @Override
    public void onChange(
            Saveable o,
            XmlFile file)
    {
        if (o instanceof JiraSitesConfiguration)
        {
            LOGGER.info("Jira Sites configuration change detected, connecting new tunnels and disconnecting old once.");
            Set<JiraSite> sites = ((JiraSitesConfiguration) o).getSites();

            sites.forEach(this::connectTunnelIfNeeded);

            for (Iterator<Map.Entry<String, NgrokConnector>> it = tunnels.entrySet()
                    .iterator(); it.hasNext(); )
            {
                Map.Entry<String, NgrokConnector> entry = it.next();
                if (sites.stream()
                        .noneMatch(site -> Objects.equals(site.getIdentifier(), entry.getKey())))
                {
                    entry.getValue()
                            .close();
                    it.remove();
                }
            }
        }
        else if (o instanceof JenkinsLocationConfiguration)
        {
            String newForwardTo = getForwardTo((JenkinsLocationConfiguration) o);
            if (!Objects.equals(forwardTo, newForwardTo))
            {
                LOGGER.info("Jenkins url change detected, reconnecting existing tunnels.");
                forwardTo = newForwardTo;
                tunnels.values()
                        .forEach(NgrokConnector::reconnect);
            }
        }
    }

    public void verifyTunnelToken(
            String tunnelId,
            String tunnelToken)
    {
        NgrokConnector connector = tunnels.get(tunnelId);
        if (connector == null)
        {
            throw new AccessDeniedException("unknown tunnel id");
        }
        connector.verifyTunnelToken(tunnelId, tunnelToken);
    }

    public String getForwardTo()
    {
        if (forwardTo == null)
        {
            this.forwardTo = getForwardTo(JenkinsLocationConfiguration.get());
        }
        return forwardTo;
    }

    private String getForwardTo(JenkinsLocationConfiguration configuration)
    {
        URI rootUrl = URI.create(Optional.ofNullable(configuration.getUrl())
                .orElse("http://localhost:8080/"));
        String forwardTo = rootUrl.getHost() + ":";
        if (rootUrl.getPort() != -1)
        {
            forwardTo += rootUrl.getPort();
        }
        else if ("https".equals(rootUrl.getScheme()))
        {
            forwardTo += "443";
        }
        else
        {
            forwardTo += "80";
        }
        return forwardTo;
    }

    public ClassLoader getTunnelClassLoader()
    {
        return tunnelClassLoader;
    }

    @Initializer(after = InitMilestone.PLUGINS_PREPARED)
    public void loadNgrokNativeLibrary()
    {
        Plugin plugin = Jenkins.get()
                .getPlugin(JiraIntegrationPlugin.SHORT_NAME);
        if (plugin != null)
        {
            PluginWrapper wrapper = plugin.getWrapper();
            String os = normalizeOs(System.getProperty("os.name"));
            String arch = normalizeArch(System.getProperty("os.arch"));

            String libraryFile = "WEB-INF/ngrok/ngrok-java-native-" + os + "-" + arch + ".jar";
            try
            {
                URL library = new URL(wrapper.baseResourceURL, libraryFile);
                LOGGER.info("Created classloader to support tunneling using " + library);
                tunnelClassLoader = new URLClassLoader(new URL[]{library}, wrapper.classLoader);
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING,
                        "Disabling tunneling, unable to add ngrok-java-native library to plugin classpath: " + libraryFile,
                        e);
            }
        }
        else
        {
            throw new IllegalStateException("Unable to locate plugin " + JiraIntegrationPlugin.SHORT_NAME);
        }
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED)
    public void addTunnelAuthenticationFilter()
    {
        if (tunnelClassLoader != null)
        {
            LOGGER.log(Level.INFO, "Adding tunnel authentication filter");
            TunnelAuthenticationFilter filter = new TunnelAuthenticationFilter(this);
            if (!PluginServletFilter.hasFilter(filter))
            {
                try
                {
                    PluginServletFilter.addFilter(filter);
                    loadedTunnelFilter = true;
                }
                catch (ServletException e)
                {
                    LOGGER.log(Level.WARNING, "Failed to set up tunnel authentication servlet filter", e);
                }
            }
        }
        else
        {
            LOGGER.log(Level.WARNING, "Skipping set up of tunnel authentication servlet filter, required libraries are not loaded.");
        }
    }

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public void connectRequiredTunnels()
    {
        if (tunnelClassLoader != null && loadedTunnelFilter)
        {
            LOGGER.log(Level.INFO, "Connecting any required tunnels");
            JiraSitesConfiguration.get()
                    .getSites()
                    .forEach(this::connectTunnelIfNeeded);

            Runtime.getRuntime()
                    .addShutdownHook(new Thread(this::stopTunneling));
        }
        else
        {
            LOGGER.log(Level.WARNING, "Not connecting any required tunnels, required libraries are not loaded.");
        }
    }

    public void disconnectTunnelForSite(JiraSite site)
    {
        NgrokConnector connector = tunnels.remove(site.getIdentifier());
        if (connector != null)
        {
            connector.close();
        }
    }

    public void connectTunnelIfNeeded(JiraSite site)
    {
        if (site.isTunneled())
        {
            if (tunnelClassLoader != null && loadedTunnelFilter)
            {
                tunnels.computeIfAbsent(site.getIdentifier(), id -> {
                    OkHttpClient httpClient = HttpClientProvider.httpClient();
                    try (Response response = httpClient.newCall(site.createGetTunnelDetailsRequest())
                            .execute();
                         ResponseBody body = response.body())
                    {
                        if (response.isSuccessful() && body != null)
                        {
                            TunnelDetails details = objectMapper.readValue(body.byteStream(), TunnelDetails.class);
                            NgrokConnector connector = new NgrokConnector(this, site, details);
                            connector.connect();
                            return connector;
                        }
                        else
                        {
                            LOGGER.severe(String.format("Failed to load tunnel details for %s [%d]", site, response.code()));
                        }
                    }
                    catch (IOException e)
                    {
                        LOGGER.log(Level.SEVERE, String.format("Failed to load tunnel details for %s", site), e);
                    }
                    return null;
                });
            }
            else if (tunnelClassLoader == null)
            {
                LOGGER.log(Level.WARNING, "Cannot connect tunnel for " + site + ", required libraries are not loaded.");
            }
            else
            {
                LOGGER.log(Level.WARNING, "Cannot connect tunnel for " + site + ", required security filter is not loaded.");
            }
        }
    }

    public void refreshTunnel(JiraSite site)
    {
        disconnectTunnelForSite(site);
        connectTunnelIfNeeded(site);
    }

    @Nullable
    public String getSiteConnectionError(JiraSite site)
    {
        if (site.isTunneled())
        {
            NgrokConnector connector = tunnels.get(site.getIdentifier());
            if (connector == null)
            {
                return Messages.site_tunnel_not_connected();
            }
            else
            {
                return Optional.ofNullable(connector.getConnectException())
                        .map(error -> {
                            if (isUnsatisfiedLinkError(error))
                            {
                                return Messages.site_tunnel_unsatisfied_link_error();
                            }
                            else
                            {
                                return error.getMessage();
                            }
                        })
                        .orElse(null);
            }
        }
        else
        {
            return null;
        }
    }

    private boolean isUnsatisfiedLinkError(Throwable error)
    {
        if (error instanceof UnsatisfiedLinkError)
        {
            return true;
        }
        else if (error.getCause() != null)
        {
            return isUnsatisfiedLinkError(error.getCause());
        }
        else
        {
            return false;
        }
    }

    private void stopTunneling()
    {
        LOGGER.info("Disconnecting and Closing all tunnels");
        tunnels.forEach((id, connector) -> connector.close());
        tunnels.clear();
    }

    @Inject
    public void setObjectMapper(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    private static String normalizeOs(String value)
    {
        value = normalize(value);
        if (value.startsWith("linux"))
        {
            return "linux";
        }
        else if (value.startsWith("mac") || value.startsWith("osx"))
        {
            return "osx";
        }
        else if (value.startsWith("windows"))
        {
            return "windows";
        }
        else
        {
            return UNKNOWN;
        }
    }

    private static String normalizeArch(String value)
    {
        value = normalize(value);
        if (value.matches("^(x8664|amd64|ia32e|em64t|x64)$"))
        {
            return "x86_64";
        }
        else if (value.matches("^(x8632|x86|i[3-6]86|ia32|x32)$"))
        {
            return "x86_32";
        }
        else if ("aarch64".equals(value))
        {
            return "aarch_64";
        }
        else
        {
            return UNKNOWN;
        }
    }

    private static String normalize(final String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "");
    }
}
