package org.marvelution.jji.sync;

import javax.annotation.*;
import javax.inject.*;
import java.util.*;

import org.marvelution.jji.*;

import hudson.*;
import hudson.model.*;
import jenkins.model.*;

@Extension
public class SyncViewTransientActionFactory
        extends TransientViewActionFactory
{
    private SitesClient sitesClient;

    @Inject
    public void setSitesClient(SitesClient sitesClient)
    {
        this.sitesClient = sitesClient;
    }

    @Nonnull
    @Override
    public List<Action> createFor(
            @Nonnull
            View view)
    {
        return Collections.singletonList(new JiraSyncAction.ViewJiraSyncAction(sitesClient, view));
    }
}
