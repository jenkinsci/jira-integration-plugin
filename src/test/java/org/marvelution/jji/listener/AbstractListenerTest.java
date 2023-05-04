package org.marvelution.jji.listener;

import javax.annotation.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

import org.marvelution.jji.*;
import org.marvelution.jji.configuration.*;
import org.marvelution.jji.synctoken.*;
import org.marvelution.jji.synctoken.utils.*;

import hudson.model.*;
import hudson.util.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.assertj.core.api.*;
import org.jenkinsci.plugins.plaincredentials.*;
import org.junit.*;
import org.junit.rules.*;
import org.jvnet.hudson.test.*;

import static java.util.Optional.*;
import static org.assertj.core.api.Assertions.*;
import static org.marvelution.jji.synctoken.SyncTokenAuthenticator.*;

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
                                                   .extracting(Headers::names)
                                                   .asInstanceOf(InstanceOfAssertFactories.iterable(String.class))
                                                   .contains(SYNC_TOKEN_HEADER_NAME);
                                String token = request.getHeader(SYNC_TOKEN_HEADER_NAME);
                                new SyncTokenAuthenticator(issuer -> getJiraSitesConfiguration().stream()
                                                                                                .filter(site -> site.getIdentifier()
                                                                                                                    .equals(issuer))
                                                                                                .findFirst()
                                                                                                .flatMap(JiraSite::getSharedSecretCredentials)
                                                                                                .map(StringCredentials::getSecret)
                                                                                                .map(Secret::getPlainText)).authenticate(
                                        token,
                                        new SimpleCanonicalHttpRequest(method,
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
