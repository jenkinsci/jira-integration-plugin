package org.marvelution.jji.listener;

import javax.inject.*;

import org.marvelution.jji.*;

import hudson.*;
import hudson.model.*;
import hudson.model.listeners.*;

/**
 * {@link SaveableListener} implementation that will notify all registered Jira instances that a {@link Run} was updated/saved after it
 * completed its build.
 *
 * @author Mark Rekveld
 * @since 3.9.0
 */
@Extension
public class RunSaveListener
		extends SaveableListener
{

	private SitesClient client;

	@Inject
	public void setClient(SitesClient client)
	{
		this.client = client;
	}

	@Override
	public void onChange(
			Saveable o,
			XmlFile file)
	{
		if (o instanceof Run && !((Run) o).isLogUpdated())
		{
			client.notifyBuildCompleted((Run) o, null);
		}
	}
}
