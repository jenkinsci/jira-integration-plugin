package org.marvelution.jji.listener;

import java.util.logging.*;
import javax.inject.*;

import org.marvelution.jji.*;

import hudson.*;
import hudson.model.*;
import hudson.model.listeners.*;
import hudson.util.*;

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

	private static final Logger LOGGER = Logger.getLogger(RunSaveListener.class.getName());
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
			client.notifyBuildCompleted((Run) o, new LogTaskListener(LOGGER, Level.INFO));
		}
	}
}
