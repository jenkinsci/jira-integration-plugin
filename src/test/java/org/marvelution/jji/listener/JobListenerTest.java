package org.marvelution.jji.listener;

import java.io.*;

import hudson.model.*;
import net.sf.json.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.assertj.core.api.Assertions.*;

@WithJenkins
class JobListenerTest
        extends AbstractListenerTest
{
    @Test
    void testCreate()
            throws IOException
    {
        fixedResponse(jira, new MockResponse().setResponseCode(202));
        fixedResponse(jira2, new MockResponse().setResponseCode(202));

        FreeStyleProject project = jenkins.createFreeStyleProject("creation-test");

        verifyCreateNotification(jira, project);
        verifyCreateNotification(jira2, project);
    }

    private void verifyCreateNotification(
            MockWebServer jira,
            FreeStyleProject project)
    {
        RecordedRequest request = verifyNotificationSend(jira, "POST", getTriggerPath(project)).get(0);
        JSONObject json = JSONObject.fromObject(request.getBody()
                                                       .readUtf8());
        assertThat(json.get("name")).isEqualTo(project.getName());
        assertThat(json.get("url")).isEqualTo(project.getAbsoluteUrl());
    }

    @Test
    void testModify()
            throws IOException
    {
        fixedResponse(jira, new MockResponse().setResponseCode(202));
        fixedResponse(jira2, new MockResponse().setResponseCode(202));

        project.setDescription("JJI-575");

        verifyModifiedNotification(jira);
        verifyModifiedNotification(jira2);
    }

    private void verifyModifiedNotification(MockWebServer jira)
    {
        RecordedRequest request = verifyNotificationSend(jira, "POST", getTriggerPath(project)).get(0);
        JSONObject json = JSONObject.fromObject(request.getBody()
                                                       .readUtf8());
        assertThat(json.get("name")).isEqualTo(project.getName());
        assertThat(json.get("url")).isEqualTo(project.getAbsoluteUrl());
        assertThat(json.get("description")).isEqualTo(project.getDescription());
    }

    @Test
    void testDelete()
            throws Exception
    {
        jira.enqueue(new MockResponse().setResponseCode(204));
        jira.enqueue(new MockResponse().setResponseCode(500));
        fixedResponse(jira2, new MockResponse().setResponseCode(202));

        project.delete();

        verifyDeletionSend(jira2, project);
        verifyDeletionSend(jira, project);
    }
}
