package org.marvelution.jji.casc;

import java.net.*;

import org.marvelution.jji.configuration.*;

import io.jenkins.plugins.casc.misc.*;
import org.assertj.core.api.*;
import org.junit.*;

import static org.assertj.core.api.Assertions.*;

public class ConfigurationAsCodeTest
{

    @Rule
    public JenkinsConfiguredWithCodeRule jenkins = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void testSupportConfigurationAsCode()
    {
        JiraSitesConfiguration sitesConfiguration = (JiraSitesConfiguration) jenkins.getInstance()
                                                                                    .getDescriptorOrDie(JiraSitesConfiguration.class);
        assertThat(sitesConfiguration).isNotNull()
                                      .extracting(JiraSitesConfiguration::getSites)
                                      .asInstanceOf(InstanceOfAssertFactories.iterable(JiraSite.class))
                                      .hasSize(3)
                                      .allSatisfy(site -> {
                                          assertThat(site.getSharedSecret()).isNull();
                                          assertThat(site.getSharedSecretId()).isNotBlank();
                                      })
                                      .extracting(JiraSite::getUri, JiraSite::getName, JiraSite::getIdentifier, JiraSite::isPostJson)
                                      .containsOnly(tuple(URI.create("http://localhost:2990/jira/rest/jenkins/latest"),
                                                      "JIRA",
                                                      "97b100155db44e809096553ec4fe6a4d",
                                                      true),
                                              tuple(URI.create("https://jjc.marvelution.com/rest/339b3e9a-2f18-4752-a978-a5ef9c17472e"),
                                                      "marveloud.atlassian.net",
                                                      "339b3e9a2f184752a978a5ef9c17472e",
                                                      false),
                                              tuple(URI.create(
                                                              "https://jjc-staging.marvelution" + ".com/rest/b37cee05-e193-43d7-80bc-2bae9ef5f171"),
                                                      "marveling.atlassian.net",
                                                      "b37cee05e19343d780bc2bae9ef5f171",
                                                      false));
    }
}
