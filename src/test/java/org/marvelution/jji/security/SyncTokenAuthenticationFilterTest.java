package org.marvelution.jji.security;

import java.net.*;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.marvelution.jji.*;
import org.marvelution.jji.configuration.*;
import org.marvelution.jji.synctoken.utils.*;

import hudson.model.*;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static jenkins.model.Jenkins.*;
import static org.assertj.core.api.Assertions.*;

@WithJenkins
class SyncTokenAuthenticationFilterTest
        extends AbstractTechnicalTest
{
    private JiraSite site;

    @BeforeEach
    void setUp()
    {
        site = new JiraSite(URI.create("https://jira.example.com")).withIdentifier("jira")
                                                                   .withName("Jira")
                                                                   .withSharedSecret(SharedSecretGenerator.generate());
        injectSite(site);
    }

    @Test
    void testAccessViaSyncToken()
            throws Exception
    {
        authorizationStrategy.grant(ADMINISTER)
                             .everywhere()
                             .to(ALICE);

        FreeStyleProject project = jenkins.createFreeStyleProject("Test");

        HttpUrl url = HttpUrl.get(project.getAbsoluteUrl())
                             .newBuilder()
                             .addPathSegments("api/json")
                             .build();

        Response response = httpClient.newCall(new Request.Builder().url(url)
                                                                    .build())
                                      .execute();
        assertThat(response.isSuccessful()).isFalse();

        response = httpClient.newCall(signTokenAuth(new Request.Builder().url(url)
                                                                         .build(), site, jenkins.contextPath))
                             .execute();
        assertThat(response.isSuccessful()).isTrue();
    }

    @Test
    void testNoSyncTokenAccessToAPI()
            throws Exception
    {
        authorizationStrategy.grant(ADMINISTER)
                             .everywhere()
                             .to(ALICE);

        FreeStyleProject project = jenkins.createFreeStyleProject("Test");

        HttpUrl url = HttpUrl.get(project.getAbsoluteUrl())
                             .newBuilder()
                             .addPathSegments("api/json")
                             .build();

        Response response = httpClient.newCall(new Request.Builder().url(url)
                                                                    .build())
                                      .execute();
        assertThat(response.isSuccessful()).isFalse();

        response = httpClient.newCall(signTokenAuth(new Request.Builder().url(url)
                                                                         .build(), "jira-1", site.getSharedSecret(), jenkins.contextPath))
                             .execute();
        assertThat(response.isSuccessful()).isFalse();
    }
}
