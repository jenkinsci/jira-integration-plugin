package org.marvelution.jji.tunnel;

import javax.inject.*;
import javax.servlet.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.marvelution.jji.*;
import org.marvelution.jji.configuration.*;

import com.fasterxml.jackson.databind.*;
import hudson.*;
import hudson.init.*;
import hudson.model.*;
import hudson.model.listeners.*;
import hudson.util.*;
import jenkins.model.*;
import okhttp3.*;
import org.springframework.security.access.AccessDeniedException;

@Extension
public class TunnelManager
        extends SaveableListener
{
    private static final Logger LOGGER = Logger.getLogger(TunnelManager.class.getName());
    private static final String UNKNOWN = "unknown";
    private final ExecutorService tunnelExecutor = Executors.newCachedThreadPool();
    private final Map<String, NgrokConnector> tunnels = new ConcurrentHashMap<>();
    private boolean loadedNgrokNative = false;
    private boolean loadedTunnelFilter = false;
    private String forwardTo;
    private OkHttpClient httpClient;
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
            String libName = "ngrok/ngrok-java-native-" + os + "-" + arch + ".jar";
            URL library = wrapper.classLoader.getResource(libName);

            if (library != null)
            {
                try
                {
                    LOGGER.fine("Adding " + library + " to plugin classpath");
                    wrapper.injectJarsToClasspath(Paths.get(library.toURI())
                            .toFile());
                    loadedNgrokNative = true;
                }
                catch (Exception e)
                {
                    LOGGER.warning("Disabling tunneling, unable to add ngrok-java-native library to plugin classpath: " + library);
                }
            }
            else
            {
                LOGGER.warning("Disabling tunneling, unable to load required ngrok-java-native library: " + libName);
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
        if (loadedNgrokNative)
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
        if (loadedNgrokNative && loadedTunnelFilter)
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
            LOGGER.log(Level.WARNING, "Not connect any required tunnels, required libraries are not loaded.");
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
            if (loadedNgrokNative && loadedTunnelFilter)
            {
                tunnels.computeIfAbsent(site.getIdentifier(), id -> {
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
            else if (!loadedNgrokNative)
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

    private void stopTunneling()
    {
        LOGGER.info("Disconnecting and Closing all tunnels");
        tunnels.forEach((id, connector) -> connector.close());
        tunnels.clear();
    }

    @Inject
    public void setHttpClient(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    @Inject
    public void setObjectMapper(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }
}
