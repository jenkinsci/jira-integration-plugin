package org.marvelution.jji.export;

import hudson.model.*;
import org.kohsuke.stapler.export.*;

/**
 * {@link Action} that exposes the deployment {@link Environment} details.
 *
 * @author Mark Rekveld
 * @since 3.8.0
 */
@ExportedBean
public class DeploymentEnvironmentAction
		implements Action
{

	private final Environment environment;

	public DeploymentEnvironmentAction(Environment environment)
	{
		this.environment = environment;
	}

	@Exported(visibility = 2)
	public Environment getEnvironment()
	{
		return environment;
	}

	@Override
	public String getIconFileName()
	{
		return null;
	}

	@Override
	public String getDisplayName()
	{
		return null;
	}

	@Override
	public String getUrlName()
	{
		return null;
	}
}
