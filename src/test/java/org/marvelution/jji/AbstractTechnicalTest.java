package org.marvelution.jji;

import org.marvelution.jji.configuration.JiraSite;
import org.marvelution.jji.configuration.JiraSitesConfiguration;
import org.marvelution.jji.rest.HttpClientProvider;
import org.marvelution.jji.rest.ObjectMapperProvider;
import org.marvelution.jji.synctoken.CanonicalHttpServletRequest;
import org.marvelution.jji.synctoken.SyncTokenBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public abstract class AbstractTechnicalTest
{

    public static final String ALICE = "alice";
    protected final MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    protected JiraSitesConfiguration sitesConfiguration;
    protected OkHttpClient httpClient;
    protected ObjectMapper objectMapper;

    @Before
    public void setUpCommonBits()
    {
        jenkins.getInstance()
                .setSecurityRealm(jenkins.createDummySecurityRealm());
        jenkins.getInstance()
                .setAuthorizationStrategy(authorizationStrategy);
        sitesConfiguration = jenkins.getInstance()
                .getDescriptorByType(JiraSitesConfiguration.class);
        httpClient = HttpClientProvider.httpClient();
        objectMapper = new ObjectMapperProvider().objectMapper();
    }

    protected void injectSite(JiraSite site)
    {
        sitesConfiguration.registerSite(new JiraSite(site.getUri()).withIdentifier(site.getIdentifier())
                .withName(site.getName())
                .withSharedSecret(site.getSharedSecret())
                .withPostJson(site.isPostJson()));
    }

    protected Request signTokenAuth(
            Request request,
            JiraSite site,
            String contextPath)
    {
        return signTokenAuth(request, site.getIdentifier(), site.getSharedSecret(), contextPath);
    }

    protected Request signTokenAuth(
            Request request,
            String identifier,
            String sharedSecret,
            String contextPath)
    {
        CanonicalHttpServletRequest canonicalHttpRequest = new CanonicalHttpServletRequest(request.method(),
                request.url()
                        .uri(),
                Optional.ofNullable(contextPath));

        Request.Builder builder = request.newBuilder();

        new SyncTokenBuilder().identifier(identifier)
                .sharedSecret(sharedSecret)
                .request(canonicalHttpRequest)
                .generateTokenAndAddHeaders(builder::addHeader);

        return builder.build();
    }
}
