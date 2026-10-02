package org.marvelution.jji.tunnel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeSpecific;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.VersionNumber;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class CloudflareClientInstallation
        extends ToolInstallation
        implements NodeSpecific<CloudflareClientInstallation>
{

    static final String DEFAULT_VERSION = "2026.9.3";

    @DataBoundConstructor
    public CloudflareClientInstallation(
            String name,
            String home,
            List<? extends ToolProperty<?>> properties)
    {
        super(name, home, properties);
    }

    @Override
    public CloudflareClientInstallation forNode(
            Node node,
            TaskListener log)
            throws IOException, InterruptedException
    {
        return new CloudflareClientInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    public FilePath getExecutable(
            Node node,
            TaskListener log)
            throws IOException, InterruptedException
    {
        FilePath homePath = node.createPath(getHome());
        if (homePath == null)
        {
            return null;
        }
        VirtualChannel channel = node.getChannel();
        boolean isWindows = channel != null && channel.call(new GetIsWindows());
        FilePath binary = homePath.child(isWindows ? "cloudflared.exe" : "cloudflared");
        log.getLogger()
                .println("Using " + binary);
        return binary;
    }

    private static final class GetIsWindows
            extends MasterToSlaveCallable<Boolean, IOException>
    {
        @Override
        public Boolean call()
        {
            return java.io.File.pathSeparatorChar == ';';
        }
    }

    @Extension
    @Symbol("cloudflare")
    public static final class DescriptorImpl
            extends ToolDescriptor<CloudflareClientInstallation>
    {

        @Override
        public String getDisplayName()
        {
            return "Cloudflare Client";
        }

        @Override
        public CloudflareClientInstallation[] getInstallations()
        {
            // Need to implement this if we want to store multiple installations
            return super.getInstallations();
        }

        @Override
        public void setInstallations(CloudflareClientInstallation... installations)
        {
            super.setInstallations(installations);
        }

        @Override
        public List<? extends hudson.tools.ToolInstaller> getDefaultInstallers()
        {
            return java.util.Collections.singletonList(new CloudflareClientInstaller(DEFAULT_VERSION));
        }

        /**
         * Upgrades every installation that is installed from cloudflare.com with a version older than {@link #DEFAULT_VERSION}.
         * Installations that point to a manually managed home (no {@link CloudflareClientInstaller}) are left untouched.
         *
         * @return {@code true} if at least one installation was upgraded and the configuration was saved.
         */
        public boolean upgradeOutdatedInstallations(TaskListener log)
        {
            CloudflareClientInstallation[] installations = getInstallations();
            boolean upgraded = false;
            for (int i = 0; i < installations.length; i++)
            {
                CloudflareClientInstallation upgradedInstallation = upgrade(installations[i], log);
                if (upgradedInstallation != null)
                {
                    installations[i] = upgradedInstallation;
                    upgraded = true;
                }
            }
            if (upgraded)
            {
                setInstallations(installations);
                save();
            }
            return upgraded;
        }

        private static CloudflareClientInstallation upgrade(
                CloudflareClientInstallation installation,
                TaskListener log)
        {
            VersionNumber latest = new VersionNumber(DEFAULT_VERSION);
            String outdatedId = null;
            List<ToolProperty<?>> properties = new ArrayList<>();
            for (ToolProperty<?> property : installation.getProperties())
            {
                if (property instanceof InstallSourceProperty sourceProperty)
                {
                    List<ToolInstaller> installers = new ArrayList<>();
                    for (ToolInstaller installer : sourceProperty.installers)
                    {
                        if (installer instanceof CloudflareClientInstaller cloudflareInstaller &&
                            new VersionNumber(cloudflareInstaller.id).isOlderThan(latest))
                        {
                            outdatedId = cloudflareInstaller.id;
                            installers.add(new CloudflareClientInstaller(DEFAULT_VERSION));
                        }
                        else
                        {
                            installers.add(installer);
                        }
                    }
                    try
                    {
                        properties.add(new InstallSourceProperty(installers));
                    }
                    catch (IOException e)
                    {
                        log.getLogger()
                                .println("Failed to upgrade Cloudflare Client installation " + installation.getName() + "; " + e.getMessage());
                        return null;
                    }
                }
                else
                {
                    properties.add(property);
                }
            }

            if (outdatedId == null)
            {
                return null;
            }

            // Installations created by the plugin are named after the version, keep that in sync so the tool directory follows.
            String name = installation.getName()
                    .equals(outdatedId) ? DEFAULT_VERSION : installation.getName();
            log.getLogger()
                    .println("Upgrading Cloudflare Client installation " + installation.getName() + " from " + outdatedId + " to " +
                             DEFAULT_VERSION);
            return new CloudflareClientInstallation(name, installation.getHome(), properties);
        }
    }
}
