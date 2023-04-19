package org.marvelution.jji;

import java.io.*;
import java.util.logging.*;

import hudson.*;
import hudson.init.*;
import jenkins.model.*;

public class JiraIntegrationPlugin
{

    public static final String OLD_SHORT_NAME = "jenkins-jira-plugin";
    public static final String SHORT_NAME = "jira-integration";
    public static final String VERSION_HEADER = "X-JJI-Version";
    private static final Logger LOGGER = Logger.getLogger(JiraIntegrationPlugin.class.getName());

    @Initializer(after = InitMilestone.STARTED)
    public static void init()
            throws Exception
    {
        Plugin plugin = Jenkins.get()
                               .getPlugin(JiraIntegrationPlugin.OLD_SHORT_NAME);
        if (plugin != null)
        {
            LOGGER.warning(String.format("Plugin %s is installed, but should be disabled or uninstalled as it doesn't work with plugin %s.",
                    OLD_SHORT_NAME,
                    SHORT_NAME));
            PluginWrapper wrapper = plugin.getWrapper();
            LOGGER.warning(String.format("Attempting to disable plugin %s", OLD_SHORT_NAME));
            PluginWrapper.PluginDisableResult result = wrapper.disable(PluginWrapper.PluginDisableStrategy.NONE);
            if (result.getStatus() == PluginWrapper.PluginDisableStatus.DISABLED)
            {
                LOGGER.warning(String.format("Stopping plugin %s", OLD_SHORT_NAME));
                wrapper.stop();
                wrapper.releaseClassLoader();
                try
                {
                    LOGGER.warning(String.format("Uninstalling %s...", OLD_SHORT_NAME));
                    wrapper.doDoUninstall();
                }
                catch (IOException e)
                {
                    LOGGER.log(Level.WARNING, String.format("Unable to uninstall %s: %s", OLD_SHORT_NAME, e.getMessage()), e);
                }
                LOGGER.warning("Triggering restart of Jenkins...");
                Jenkins.get()
                       .restart();
            }
            else if (result.getStatus() != PluginWrapper.PluginDisableStatus.ALREADY_DISABLED)
            {
                LOGGER.severe(String.format("Failed to disable %s, this plugin should be manually disabled followed by a restart of Jenkins",
                        OLD_SHORT_NAME));
                throw new IllegalStateException("Plugin " + plugin.getWrapper()
                                                                  .getDisplayName() + " (" + OLD_SHORT_NAME + ") needs to be uninstalled.");
            }
            else
            {
                LOGGER.info(String.format("Plugin %s is %s with message %s", OLD_SHORT_NAME, result.getStatus(), result.getMessage()));
            }
        }
        else
        {
            LOGGER.fine(String.format("No need to act, plugin %s was not found.", OLD_SHORT_NAME));
        }
    }
}
