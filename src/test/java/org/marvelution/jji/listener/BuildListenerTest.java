package org.marvelution.jji.listener;

import java.util.concurrent.*;

import org.marvelution.jji.*;
import org.marvelution.jji.model.parsers.*;

import hudson.model.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.jvnet.hudson.test.*;

import static org.assertj.core.api.Assertions.*;
import static org.marvelution.jji.JiraUtils.*;

public class BuildListenerTest
        extends AbstractListenerTest
{
    @Test
    public void testNotifyAndDelete()
            throws Exception
    {
        jira.enqueue(new MockResponse().setResponseCode(500));
        jira2.enqueue(new MockResponse().setResponseCode(204));

        FreeStyleBuild build = project.scheduleBuild2(0)
                                      .get(10, TimeUnit.SECONDS);

        jenkins.assertBuildStatusSuccess(build);
        jenkins.assertLogContains("Notified Jira 2 that a build has completed", build);
        jenkins.assertLogContains("ERROR: Unable to notify Jira: [500]", build);
        verifyNotificationSend(jira, build, "PUT");
        verifyNotificationSend(jira2, build, "POST");

        jira.enqueue(new MockResponse().setResponseCode(204));
        jira2.enqueue(new MockResponse().setResponseCode(204));

        build.delete();

        verifyDeletionSend(jira, build);
        verifyDeletionSend(jira2, build);
    }

    @Test
    public void testNotify_WithParents()
            throws Exception
    {
        jira.enqueue(new MockResponse().setResponseCode(202));
        jira.enqueue(new MockResponse().setResponseCode(202));
        jira2.enqueue(new MockResponse().setResponseCode(202));
        jira2.enqueue(new MockResponse().setResponseCode(202));

        MockFolder folder = jenkins.createFolder("folder");

        verifyNotificationSend(jira, folder, "POST");
        verifyNotificationSend(jira2, folder, "POST");

        FreeStyleProject project = folder.createProject(FreeStyleProject.class, "child");

        verifyNotificationSend(jira, project, "POST");
        verifyNotificationSend(jira2, project, "POST");

        jira.enqueue(new MockResponse().setResponseCode(202));
        jira2.enqueue(new MockResponse().setResponseCode(202));

        FreeStyleBuild build = project.scheduleBuild2(0)
                                      .get(10000, TimeUnit.SECONDS);

        jenkins.assertBuildStatusSuccess(build);
        jenkins.assertLogContains("Notified Jira that a build has completed", build);
        jenkins.assertLogContains("Notified Jira 2 that a build has completed", build);
        RecordedRequest request = verifyNotificationSend(jira, build, "PUT");
        assertThat(request.getBody()
                          .readUtf8()).isEqualTo(
                "[\"50dddc5b48dc20974a0467db6143bf4474f8c871\",\"afffdd08d81dd168981d9a0dcceb2fb24c2ab56a\"]");
        request = verifyNotificationSend(jira2, "POST", getBuildTriggerPath(project)).get(0);
        assertThat(request.getBody()
                          .readUtf8()).isEqualTo(asJson(build,
                ParserProvider.buildParser()
                              .fields()));
    }

    @Test
    public void testNotify_WithData()
            throws Exception
    {
        jira.enqueue(new MockResponse().setResponseCode(202));
        jira2.enqueue(new MockResponse().setResponseCode(202));

        FreeStyleBuild build = project.scheduleBuild2(0)
                                      .get(10, TimeUnit.SECONDS);

        jenkins.assertBuildStatusSuccess(build);
        jenkins.assertLogContains("Notified Jira 2 that a build has completed", build);
        RecordedRequest request = verifyNotificationSend(jira2, build, "POST");
        assertThat(request.getBody()
                          .readUtf8()).isEqualTo(asJson(build,
                ParserProvider.buildParser()
                              .fields()));
    }

    String getBuildTriggerPath(Item item)
    {
        return REST_BASE_PATH + JiraUtils.getJobHash(item) + "/1";
    }
}
