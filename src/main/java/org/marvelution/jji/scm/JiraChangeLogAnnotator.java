package org.marvelution.jji.scm;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.annotation.*;
import javax.inject.*;

import org.marvelution.jji.*;

import com.google.common.cache.*;
import hudson.*;
import hudson.model.*;
import hudson.scm.*;

/**
 * {@link ChangeLogAnnotator} implementation that annotated Jira issues keys that of related Entity Links
 *
 * @author Mark Rekveld
 * @since 1.2.0
 */
@Extension
public class JiraChangeLogAnnotator
		extends ChangeLogAnnotator
{

	private SitesClient client;
	private final LoadingCache<CacheKey, Map<String, String>> cache = CacheBuilder.newBuilder()
			.expireAfterAccess(1, TimeUnit.HOURS)
			.build(new CacheLoader<CacheKey, Map<String, String>>()
			{
				@Override
				public Map<String, String> load(@Nonnull CacheKey cacheKey)
				{
					return client.getIssueLinks(cacheKey.jobHash, cacheKey.buildNumber);
				}
			});

	@Override
	public void annotate(
			Run<?, ?> build,
			@Nullable ChangeLogSet.Entry entry,
			MarkupText markupText)
	{
		Map<String, String> issueLinks = cache.getUnchecked(new CacheKey(build));
		if (!issueLinks.isEmpty())
		{
			Pattern pattern = Pattern.compile("(" + String.join("|", issueLinks.keySet()) + ")");
			Matcher matcher = pattern.matcher(markupText.getText());
			while (matcher.find())
			{
				if (issueLinks.containsKey(matcher.group()))
				{
					markupText.addMarkup(matcher.start(), matcher.end(), "<a href='" + issueLinks.get(matcher.group()) + "'>", "</a>");
				}
			}
		}
	}

	@Inject
	public void setClient(SitesClient client)
	{
		this.client = client;
	}

	static class CacheKey
	{

		final String jobHash;
		final int buildNumber;

		CacheKey(
				String jobHash,
				int buildNumber)
		{
			this.jobHash = jobHash;
			this.buildNumber = buildNumber;
		}

		CacheKey(Run<?, ?> build)
		{
			this(JiraUtils.getJobHash(build), build.getNumber());
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(jobHash, buildNumber);
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (o == null || getClass() != o.getClass())
			{
				return false;
			}
			CacheKey cacheKey = (CacheKey) o;
			return buildNumber == cacheKey.buildNumber && Objects.equals(jobHash, cacheKey.jobHash);
		}
	}
}
