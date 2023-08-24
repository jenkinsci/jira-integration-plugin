package org.marvelution.jji.export;

import javax.annotation.*;
import java.util.*;

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
@SuppressWarnings("rawtypes")
public class BuildParentTransientActionFactory
		extends TransientActionFactory<Run>
{

	@Override
	public Class<Run> type()
	{
		return Run.class;
	}

	@Nonnull
	@Override
	public Collection<? extends Action> createFor(
			@Nonnull
			Run run)
	{
		return Collections.singletonList(new ParentAction((run).getParent()));
	}
}
