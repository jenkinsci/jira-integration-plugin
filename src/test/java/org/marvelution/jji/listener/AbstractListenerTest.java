package org.marvelution.jji.listener;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import org.marvelution.jji.JiraUtils;
import org.marvelution.jji.configuration.JiraSite;
import org.marvelution.jji.configuration.JiraSitesConfiguration;
import org.marvelution.jji.synctoken.CanonicalHttpServletRequest;
import org.marvelution.jji.synctoken.SyncTokenAuthenticator;
import org.marvelution.jji.synctoken.utils.SharedSecretGenerator;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.util.Secret;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.marvelution.jji.Headers.SYNC_TOKEN;

public class AbstractListenerTest
{
    static final String REST_BASE_PATH = "/rest/jenkins/latest/integration/";
    @Rule
    public TestName testName = new TestName();
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    protected MockWebServer jira;
    protected MockWebServer jira2;
    FreeStyleProject project;

    @Before
    public void setup()
            throws Exception
    {
        jira = new MockWebServer();
        jira.setDispatcher(new MemorizingQueueDispatcher());
        jira.start();
        jira2 = new MockWebServer();
        jira2.setDispatcher(new MemorizingQueueDispatcher());
        jira2.start();

        project = jenkins.createFreeStyleProject("Trigger Test");
        registerJiraSite(jira, empty(), false);
        registerJiraSite(jira2, of("Jira 2"), true);
    }

    @After
    public void tearDown()
            throws Exception
    {
        jira.shutdown();
        jira2.shutdown();
    }

    void fixedResponse(
            MockWebServer jira,
            MockResponse response)
    {
        ((MemorizingQueueDispatcher) jira.getDispatcher()).fixedResponse = response;
    }

    private void registerJiraSite(
            MockWebServer jira,
            Optional<String> name,
            boolean postJson)
    {
        URI serverUri = jira.url("rest/jenkins/latest")
                .uri();
        JiraSite site = new JiraSite(serverUri).withIdentifier(UUID.randomUUID()
                        .toString())
                .withSharedSecret(SharedSecretGenerator.generate())
                .withPostJson(postJson);
        name.ifPresent(site::withName);
        getJiraSitesConfiguration().registerSite(site);
    }

    void verifyDeletionSend(
            MockWebServer jira,
            Item item)
    {
        verifyNotificationSend(jira, "DELETE", getTriggerPath(item));
    }

    void verifyDeletionSend(
            MockWebServer jira,
            Run run)
    {
        verifyNotificationSend(jira, "DELETE", getTriggerPath(run));
    }

    RecordedRequest verifyNotificationSend(
            MockWebServer jira,
            Item item,
            String method)
    {
        return verifyNotificationSend(jira, item, method, 0);
    }

    RecordedRequest verifyNotificationSend(
            MockWebServer jira,
            Item item,
            String method,
            int requestIndex)
    {
        return verifyNotificationSend(jira, method, getTriggerPath(item)).get(requestIndex);
    }

    RecordedRequest verifyNotificationSend(
            MockWebServer jira,
            Run run,
            String method)
    {
        return verifyNotificationSend(jira, run, method, 0);
    }

    RecordedRequest verifyNotificationSend(
            MockWebServer jira,
            Run run,
            String method,
            int requestIndex)
    {
        return verifyNotificationSend(jira, method, getTriggerPath(run)).get(requestIndex);
    }

    List<RecordedRequest> verifyNotificationSend(
            MockWebServer jira,
            String method,
            String path)
    {
        List<RecordedRequest> requests = ((MemorizingQueueDispatcher) jira.getDispatcher()).findRequest(method, path);

        assertThat(requests).isNotEmpty()
                .allSatisfy(request -> {
                    // verify token header on all matching requests
                    assertThat(request).isNotNull()
                            .extracting(RecordedRequest::getPath, RecordedRequest::getMethod)
                            .containsOnly(path, method);

                    assertThat(request).extracting(RecordedRequest::getHeaders)
                            .extracting(okhttp3.Headers::names)
                            .asInstanceOf(InstanceOfAssertFactories.iterable(String.class))
                            .contains(SYNC_TOKEN);
                    String token = request.getHeader(SYNC_TOKEN);
                    new SyncTokenAuthenticator(issuer -> getJiraSitesConfiguration().findSite(issuer)
                            .flatMap(JiraSite::getSharedSecretCredentials)
                            .map(StringCredentials::getSecret)
                            .map(Secret::getPlainText)).authenticate(token,
                            new CanonicalHttpServletRequest(method,
                                    request.getRequestUrl()
                                            .uri(),
                                    empty()));
                });
        return requests;
    }

    String getTriggerPath(Item item)
    {
        return REST_BASE_PATH + JiraUtils.getJobHash(item);
    }

    String getTriggerPath(Run run)
    {
        return getTriggerPath(run.getParent()) + "/" + run.getNumber();
    }

    private JiraSitesConfiguration getJiraSitesConfiguration()
    {
        return (JiraSitesConfiguration) jenkins.getInstance()
                .getDescriptor(JiraSitesConfiguration.class);
    }

    private static class MemorizingQueueDispatcher
            extends QueueDispatcher
    {
        private final List<RecordedRequest> requests = new ArrayList<>();
        private MockResponse fixedResponse = null;

        public List<RecordedRequest> findRequest(
                String method,
                String path)
        {
            return requests.stream()
                    .filter(request -> Objects.equals(method, request.getMethod()) && Objects.equals(path, request.getPath()))
                    .collect(Collectors.toList());
        }

        @Nonnull
        @Override
        public MockResponse dispatch(
                @Nonnull
                RecordedRequest request)
                throws InterruptedException
        {
            requests.add(request);
            if (fixedResponse != null)
            {
                return fixedResponse;
            }
            else
            {
                return super.dispatch(request);
            }
        }
    }
}
