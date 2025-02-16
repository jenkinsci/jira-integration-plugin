package org.marvelution.jji.listener;

import java.util.concurrent.*;

import hudson.model.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.*;

@WithJenkins
class RunSaveListenerTest
        extends AbstractListenerTest
{
    @Test
    void testOnChange()
            throws Exception
    {

        jira.enqueue(new MockResponse().setResponseCode(202));
        jira2.enqueue(new MockResponse().setResponseCode(202));

        FreeStyleBuild build = project.scheduleBuild2(0)
                                      .get(10, TimeUnit.SECONDS);

        jenkins.assertBuildStatusSuccess(build);
        RecordedRequest firstRequest = verifyNotificationSend(jira2, "POST", getTriggerPath(build)).get(0);
        assertThat(firstRequest.getBody()
                               .readUtf8()).doesNotContain("New Description");

        jira.enqueue(new MockResponse().setResponseCode(204));
        jira2.enqueue(new MockResponse().setResponseCode(204));

        build.setDescription("New Description");

        RecordedRequest updateRequest = verifyNotificationSend(jira2, "POST", getTriggerPath(build)).get(1);
        assertThat(updateRequest.getBody()
                                .readUtf8()).contains("New Description");
    }
}
