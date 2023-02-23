package org.marvelution.jji.configuration;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.*;

import hudson.*;
import jenkins.model.*;
import net.sf.json.*;
import org.jenkinsci.*;
import org.kohsuke.stapler.*;

@Symbol(JiraSitesConfiguration.ID)
@Extension
public class JiraSitesConfiguration
		extends GlobalConfiguration
{

	static final String ID = "jira-integration-configuration";
	private static final Logger LOGGER = Logger.getLogger(JiraSitesConfiguration.class.getName());
	private Set<JiraSite> sites = new CopyOnWriteArraySet<>();

	public JiraSitesConfiguration()
	{
		load();
	}

	public static JiraSitesConfiguration get()
	{
		return (JiraSitesConfiguration) Jenkins.get().getDescriptorOrDie(JiraSitesConfiguration.class);
	}

	@Override
	public boolean configure(
			StaplerRequest req,
			JSONObject json)
	{
		return true;
	}

	public Set<JiraSite> getSites()
	{
		return sites;
	}

	@DataBoundSetter
	public void setSites(Set<JiraSite> sites)
	{
		this.sites = sites;
	}

	public void registerSite(JiraSite site)
	{
		if (sites.removeIf(existing -> existing.getUri().equals(site.getUri())))
		{
			LOGGER.log(Level.INFO, "Updating registration for Jira Site {0}", site);
		}
		else
		{
			LOGGER.log(Level.INFO, "Adding registration for Jira Site {0}", site);
		}
		sites.add(site);
		save();
	}

	public void unregisterSite(URI uri)
	{
		if (sites.removeIf(site -> site.getUri().equals(uri)))
		{
			save();
			LOGGER.log(Level.INFO, "Unregistered Jira Site at: {0}", uri);
		}
	}

	@Override
	public String getDisplayName()
	{
		return "Jira Sites";
	}

	@Override
	public String getId()
	{
		return ID;
	}

	public Stream<JiraSite> stream()
	{
		return sites.stream();
	}
}
