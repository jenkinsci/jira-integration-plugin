package org.marvelution.jji.marker;

import java.util.UUID;

import org.marvelution.jji.export.DeploymentEnvironmentAction;
import org.marvelution.jji.export.Environment;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.ParametersAction;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DeploymentBuildMarker}: verifies the pipeline {@code jiraDeploymentInfo} symbol,
 * default derivation of name/id, environment-variable expansion, freestyle usage, and the
 * descriptor form validation.
 */
@WithJenkins
class DeploymentBuildMarkerTest
{

    private static String expectedId(String seed)
    {
        return UUID.nameUUIDFromBytes(seed.getBytes(UTF_8))
                .toString();
    }

    private static DeploymentEnvironmentAction actionOf(hudson.model.Run<?, ?> run)
    {
        DeploymentEnvironmentAction action = run.getAction(DeploymentEnvironmentAction.class);
        assertThat(action).as("DeploymentEnvironmentAction present")
                .isNotNull();
        return action;
    }

    @Test
    void pipelineWithOnlyTypeDerivesNameAndId(JenkinsRule jenkins) throws Exception
    {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "only-type");
        job.setDefinition(new CpsFlowDefinition(
                "node { jiraDeploymentInfo environmentType: 'staging' }", true));

        WorkflowRun run = jenkins.buildAndAssertSuccess(job);

        Environment environment = actionOf(run).getEnvironment();
        // Name falls back to the type; id is generated from the name.
        assertThat(environment.getType()).isEqualTo(Environment.Type.staging);
        assertThat(environment.getName()).isEqualTo("staging");
        assertThat(environment.getId()).isEqualTo(expectedId("staging"));
    }

    @Test
    void pipelineWithExplicitValuesArePreserved(JenkinsRule jenkins) throws Exception
    {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "explicit");
        job.setDefinition(new CpsFlowDefinition(
                "node { jiraDeploymentInfo environmentId: 'app-stg-eu-west-1', " +
                "environmentName: 'Staging', environmentType: 'staging' }", true));

        WorkflowRun run = jenkins.buildAndAssertSuccess(job);

        Environment environment = actionOf(run).getEnvironment();
        assertThat(environment.getId()).isEqualTo("app-stg-eu-west-1");
        assertThat(environment.getName()).isEqualTo("Staging");
        assertThat(environment.getType()).isEqualTo(Environment.Type.staging);
    }

    @Test
    void pipelineExpandsEnvironmentVariables(JenkinsRule jenkins) throws Exception
    {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "expand");
        job.setDefinition(new CpsFlowDefinition(
                "node { withEnv(['TARGET=production']) { " +
                "jiraDeploymentInfo environmentName: \"App ${env.TARGET}\", environmentType: 'production' } }", true));

        WorkflowRun run = jenkins.buildAndAssertSuccess(job);

        Environment environment = actionOf(run).getEnvironment();
        assertThat(environment.getName()).isEqualTo("App production");
        assertThat(environment.getType()).isEqualTo(Environment.Type.production);
    }

    @Test
    void longEnvironmentIdIsHashed(JenkinsRule jenkins) throws Exception
    {
        String longId = "this-environment-id-is-definitely-longer-than-forty-characters";
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "long-id");
        job.setDefinition(new CpsFlowDefinition(
                "node { jiraDeploymentInfo environmentId: '" + longId + "', environmentType: 'staging' }", true));

        WorkflowRun run = jenkins.buildAndAssertSuccess(job);

        Environment environment = actionOf(run).getEnvironment();
        assertThat(environment.getId()).isEqualTo(expectedId(longId));
    }

    @Test
    void unknownTypeFallsBackToUnmapped(JenkinsRule jenkins) throws Exception
    {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "unknown-type");
        job.setDefinition(new CpsFlowDefinition(
                "node { jiraDeploymentInfo environmentType: 'qa' }", true));

        WorkflowRun run = jenkins.buildAndAssertSuccess(job);

        Environment environment = actionOf(run).getEnvironment();
        assertThat(environment.getType()).isEqualTo(Environment.Type.unmapped);
    }

    @Test
    void freestyleJobMarksDeployment(JenkinsRule jenkins) throws Exception
    {
        FreeStyleProject project = jenkins.createFreeStyleProject("freestyle");
        DeploymentBuildMarker marker = new DeploymentBuildMarker("production");
        marker.setEnvironmentName("Production");
        project.getPublishersList()
                .add(marker);

        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);

        Environment environment = actionOf(build).getEnvironment();
        assertThat(environment.getType()).isEqualTo(Environment.Type.production);
        assertThat(environment.getName()).isEqualTo("Production");
        assertThat(environment.getId()).isEqualTo(expectedId("Production"));
    }

    @Test
    void freestyleExpandsBuildParameter(JenkinsRule jenkins) throws Exception
    {
        FreeStyleProject project = jenkins.createFreeStyleProject("freestyle-expand");
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("TARGET", "staging")));
        DeploymentBuildMarker marker = new DeploymentBuildMarker("staging");
        marker.setEnvironmentName("App ${TARGET}");
        project.getPublishersList()
                .add(marker);

        FreeStyleBuild build = jenkins.assertBuildStatusSuccess(project.scheduleBuild2(0,
                new ParametersAction(new StringParameterValue("TARGET", "staging"))));

        Environment environment = actionOf(build).getEnvironment();
        assertThat(environment.getName()).isEqualTo("App staging");
    }

    @Test
    void configRoundTripPreservesValues(JenkinsRule jenkins) throws Exception
    {
        FreeStyleProject project = jenkins.createFreeStyleProject("round-trip");
        DeploymentBuildMarker marker = new DeploymentBuildMarker("staging");
        marker.setEnvironmentId("app-stg-eu-west-1");
        marker.setEnvironmentName("Staging");
        project.getPublishersList()
                .add(marker);

        project = jenkins.configRoundtrip(project);

        DeploymentBuildMarker after = project.getPublishersList()
                .get(DeploymentBuildMarker.class);
        assertThat(after.getEnvironmentType()).isEqualTo("staging");
        assertThat(after.getEnvironmentName()).isEqualTo("Staging");
        assertThat(after.getEnvironmentId()).isEqualTo("app-stg-eu-west-1");
    }

    @Test
    void descriptorValidatesEnvironmentType(JenkinsRule jenkins)
    {
        DeploymentBuildMarker.Descriptor descriptor = jenkins.getInstance()
                .getDescriptorByType(DeploymentBuildMarker.Descriptor.class);

        assertThat(descriptor.doCheckEnvironmentType("staging").kind).isEqualTo(FormValidation.Kind.OK);
        assertThat(descriptor.doCheckEnvironmentType("").kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(descriptor.doCheckEnvironmentType("nope").kind).isEqualTo(FormValidation.Kind.ERROR);
    }

    @Test
    void descriptorValidatesEnvironmentId(JenkinsRule jenkins)
    {
        DeploymentBuildMarker.Descriptor descriptor = jenkins.getInstance()
                .getDescriptorByType(DeploymentBuildMarker.Descriptor.class);

        assertThat(descriptor.doCheckEnvironmentId("app-stg-eu-west-1").kind).isEqualTo(FormValidation.Kind.OK);
        assertThat(descriptor.doCheckEnvironmentId("has space").kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(descriptor.doCheckEnvironmentId(
                "this-environment-id-is-definitely-longer-than-forty-characters").kind)
                .isEqualTo(FormValidation.Kind.ERROR);
    }
}
