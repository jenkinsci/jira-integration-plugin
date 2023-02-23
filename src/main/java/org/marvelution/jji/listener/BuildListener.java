package org.marvelution.jji.listener;

import javax.annotation.*;
import javax.inject.*;

import org.marvelution.jji.*;

import hudson.*;
import hudson.model.*;
import hudson.model.listeners.*;

/**
 * {@link RunListener} implementation that will notify all registered Jira instances that a build completed or was deleted.
 *
 * @author Mark Rekveld
 * @since 3.5.0
 */
@Extension
public class BuildListener
		extends RunListener<Run>
{

	private SitesClient client;

	@Inject
	public void setClient(SitesClient client)
	{
		this.client = client;
	}

	@Override
	public void onCompleted(
			Run run,
			@Nonnull TaskListener listener)
	{
		client.notifyBuildCompleted(run, listener);
	}

	@Override
	public void onDeleted(Run run)
	{
		client.notifyBuildDeleted(run);
	}
}
