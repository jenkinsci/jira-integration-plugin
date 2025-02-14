package org.marvelution.jji.listener;

import java.util.concurrent.*;

import hudson.model.*;
import javaposse.jobdsl.plugin.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class JobDSLListenerTest
        extends AbstractListenerTest
{
    @Test
    void testCreate(TestInfo info)
            throws Exception
    {
        fixedResponse(jira, new MockResponse().setResponseCode(202));
        fixedResponse(jira2, new MockResponse().setResponseCode(202));

        configureJobDsl(info);

        FreeStyleBuild build = project.scheduleBuild2(0)
                                      .get(10, TimeUnit.SECONDS);

        jenkins.assertBuildStatusSuccess(build);

        TopLevelItem item = jenkins.jenkins.getItem(info.getTestMethod().orElseThrow().getName());
        verifyNotificationSend(jira, item, "POST");
        verifyNotificationSend(jira2, item, "POST");

        verifyNotificationSend(jira, build, "PUT");
        verifyNotificationSend(jira2, build, "POST");
    }

    @Test
    void testModify(TestInfo info)
            throws Exception
    {
        fixedResponse(jira, new MockResponse().setResponseCode(202));
        fixedResponse(jira2, new MockResponse().setResponseCode(202));

        jenkins.createFreeStyleProject(info.getTestMethod().orElseThrow().getName());

        configureJobDsl(info);

        FreeStyleBuild build = project.scheduleBuild2(0)
                                      .get(10, TimeUnit.SECONDS);

        jenkins.assertBuildStatusSuccess(build);

        TopLevelItem item = jenkins.jenkins.getItem(info.getTestMethod().orElseThrow().getName());
        verifyNotificationSend(jira, item, "POST");
        verifyNotificationSend(jira2, item, "POST");

        verifyNotificationSend(jira, build, "PUT");
        verifyNotificationSend(jira2, build, "POST");
    }

    public void configureJobDsl(TestInfo info)
    {
        ExecuteDslScripts executeDslScripts = new ExecuteDslScripts();
        executeDslScripts.setUseScriptText(true);
        executeDslScripts.setScriptText("job('" + info.getTestMethod().orElseThrow().getName() + "') {\n  steps {\n    shell('echo Hello World!')\n  }\n}");
        project.getBuildersList()
               .add(executeDslScripts);
    }
}
