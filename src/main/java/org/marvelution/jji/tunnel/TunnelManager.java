package org.marvelution.jji.tunnel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.marvelution.jji.configuration.JiraSite;
import org.marvelution.jji.configuration.JiraSitesConfiguration;

import hudson.Extension;
import hudson.FilePath;
import hudson.Proc;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.listeners.SaveableListener;
import hudson.tools.InstallSourceProperty;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import jenkins.util.Timer;

@Extension
public class TunnelManager
        extends SaveableListener
{

    private static final Logger LOGGER = Logger.getLogger(TunnelManager.class.getName());
    private final Map<String, Proc> activeTunnels = new ConcurrentHashMap<>();

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public void startTunnels()
    {
        Jenkins.get()
                .getExtensionList(TunnelManager.class)
                .get(0)
                .verifyTunnels(JiraSitesConfiguration.get());
    }

    @Override
    public void onChange(
            Saveable o,
            XmlFile file)
    {
        if (o instanceof JiraSitesConfiguration configuration)
        {
            verifyTunnels(configuration);
        }
    }

    private void verifyTunnels(JiraSitesConfiguration configuration)
    {
        if (Computer.currentComputer() != null && !(Computer.currentComputer() instanceof jenkins.model.Jenkins.MasterComputer))
        {
            // Tunnels should only run on master.
            return;
        }

        configuration.getSites()
                .forEach(site -> {
                    if (site.isTunneled())
                    {
                        startTunnel(site);
                    }
                    else
                    {
                        stopTunnel(site);
                    }
                });

        // Cleanup any tunnels that are no longer in configuration
        activeTunnels.keySet()
                .removeIf(identifier -> {
                    boolean exists = configuration.getSites()
                            .stream()
                            .anyMatch(s -> s.getIdentifier()
                                    .equals(identifier));
                    if (!exists)
                    {
                        LOGGER.log(Level.INFO, "Stopping tunnel for removed site {0}", identifier);
                        try
                        {
                            activeTunnels.get(identifier)
                                    .kill();
                        }
                        catch (IOException | InterruptedException e)
                        {
                            LOGGER.log(Level.WARNING, "Failed to kill tunnel for removed site " + identifier, e);
                        }
                        return true;
                    }
                    return false;
                });
    }

    private synchronized void startTunnel(JiraSite site)
    {
        try
        {
            FilePath logFile = getTunnelLogFile(site);
            TaskListener log = new StreamTaskListener(logFile.write(), StandardCharsets.UTF_8);

            if (activeTunnels.containsKey(site.getIdentifier()))
            {
                Proc proc = activeTunnels.get(site.getIdentifier());
                try
                {
                    if (proc.isAlive())
                    {
                        return;
                    }
                }
                catch (IOException | InterruptedException e)
                {
                    log.getLogger()
                            .println("Failed to check if tunnel for " + site.getIdentifier() + " is alive; " + e.getMessage());
                }
                log.getLogger()
                        .println("WARN: Tunnel for " + site.getIdentifier() + " is dead, restarting");
                activeTunnels.remove(site.getIdentifier());
            }

            String token = site.getContext()
                    .optString("token");
            if (token == null || token.isEmpty())
            {
                log.getLogger()
                        .println("No token found in context for tunneled site " + site.getIdentifier());
                return;
            }

            Node master = Jenkins.get();
            CloudflareClientInstallation installation = getInstallation();
            if (installation == null)
            {
                log.getLogger()
                        .println("No Cloudflare Client installation found, creating default one");
                installation = createDefaultInstallation();
            }

            if (installation == null)
            {
                log.getLogger()
                        .println("FATAL: Failed to find or create Cloudflare Client installation");
                return;
            }

            installation = installation.forNode(master, log);
            FilePath executable = installation.getExecutable(master, log);
            if (executable == null || !executable.exists())
            {
                log.getLogger()
                        .println("FATAL: Cloudflare Client executable not found for site " + site.getIdentifier());
                return;
            }

            ArgumentListBuilder args = new ArgumentListBuilder();
            args.add(executable.getRemote());
            args.add("tunnel");
            args.add("--no-autoupdate");
            args.add("run");
            args.add("--token", token);

            Objects.requireNonNull(logFile.getParent(), "Parent directory for log file cannot be null")
                    .mkdirs();

            log.getLogger()
                    .println("Starting tunnel for site " + site.getIdentifier());
            Proc proc = master.createLauncher(TaskListener.NULL)
                    .launch()
                    .cmds(args)
                    .stdout(log)
                    .stderr(log.getLogger())
                    .start();
            activeTunnels.put(site.getIdentifier(), proc);
            Timer.get()
                    .submit(() -> {
                        try
                        {
                            int exitCode = proc.join();
                            log.getLogger()
                                    .println("Tunnel for site " + site.getIdentifier() + " stopped with exit code " + exitCode);
                        }
                        catch (IOException | InterruptedException e)
                        {
                            log.getLogger()
                                    .println("Failed to exit tunnel process for site " + site.getIdentifier() + "; " + e.getMessage());
                        }
                        finally
                        {
                            log.getLogger()
                                    .close();
                        }
                    });
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to start tunnel for site " + site.getIdentifier(), e);
        }
    }

    private synchronized void stopTunnel(JiraSite site)
    {
        Proc proc = activeTunnels.remove(site.getIdentifier());
        if (proc != null)
        {
            LOGGER.log(Level.INFO, "Stopping tunnel for site {0}", site.getIdentifier());
            try
            {
                proc.kill();
            }
            catch (IOException | InterruptedException e)
            {
                LOGGER.log(Level.WARNING, "Failed to kill tunnel for site " + site.getIdentifier(), e);
            }
        }
    }

    public synchronized void restartTunnel(JiraSite site)
    {
        if (site.isTunneled())
        {
            stopTunnel(site);
            startTunnel(site);
        }
    }

    private CloudflareClientInstallation getInstallation()
    {
        CloudflareClientInstallation[] installations = Jenkins.get()
                .getDescriptorByType(CloudflareClientInstallation.DescriptorImpl.class)
                .getInstallations();
        if (installations.length > 0)
        {
            return installations[0];
        }
        return null;
    }

    private CloudflareClientInstallation createDefaultInstallation()
    {
        try
        {
            CloudflareClientInstallation.DescriptorImpl descriptor = Jenkins.get()
                    .getDescriptorByType(CloudflareClientInstallation.DescriptorImpl.class);

            CloudflareClientInstaller installer = new CloudflareClientInstaller();
            try
            {
                hudson.tools.DownloadFromUrlInstaller.Installable list = installer.getInstallable();
                if (list != null)
                {
                    installer = new CloudflareClientInstaller(list.id);
                }
            }
            catch (IOException e)
            {
                LOGGER.log(Level.FINE, "Failed to get latest installable ID, using default", e);
            }

            InstallSourceProperty property = new InstallSourceProperty(Collections.singletonList(installer));
            CloudflareClientInstallation installation = new CloudflareClientInstallation(installer.id,
                    null,
                    Collections.singletonList(property));

            descriptor.setInstallations(installation);
            return installation;
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to create default Cloudflare Client installation", e);
            return null;
        }
    }

    public static FilePath getTunnelLogFile(JiraSite site)
    {
        return new FilePath(Jenkins.get()
                .getRootDir()).child("logs")
                .child("tunnels")
                .child(site.getIdentifier() + ".log");
    }
}
