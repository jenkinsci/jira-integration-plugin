package org.marvelution.jji.configuration;

import java.io.*;
import java.net.*;

import hudson.*;
import hudson.util.*;
import org.assertj.core.groups.*;
import org.jenkinsci.plugins.plaincredentials.*;
import org.junit.*;
import org.jvnet.hudson.test.*;
import org.jvnet.hudson.test.recipes.*;

import static org.assertj.core.api.Assertions.*;
import static org.marvelution.jji.configuration.JiraSitesConfiguration.*;

public class JiraSitesConfigurationTest
{
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    @LocalData
    public void testStoreSharedSecretAsCredentials()
            throws IOException
    {
        URL resource = getClass().getResource(getClass().getSimpleName() + "/testStoreSharedSecretAsCredentials/" + ID + ".xml");
        XmlFile xmlFile = new XmlFile(new File(resource.getFile()));
        JiraSitesConfiguration originalConfig = (JiraSitesConfiguration) xmlFile.read();
        Tuple[] tuples = originalConfig.getSites()
                                       .stream()
                                       .map(site -> tuple(site.getName(), site.getSharedSecret()))
                                       .toArray(Tuple[]::new);

        JiraSitesConfiguration config = (JiraSitesConfiguration) jenkins.jenkins.getDescriptorOrDie(JiraSitesConfiguration.class);

        assertThat(config.getSites()).hasSize(2)
                                     .allSatisfy(site -> {
                                         assertThat(site.getSharedSecret()).isNull();
                                         assertThat(site.getSharedSecretId()).isNotBlank();
                                     })
                                     .extracting(JiraSite::getName,
                                             site -> site.getSharedSecretCredentials()
                                                         .map(StringCredentials::getSecret)
                                                         .map(Secret::getPlainText)
                                                         .orElse(null))
                                     .containsOnly(tuples);


    }
}
