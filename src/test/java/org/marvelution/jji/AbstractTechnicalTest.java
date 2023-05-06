package org.marvelution.jji;

import java.util.*;

import org.marvelution.jji.configuration.*;
import org.marvelution.jji.rest.*;
import org.marvelution.jji.synctoken.*;

import com.fasterxml.jackson.databind.*;
import okhttp3.*;
import org.junit.*;
import org.jvnet.hudson.test.*;

import static org.marvelution.jji.synctoken.SyncTokenAuthenticator.*;

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
        httpClient = new HttpClientProvider().httpClient();
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
        SimpleCanonicalHttpRequest canonicalHttpRequest = new SimpleCanonicalHttpRequest(request.method(),
                request.url()
                       .uri(),
                Optional.ofNullable(contextPath));

        return request.newBuilder()
                      .addHeader(SYNC_TOKEN_HEADER_NAME,
                              new SyncTokenBuilder().identifier(identifier)
                                                    .sharedSecret(sharedSecret)
                                                    .request(canonicalHttpRequest)
                                                    .generateToken())
                      .build();
    }
}
