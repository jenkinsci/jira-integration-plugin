package org.marvelution.jji.marker;

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
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

import static org.apache.commons.lang3.StringUtils.*;
import static org.marvelution.jji.Messages.deployment_build_marker;

/**
 * {@link Recorder}/{@link SimpleBuildStep} that marks a build as a deployment to a specific environment.
 *
 * @author Mark Rekveld
 * @since 3.8.0
 */
public class DeploymentBuildMarker
        extends Recorder
        implements SimpleBuildStep, Serializable
{

    private static final long serialVersionUID = 1L;
    private static final int ID_MAX_LENGTH = 40;
    private final String environmentId;
    private final String environmentName;
    private final Environment.Type environmentType;

    @DataBoundConstructor
    public DeploymentBuildMarker(
            String environmentId,
            String environmentName,
            String environmentType)
    {
        this.environmentType = Environment.Type.fromString(environmentType);
        if (isNotBlank(environmentName))
        {
            this.environmentName = environmentName;
        }
        else
        {
            this.environmentName = this.environmentType.name();
        }
        if (isNotBlank(environmentId))
        {
            this.environmentId = environmentId.length() > ID_MAX_LENGTH ? generateId(environmentId) : environmentId;
        }
        else
        {
            this.environmentId = generateId(this.environmentName);
        }
    }

    public String getEnvironmentId()
    {
        return environmentId;
    }

    public String getEnvironmentName()
    {
        return environmentName;
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
    {
        perform(build, listener);
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
        perform(build, listener);
    }

    private void perform(
            Run<?, ?> build,
            TaskListener listener)
    {
        Environment environment = new Environment(environmentId, environmentName, environmentType);
        listener.getLogger()
                .format("Marking %s as deployment to %s", build, environment);
        build.addAction(new DeploymentEnvironmentAction(environment));
    }

    private static String generateId(String environmentId)
    {
        return UUID.nameUUIDFromBytes(environmentId.getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    @Extension
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
                return FormValidation.validateRequired(value);
            }
            else if (length(value.trim()) > 255)
            {
                return FormValidation.error(org.marvelution.jji.Messages.maximum_length(255));
            }
            else
            {
                return FormValidation.ok();
            }
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
