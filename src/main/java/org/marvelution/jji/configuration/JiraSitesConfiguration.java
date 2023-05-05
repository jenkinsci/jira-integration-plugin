package org.marvelution.jji.configuration;

import javax.annotation.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.*;

import com.cloudbees.plugins.credentials.*;
import hudson.*;
import hudson.init.*;
import hudson.security.*;
import hudson.util.*;
import jenkins.model.*;
import net.sf.json.*;
import org.apache.commons.lang3.*;
import org.jenkinsci.*;
import org.jenkinsci.plugins.plaincredentials.*;
import org.jenkinsci.plugins.plaincredentials.impl.*;
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
        return (JiraSitesConfiguration) Jenkins.get()
                                               .getDescriptorOrDie(JiraSitesConfiguration.class);
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

    public Stream<JiraSite> stream()
    {
        return sites.stream();
    }

    public Optional<JiraSite> findSite(URI uri)
    {
        return sites.stream()
                    .filter(site -> Objects.equals(site.getUri(), uri))
                    .findFirst();
    }

    public Optional<JiraSite> findSite(String identifier)
    {
        return sites.stream()
                    .filter(site -> Objects.equals(site.getIdentifier(), identifier))
                    .findFirst();
    }

    public void registerSite(JiraSite site)
    {
        Optional<JiraSite> existing = getExistingSite(site.getUri());
        if (existing.isPresent())
        {
            LOGGER.log(Level.INFO, "Updating registration for Jira site {0}", site);
            updateSiteRegistration(existing.get(), site);
        }
        else
        {
            LOGGER.log(Level.INFO, "Adding registration for Jira site {0}", site);
            storeSharedSecretAsCredentials(site);
            sites.add(site);
        }
        save();
    }

    private void updateSiteRegistration(
            JiraSite existing,
            JiraSite newSite)
    {
        existing.setName(newSite.getName());
        existing.setIdentifier(newSite.getIdentifier());
        existing.setPostJson(newSite.isPostJson());
        updateSharedSecretCredentials(existing, newSite);
    }

    public void unregisterSite(URI uri)
    {
        getExistingSite(uri).ifPresent(site -> {
            removeSharedSecretCredentials(site);
            sites.remove(site);
            save();
            LOGGER.log(Level.INFO, "Unregistered Jira Site at: {0}", uri);
        });
    }

    private void storeSharedSecretAsCredentials(JiraSite site)
    {
        StringCredentials credentials = createSharedSecretCredentials(site);
        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2))
        {
            new SystemCredentialsProvider.StoreImpl().addDomain(site.getDomain(), credentials);
            site.setSharedSecretId(credentials.getId());
            site.setSharedSecret(null);
        }
        catch (IOException e)
        {
            LOGGER.log(Level.WARNING, "Failed to store shared secret credentials for Jira site " + site, e);
        }
    }

    private void updateSharedSecretCredentials(
            JiraSite existing,
            JiraSite site)
    {
        if (Objects.equals(existing.getDomain(), site.getDomain()))
        {
            existing.getSharedSecretCredentials()
                    .ifPresent(credentials -> {
                        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2))
                        {
                            StringCredentials newCredentials = createSharedSecretCredentials(site);
                            new SystemCredentialsProvider.StoreImpl().updateCredentials(existing.getDomain(), credentials, newCredentials);
                            existing.setSharedSecretId(newCredentials.getId());
                            existing.setSharedSecret(null);
                        }
                        catch (IOException e)
                        {
                            LOGGER.log(Level.WARNING, "Failed to store shared secret credentials for Jira site " + site, e);
                        }
                    });
        }
        else
        {
            removeSharedSecretCredentials(existing);
            storeSharedSecretAsCredentials(site);
            existing.setSharedSecretId(site.getSharedSecretId());
            existing.setSharedSecret(site.getSharedSecret());
        }
    }

    private void removeSharedSecretCredentials(JiraSite site)
    {
        site.getSharedSecretCredentials()
            .ifPresent(credentials -> {
                try (ACLContext ignored = ACL.as2(ACL.SYSTEM2))
                {
                    new SystemCredentialsProvider.StoreImpl().removeCredentials(site.getDomain(), credentials);
                    site.setSharedSecretId(null);
                    site.setSharedSecret(credentials.getSecret()
                                                    .getPlainText());
                }
                catch (IOException e)
                {
                    LOGGER.log(Level.WARNING, "Failed to store shared secret credentials for Jira site " + site, e);
                }
            });
    }

    @Nonnull
    private StringCredentials createSharedSecretCredentials(JiraSite site)
    {
        String description = String.format("Jira Integration (%s) auto generated shared secret credentials", site.getName());
        return new StringCredentialsImpl(CredentialsScope.GLOBAL,
                UUID.randomUUID()
                    .toString(),
                description,
                Secret.fromString(site.getSharedSecret()));
    }

    @Nonnull
    private Optional<JiraSite> getExistingSite(URI uri)
    {
        return sites.stream()
                    .filter(site -> site.getUri()
                                        .equals(uri))
                    .findFirst();
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

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED)
    public void migrateSharedSecretsToCredentials()
    {
        boolean updated = false;
        for (JiraSite site : sites)
        {
            if (StringUtils.isNotBlank(site.getSharedSecret()) && StringUtils.isBlank(site.getSharedSecretId()))
            {
                storeSharedSecretAsCredentials(site);
                updated = true;
            }
        }
        if (updated)
        {
            try
            {
                getConfigFile().write(this);
            }
            catch (IOException e)
            {
                LOGGER.log(Level.WARNING, "Failed to save " + getConfigFile(), e);
            }
        }
    }
}
