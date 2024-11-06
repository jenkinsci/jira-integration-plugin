package org.marvelution.jji.trigger;

import org.marvelution.jji.Messages;

import hudson.model.Cause;
import org.kohsuke.stapler.export.Exported;

/**
 * {@link Cause} used when a build is triggered through Jira.
 *
 * @author Mark Rekveld
 * @since 3.3.0
 */
@SuppressWarnings("lgtm[jenkins/plaintext-storage]")
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
        return issueKey != null ? Messages.cause_triggered_through(by, issueKey) : Messages.cause_triggered_by(by);
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
