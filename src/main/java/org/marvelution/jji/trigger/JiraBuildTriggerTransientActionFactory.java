package org.marvelution.jji.trigger;

import java.util.*;
import javax.annotation.*;

import hudson.*;
import hudson.model.Queue;
import hudson.model.*;
import jenkins.model.*;

import static java.util.Collections.*;

@Extension
@SuppressWarnings("rawtypes")
public class JiraBuildTriggerTransientActionFactory
		extends TransientActionFactory<Job>
{

	@Override
	public Class<Job> type()
	{
		return Job.class;
	}

	@Nonnull
	@Override
	@SuppressWarnings("unchecked")
	public Collection<? extends Action> createFor(@Nonnull Job target)
	{
		if (target instanceof Queue.Task)
		{
			return singleton(new JiraBuildTriggerAction<>((Job & Queue.Task) target));
		}
		else
		{
			return emptyList();
		}
	}
}
