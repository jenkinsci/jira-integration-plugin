package org.marvelution.jji.trigger;

import java.util.*;
import javax.annotation.*;

import hudson.*;
import hudson.model.Queue;
import hudson.model.*;
import jenkins.model.*;

import static java.util.Collections.*;

@Extension
public class JiraBuildTriggerTransientActionFactory
		extends TransientActionFactory
{

	@Override
	public Class type()
	{
		return Job.class;
	}

	@Nonnull
	@Override
	public Collection<? extends Action> createFor(@Nonnull Object target)
	{
		if (target instanceof Job && target instanceof Queue.Task)
		{
			return singleton(new JiraBuildTriggerAction<>((Job & Queue.Task) target));
		}
		else
		{
			return emptyList();
		}
	}
}
