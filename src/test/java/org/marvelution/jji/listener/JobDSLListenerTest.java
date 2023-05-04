package org.marvelution.jji.listener;

import java.util.concurrent.*;

import hudson.model.*;
import javaposse.jobdsl.plugin.*;
import okhttp3.mockwebserver.*;
import org.junit.*;

public class JobDSLListenerTest
        extends AbstractListenerTest
{
    @Test
    public void testCreate()
            throws Exception
    {
        fixedResponse(jira, new MockResponse().setResponseCode(202));
        fixedResponse(jira2, new MockResponse().setResponseCode(202));

        configureJobDsl();

        FreeStyleBuild build = project.scheduleBuild2(0)
                                      .get(10, TimeUnit.SECONDS);

        jenkins.assertBuildStatusSuccess(build);

        TopLevelItem item = jenkins.jenkins.getItem(testName.getMethodName());
        verifyNotificationSend(jira, item, "POST");
        verifyNotificationSend(jira2, item, "POST");

        verifyNotificationSend(jira, build, "PUT");
        verifyNotificationSend(jira2, build, "POST");
    }

    @Test
    public void testModify()
            throws Exception
    {
        fixedResponse(jira, new MockResponse().setResponseCode(202));
        fixedResponse(jira2, new MockResponse().setResponseCode(202));

        jenkins.createFreeStyleProject(testName.getMethodName());

        configureJobDsl();

        FreeStyleBuild build = project.scheduleBuild2(0)
                                      .get(10, TimeUnit.SECONDS);

        jenkins.assertBuildStatusSuccess(build);

        TopLevelItem item = jenkins.jenkins.getItem(testName.getMethodName());
        verifyNotificationSend(jira, item, "POST");
        verifyNotificationSend(jira2, item, "POST");

        verifyNotificationSend(jira, build, "PUT");
        verifyNotificationSend(jira2, build, "POST");
    }

    public void configureJobDsl()
    {
        ExecuteDslScripts executeDslScripts = new ExecuteDslScripts();
        executeDslScripts.setUseScriptText(true);
        executeDslScripts.setScriptText("job('" + testName.getMethodName() + "') {\n  steps {\n    shell('echo Hello World!')\n  }\n}");
        project.getBuildersList()
               .add(executeDslScripts);
    }
}
