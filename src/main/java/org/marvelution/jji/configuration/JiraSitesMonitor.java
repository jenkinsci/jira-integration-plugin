package org.marvelution.jji.configuration;

import javax.inject.Inject;

import org.marvelution.jji.Messages;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.util.HttpResponses;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
@Symbol("jiraSites")
public class JiraSitesMonitor
        extends AdministrativeMonitor
{
    private JiraSitesConfiguration sitesConfiguration;

    @Inject
    public void setSitesConfiguration(JiraSitesConfiguration sitesConfiguration)
    {
        this.sitesConfiguration = sitesConfiguration;
    }

    @Override
    public boolean isActivated()
    {
        return !getSites().isEmpty();
    }

    public List<JiraSite> getSites()
    {
        return sitesConfiguration.getSites()
                .stream()
                .filter(site -> !site.isEnabled() || !site.isUpToDate())
                .collect(Collectors.toList());
    }

    public String describe(JiraSite site)
    {
        if (!site.isUpToDate())
        {
            return Messages.site_not_up_to_date();
        }
        else if (!site.isEnabled())
        {
            if (site.getLastStatus() == -1)
            {
                return Messages.site_check_failed();
            }
            else if (site.getLastStatus() == 402)
            {
                return Messages.site_license_issue();
            }
            else
            {
                return Messages.site_status_unknown();
            }
        }
        else
        {
            return Messages.site_up_and_ready();
        }
    }

    @RequirePOST
    public HttpResponse doForward()
    {
        Jenkins.get()
                .checkPermission(Jenkins.ADMINISTER);
        return HttpResponses.redirectViaContextPath("/manage/jji");
    }
}
