package org.marvelution.jji.export;

import hudson.model.*;
import org.kohsuke.stapler.export.*;

/**
 * {@link Action} to expose the {@link Item parent}.
 *
 * @author Mark Rekveld
 * @since 3.6.0
 */
@ExportedBean
public class ParentAction
		implements Action
{

	private final Parent parent;

	public ParentAction(Item item)
	{
		parent = new Parent(item);
	}

	@Exported(visibility = 2)
	public Parent getParent()
	{
		return parent;
	}

	@Override
	public String getIconFileName()
	{
		return null;
	}

	@Override
	public String getDisplayName()
	{
		return null;
	}

	@Override
	public String getUrlName()
	{
		return null;
	}

}
