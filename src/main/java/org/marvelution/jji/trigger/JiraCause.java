package org.marvelution.jji.trigger;

import org.marvelution.jji.Messages;

import hudson.model.*;
import org.kohsuke.stapler.export.*;

/**
 * {@link Cause} used when a build is triggered through Jira.
 *
 * @author Mark Rekveld
 * @since 3.3.0
 */
public class JiraCause
		extends Cause
{

	private final String issueKey;
	private final String by;

	public JiraCause(
			String issueKey,
			String by)
	{
		this.issueKey = issueKey;
		this.by = by;
	}

	@Override
	public String getShortDescription()
	{
		return Messages.cause_triggered_through(by, issueKey);
	}

	@Exported(visibility = 3)
	public String getIssueKey()
	{
		return issueKey;
	}

	@Exported(visibility = 3)
	public String getBy()
	{
		return by;
	}
}
