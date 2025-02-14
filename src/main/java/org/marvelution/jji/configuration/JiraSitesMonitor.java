package org.marvelution.jji.configuration;

import java.util.List;
import javax.inject.Inject;

import org.marvelution.jji.Messages;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.util.HttpResponses;
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
                .toList();
    }

    public String describe(JiraSite site)
    {
        if (!site.isUpToDate())
        {
            return Messages.site_not_up_to_date();
        }
        else if (!site.isEnabled())
        {
            return switch (site.getLastStatus())
            {
                case -1 -> Messages.site_check_failed();
                case 402 -> Messages.site_license_issue();
                default -> Messages.site_status_unknown();
            };
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
