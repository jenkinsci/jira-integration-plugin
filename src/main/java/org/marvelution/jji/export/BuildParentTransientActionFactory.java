package org.marvelution.jji.export;

import java.util.*;
import javax.annotation.*;

import hudson.*;
import hudson.model.*;
import jenkins.model.*;

/**
 * {@link TransientActionFactory} implementation for adding a {@link ParentAction} to a {@link Run}.
 *
 * @author Mark Rekveld
 * @since 3.6.0
 */
@Extension
public class BuildParentTransientActionFactory
		extends TransientActionFactory
{

	@Override
	public Class type()
	{
		return Run.class;
	}

	@Nonnull
	@Override
	public Collection<? extends Action> createFor(@Nonnull Object target)
	{
		if (target instanceof Run)
		{
			return Collections.singletonList(new ParentAction(((Run) target).getParent()));
		}
		else
		{
			return Collections.emptyList();
		}
	}
}
