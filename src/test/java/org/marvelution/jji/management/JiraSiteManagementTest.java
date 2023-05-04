package org.marvelution.jji.management;

import java.net.*;
import java.util.*;
import java.util.function.*;

import org.marvelution.jji.*;
import org.marvelution.jji.configuration.*;
import org.marvelution.jji.synctoken.utils.*;

import okhttp3.*;
import org.junit.*;
import org.jvnet.hudson.test.*;

import static jenkins.model.Jenkins.*;
import static org.assertj.core.api.Assertions.*;

public class JiraSiteManagementTest
        extends AbstractTechnicalTest
{
    private static final String ALICE = "alice";
    private final MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    private JiraSitesConfiguration sitesConfiguration;
    private JiraSite jiraSite;

    @Before
    public void setUp()
    {
        jenkins.getInstance()
               .setSecurityRealm(jenkins.createDummySecurityRealm());
        jenkins.getInstance()
               .setAuthorizationStrategy(authorizationStrategy);
        sitesConfiguration = jenkins.getInstance()
                                    .getDescriptorByType(JiraSitesConfiguration.class);
        jiraSite = new JiraSite(URI.create("http://localhost:2990/jira/rest/jenkins/latest")).withName("Local Jira")
                                                                                             .withIdentifier("identifier")
                                                                                             .withSharedSecret(SharedSecretGenerator.generate());
    }

    @Test
    public void testRegisterJiraSite()
            throws Exception
    {
        authorizationStrategy.grant(ADMINISTER)
                             .everywhere()
                             .to(ALICE);

        try (Response response = registerJiraSite(jiraSite, Credentials.basic(ALICE, ALICE)))
        {
            assertThat(response.isSuccessful()).isTrue();
            assertThat(sitesConfiguration.getSites()).hasSize(1)
                                                     .containsOnly(jiraSite);
        }
    }

    @Test
    public void testRegisterJiraSite_NoAdmin()
            throws Exception
    {
        try (Response response = registerJiraSite(jiraSite, Credentials.basic(ALICE, ALICE)))
        {
            assertThat(response.isSuccessful()).isFalse();
            assertThat(sitesConfiguration.getSites()).isEmpty();
        }
    }

    @Test
    public void testRegisterJiraSite_NoBasicAuth()
            throws Exception
    {
        try (Response response = registerJiraSite(jiraSite, (String) null))
        {
            assertThat(response.isSuccessful()).isFalse();
            assertThat(sitesConfiguration.getSites()).isEmpty();
        }
    }

    @Test
    public void testRegisterJiraSite_Update()
            throws Exception
    {
        injectSite(jiraSite);

        try (Response response = registerJiraSite(jiraSite.withName("Test Name"), (String) null))
        {
            assertThat(response.isSuccessful()).isTrue();
            assertThat(sitesConfiguration.getSites()).hasSize(1)
                                                     .containsOnly(jiraSite);
        }
    }

    @Test
    public void testRegisterJiraSite_AccessDenied()
            throws Exception
    {
        try (Response response = registerJiraSite(jiraSite, request -> request.addHeader("Authorization", Credentials.basic(ALICE, ALICE))))
        {
            assertThat(response.isSuccessful()).isFalse();
            assertThat(sitesConfiguration.getSites()).isEmpty();
        }
    }

    @Test
    public void testUnregisterJiraSite()
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
    public void testUnregisterJiraSite_UnknownJiraSite()
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
    public void testUnregisterJiraSite_AccessDenied()
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

    private void injectSite(JiraSite site)
    {
        sitesConfiguration.registerSite(new JiraSite(site.getUri()).withIdentifier(site.getIdentifier())
                                                                   .withName(site.getName())
                                                                   .withSharedSecret(site.getSharedSecret())
                                                                   .withPostJson(site.isPostJson()));
    }

    private Response registerJiraSite(
            JiraSite jiraSite,
            String basicAuth)
            throws Exception
    {
        return registerJiraSite(jiraSite, request -> {
            Request.Builder builder = signTokenAuth(request.build(), jiraSite, jenkins.contextPath).newBuilder();
            if (basicAuth != null)
            {
                builder.addHeader("Authorization", basicAuth);
            }
            return builder;
        });
    }

    private Response registerJiraSite(
            JiraSite jiraSite,
            UnaryOperator<Request.Builder> requestCustomizer)
            throws Exception
    {
        Map<String, Object> site = new HashMap<>();
        site.put("url",
                jiraSite.getUri()
                        .toASCIIString());
        site.put("name", jiraSite.getName());
        site.put("identifier", jiraSite.getIdentifier());
        site.put("sharedSecret", jiraSite.getSharedSecret());
        return doAction("register", site, requestCustomizer);
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
