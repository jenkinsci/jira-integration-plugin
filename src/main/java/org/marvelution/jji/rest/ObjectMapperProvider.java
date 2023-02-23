package org.marvelution.jji.rest;

import com.fasterxml.jackson.databind.*;
import com.google.inject.*;

public class ObjectMapperProvider
{

	private final ObjectMapper objectMapper;

	public ObjectMapperProvider()
	{
		objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Provides
	public ObjectMapper objectMapper()
	{
		return objectMapper;
	}
}
