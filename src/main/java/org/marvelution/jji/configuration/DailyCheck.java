package org.marvelution.jji.configuration;

import javax.inject.Inject;

import org.marvelution.jji.rest.HttpClientProvider;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.OkHttpClient;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class DailyCheck
        extends AsyncPeriodicWork
{
    private static final Logger LOGGER = Logger.getLogger(DailyCheck.class.getName());
    private JiraSitesConfiguration sitesConfiguration;

    public DailyCheck()
    {
        super("Daily Check");
    }

    @Inject
    public void setSitesConfiguration(JiraSitesConfiguration sitesConfiguration)
    {
        this.sitesConfiguration = sitesConfiguration;
    }

    @Override
    public long getRecurrencePeriod()
    {
        return DAY;
    }

    @Override
    public long getInitialDelay()
    {
        return 0;
    }

    @Override
    protected void execute(TaskListener listener)
    {
        LOGGER.log(Level.INFO, "Checking Jira Sites...");
        OkHttpClient httpClient = HttpClientProvider.httpClient();
        sitesConfiguration.updateSiteRegistrations(httpClient);
    }
}
