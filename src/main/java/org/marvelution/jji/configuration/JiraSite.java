package org.marvelution.jji.configuration;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

import org.marvelution.jji.*;
import org.marvelution.jji.model.parsers.*;
import org.marvelution.jji.synctoken.*;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.*;
import hudson.*;
import hudson.model.*;
import hudson.security.*;
import hudson.util.*;
import jenkins.model.*;
import okhttp3.*;
import org.apache.commons.lang3.*;
import org.jenkinsci.plugins.plaincredentials.*;
import org.kohsuke.stapler.*;

import static org.marvelution.jji.JiraUtils.*;
import static org.marvelution.jji.synctoken.SyncTokenAuthenticator.*;

import static java.util.Arrays.*;
import static java.util.Optional.*;
import static org.apache.commons.lang.StringUtils.*;

public class JiraSite
		extends AbstractDescribableImpl<JiraSite>
		implements Serializable
{

	public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final long serialVersionUID = 1L;
	private final URI uri;
	private String identifier;
	private String sharedSecret;
	private String sharedSecretId;
	private String name;
	private boolean postJson;

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
		return defaultIfBlank(name, "Jira");
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

	public Request createGetBaseUrlRequest()
	{
		return signRequest(new Request.Builder().get().url(getHttpUrl("base-url")).build());
	}

	public Request createGetIssueLinksRequest(
			String jobHash,
			int buildNumber)
	{
		return signRequest(new Request.Builder().get().url(getHttpUrl("integration/" + jobHash + "/" + buildNumber + "/links")).build());
	}

	public Request createRegisterRequest()
	{
		// force setting the Content-Length header on the request
		return signRequest(new Request.Builder().post(RequestBody.create("", JSON))
				                   .url(getHttpUrl("integration/register/" + identifier))
				                   .addHeader("Content-Length", "0")
				                   .build());
	}

	public Request createNotifyJobCreatedRequest(Item item)
	{
		return signRequest(new Request.Builder().url(getHttpUrl("integration/" + JiraUtils.getJobHash(item)))
				                   .post(RequestBody.create(asJson(item, asList("name", "url")), JSON))
				                   .build());
	}

	public Request createNotifyJobModifiedRequest(Item item)
	{
		return signRequest(new Request.Builder().url(getHttpUrl("integration/" + JiraUtils.getJobHash(item)))
				                   .post(RequestBody.create(asJson(item, ParserProvider.jobParser().fields()), JSON))
				                   .build());
	}

	public Request createNotifyJobMovedRequest(
			String oldJobHash,
			Item newItem)
	{
		return signRequest(new Request.Builder().post(RequestBody.create(asJson(newItem, ParserProvider.jobParser().fields()), JSON))
				                   .url(getHttpUrl("integration/" + oldJobHash))
				                   .build());
	}

	public Request createNotifyBuildCompleted(Run run)
	{
		Request.Builder request;
		if (isPostJson())
		{
			request = new Request.Builder().post(RequestBody.create(asJson(run, ParserProvider.buildParser().fields()), JSON));
		}
		else
		{
			request = new Request.Builder().put(
					RequestBody.create(JiraUtils.getAllParentHashes(run).stream().collect(Collectors.joining("\",\"", "[\"", "\"]")),
					                   JSON));
		}

		return signRequest(request.url(getHttpUrl("integration/" + JiraUtils.getJobHash(run) + "/" + run.getNumber())).build());
	}

	public Request createNotifyJobDeletedRequest(Item item)
	{
		return signRequest(new Request.Builder().delete().url(getHttpUrl("integration/" + JiraUtils.getJobHash(item))).build());
	}

	public Request createNotifyBuildDeletedRequest(Run run)
	{
		return signRequest(
				new Request.Builder().delete().url(getHttpUrl("integration/" + JiraUtils.getJobHash(run) + "/" + run.getNumber())).build());
	}

	private HttpUrl getHttpUrl(String uri)
	{
		HttpUrl httpUrl = HttpUrl.get(this.uri);
		if (httpUrl == null)
		{
			throw new IllegalStateException("Unable to get HttpUrl from " + this.uri);
		}
		return httpUrl.newBuilder().addPathSegments(uri).build();
	}

	public Request signRequest(Request request)
	{
		return getSharedSecretCredentials().map(StringCredentials::getSecret)
				.map(Secret::getPlainText)
				.or(() -> Optional.ofNullable(sharedSecret))
				.filter(StringUtils::isNotBlank)
				.map(sharedSecret -> {
					SimpleCanonicalHttpRequest canonicalHttpRequest = new SimpleCanonicalHttpRequest(request.method(), request.url().uri(),
					                                                                                 getContextPath());
					return request.newBuilder()
							.addHeader(SYNC_TOKEN_HEADER_NAME, new SyncTokenBuilder().identifier(identifier)
									.sharedSecret(sharedSecret)
									.request(canonicalHttpRequest)
									.generateToken())
							.build();
				})
				.orElse(request);
	}

	private Optional<String> getContextPath()
	{
		return of(getUri().getPath()).map(path -> path.substring(0, path.indexOf("/rest/"))).filter(StringUtils::isNotBlank);
	}

	Domain getDomain()
	{
		return new Domain(uri.getHost(), "Jira Integration domain (autogenerated)",
		                  List.of(new SchemeSpecification(uri.getScheme()), new HostnameSpecification(uri.getHost(), null)));
	}

	public Optional<StringCredentials> getSharedSecretCredentials()
	{
		List<StringCredentials> credentials = CredentialsProvider.lookupCredentials(StringCredentials.class, Jenkins.get(), ACL.SYSTEM,
		                                                                            List.of(new SchemeRequirement(getUri().getScheme()),
		                                                                                    new HostnameRequirement(getUri().getHost())));
		return Optional.ofNullable(CredentialsMatchers.firstOrNull(credentials, CredentialsMatchers.withId(sharedSecretId)));
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

	@Extension
	public static class Descriptor
			extends hudson.model.Descriptor<JiraSite>
	{

		@Override
		public String getDisplayName()
		{
			return "Jira Site";
		}
	}
}
