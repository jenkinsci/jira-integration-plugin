package org.marvelution.jji.rest;

import java.time.*;

import org.marvelution.jji.*;

import com.google.inject.*;
import io.jenkins.plugins.okhttp.api.*;
import okhttp3.*;

public class HttpClientProvider
{

	private final OkHttpClient httpClient;

	public HttpClientProvider()
	{
		httpClient = JenkinsOkHttpClient.newClientBuilder(new OkHttpClient())
				.connectTimeout(Duration.ofMillis(5000))
				.readTimeout(Duration.ofMillis(5000))
				.writeTimeout(Duration.ofMillis(5000))
				.addInterceptor(chain -> {
					Request originalRequest = chain.request();
					Request userAgentRequest = originalRequest.newBuilder().header("User-Agent", JiraPlugin.SHORT_NAME).build();
					return chain.proceed(userAgentRequest);
				})
				.build();
	}

	@Provides
	public OkHttpClient httpClient()
	{
		return httpClient;
	}
}
