package org.marvelution.jji.listener;

import javax.inject.*;

import org.marvelution.jji.*;

import hudson.*;
import hudson.model.*;
import javaposse.jobdsl.plugin.*;

@Extension(optional = true)
public class JobDSLListener
		extends ContextExtensionPoint
{

	private SitesClient client;

	@Inject
	public void setClient(SitesClient client)
	{
		this.client = client;
	}

	@Override
	public void notifyItemCreated(
			Item item,
			DslEnvironment dslEnvironment)
	{
		client.notifyJobCreated(item);
	}

	@Override
	public void notifyItemUpdated(
			Item item,
			DslEnvironment dslEnvironment)
	{
		client.notifyJobModified(item);
	}
}
