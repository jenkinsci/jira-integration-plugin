package org.marvelution.jji.tunnel;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class CloudflareClientInstaller
        extends DownloadFromUrlInstaller
{

    @DataBoundConstructor
    public CloudflareClientInstaller(String id)
    {
        super(id);
    }

    @Override
    public Installable getInstallable()
            throws IOException
    {
        for (Installable i : ((DescriptorImpl) getDescriptor()).getInstallables())
        {
            if (id.equals(i.id))
            {
                return i;
            }
        }
        return null;
    }

    @Override
    public FilePath performInstallation(
            ToolInstallation tool,
            Node node,
            TaskListener log)
            throws IOException, InterruptedException
    {
        FilePath expected = preferredLocation(tool, node);

        Installable installable = getInstallable();
        if (installable == null)
        {
            log.getLogger()
                    .println("No installable found for Cloudflare Client for this OS/Arch");
            return expected;
        }

        VirtualChannel channel = node.getChannel();
        String os = channel != null ? channel.call(new GetOs()) : System.getProperty("os.name");
        String arch = channel != null ? channel.call(new GetArch()) : System.getProperty("os.arch");

        Release release = installable.getRelease(os, arch);
        if (release == null)
        {
            log.getLogger()
                    .println("No release found for Cloudflare Client for " + os + "/" + arch);
            return expected;
        }

        if (isUpToDate(expected, installable))
        {
            return expected;
        }

        String url = release.url;
        boolean isWindows = channel != null && channel.call(new GetIsWindows());
        String binaryName = isWindows ? "cloudflared.exe" : "cloudflared";

        FilePath binaryPath = expected.child(binaryName);

        if (url.endsWith(".zip") || url.endsWith(".tar.gz") || url.endsWith(".tgz"))
        {
            // Jenkins' installIfNecessaryFrom handles archives by unzipping into the 'expected' directory.
            if (expected.installIfNecessaryFrom(new URL(url), log, "Installing Cloudflare Client from archive"))
            {
                expected.child(".timestamp")
                        .delete();
                expected.child(".timestamp")
                        .touch(System.currentTimeMillis());
            }
        }
        else
        {
            // It's a direct binary download.
            log.getLogger()
                    .println("Downloading Cloudflare Client binary from " + url);
            binaryPath.copyFrom(new URL(url));
            binaryPath.chmod(0755);
            expected.child(".timestamp")
                    .delete();
            expected.child(".timestamp")
                    .touch(System.currentTimeMillis());
        }

        return expected;
    }

    private static final class GetOs
            extends MasterToSlaveCallable<String, IOException>
    {
        @Override
        public String call()
                throws IOException
        {
            String os = System.getProperty("os.name")
                    .toLowerCase();
            if (os.contains("linux"))
            {
                return "linux";
            }
            else if (os.contains("mac"))
            {
                return "mac";
            }
            else if (os.contains("windows"))
            {
                return "windows";
            }
            return os;
        }
    }

    private static final class GetArch
            extends MasterToSlaveCallable<String, IOException>
    {
        @Override
        public String call()
                throws IOException
        {
            String arch = System.getProperty("os.arch")
                    .toLowerCase();
            if (arch.contains("amd64") || arch.contains("x86_64"))
            {
                return "amd64";
            }
            else if (arch.contains("arm64") || arch.contains("aarch64"))
            {
                return "arm64";
            }
            else if (arch.contains("86"))
            {
                return "386";
            }
            return arch;
        }
    }

    public static final class Installable
            extends DownloadFromUrlInstaller.Installable
    {
        public List<Release> releases;

        public Release getRelease(
                String os,
                String arch)
        {
            if (releases == null)
            {
                return null;
            }
            for (Release release : releases)
            {
                if (release.os.equals(os) && release.arch.equals(arch))
                {
                    return release;
                }
            }
            return null;
        }
    }

    public static final class Release
    {
        public String os;
        public String arch;
        public String url;
    }

    public static final class InstallableList
    {
        public Installable[] list;
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
    public static final class DescriptorImpl
            extends DownloadFromUrlInstaller.DescriptorImpl<CloudflareClientInstaller>
    {
        @Override
        public String getDisplayName()
        {
            return "Install from cloudflare.com";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType)
        {
            return toolType == CloudflareClientInstallation.class;
        }

        @Override
        public List<? extends Installable> getInstallables()
                throws IOException
        {
            try (InputStream resource = CloudflareClientInstaller.class.getResourceAsStream("CloudflareClientInstaller/installables.json"))
            {
                JSONObject d = JSONObject.fromObject(IOUtils.toString(Objects.requireNonNull(resource), StandardCharsets.UTF_8));
                return Arrays.asList(((InstallableList) JSONObject.toBean(d, InstallableList.class, Map.of("releases", Release.class))).list);
            }
        }
    }
}
