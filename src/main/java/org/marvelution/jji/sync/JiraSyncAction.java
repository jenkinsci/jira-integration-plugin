package org.marvelution.jji.sync;

import javax.servlet.*;
import java.io.*;
import java.util.function.*;

import org.marvelution.jji.Messages;
import org.marvelution.jji.*;
import org.marvelution.jji.configuration.*;

import hudson.model.*;
import hudson.security.*;
import net.sf.json.*;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.interceptor.*;

public abstract class JiraSyncAction<S extends Saveable & AccessControlled>
        implements Action
{
    protected final SitesClient sitesClient;
    protected final S target;

    protected JiraSyncAction(
            SitesClient sitesClient,
            S target)
    {
        this.target = target;
        this.sitesClient = sitesClient;
    }

    @Override
    public String getIconFileName()
    {
        return "/plugin/" + JiraIntegrationPlugin.SHORT_NAME + "/images/48x48/jji.png";
    }

    @Override
    public String getDisplayName()
    {
        return Messages.jira_sync();
    }

    @Override
    public String getUrlName()
    {
        return "syncWithJira";
    }

    public S getTarget()
    {
        return target;
    }

    @RequirePOST
    public synchronized void doSubmit(
            StaplerRequest request,
            StaplerResponse response)
            throws IOException, ServletException
    {
        target.getACL()
                .checkPermission(Item.CONFIGURE);

        JSONObject form = request.getSubmittedForm();

        // TODO Build filter from request
        sync(site -> true);

        response.sendRedirect(".");
    }

    protected abstract void sync(Predicate<JiraSite> siteFilter);

    @SuppressWarnings("unchecked")
    protected void syncItem(
            Predicate<JiraSite> siteFilter,
            Item item)
    {
        for (Job<? extends Job<?, ?>, ? extends Run<?, ?>> job : item.getAllJobs())
        {
            sitesClient.syncJob(siteFilter, job);
            for (Run<?, ?> build : job.getBuilds()
                    .completedOnly())
            {
                syncRun(siteFilter, build);
            }
        }
    }

    protected void syncRun(
            Predicate<JiraSite> siteFilter,
            Run<?, ?> run)
    {
        sitesClient.syncBuild(siteFilter, run);
    }

    public static class ItemJiraSyncAction
            extends JiraSyncAction<Item>
    {
        public ItemJiraSyncAction(
                SitesClient sitesClient,
                Item target)
        {
            super(sitesClient, target);
        }

        @Override
        protected void sync(Predicate<JiraSite> siteFilter)
        {
            syncItem(siteFilter, target);
        }
    }

    @SuppressWarnings("rawtypes")
    public static class BuildJiraSyncAction
            extends JiraSyncAction<Run>
    {
        public BuildJiraSyncAction(
                SitesClient sitesClient,
                Run target)
        {
            super(sitesClient, target);
        }

        @Override
        protected void sync(Predicate<JiraSite> siteFilter)
        {
            syncRun(siteFilter, target);
        }
    }

    public static class ViewJiraSyncAction
            extends JiraSyncAction<View>
    {
        public ViewJiraSyncAction(
                SitesClient sitesClient,
                View view)
        {
            super(sitesClient, view);
        }

        @Override
        protected void sync(Predicate<JiraSite> siteFilter)
        {
            target.getAllItems()
                    .forEach(item -> syncItem(siteFilter, item));
        }
    }
}
