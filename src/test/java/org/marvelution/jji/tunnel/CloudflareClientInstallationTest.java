package org.marvelution.jji.tunnel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.model.TaskListener;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.marvelution.jji.tunnel.CloudflareClientInstallation.DEFAULT_VERSION;

@WithJenkins
class CloudflareClientInstallationTest
{

    private static final String OUTDATED_VERSION = "2026.7.3";

    private CloudflareClientInstallation.DescriptorImpl descriptor;
    private File configFile;

    @BeforeEach
    void setUp(JenkinsRule jenkins)
    {
        descriptor = jenkins.getInstance()
                .getDescriptorByType(CloudflareClientInstallation.DescriptorImpl.class);
        configFile = new File(jenkins.getInstance()
                .getRootDir(), descriptor.getId() + ".xml");
    }

    @Test
    void testUpgradeOutdatedPluginCreatedInstallation()
            throws IOException
    {
        descriptor.setInstallations(installedFrom(OUTDATED_VERSION, OUTDATED_VERSION));

        assertThat(descriptor.upgradeOutdatedInstallations(TaskListener.NULL)).isTrue();

        assertThat(descriptor.getInstallations()).singleElement()
                .satisfies(installation -> {
                    assertThat(installation.getName()).isEqualTo(DEFAULT_VERSION);
                    assertThat(installerIds(installation)).containsExactly(DEFAULT_VERSION);
                });
        assertThat(configFile).exists();
    }

    @Test
    void testUpgradeOutdatedInstallationKeepsCustomName()
            throws IOException
    {
        descriptor.setInstallations(installedFrom("cloudflared", OUTDATED_VERSION));

        assertThat(descriptor.upgradeOutdatedInstallations(TaskListener.NULL)).isTrue();

        assertThat(descriptor.getInstallations()).singleElement()
                .satisfies(installation -> {
                    assertThat(installation.getName()).isEqualTo("cloudflared");
                    assertThat(installerIds(installation)).containsExactly(DEFAULT_VERSION);
                });
    }

    @Test
    void testUpToDateInstallationIsNotTouched()
            throws IOException
    {
        CloudflareClientInstallation installation = installedFrom(DEFAULT_VERSION, DEFAULT_VERSION);
        descriptor.setInstallations(installation);

        assertThat(descriptor.upgradeOutdatedInstallations(TaskListener.NULL)).isFalse();

        assertThat(descriptor.getInstallations()).containsExactly(installation);
        assertThat(configFile).doesNotExist();
    }

    @Test
    void testManuallyManagedInstallationIsNotTouched()
    {
        CloudflareClientInstallation installation = new CloudflareClientInstallation("manual", "/opt/cloudflared", Collections.emptyList());
        descriptor.setInstallations(installation);

        assertThat(descriptor.upgradeOutdatedInstallations(TaskListener.NULL)).isFalse();

        assertThat(descriptor.getInstallations()).containsExactly(installation);
    }

    private static CloudflareClientInstallation installedFrom(
            String name,
            String version)
            throws IOException
    {
        List<? extends ToolProperty<?>> properties = Collections.singletonList(new InstallSourceProperty(Collections.singletonList(new CloudflareClientInstaller(
                version))));
        return new CloudflareClientInstallation(name, null, properties);
    }

    private static List<String> installerIds(CloudflareClientInstallation installation)
    {
        List<String> ids = new ArrayList<>();
        for (ToolInstaller installer : installation.getProperties()
                .get(InstallSourceProperty.class).installers)
        {
            ids.add(((CloudflareClientInstaller) installer).id);
        }
        return ids;
    }
}
