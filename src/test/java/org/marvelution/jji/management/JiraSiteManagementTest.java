package org.marvelution.jji.management;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.marvelution.jji.AbstractTechnicalTest;
import org.marvelution.jji.configuration.JiraSite;
import org.marvelution.jji.synctoken.utils.SharedSecretGenerator;

import net.sf.json.JSONObject;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static jenkins.model.Jenkins.ADMINISTER;
import static org.assertj.core.api.Assertions.assertThat;

class JiraSiteManagementTest
        extends AbstractTechnicalTest
{
    private JiraSite jiraSite;

    @BeforeEach
    void setUp()
    {
        jiraSite = new JiraSite(URI.create("http://localhost:2990/jira/rest/jenkins/latest")).withName("Local Jira")
                .withIdentifier("identifier")
                .withSharedSecret(SharedSecretGenerator.generate())
                .withContext(new JSONObject().element("test", "field"));
    }

    @Test
    void testUnregisterJiraSite()
            throws Exception
    {
        injectSite(jiraSite);

        try (Response response = unregisterJiraSite(jiraSite))
        {
            assertThat(response.isSuccessful()).isTrue();
            assertThat(sitesConfiguration.getSites()).isEmpty();
        }
    }

    @Test
    void testUnregisterJiraSite_UnknownJiraSite()
            throws Exception
    {
        JiraSite site = new JiraSite(URI.create("http://localhost:8080/jira/rest/jenkins/latest")).withName("Local Jira")
                .withIdentifier("identifier")
                .withSharedSecret("shared-secret");
        injectSite(site);

        try (Response response = unregisterJiraSite(jiraSite))
        {
            assertThat(response.isSuccessful()).isFalse();
            assertThat(sitesConfiguration.getSites()).hasSize(1)
                    .containsOnly(site);
        }
    }

    @Test
    void testUnregisterJiraSite_AccessDenied()
            throws Exception
    {
        JiraSite site = new JiraSite(URI.create("http://localhost:8080/jira/rest/jenkins/latest")).withName("Local Jira")
                .withIdentifier("identifier")
                .withSharedSecret("shared-secret");
        injectSite(site);

        try (Response response = unregisterJiraSite(site, request -> request.addHeader("Authorization", Credentials.basic(ALICE, ALICE))))
        {
            assertThat(response.isSuccessful()).isFalse();
            assertThat(sitesConfiguration.getSites()).hasSize(1)
                    .containsOnly(site);
        }

    }

    private Response unregisterJiraSite(JiraSite jiraSite)
            throws Exception
    {
        return unregisterJiraSite(jiraSite, request -> signTokenAuth(request.build(), jiraSite, jenkins.contextPath).newBuilder());
    }

    private Response unregisterJiraSite(
            JiraSite jiraSite,
            UnaryOperator<Request.Builder> requestCustomizer)
            throws Exception
    {
        Map<String, Object> site = new HashMap<>();
        site.put("url",
                jiraSite.getUri()
                        .toASCIIString());

        return doAction("unregister", site, requestCustomizer);
    }

    private Response doAction(
            String action,
            Map<String, Object> payload,
            UnaryOperator<Request.Builder> requestCustomizer)
            throws Exception
    {

        HttpUrl url = Objects.requireNonNull(HttpUrl.get(jenkins.getURL()))
                .newBuilder()
                .addPathSegment(JiraSiteManagement.URL_NAME)
                .addPathSegment(action)
                .build();

        Request.Builder request = new Request.Builder().url(url)
                .post(RequestBody.create(objectMapper.writeValueAsString(payload), JiraSite.JSON));

        request = requestCustomizer.apply(request);

        return httpClient.newCall(request.build())
                .execute();
    }
}
