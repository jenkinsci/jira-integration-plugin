package org.marvelution.jji;

import javax.inject.*;
import java.io.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;

import org.marvelution.jji.configuration.*;

import com.fasterxml.jackson.core.type.*;
import com.fasterxml.jackson.databind.*;
import hudson.model.*;
import hudson.util.*;
import okhttp3.*;

@SuppressWarnings("resource")
public class SitesClient
{

	public static final TypeReference<Map<String, String>> LINKS_TYPE = new TypeReference<>() {};
	public static final String SYNC_RESULT_HEADER = "X-Sync-Result-Id";
	private static final Logger LOGGER = Logger.getLogger(SitesClient.class.getName());
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

	public Map<String, String> getIssueLinks(
			String jobHash,
			int buildNumber)
	{
		return getIssueLinks(site -> true, jobHash, buildNumber);
	}

	public Map<String, String> getIssueLinks(
			Predicate<JiraSite> siteFilter,
			String jobHash,
			int buildNumber)
	{
		Map<String, String> issueLinks = new HashMap<>();
		doWithSites(siteFilter, site -> {
			try (Response response = httpClient.newCall(site.createGetIssueLinksRequest(jobHash, buildNumber)).execute())
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
					LOGGER.log(Level.SEVERE, "Jira Site {0} didn't respond with any links; {1} {2}",
					           new Object[] { site, response.code(), response.message() });
				}
			}
			catch (IOException e)
			{
				LOGGER.log(Level.SEVERE, String.format("Failed to get issue links from Jira Site: %s; %s", site, e.getMessage()), e);
			}
		});
		return issueLinks;
	}

	public void syncBuild(
			Predicate<JiraSite> siteFilter,
			Run<?, ?> run)
	{
		notifyBuildCompleted(siteFilter, run, new LogTaskListener(LOGGER, Level.INFO));
	}

	public void syncJob(
			Predicate<JiraSite> siteFilter,
			Item item)
	{
		notifyJobModified(siteFilter, item);
	}

	public void notifyBuildCompleted(
			Run<?, ?> run,
			TaskListener listener)
	{
		notifyBuildCompleted(site -> true, run, listener);
	}

	public void notifyBuildCompleted(
			Predicate<JiraSite> siteFilter,
			Run<?, ?> run,
			TaskListener listener)
	{
		doWithSites(siteFilter, site -> {
			try (Response response = httpClient.newCall(site.createNotifyBuildCompleted(run)).execute())
			{
				String syncRequestId = response.header(SYNC_RESULT_HEADER);
				if (response.isSuccessful())
				{
					listener.getLogger().printf("Notified %s that a build has completed (%s).%n", site.getName(), syncRequestId);
				}
				else
				{
					listener.error("Unable to notify %s: [%d] %s", site.getName(), response.code(), response.message());
				}
			}
			catch (Exception e)
			{
				listener.error("Failed to notify %s on this builds completion -> %s", site, e.getMessage());
			}
		});
	}

	public void notifyJobCreated(Item item)
	{
		notifyJobCreated(site -> true, item);
	}

	public void notifyJobCreated(
			Predicate<JiraSite> siteFilter,
			Item item)
	{
		doWithSites(siteFilter, site -> {
			try (Response response = httpClient.newCall(site.createNotifyJobCreatedRequest(item)).execute())
			{
				if (response.isSuccessful())
				{
					LOGGER.log(Level.FINE, "Notified {0} that {1} was created.",
					           new Object[] { site.getName(), item.getFullDisplayName() });
				}
				else
				{
					LOGGER.log(Level.WARNING, "Unable to notify {1} of creation of {0}; [{2}] {3}",
					           new Object[] { item.getFullDisplayName(), site.getName(), response.code(), response.message() });
				}
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Failed to notify {1} of creation of {0}; {2}",
				           new Object[] { item.getFullDisplayName(), site, e.getMessage() });
			}
		});
	}

	public void notifyJobModified(Item item)
	{
		notifyJobModified(site -> true, item);
	}

	public void notifyJobModified(
			Predicate<JiraSite> siteFilter,
			Item item)
	{
		doWithSites(siteFilter, site -> {
			try (Response response = httpClient.newCall(site.createNotifyJobModifiedRequest(item)).execute())
			{
				if (response.isSuccessful())
				{
					LOGGER.log(Level.FINE, "Notified {0} that {1} was modified.",
					           new Object[] { site.getName(), item.getFullDisplayName() });
				}
				else
				{
					LOGGER.log(Level.WARNING, "Unable to notify {1} of modification of {0}; [{2}] {3}",
					           new Object[] { item.getFullDisplayName(), site.getName(), response.code(), response.message() });
				}
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Failed to notify {1} of modification of {0}; {2}",
				           new Object[] { item.getFullDisplayName(), site, e.getMessage() });
			}
		});
	}

	public void notifyJobMoved(
			String oldJobHash,
			Item newItem)
	{
		notifyJobMoved(site -> true, oldJobHash, newItem);
	}

	public void notifyJobMoved(
			Predicate<JiraSite> siteFilter,
			String oldJobHash,
			Item newItem)
	{
		doWithSites(siteFilter, site -> {
			try (Response response = httpClient.newCall(site.createNotifyJobMovedRequest(oldJobHash, newItem)).execute())
			{
				if (response.isSuccessful())
				{
					LOGGER.log(Level.FINE, "Notified {0} that {1} was moved.",
					           new Object[] { site.getName(), newItem.getFullDisplayName() });
				}
				else
				{
					LOGGER.log(Level.WARNING, "Unable to notify {0} that {1} was moved; {2} [{3}]",
					           new Object[] { newItem.getFullDisplayName(), site.getName(), response.code(), response.message() });
				}
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Failed to notify {1} of the move of {0}; {2}",
				           new Object[] { newItem.getFullDisplayName(), site, e.getMessage() });
			}
		});
	}

	public void notifyJobDeleted(Item item)
	{
		notifyJobDeleted(site -> true, item);
	}

	public void notifyJobDeleted(
			Predicate<JiraSite> siteFilter,
			Item item)
	{
		notifyDeletion(siteFilter, site -> site.createNotifyJobDeletedRequest(item), item::getFullDisplayName);
	}

	public void notifyBuildDeleted(Run<?, ?> run)
	{
		notifyBuildDeleted(site -> true, run);
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
			try (Response response = httpClient.newCall(request.apply(site)).execute())
			{
				if (response.isSuccessful())
				{
					LOGGER.log(Level.FINE, "Notified {0} that {1} was deleted.", new Object[] { site.getName(), nameSupplier.get() });
				}
				else if (response.code() != 404)
				{
					LOGGER.log(Level.WARNING, "Unable to notify {1} of deletion of {0}; [{2}] {3}",
					           new Object[] { nameSupplier.get(), site.getName(), response.code(), response.message() });
				}
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Failed to notify {1} of deletion of {0}; {2}",
				           new Object[] { nameSupplier.get(), site, e.getMessage() });
			}
		});
	}

	private void doWithSites(
			Predicate<JiraSite> filter,
			Consumer<JiraSite> action)
	{
		sitesConfiguration.stream().filter(filter).forEach(action);
	}
}
