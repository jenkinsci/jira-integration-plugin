package org.marvelution.jji.casc;

import javax.annotation.*;

import org.marvelution.jji.configuration.*;

import hudson.*;
import io.jenkins.plugins.casc.*;
import io.jenkins.plugins.casc.model.*;
import jenkins.model.*;

@Extension(optional = true)
public class JiraSitesConfigurationConfigurator
        extends BaseConfigurator<JiraSitesConfiguration>
{
    @Override
    protected JiraSitesConfiguration instance(
            Mapping mapping,
            ConfigurationContext context)
            throws ConfiguratorException
    {
        return Jenkins.get()
                      .getExtensionList(JiraSitesConfiguration.class)
                      .get(0);
    }

    @Override
    public Class<JiraSitesConfiguration> getTarget()
    {
        return JiraSitesConfiguration.class;
    }

    @Nonnull
    @Override
    public JiraSitesConfiguration configure(
            CNode c,
            ConfigurationContext context)
            throws ConfiguratorException
    {
        JiraSitesConfiguration configuration = super.configure(c, context);
        configuration.migrateSharedSecretsToCredentials();
        return configuration;
    }
}
