package org.marvelution.jji;

import hudson.model.*;
import org.junit.*;

import static org.apache.commons.codec.digest.DigestUtils.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.core.Is.*;
import static org.mockito.Mockito.*;

public class JiraUtilsTest
		extends TestSupport
{

	@Test
	public void testGetJobHash_ForRun()
	{
		Run run = mock(Run.class);
		Job job = mock(Job.class);
		when(job.getParent()).thenAnswer(invocation -> {
			ItemGroup group = mock(ItemGroup.class);
			when(group.getUrl()).thenReturn("");
			when(group.getUrlChildPrefix()).thenReturn("job");
			return group;
		});
		when(job.getName()).thenReturn("Test");
		when(job.getShortUrl()).thenCallRealMethod();
		when(run.getParent()).thenReturn(job);
		assertThat(JiraUtils.getJobHash(run), is("640ab2bae07bedc4c163f679a746f7ab7fb5d1fa"));
		assertThat(sha1Hex("Test"), is("640ab2bae07bedc4c163f679a746f7ab7fb5d1fa"));
	}

	@Test
	public void testGetJobHash_ForJob()
	{
		Job job = mock(Job.class);
		when(job.getParent()).thenAnswer(invocation -> {
			ItemGroup group = mock(ItemGroup.class);
			when(group.getUrl()).thenReturn("job/Folder/");
			when(group.getUrlChildPrefix()).thenReturn("job");
			return group;
		});
		when(job.getName()).thenReturn("Test");
		when(job.getShortUrl()).thenCallRealMethod();
		assertThat(JiraUtils.getJobHash(job), is("291e1d188dc7acf982260b1f775b2016d5fe6aea"));
		assertThat(sha1Hex("Folder/job/Test"), is("291e1d188dc7acf982260b1f775b2016d5fe6aea"));
	}
}
