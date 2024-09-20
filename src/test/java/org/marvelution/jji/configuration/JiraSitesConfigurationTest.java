package org.marvelution.jji.configuration;

import javax.annotation.*;
import java.io.*;
import java.net.*;
import java.util.*;

import org.marvelution.jji.synctoken.utils.*;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.*;
import hudson.*;
import hudson.security.*;
import hudson.util.*;
import jenkins.model.*;
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
    public void testMigrateSharedSecretAsCredentials()
            throws IOException
    {
        URL resource = getClass().getResource(getClass().getSimpleName() + "/" + jenkins.getTestDescription()
                                                                                        .getMethodName() + "/" + ID + ".xml");
        XmlFile xmlFile = new XmlFile(new File(resource.getFile()));
        JiraSitesConfiguration originalConfig = (JiraSitesConfiguration) xmlFile.read();
        Tuple[] tuples = originalConfig.getSites()
                                       .stream()
                                       .map(site -> tuple(site.getName(), site.getSharedSecret()))
                                       .toArray(Tuple[]::new);

        JiraSitesConfiguration config = getJiraSitesConfiguration();

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

    @Test
    public void testRegisterSiteStoresSharedSecretAsCredential()
    {
        String sharedSecret = SharedSecretGenerator.generate();

        JiraSitesConfiguration configuration = getJiraSitesConfiguration();

        configuration.registerSite(new JiraSite(URI.create("http://jira.local/rest/jenkins/latest")).withName("Jira")
                                                                                                    .withIdentifier("site-1")
                                                                                                    .withSharedSecret(sharedSecret));

        Optional<JiraSite> registeredSite = configuration.findSite("site-1");
        assertThat(registeredSite).isPresent()
                                  .get()
                                  .returns("Jira", JiraSite::getName)
                                  .returns("site-1", JiraSite::getIdentifier)
                                  .returns(URI.create("http://jira.local/rest/jenkins/latest"), JiraSite::getUri)
                                  .returns(null, JiraSite::getSharedSecret)
                                  .returns(sharedSecret,
                                          site -> site.getSharedSecretCredentials()
                                                      .map(StringCredentials::getSecret)
                                                      .map(Secret::getPlainText)
                                                      .orElse(null));
    }

    @Test
    public void testUnRegisterSiteRemovedSharedSecretCredential()
    {
        JiraSitesConfiguration configuration = getJiraSitesConfiguration();

        String sharedSecret = SharedSecretGenerator.generate();
        configuration.registerSite(new JiraSite(URI.create("http://jira.local/rest/jenkins/latest")).withName("Jira")
                                                                                                    .withIdentifier("site-1")
                                                                                                    .withSharedSecret(sharedSecret));

        Optional<JiraSite> registeredSite = configuration.findSite("site-1");
        assertThat(registeredSite).isPresent()
                                  .get()
                                  .extracting(JiraSite::getSharedSecretId)
                                  .isNotNull();

        URI uri = registeredSite.get()
                                .getUri();
        String sharedSecretId = registeredSite.get()
                                              .getSharedSecretId();

        List<StringCredentials> credentials = CredentialsProvider.lookupCredentials(StringCredentials.class,
                Jenkins.get(),
                ACL.SYSTEM,
                List.of(new SchemeRequirement(uri.getScheme()), new HostnameRequirement(uri.getHost())));
        assertThat(CredentialsMatchers.firstOrNull(credentials, CredentialsMatchers.withId(sharedSecretId))).isNotNull()
                                                                                                            .extracting(StringCredentials::getSecret)
                                                                                                            .extracting(Secret::getPlainText)
                                                                                                            .isEqualTo(sharedSecret);

        configuration.unregisterSite(registeredSite.get());

        assertThat(configuration.findSite("site-1")).isEmpty();
        credentials = CredentialsProvider.lookupCredentials(StringCredentials.class,
                Jenkins.get(),
                ACL.SYSTEM,
                List.of(new SchemeRequirement(uri.getScheme()), new HostnameRequirement(uri.getHost())));
        assertThat(CredentialsMatchers.firstOrNull(credentials, CredentialsMatchers.withId(sharedSecretId))).isNull();
    }

    @Test
    public void testRegisterUpdatedSiteUpdatesSharedSecretCredential()
    {
        String sharedSecret = SharedSecretGenerator.generate();

        JiraSitesConfiguration configuration = getJiraSitesConfiguration();

        configuration.registerSite(new JiraSite(URI.create("http://jira.local/rest/jenkins/latest")).withName("Jira")
                                                                                                    .withIdentifier("site-1")
                                                                                                    .withSharedSecret(sharedSecret));

        Optional<JiraSite> registeredSite = configuration.findSite("site-1");
        assertThat(registeredSite).isPresent()
                                  .get()
                                  .returns("Jira", JiraSite::getName)
                                  .returns("site-1", JiraSite::getIdentifier)
                                  .returns(URI.create("http://jira.local/rest/jenkins/latest"), JiraSite::getUri)
                                  .returns(null, JiraSite::getSharedSecret)
                                  .returns(sharedSecret,
                                          site -> site.getSharedSecretCredentials()
                                                      .map(StringCredentials::getSecret)
                                                      .map(Secret::getPlainText)
                                                      .orElse(null));

        String updatedSharedSecret = SharedSecretGenerator.generate();
        configuration.registerSite(new JiraSite(URI.create("http://jira.local/rest/jenkins/latest")).withName("Local")
                                                                                                    .withIdentifier("site-2")
                                                                                                    .withSharedSecret(updatedSharedSecret));

        assertThat(configuration.findSite("site-1")).isEmpty();
        assertThat(configuration.findSite("site-2")).isPresent()
                                                    .get()
                                                    .returns("Local", JiraSite::getName)
                                                    .returns("site-2", JiraSite::getIdentifier)
                                                    .returns(URI.create("http://jira.local/rest/jenkins/latest"), JiraSite::getUri)
                                                    .returns(null, JiraSite::getSharedSecret)
                                                    .returns(registeredSite.get()
                                                                           .getSharedSecretId(), JiraSite::getSharedSecretId)
                                                    .returns(updatedSharedSecret,
                                                            site -> site.getSharedSecretCredentials()
                                                                        .map(StringCredentials::getSecret)
                                                                        .map(Secret::getPlainText)
                                                                        .orElse(null));
    }

    @Nonnull
    private JiraSitesConfiguration getJiraSitesConfiguration()
    {
        return (JiraSitesConfiguration) jenkins.getInstance()
                                               .getDescriptorOrDie(JiraSitesConfiguration.class);
    }
}
