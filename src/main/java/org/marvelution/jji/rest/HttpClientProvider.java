package org.marvelution.jji.rest;

import org.marvelution.jji.JiraIntegrationPlugin;

import io.jenkins.plugins.okhttp.api.JenkinsOkHttpClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class HttpClientProvider
{

    public static OkHttpClient httpClient()
    {
        return JenkinsOkHttpClient.newClientBuilder(new OkHttpClient())
                .addInterceptor(chain -> {
                    Request request = chain.request()
                            .newBuilder()
                            .header(JiraIntegrationPlugin.VERSION_HEADER, JiraIntegrationPlugin.getVersion())
                            .build();
                    return chain.proceed(request);
                })
                .build();
    }
}
