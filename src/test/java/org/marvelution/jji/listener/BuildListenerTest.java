package org.marvelution.jji.listener;

import java.util.concurrent.*;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.marvelution.jji.*;
import org.marvelution.jji.model.parsers.*;

import hudson.model.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.*;

import static org.assertj.core.api.Assertions.*;
import static org.marvelution.jji.JiraUtils.*;

@WithJenkins
class BuildListenerTest
        extends AbstractListenerTest
{
    @Test
    void testNotifyAndDelete()
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
    void testNotify_WithParents()
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
    void testNotify_WithData()
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

    @Test
    void testNotify_BuildStarted()
            throws Exception
    {
        enableBuildStarted(jira);
        jira.enqueue(new MockResponse().setResponseCode(202));
        jira.enqueue(new MockResponse().setResponseCode(202));
        jira2.enqueue(new MockResponse().setResponseCode(202));
        BuildListener.startNotificationDelay = 0;
        // Still running once the start notification is due
        project.getBuildersList()
               .add(new SleepBuilder(2000));

        FreeStyleBuild build = project.scheduleBuild2(0)
                                      .get(10, TimeUnit.SECONDS);

        jenkins.assertBuildStatusSuccess(build);
        jenkins.assertLogContains("Notified Jira that a build has started", build);
        jenkins.assertLogNotContains("Notified Jira 2 that a build has started", build);
        // The build data is posted when the build starts, also to a site that otherwise gets the parent hashes
        RecordedRequest started = verifyNotificationSend(jira, build, "POST");
        assertThat(started.getBody()
                          .readUtf8()).contains("\"building\":true")
                                      .doesNotContain("\"result\":\"SUCCESS\"");
        verifyNotificationSend(jira, build, "PUT");
        // Sites that don't store running builds only hear of the completed build
        assertThat(verifyNotificationSend(jira2, build, "POST").getBody()
                           .readUtf8()).contains("\"building\":false");
        assertThat(jira2.getRequestCount()).isEqualTo(1);
    }

    @Test
    void testNotify_BuildStarted_CompletedBeforeDue()
            throws Exception
    {
        enableBuildStarted(jira);
        jira.enqueue(new MockResponse().setResponseCode(202));
        jira2.enqueue(new MockResponse().setResponseCode(202));
        BuildListener.startNotificationDelay = 30;

        FreeStyleBuild build = project.scheduleBuild2(0)
                                      .get(10, TimeUnit.SECONDS);

        jenkins.assertBuildStatusSuccess(build);
        jenkins.assertLogNotContains("that a build has started", build);
        // A build that completes before its start notification is due is only notified as completed
        verifyNotificationSend(jira, build, "PUT");
        assertThat(jira.getRequestCount()).isEqualTo(1);
    }

    String getBuildTriggerPath(Item item)
    {
        return REST_BASE_PATH + JiraUtils.getJobHash(item) + "/1";
    }
}
