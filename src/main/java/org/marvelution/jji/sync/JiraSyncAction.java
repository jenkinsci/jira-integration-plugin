package org.marvelution.jji.sync;

import jakarta.servlet.*;
import java.io.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.logging.Logger;
import java.util.stream.*;

import org.marvelution.jji.Messages;
import org.marvelution.jji.*;
import org.marvelution.jji.configuration.*;

import hudson.model.*;
import hudson.security.*;
import hudson.util.*;
import net.sf.json.*;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.interceptor.*;
import org.slf4j.*;

import static hudson.util.QuotedStringTokenizer.*;

public abstract class JiraSyncAction<S extends Saveable & AccessControlled>
        implements Action
{
    private final Logger logger = Logger.getLogger(getClass().getName());
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

    public abstract String getTargetDisplayName();

    public Set<JiraSite> getSites()
    {
        return sitesClient.getSites();
    }

    @RequirePOST
    public synchronized void doSubmit(
            StaplerRequest2 request,
            StaplerResponse2 response)
            throws IOException, ServletException
    {
        target.getACL()
                .checkPermission(Item.CONFIGURE);

        try
        {
            Set<String> selectedSites = new HashSet<>();

            JSONObject form = request.getSubmittedForm();
            Object jsonSite = form.get("site");
            if (jsonSite instanceof String)
            {
                selectedSites.add((String) jsonSite);
            }
            else if (jsonSite instanceof JSONArray)
            {
                selectedSites.addAll(((JSONArray) jsonSite).stream().map(Object::toString).collect(Collectors.toList()));
            }

            if (selectedSites.isEmpty())
            {
                generateResponse(request, response, Messages.no_sites_selected(), "WARNING");
            }
            else
            {
                sync(site -> selectedSites.contains(site.getIdentifier()));

                generateResponse(request, response, Messages.triggered_sync_of(getTargetDisplayName()), "OK");
            }
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, "Failed to synchronize " + getTargetDisplayName() + " with Jira; " + e.getMessage(), e);
            generateResponse(request, response, Messages.unable_to_trigger_sync_of(getTargetDisplayName()), "ERROR");
        }

    }

    private void generateResponse(
            StaplerRequest2 request,
            StaplerResponse2 response,
            String message,
            String type)
            throws IOException, ServletException
    {
        FormApply.applyResponse("notificationBar.show(" + quote(message) + ",notificationBar." + type + ")")
                .generateResponse(request, response, this);
    }

    protected abstract void sync(Predicate<JiraSite> siteFilter);

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
        public String getTargetDisplayName()
        {
            return target.getFullDisplayName();
        }

        @Override
        protected void sync(Predicate<JiraSite> siteFilter)
        {
            sitesClient.syncJob(siteFilter, target);
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
        public String getTargetDisplayName()
        {
            return target.getFullDisplayName();
        }

        @Override
        protected void sync(Predicate<JiraSite> siteFilter)
        {
            sitesClient.syncBuild(siteFilter, target);
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
        public String getTargetDisplayName()
        {
            return target.getViewName() + " of " + target.getOwner()
                    .getDisplayName();
        }

        @Override
        protected void sync(Predicate<JiraSite> siteFilter)
        {
            target.getAllItems()
                    .forEach(item -> sitesClient.syncJob(siteFilter, item));
        }
    }
}
