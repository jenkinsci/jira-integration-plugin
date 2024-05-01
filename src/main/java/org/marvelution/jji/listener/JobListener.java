package org.marvelution.jji.listener;

import javax.inject.*;

import org.marvelution.jji.*;
import org.marvelution.jji.events.JobNotificationType;

import hudson.*;
import hudson.model.*;
import hudson.model.listeners.*;

@Extension
public class JobListener
		extends ItemListener
{

	private SitesClient client;

	@Inject
	public void setClient(SitesClient client)
	{
		this.client = client;
	}

	@Override
	public void onCreated(Item item)
	{
		client.notifyJobCreated(item);
	}

	@Override
	public void onDeleted(Item item)
	{
		client.notifyJobDeleted(item);
	}

	@Override
	public void onLocationChanged(
			Item item,
			String oldFullName,
			String newFullName)
	{
		String jobHash = JiraUtils.getJobHash(item);
		// Execute the notification in a separate thread so url generation doesn't look at the current request.
		new Thread(() -> client.notifyJobMoved(jobHash, item)).start();
	}

	@Override
	public void onUpdated(Item item)
	{
		client.notifyJobModified(item, JobNotificationType.JOB_MODIFIED);
	}
}
