package org.marvelution.jji.export;

import hudson.model.*;
import org.kohsuke.stapler.export.*;

/**
 * @author Mark Rekveld
 * @since 3.6.0
 */
@ExportedBean
public class Parent
{

	private final Item item;

	Parent(Item item)
	{
		this.item = item;
	}

	@Exported(visibility = 3)
	public String getName()
	{
		return item.getName();
	}

	@Exported(visibility = 3)
	@SuppressWarnings("deprecation")
	public String getUrl()
	{
		return item.getAbsoluteUrl();
	}
}
