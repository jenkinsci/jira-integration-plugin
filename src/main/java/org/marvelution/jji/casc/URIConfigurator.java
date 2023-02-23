package org.marvelution.jji.casc;

import java.net.*;
import java.util.*;
import javax.annotation.*;

import hudson.*;
import io.jenkins.plugins.casc.*;
import io.jenkins.plugins.casc.model.*;

@Extension(optional = true)
public class URIConfigurator
		implements Configurator<URI>
{

	@Override
	public Class<URI> getTarget()
	{
		return URI.class;
	}

	@Nonnull
	@Override
	public Set<Attribute<URI, ?>> describe()
	{
		return Collections.emptySet();
	}

	@Nonnull
	@Override
	public URI configure(
			CNode config,
			ConfigurationContext context)
			throws ConfiguratorException
	{
		return URI.create(config.asScalar().getValue());
	}

	@Override
	public URI check(
			CNode config,
			ConfigurationContext context)
			throws ConfiguratorException
	{
		return configure(config, context);
	}

	@Override
	public CNode describe(
			URI instance,
			ConfigurationContext context)
	{
		return new Scalar(instance.toASCIIString());
	}
}
