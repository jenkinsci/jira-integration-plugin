package org.marvelution.jji.configuration;

import javax.annotation.Nonnull;

import org.marvelution.jji.JiraUtils;
import org.marvelution.jji.events.JobNotificationType;
import org.marvelution.jji.model.parsers.ParserProvider;
import org.marvelution.jji.synctoken.CanonicalHttpServletRequest;
import org.marvelution.jji.synctoken.SyncTokenBuilder;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.*;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Item;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.util.Secret;
import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import static org.marvelution.jji.Headers.NOTIFICATION_TYPE;

public class JiraSite
        extends AbstractDescribableImpl<JiraSite>
        implements Serializable
{

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    @Serial
    private static final long serialVersionUID = 1L;
    private URI uri;
    private String identifier;
    private String sharedSecret;
    private String sharedSecretId;
    private String name;
    private String contextJson;
    private transient JSONObject context;
    private boolean postJson;
    private boolean tunneled;
    private int lastStatus = 200;
    private boolean upToDate = true;

    @DataBoundConstructor
    public JiraSite(URI uri)
    {
        this.uri = uri;
    }

    public URI getUri()
    {
        return uri;
    }

    public String getIdentifier()
    {
        return identifier;
    }

    @DataBoundSetter
    public void setIdentifier(String identifier)
    {
        this.identifier = identifier;
    }

    public JiraSite withIdentifier(String identifier)
    {
        this.identifier = identifier;
        return this;
    }

    public String getSharedSecret()
    {
        return sharedSecret;
    }

    @DataBoundSetter
    public void setSharedSecret(String sharedSecret)
    {
        this.sharedSecret = sharedSecret;
    }

    public JiraSite withSharedSecret(String sharedSecret)
    {
        this.sharedSecret = sharedSecret;
        return this;
    }

    public String getSharedSecretId()
    {
        return sharedSecretId;
    }

    @DataBoundSetter
    public JiraSite setSharedSecretId(String sharedSecretId)
    {
        this.sharedSecretId = sharedSecretId;
        return this;
    }

    public String getName()
    {
        return StringUtils.defaultIfBlank(name, "Jira");
    }

    @DataBoundSetter
    public void setName(String name)
    {
        this.name = name;
    }

    public JiraSite withName(String name)
    {
        this.name = name;
        return this;
    }

    public String getContextJson()
    {
        if (context != null)
        {
            return context.toString();
        }
        else
        {
            return Objects.requireNonNullElse(contextJson, "{}");
        }
    }

    @DataBoundSetter
    public void setContextJson(String contextJson)
    {
        this.contextJson = contextJson;
        this.context = JSONObject.fromObject(contextJson);
    }

    public JSONObject getContext()
    {
        return context;
    }

    public void setContext(JSONObject context)
    {
        this.context = context;
        this.contextJson = getContextJson();
    }

    public JiraSite withContext(JSONObject context)
    {
        setContext(context);
        return this;
    }

    public boolean isPostJson()
    {
        return postJson;
    }

    @DataBoundSetter
    public void setPostJson(boolean postJson)
    {
        this.postJson = postJson;
    }

    public JiraSite withPostJson(boolean postJson)
    {
        this.postJson = postJson;
        return this;
    }

    public boolean isTunneled()
    {
        return tunneled;
    }

    @DataBoundSetter
    public void setTunneled(boolean tunneled)
    {
        this.tunneled = tunneled;
    }

    public JiraSite withTunneled(boolean tunneled)
    {
        this.tunneled = tunneled;
        return this;
    }

    public boolean isEnabled()
    {
        int family = lastStatus / 100;
        return family == 2 || family == 3;
    }

    public int getLastStatus()
    {
        return lastStatus;
    }

    @DataBoundSetter
    public void setLastStatus(int lastStatus)
    {
        this.lastStatus = lastStatus;
    }

    public boolean isUpToDate()
    {
        return upToDate;
    }

    @DataBoundSetter
    public void setUpToDate(boolean upToDate)
    {
        this.upToDate = upToDate;
    }

    public Request createGetBaseUrlRequest()
    {
        return signRequest(new Request.Builder().get()
                .url(getHttpUrl("base-url"))
                .build());
    }

    public Request createGetIssueLinksRequest(
            String jobHash,
            int buildNumber)
    {
        return signRequest(new Request.Builder().get()
                .url(getHttpUrl("integration/" + jobHash + "/" + buildNumber + "/links"))
                .build());
    }

    public Request createRegisterRequest()
    {
        // force setting the Content-Length header on the request
        return signRequest(new Request.Builder().post(RequestBody.create("", JSON))
                .url(getHttpUrl("integration/register/" + identifier))
                .addHeader("Content-Length", "0")
                .build());
    }

    public Request createGetRegisterDetailsRequest()
    {
        return signRequest(new Request.Builder().get()
                .url(getHttpUrl("integration/register/" + identifier))
                .build());
    }

    public Request createUnregisterRequest()
    {
        return signRequest(new Request.Builder().delete()
                .url(getHttpUrl("integration/register/" + identifier))
                .build());
    }

    public Request createNotifyJobCreatedRequest(Item item)
    {
        return signRequest(new Request.Builder().url(getHttpUrl("integration/" + JiraUtils.getJobHash(item)))
                .header(NOTIFICATION_TYPE, JobNotificationType.JOB_CREATED.value())
                .post(RequestBody.create(JiraUtils.asJson(item, Arrays.asList("name", "url")), JSON))
                .build());
    }

    public Request createNotifyJobRequest(
            Item item,
            JobNotificationType notificationType)
    {
        return signRequest(new Request.Builder().url(getHttpUrl("integration/" + JiraUtils.getJobHash(item)))
                .header(NOTIFICATION_TYPE, notificationType.value())
                .post(RequestBody.create(JiraUtils.asJson(item,
                        ParserProvider.jobParser()
                                .fields()), JSON))
                .build());
    }

    public Request createNotifyJobMovedRequest(
            String oldJobHash,
            Item newItem)
    {
        return signRequest(new Request.Builder().post(RequestBody.create(JiraUtils.asJson(newItem,
                        ParserProvider.jobParser()
                                .fields()), JSON))
                .header(NOTIFICATION_TYPE, JobNotificationType.JOB_MOVED.value())
                .url(getHttpUrl("integration/" + oldJobHash))
                .build());
    }

    public Request createNotifyBuildCompleted(Run run)
    {
        Request.Builder request;
        if (isPostJson())
        {
            request = new Request.Builder().post(RequestBody.create(JiraUtils.asJson(run,
                    ParserProvider.buildParser()
                            .fields()), JSON));
        }
        else
        {
            request = new Request.Builder().put(RequestBody.create(JiraUtils.getAllParentHashes(run)
                    .stream()
                    .collect(Collectors.joining("\",\"", "[\"", "\"]")), JSON));
        }

        return signRequest(request.url(getHttpUrl("integration/" + JiraUtils.getJobHash(run) + "/" + run.getNumber()))
                .build());
    }

    public Request createNotifyJobDeletedRequest(Item item)
    {
        return signRequest(new Request.Builder().delete()
                .url(getHttpUrl("integration/" + JiraUtils.getJobHash(item)))
                .build());
    }

    public Request createNotifyBuildDeletedRequest(Run run)
    {
        return signRequest(new Request.Builder().delete()
                .url(getHttpUrl("integration/" + JiraUtils.getJobHash(run) + "/" + run.getNumber()))
                .build());
    }

    public Request createGetTunnelDetailsRequest()
    {
        return signRequest(new Request.Builder().get()
                .url(getHttpUrl("integration/tunnel/" + getIdentifier()))
                .build());
    }

    private HttpUrl getHttpUrl(String uri)
    {
        HttpUrl httpUrl = HttpUrl.get(this.uri);
        if (httpUrl == null)
        {
            throw new IllegalStateException("Unable to get HttpUrl from " + this.uri);
        }
        return httpUrl.newBuilder()
                .addPathSegments(uri)
                .build();
    }

    public Request signRequest(Request request)
    {
        return getSharedSecretCredentials().map(StringCredentials::getSecret)
                .map(Secret::getPlainText)
                .or(() -> Optional.ofNullable(sharedSecret))
                .filter(StringUtils::isNotBlank)
                .map(sharedSecret -> {
                    CanonicalHttpServletRequest canonicalHttpRequest = new CanonicalHttpServletRequest(request.method(),
                            request.url()
                                    .uri(),
                            getContextPath());
                    Request.Builder builder = request.newBuilder();
                    SyncTokenBuilder syncTokenBuilder = new SyncTokenBuilder().identifier(identifier)
                            .sharedSecret(sharedSecret)
                            .request(canonicalHttpRequest);
                    if (context != null && !context.isEmpty())
                    {
                        syncTokenBuilder.context(context);
                    }
                    syncTokenBuilder.generateTokenAndAddHeaders(builder::addHeader);
                    return builder.build();
                })
                .orElse(request);
    }

    private Optional<String> getContextPath()
    {
        return Optional.of(getUri().getPath())
                .map(path -> path.substring(0, path.indexOf("/rest/")))
                .filter(StringUtils::isNotBlank);
    }

    Domain getDomain()
    {
        return new Domain(uri.getHost(),
                "Jira Integration domain (autogenerated)",
                List.of(new SchemeSpecification(uri.getScheme()), new HostnameSpecification(uri.getHost(), null)));
    }

    public Optional<StringCredentials> getSharedSecretCredentials()
    {
        if (StringUtils.isNotBlank(sharedSecretId))
        {
            List<StringCredentials> credentials = CredentialsProvider.lookupCredentials(StringCredentials.class,
                    Jenkins.get(),
                    ACL.SYSTEM,
                    List.of(new SchemeRequirement(getUri().getScheme()), new HostnameRequirement(getUri().getHost())));
            return Optional.ofNullable(CredentialsMatchers.firstOrNull(credentials, CredentialsMatchers.withId(sharedSecretId)));
        }
        else
        {
            return Optional.empty();
        }
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(uri, identifier, postJson);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        JiraSite jiraSite = (JiraSite) o;
        return postJson == jiraSite.postJson && Objects.equals(uri, jiraSite.uri) && Objects.equals(identifier, jiraSite.identifier);
    }

    @Override
    public String toString()
    {
        return "Jira site " + getName() + " at " + uri;
    }

    public void updateSiteDetails(JSONObject details)
    {
        URI url = URI.create(details.getString("url"));
        if (!uri.equals(url))
        {
            uri = url;
        }
        setName(details.getString("name"));
        JSONObject context = details.optJSONObject("context");
        if (context != null)
        {
            setContext(context);
        }
    }

    public static JiraSite getSite(JSONObject details)
    {
        JiraSite site = new JiraSite(URI.create(details.getString("url"))).withIdentifier(details.getString("identifier"))
                .withName(details.getString("name"))
                .withSharedSecret(details.getString("sharedSecret"))
                .withPostJson(details.optBoolean("firewalled", false))
                .withTunneled(details.optBoolean("tunneled", false));
        JSONObject context = details.optJSONObject("context");
        if (context == null && details.has("contextJson"))
        {
            context = JSONObject.fromObject(details.getString("contextJson"));
        }
        if (context != null)
        {
            site.setContext(context);
        }
        return site;
    }

    @Extension
    public static class Descriptor
            extends hudson.model.Descriptor<JiraSite>
    {

        @Nonnull
        @Override
        public String getDisplayName()
        {
            return "Jira Site";
        }
    }
}
