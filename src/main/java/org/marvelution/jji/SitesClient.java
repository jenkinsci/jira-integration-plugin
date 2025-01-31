package org.marvelution.jji;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

import org.marvelution.jji.configuration.JiraSite;
import org.marvelution.jji.configuration.JiraSitesConfiguration;
import org.marvelution.jji.events.JobNotificationType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.NamingThreadFactory;
import jenkins.security.ImpersonatingExecutorService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SitesClient
{

    public static final TypeReference<Map<String, String>> LINKS_TYPE = new TypeReference<>() {};
    public static final String SYNC_RESULT_HEADER = "X-Sync-Result-Id";
    private static final Logger LOGGER = Logger.getLogger(SitesClient.class.getName());
    private static final Predicate<JiraSite> DEFAULT_SITE_FILTER = site -> true;
    private final ExecutorService executor =
            new ImpersonatingExecutorService(Executors.newCachedThreadPool(new ExceptionCatchingThreadFactory(new NamingThreadFactory(new DaemonThreadFactory(),
                    "JiraSync"))), ACL.SYSTEM2);
    private final JiraSitesConfiguration sitesConfiguration;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Inject
    public SitesClient(
            JiraSitesConfiguration sitesConfiguration,
            OkHttpClient httpClient,
            ObjectMapper objectMapper)
    {
        this.sitesConfiguration = sitesConfiguration;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public Set<JiraSite> getSites()
    {
        return sitesConfiguration.getSites();
    }

    public void syncBuild(
            Predicate<JiraSite> siteFilter,
            Run<?, ?> run)
    {
        executor.execute(() -> notifyBuildCompleted(siteFilter, run, null));
    }

    @SuppressWarnings("unchecked")
    public void syncJob(
            Predicate<JiraSite> siteFilter,
            Item item)
    {
        executor.execute(() -> {
            for (Job<? extends Job<?, ?>, ? extends Run<?, ?>> job : item.getAllJobs())
            {
                notifyJobModified(siteFilter, item, JobNotificationType.JOB_SYNC);
                for (Run<?, ?> build : job.getBuilds()
                        .completedOnly())
                {
                    syncBuild(siteFilter, build);
                }
            }
        });
    }

    public Map<String, String> getIssueLinks(
            String jobHash,
            int buildNumber)
    {
        return getIssueLinks(DEFAULT_SITE_FILTER, jobHash, buildNumber);
    }

    public Map<String, String> getIssueLinks(
            Predicate<JiraSite> siteFilter,
            String jobHash,
            int buildNumber)
    {
        Map<String, String> issueLinks = new HashMap<>();
        doWithSites(siteFilter, site -> {
            try (Response response = httpClient.newCall(site.createGetIssueLinksRequest(jobHash, buildNumber))
                    .execute())
            {
                if (response.isSuccessful() && response.body() != null)
                {
                    try (ResponseBody body = response.body())
                    {
                        Map<String, String> links = objectMapper.readValue(body.bytes(), LINKS_TYPE);
                        if (links != null && !links.isEmpty())
                        {
                            issueLinks.putAll(links);
                        }
                    }
                }
                else
                {
                    LOGGER.log(Level.SEVERE,
                            "Jira Site {0} didn't respond with any links; {1} {2}",
                            new Object[]{site,
                                    response.code(),
                                    response.message()});
                }
            }
            catch (IOException e)
            {
                LOGGER.log(Level.SEVERE, String.format("Failed to get issue links from Jira Site: %s; %s", site, e.getMessage()), e);
            }
        });
        return issueLinks;
    }

    public void notifyBuildCompleted(
            Run<?, ?> run,
            TaskListener listener)
    {
        notifyBuildCompleted(DEFAULT_SITE_FILTER, run, listener);
    }

    public void notifyBuildCompleted(
            Predicate<JiraSite> siteFilter,
            Run<?, ?> run,
            TaskListener listener)
    {
        BuildLogger logger = new BuildLogger(listener);

        doWithSites(siteFilter, site -> {
            try (Response response = httpClient.newCall(site.createNotifyBuildCompleted(run))
                    .execute())
            {
                String syncRequestId = response.header(SYNC_RESULT_HEADER);
                if (response.isSuccessful())
                {
                    logger.info("Notified %s that a build has completed (%s).%n", site.getName(), syncRequestId);
                }
                else
                {
                    logger.error("Unable to notify %s: [%d] %s", site.getName(), response.code(), response.message());
                }
            }
            catch (Exception e)
            {
                logger.error("Failed to notify %s on this builds completion -> %s", site, e.getMessage());
            }
        });
    }

    public void notifyJobCreated(Item item)
    {
        notifyJobCreated(DEFAULT_SITE_FILTER, item);
    }

    public void notifyJobCreated(
            Predicate<JiraSite> siteFilter,
            Item item)
    {
        doWithSites(siteFilter, site -> {
            try (Response response = httpClient.newCall(site.createNotifyJobCreatedRequest(item))
                    .execute())
            {
                if (response.isSuccessful())
                {
                    LOGGER.log(Level.INFO,
                            "Notified {0} that {1} was created.",
                            new Object[]{site.getName(),
                                    item.getFullDisplayName()});
                }
                else
                {
                    LOGGER.log(Level.WARNING,
                            "Unable to notify {1} of creation of {0}; [{2}] {3}",
                            new Object[]{item.getFullDisplayName(),
                                    site.getName(),
                                    response.code(),
                                    response.message()});
                }
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING,
                        "Failed to notify {1} of creation of {0}; {2}",
                        new Object[]{item.getFullDisplayName(),
                                site,
                                e.getMessage()});
            }
        });
    }

    public void notifyJobModified(
            Item item,
            JobNotificationType notificationType)
    {
        notifyJobModified(DEFAULT_SITE_FILTER, item, notificationType);
    }

    public void notifyJobModified(
            Predicate<JiraSite> siteFilter,
            Item item,
            JobNotificationType notificationType)
    {
        doWithSites(siteFilter, site -> {
            try (Response response = httpClient.newCall(site.createNotifyJobRequest(item, notificationType))
                    .execute())
            {
                if (response.isSuccessful())
                {
                    LOGGER.log(Level.INFO,
                            "Notified {0} that {1} was modified.",
                            new Object[]{site.getName(),
                                    item.getFullDisplayName()});
                }
                else
                {
                    LOGGER.log(Level.WARNING,
                            "Unable to notify {1} of modification of {0}; [{2}] {3}",
                            new Object[]{item.getFullDisplayName(),
                                    site.getName(),
                                    response.code(),
                                    response.message()});
                }
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING,
                        "Failed to notify {1} of modification of {0}; {2}",
                        new Object[]{item.getFullDisplayName(),
                                site,
                                e.getMessage()});
            }
        });
    }

    public void notifyJobMoved(
            String oldJobHash,
            Item newItem)
    {
        notifyJobMoved(DEFAULT_SITE_FILTER, oldJobHash, newItem);
    }

    public void notifyJobMoved(
            Predicate<JiraSite> siteFilter,
            String oldJobHash,
            Item newItem)
    {
        doWithSites(siteFilter, site -> {
            try (Response response = httpClient.newCall(site.createNotifyJobMovedRequest(oldJobHash, newItem))
                    .execute())
            {
                if (response.isSuccessful())
                {
                    LOGGER.log(Level.INFO,
                            "Notified {0} that {1} was moved.",
                            new Object[]{site.getName(),
                                    newItem.getFullDisplayName()});
                }
                else
                {
                    LOGGER.log(Level.WARNING,
                            "Unable to notify {0} that {1} was moved; {2} [{3}]",
                            new Object[]{newItem.getFullDisplayName(),
                                    site.getName(),
                                    response.code(),
                                    response.message()});
                }
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING,
                        "Failed to notify {1} of the move of {0}; {2}",
                        new Object[]{newItem.getFullDisplayName(),
                                site,
                                e.getMessage()});
            }
        });
    }

    public void notifyJobDeleted(Item item)
    {
        notifyJobDeleted(DEFAULT_SITE_FILTER, item);
    }

    public void notifyJobDeleted(
            Predicate<JiraSite> siteFilter,
            Item item)
    {
        notifyDeletion(siteFilter, site -> site.createNotifyJobDeletedRequest(item), item::getFullDisplayName);
    }

    public void notifyBuildDeleted(Run<?, ?> run)
    {
        notifyBuildDeleted(DEFAULT_SITE_FILTER, run);
    }

    public void notifyBuildDeleted(
            Predicate<JiraSite> siteFilter,
            Run<?, ?> run)
    {
        notifyDeletion(siteFilter, site -> site.createNotifyBuildDeletedRequest(run), run::getFullDisplayName);
    }

    private void notifyDeletion(
            Predicate<JiraSite> siteFilter,
            Function<JiraSite, Request> request,
            Supplier<String> nameSupplier)
    {
        doWithSites(siteFilter, site -> {
            try (Response response = httpClient.newCall(request.apply(site))
                    .execute())
            {
                if (response.isSuccessful())
                {
                    LOGGER.log(Level.INFO,
                            "Notified {0} that {1} was deleted.",
                            new Object[]{site.getName(),
                                    nameSupplier.get()});
                }
                else if (response.code() != 404)
                {
                    LOGGER.log(Level.WARNING,
                            "Unable to notify {1} of deletion of {0}; [{2}] {3}",
                            new Object[]{nameSupplier.get(),
                                    site.getName(),
                                    response.code(),
                                    response.message()});
                }
            }
            catch (Exception e)
            {
                LOGGER.log(Level.WARNING,
                        "Failed to notify {1} of deletion of {0}; {2}",
                        new Object[]{nameSupplier.get(),
                                site,
                                e.getMessage()});
            }
        });
    }

    private void doWithSites(
            Predicate<JiraSite> filter,
            Consumer<JiraSite> action)
    {
        sitesConfiguration.stream()
                .filter(JiraSite::isEnabled)
                .filter(filter)
                .forEach(action);
    }

    @SuppressWarnings("resource")
    private static class BuildLogger
    {
        private final TaskListener taskListener;

        BuildLogger(TaskListener taskListener)
        {
            this.taskListener = taskListener;
        }

        void info(
                String format,
                Object... args)
        {
            String message = String.format(format, args);
            LOGGER.info(message);
            if (taskListener != null)
            {
                taskListener.getLogger()
                        .println(message);
            }
        }

        void error(
                String format,
                Object... args)
        {
            String message = String.format(format, args);
            LOGGER.log(Level.SEVERE, message);
            if (taskListener != null)
            {
                taskListener.error(message);
            }
        }
    }
}
