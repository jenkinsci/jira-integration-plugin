package org.marvelution.jji.sync;

import javax.annotation.*;
import javax.inject.Inject;
import java.util.*;

import org.marvelution.jji.*;

import com.google.inject.*;
import hudson.*;
import hudson.model.*;
import jenkins.model.*;

@Extension
@SuppressWarnings("rawtypes")
public class SyncBuildTransientActionFactory
        extends TransientActionFactory<Run>
{
    private SitesClient sitesClient;

    @Inject
    public void setSitesClient(SitesClient sitesClient)
    {
        this.sitesClient = sitesClient;
    }

    @Override
    public Class<Run> type()
    {
        return Run.class;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(
            @Nonnull
            Run run)
    {
        return Collections.singleton(new JiraSyncAction.BuildJiraSyncAction(sitesClient, run));
    }
}
