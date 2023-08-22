package org.marvelution.jji.sync;

import javax.annotation.*;
import javax.inject.*;
import java.util.*;

import org.marvelution.jji.*;

import hudson.*;
import hudson.model.*;
import jenkins.model.*;

@Extension
@SuppressWarnings("rawtypes")
public class SyncJobTransientActionFactory
        extends TransientActionFactory<Job>
{
    private SitesClient sitesClient;

    @Inject
    public void setSitesClient(SitesClient sitesClient)
    {
        this.sitesClient = sitesClient;
    }

    @Override
    public Class<Job> type()
    {
        return Job.class;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(
            @Nonnull
            Job target)
    {
        return Collections.singleton(new JiraSyncAction.ItemJiraSyncAction(sitesClient, target));
    }
}
