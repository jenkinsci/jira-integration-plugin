package org.marvelution.jji;

import java.util.logging.*;

import hudson.*;
import hudson.init.*;
import jenkins.model.*;

public class JiraPlugin
{

	public static final String OLD_SHORT_NAME = "jenkins-jira-plugin";
	public static final String SHORT_NAME = "jira-integration";
	public static final String VERSION_HEADER = "X-JJI-Version";
	private static final Logger LOGGER = Logger.getLogger(JiraPlugin.class.getName());

	@Initializer(after = InitMilestone.PLUGINS_PREPARED)
	public static void disableOldPlugin()
	{
		Plugin plugin = Jenkins.get().getPlugin(JiraPlugin.OLD_SHORT_NAME);
		if (plugin != null)
		{
			LOGGER.warning(String.format("Plugin %s is installed, but should be disabled or uninstalled as it doesn't work with plugin %s.",
			                             OLD_SHORT_NAME, SHORT_NAME));
			PluginWrapper wrapper = plugin.getWrapper();
			LOGGER.warning(String.format("Stopping plugin %s", OLD_SHORT_NAME));
			wrapper.stop();
			LOGGER.warning(String.format("Attempting to disable plugin %s", OLD_SHORT_NAME));
			PluginWrapper.PluginDisableResult result = wrapper.disable(PluginWrapper.PluginDisableStrategy.NONE);
			LOGGER.warning(String.format("Plugin %s is %s with message %s", OLD_SHORT_NAME, result.getStatus(), result.getMessage()));
		}
		else
		{
			LOGGER.fine(String.format("No need to act, plugin %s was not found.", OLD_SHORT_NAME));
		}
	}
}
