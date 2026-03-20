package org.marvelution.jji.tunnel;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
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
                LOGGER.log(Level.WARNING, "Failed to check if tunnel for " + site.getIdentifier() + " is alive", e);
            }
            LOGGER.log(Level.INFO, "Tunnel for {0} is dead, restarting", site.getIdentifier());
            activeTunnels.remove(site.getIdentifier());
        }

        String token = site.getContext()
                .optString("token");
        if (token == null || token.isEmpty())
        {
            LOGGER.log(Level.WARNING, "No token found in context for tunneled site {0}", site.getIdentifier());
            return;
        }

        try
        {
            Node master = Jenkins.get();
            CloudflareClientInstallation installation = getInstallation();
            if (installation == null)
            {
                LOGGER.log(Level.INFO, "No Cloudflare Client installation found, creating default one");
                installation = createDefaultInstallation();
            }

            if (installation == null)
            {
                LOGGER.log(Level.SEVERE, "Failed to find or create Cloudflare Client installation");
                return;
            }

            installation = installation.forNode(master, TaskListener.NULL);
            FilePath executable = installation.getExecutable(master, TaskListener.NULL);
            if (executable == null || !executable.exists())
            {
                LOGGER.log(Level.SEVERE, "Cloudflare Client executable not found for site {0}", site.getIdentifier());
                return;
            }

            ArgumentListBuilder args = new ArgumentListBuilder();
            args.add(executable.getRemote());
            args.add("tunnel");
            args.add("--no-autoupdate");
            args.add("run");
            args.add("--token", token);

            FilePath logFile = getTunnelLogFile(site);
            logFile.getParent().mkdirs();

            LOGGER.log(Level.INFO, "Starting tunnel for site {0}", site.getIdentifier());
            OutputStream output = logFile.write();
            Proc proc = master.createLauncher(TaskListener.NULL)
                    .launch()
                    .cmds(args)
                    .stdout(output)
                    .stderr(output)
                    .start();
            activeTunnels.put(site.getIdentifier(), proc);
            Timer.get().submit(() -> {
                try {
                    int exitCode = proc.join();
                    LOGGER.log(Level.INFO, "Tunnel for site {0} stopped with exit code {1}", new Object[]{site.getIdentifier(), exitCode});
                } catch (IOException | InterruptedException e) {
                    LOGGER.log(Level.WARNING, "Failed to join tunnel for site " + site.getIdentifier(), e);
                } finally {
                    try {
                        output.close();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to close tunnel log for site " + site.getIdentifier(), e);
                    }
                }
            });
        }
        catch (Exception e)
        {
            LOGGER.log(Level.SEVERE, "Failed to start tunnel for site " + site.getIdentifier(), e);
        }
    }

    public static FilePath getTunnelLogFile(JiraSite site)
    {
        return new FilePath(Jenkins.get().getRootDir()).child("logs").child("tunnels").child(site.getIdentifier() + ".log");
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

            CloudflareClientInstaller installer = new CloudflareClientInstaller("2026.3.0");
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
}
