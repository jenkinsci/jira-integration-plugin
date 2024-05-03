package org.marvelution.jji.listener;

import javax.inject.Inject;

import org.marvelution.jji.SitesClient;
import org.marvelution.jji.events.JobNotificationType;

import hudson.Extension;
import hudson.model.Item;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslEnvironment;

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
        client.notifyJobModified(item, JobNotificationType.JOB_MODIFIED);
    }
}
