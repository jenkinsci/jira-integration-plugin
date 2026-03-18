package org.marvelution.jji.tunnel;

import java.io.IOException;
import java.util.List;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class CloudflareClientInstallation
        extends ToolInstallation
        implements NodeSpecific<CloudflareClientInstallation>
{

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
        return homePath.child(isWindows ? "cloudflared.exe" : "cloudflared");
    }

    private static final class GetIsWindows
            extends MasterToSlaveCallable<Boolean, IOException>
    {
        @Override
        public Boolean call()
                throws IOException
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
            return java.util.Collections.singletonList(new CloudflareClientInstaller("2026.3.0"));
        }
    }
}
