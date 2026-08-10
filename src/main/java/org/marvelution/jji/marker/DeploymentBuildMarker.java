package org.marvelution.jji.marker;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.marvelution.jji.export.DeploymentEnvironmentAction;
import org.marvelution.jji.export.Environment;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import static org.apache.commons.lang3.StringUtils.*;
import static org.marvelution.jji.Messages.deployment_build_marker;

/**
 * {@link Recorder}/{@link SimpleBuildStep} that marks a build as a deployment to a specific environment.
 * <p>
 * In a pipeline the step is available under the {@code jiraDeploymentInfo} symbol, requiring only the
 * {@code environmentType}:
 * <pre>
 *     jiraDeploymentInfo environmentType: 'staging'
 *     jiraDeploymentInfo environmentId: 'app-stg-eu-west-1', environmentName: 'Staging', environmentType: 'staging'
 * </pre>
 * The {@code environmentId} and {@code environmentName} values support environment-variable expansion, so
 * dynamic values such as {@code environmentType: env.TARGET_ENVIRONMENT} work as expected.
 *
 * @author Mark Rekveld
 * @since 3.8.0
 */
@Symbol("jiraDeploymentInfo")
public class DeploymentBuildMarker
        extends Recorder
        implements SimpleBuildStep, Serializable
{

    private static final long serialVersionUID = 2L;
    private static final int ID_MAX_LENGTH = 40;
    private static final int NAME_MAX_LENGTH = 255;
    private final Environment.Type environmentType;
    private String environmentId;
    private String environmentName;

    @DataBoundConstructor
    public DeploymentBuildMarker(String environmentType)
    {
        this.environmentType = Environment.Type.fromString(environmentType);
    }

    public String getEnvironmentId()
    {
        return environmentId;
    }

    @DataBoundSetter
    public void setEnvironmentId(String environmentId)
    {
        this.environmentId = trimToNull(environmentId);
    }

    public String getEnvironmentName()
    {
        return environmentName;
    }

    @DataBoundSetter
    public void setEnvironmentName(String environmentName)
    {
        this.environmentName = trimToNull(environmentName);
    }

    public String getEnvironmentType()
    {
        return environmentType.name();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService()
    {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(
            AbstractBuild<?, ?> build,
            Launcher launcher,
            BuildListener listener)
            throws InterruptedException, IOException
    {
        perform(build, build.getEnvironment(listener), listener);
        return true;
    }

    @Override
    public void perform(
            @NonNull
            Run<?, ?> build,
            @Nullable
            FilePath workspace,
            @NonNull
            EnvVars env,
            @NonNull
            Launcher launcher,
            @NonNull
            TaskListener listener)
    {
        perform(build, env, listener);
    }

    public void perform(
            @NonNull
            Run<?, ?> build,
            @NonNull
            EnvVars env,
            @NonNull
            TaskListener listener)
    {
        String name = isNotBlank(environmentName) ? env.expand(environmentName) : environmentType.name();
        String id;
        if (isNotBlank(environmentId))
        {
            String expanded = env.expand(environmentId);
            id = expanded.length() > ID_MAX_LENGTH ? generateId(expanded) : expanded;
        }
        else
        {
            id = generateId(name);
        }

        Environment environment = new Environment(id, name, environmentType);
        listener.getLogger()
                .format("Marking %s as deployment to %s%n", build, environment);
        build.addAction(new DeploymentEnvironmentAction(environment));
    }

    private static String generateId(String environmentId)
    {
        return UUID.nameUUIDFromBytes(environmentId.getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    @Extension
    @Symbol("jiraDeploymentInfo")
    public static class Descriptor
            extends BuildStepDescriptor<Publisher>
    {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType)
        {
            return true;
        }

        @Override
        public String getDisplayName()
        {
            return deployment_build_marker();
        }

        @SuppressWarnings({"lgtm[jenkins/csrf]",
                "lgtm[jenkins/no-permission-check]"})
        public FormValidation doCheckEnvironmentId(
                @QueryParameter
                String value)
        {
            if (isNotBlank(value) && length(value.trim()) > ID_MAX_LENGTH)
            {
                return FormValidation.error(org.marvelution.jji.Messages.maximum_length(ID_MAX_LENGTH));
            }
            else if (containsWhitespace(value))
            {
                return FormValidation.error(org.marvelution.jji.Messages.no_whitespaces_allowed());
            }
            else
            {
                return FormValidation.ok();
            }
        }

        @SuppressWarnings({"lgtm[jenkins/csrf]",
                "lgtm[jenkins/no-permission-check]"})
        public FormValidation doCheckEnvironmentName(
                @QueryParameter
                String value)
        {
            if (isBlank(value))
            {
                // Optional: falls back to the environment type when left blank.
                return FormValidation.ok();
            }
            else if (length(value.trim()) > NAME_MAX_LENGTH)
            {
                return FormValidation.error(org.marvelution.jji.Messages.maximum_length(NAME_MAX_LENGTH));
            }
            else
            {
                return FormValidation.ok();
            }
        }

        @SuppressWarnings({"lgtm[jenkins/csrf]",
                "lgtm[jenkins/no-permission-check]"})
        public FormValidation doCheckEnvironmentType(
                @QueryParameter
                String value)
        {
            if (isBlank(value))
            {
                return FormValidation.error(org.marvelution.jji.Messages.environment_type_required());
            }
            for (Environment.Type type : Environment.Type.values())
            {
                if (type.name()
                        .equalsIgnoreCase(value.trim()))
                {
                    return FormValidation.ok();
                }
            }
            return FormValidation.error(org.marvelution.jji.Messages.invalid_environment_type(value.trim()));
        }

        @SuppressWarnings({"lgtm[jenkins/csrf]",
                "lgtm[jenkins/no-permission-check]"})
        public ListBoxModel doFillEnvironmentTypeItems()
        {
            ListBoxModel items = new ListBoxModel();
            for (Environment.Type type : Environment.Type.values())
            {
                items.add(type.name());
            }
            return items;
        }
    }
}
