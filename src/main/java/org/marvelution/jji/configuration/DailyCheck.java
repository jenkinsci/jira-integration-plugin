package org.marvelution.jji.configuration;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.inject.Provider;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
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
    private Provider<OkHttpClient> httpClient;

    public DailyCheck()
    {
        super("Daily Check");
    }

    @Inject
    public void setSitesConfiguration(JiraSitesConfiguration sitesConfiguration)
    {
        this.sitesConfiguration = sitesConfiguration;
    }

    @Inject
    public void setHttpClient(Provider<OkHttpClient> httpClient)
    {
        this.httpClient = httpClient;
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
            throws IOException, InterruptedException
    {
        LOGGER.log(Level.INFO, "Checking Jira Sites...");
        sitesConfiguration.updateSiteRegistrations(httpClient.get());
    }
}
