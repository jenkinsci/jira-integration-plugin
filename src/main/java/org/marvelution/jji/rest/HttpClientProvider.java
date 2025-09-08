package org.marvelution.jji.rest;

import org.marvelution.jji.JiraIntegrationPlugin;

import com.google.inject.Provides;
import io.jenkins.plugins.okhttp.api.JenkinsOkHttpClient;
import java.time.Duration;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class HttpClientProvider
{
    @Provides
    public OkHttpClient httpClient()
    {
        return JenkinsOkHttpClient.newClientBuilder(new OkHttpClient())
                .connectTimeout(Duration.ofMillis(5000))
                .readTimeout(Duration.ofMillis(5000))
                .writeTimeout(Duration.ofMillis(5000))
                .addInterceptor(chain -> {
                    Request originalRequest = chain.request();
                    Request userAgentRequest = originalRequest.newBuilder()
                            .header("User-Agent", JiraIntegrationPlugin.SHORT_NAME)
                            .build();
                    return chain.proceed(userAgentRequest);
                })
                .build();
    }
}
