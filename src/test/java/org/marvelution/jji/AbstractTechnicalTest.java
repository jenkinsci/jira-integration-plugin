package org.marvelution.jji;

import java.util.*;

import org.marvelution.jji.configuration.*;
import org.marvelution.jji.rest.*;
import org.marvelution.jji.synctoken.*;

import com.fasterxml.jackson.databind.*;
import okhttp3.*;
import org.junit.*;

import static org.marvelution.jji.synctoken.SyncTokenAuthenticator.*;

public class AbstractTechnicalTest
{
    protected OkHttpClient httpClient;
    protected ObjectMapper objectMapper;

    @Before
    public void setUpHttpClientAndObjectMapper()
    {
        httpClient = new HttpClientProvider().httpClient();
        objectMapper = new ObjectMapperProvider().objectMapper();
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
