package org.marvelution.jji.configuration;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

@Extension
@Symbol(JiraSitesConfiguration.ID)
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
            LOGGER.log(Level.INFO, "Updating registration for {0}", site);
            updateSiteRegistration(existing.get(), site);
        }
        else
        {
            LOGGER.log(Level.INFO, "Adding registration for {0}", site);
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

    public void unregisterSite(JiraSite site)
    {
        removeSharedSecretCredentials(site);
        sites.remove(site);
        save();
        LOGGER.log(Level.INFO, "Unregistered Jira Site at: {0}", site.getUri());
    }

    private void storeSharedSecretAsCredentials(JiraSite site)
    {
        StringCredentials credentials = createSharedSecretCredentials(site, null);
        try (ACLContext ignored = ACL.as2(ACL.SYSTEM2))
        {
            new SystemCredentialsProvider.StoreImpl().addDomain(site.getDomain(), credentials);
            site.setSharedSecretId(credentials.getId());
            site.setSharedSecret(null);
        }
        catch (IOException e)
        {
            LOGGER.log(Level.WARNING, "Failed to store shared secret credentials for " + site, e);
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
                            StringCredentials newCredentials = createSharedSecretCredentials(site, credentials.getId());
                            new SystemCredentialsProvider.StoreImpl().updateCredentials(existing.getDomain(), credentials, newCredentials);
                            existing.setSharedSecretId(newCredentials.getId());
                            existing.setSharedSecret(null);
                        }
                        catch (IOException e)
                        {
                            LOGGER.log(Level.WARNING, "Failed to store shared secret credentials for " + site, e);
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
                        LOGGER.log(Level.WARNING, "Failed to store shared secret credentials for " + site, e);
                    }
                });
    }

    @Nonnull
    private StringCredentials createSharedSecretCredentials(
            JiraSite site,
            @Nullable
            String id)
    {
        String description = String.format("Jira Integration (%s) auto generated shared secret credentials", site.getName());
        return new StringCredentialsImpl(CredentialsScope.GLOBAL,
                id != null ? id : UUID.randomUUID()
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

    public void updateSiteRegistrations(OkHttpClient httpClient)
    {
        for (JiraSite site : getSites())
        {
            try (Response response = httpClient.newCall(site.createGetRegisterDetailsRequest())
                    .execute();
                 ResponseBody body = response.body())
            {
                LOGGER.log(Level.INFO, "Checking " + site);
                site.setLastStatus(response.code());
                Optional.ofNullable(response.header("X-Registration-Status", "0"))
                        .map(header -> {
                            try
                            {
                                return Integer.parseInt(header);
                            }
                            catch (NumberFormatException e)
                            {
                                LOGGER.log(Level.FINE, "Unexpected value returned for X-Registration-Status", e);
                                return 0;
                            }
                        })
                        .ifPresent(status -> site.setUpToDate(status == 0));
                if (response.isSuccessful() && body != null)
                {
                    JSONObject details = JSONObject.fromObject(body.string());
                    LOGGER.log(Level.INFO, "Updating {0}", site);
                    site.updateSiteDetails(details);
                }
                else if (response.code() == 402)
                {
                    LOGGER.log(Level.INFO, "Disabling " + site + " as payment is required");
                }
                else
                {
                    LOGGER.log(Level.SEVERE, "Unable to update " + site + "; " + response.code());
                }
            }
            catch (Exception e)
            {
                LOGGER.log(Level.SEVERE, "Failed to update " + site, e);
                site.setLastStatus(-1);
            }
        }
        save();
    }

    public static JiraSitesConfiguration get()
    {
        return (JiraSitesConfiguration) Jenkins.get()
                .getDescriptorOrDie(JiraSitesConfiguration.class);
    }
}
