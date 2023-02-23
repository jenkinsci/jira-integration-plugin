package org.marvelution.jji.trigger;

import hudson.model.*;

import static org.marvelution.jji.JiraPlugin.*;

@SuppressWarnings("lgtm[jenkins/plaintext-storage]")
public class JiraIssueAction
		implements Action
{

	private final String issueUrl;
	private final String issueKey;

	public JiraIssueAction(
			String issueUrl,
			String issueKey)
	{
		this.issueUrl = issueUrl;
		this.issueKey = issueKey;
	}

	@Override
	public String getIconFileName()
	{
		return "/plugin/" + SHORT_NAME + "/images/24x24/jji.png";
	}

	@Override
	public String getDisplayName()
	{
		return issueKey;
	}

	@Override
	public String getUrlName()
	{
		return issueUrl;
	}
}
