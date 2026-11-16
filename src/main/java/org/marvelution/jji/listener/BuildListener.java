package org.marvelution.jji.listener;

import java.util.concurrent.*;
import javax.annotation.*;
import javax.inject.*;

import org.marvelution.jji.*;

import hudson.*;
import hudson.model.*;
import hudson.model.listeners.*;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;

/**
 * {@link RunListener} implementation that will notify all registered Jira instances that a build started, completed or was
 * deleted.
 *
 * @author Mark Rekveld
 * @since 3.5.0
 */
@Extension
public class BuildListener
		extends RunListener<Run>
{

	/**
	 * How many seconds after its start a build that still runs is notified as started. Builds that complete sooner are only
	 * notified once completed, so their completion can't overtake the synchronization of their start.
	 */
	static long startNotificationDelay = SystemProperties.getLong(BuildListener.class.getName() + ".startNotificationDelay", 30L);

	private SitesClient client;

	@Inject
	public void setClient(SitesClient client)
	{
		this.client = client;
	}

	@Override
	public void onStarted(
			Run run,
			TaskListener listener)
	{
		Timer.get()
				.schedule(() -> {
					if (run.isBuilding())
					{
						client.notifyBuildStarted(run, listener);
					}
				}, startNotificationDelay, TimeUnit.SECONDS);
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
