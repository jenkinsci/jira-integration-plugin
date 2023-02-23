package org.marvelution.jji.scm;

import java.net.*;
import java.util.*;

import org.marvelution.jji.*;
import org.marvelution.jji.configuration.*;
import org.marvelution.jji.rest.*;
import org.marvelution.jji.synctoken.utils.*;

import hudson.*;
import hudson.model.*;
import okhttp3.*;
import okhttp3.mock.*;
import org.junit.*;
import org.mockito.*;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JiraChangeLogAnnotatorTest
		extends TestSupport
{

	private MockInterceptor requests;
	@Mock
	private Run build;
	private JiraChangeLogAnnotator annotator;
	private JiraSitesConfiguration sitesConfiguration;

	@Before
	public void setup()
	{
		annotator = new JiraChangeLogAnnotator();
		sitesConfiguration = new JiraSitesConfiguration()
		{
			@Override
			public synchronized void load()
			{
			}
		};
		requests = new MockInterceptor(Behavior.UNORDERED);
		OkHttpClient httpClient = new OkHttpClient.Builder().addInterceptor(requests).build();
		annotator.setClient(new SitesClient(sitesConfiguration, httpClient, new ObjectMapperProvider().objectMapper()));
		ItemGroup itemGroup = mock(ItemGroup.class);
		when(itemGroup.getUrl()).thenReturn("");
		when(itemGroup.getUrlChildPrefix()).thenReturn("job");
		Job job = new FreeStyleProject(itemGroup, "FreeStyleProject");
		when(build.getParent()).thenReturn(job);
		when(build.getNumber()).thenReturn(1);
	}

	@Test
	public void testAnnotate_NoJiraSites()
	{
		MarkupText text = new MarkupText("DUMMY-1!");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("DUMMY-1!"));
	}

	@Test
	public void testAnnotate_MultipleJiraSites()
	{
		requests.addRule()
				.get()
				.urlStarts("http://localhost:2990/jira/")
				.urlEnds("links")
				.respond(200, ResponseBody.create("{\"DUMMY-1\": \"http://localhost:2990/jira/browse/DUMMY-1\"}", JiraSite.JSON));
		requests.addRule()
				.get()
				.urlStarts("http://jira.example.com")
				.urlEnds("links")
				.respond(200, ResponseBody.create("{\"DUMMY-2\": \"http://jira.example.com/browse/DUMMY-2\"}", JiraSite.JSON));
		sitesConfiguration.getSites().add(createJiraSite());
		sitesConfiguration.getSites()
				.add(createJiraSite(URI.create("http://jira.example.com/failing/rest/jenkins/latest"), "Failing Jira"));
		MarkupText text = new MarkupText("DUMMY-1!, DUMMY-2");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("<a href='http://localhost:2990/jira/browse/DUMMY-1'>DUMMY-1</a>!, " +
				                                    "<a href='http://jira.example.com/browse/DUMMY-2'>DUMMY-2</a>"));
	}

	@Test
	public void testAnnotate_MultipleJiraSites_OneFailing()
	{
		requests.addRule()
				.get()
				.urlStarts("http://localhost:2990/jira/")
				.urlEnds("links")
				.respond(200, ResponseBody.create("{\"DUMMY-1\": \"http://localhost:2990/jira/browse/DUMMY-1\"}", JiraSite.JSON));
		requests.addRule().get().urlStarts("http://jira.example.com").urlEnds("links").respond(500);
		sitesConfiguration.getSites().add(createJiraSite());
		sitesConfiguration.getSites().add(createJiraSite(URI.create("http://jira.example.com/rest/jenkins/latest"), "Jira"));
		MarkupText text = new MarkupText("DUMMY-1!, DUMMY-2");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("<a href='http://localhost:2990/jira/browse/DUMMY-1'>DUMMY-1</a>!, DUMMY-2"));
	}

	@Test
	public void testAnnotate_SingleIssue()
	{
		sitesConfiguration.getSites().add(createJiraSite());
		requests.addRule()
				.get()
				.urlStarts("http://localhost:2990/jira/")
				.urlEnds("links")
				.respond(200, ResponseBody.create("{\"DUMMY-1\": \"http://localhost:2990/jira/browse/DUMMY-1\"}", JiraSite.JSON));
		MarkupText text = new MarkupText("DUMMY-1!");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("<a href='http://localhost:2990/jira/browse/DUMMY-1'>DUMMY-1</a>!"));
	}

	@Test
	public void testAnnotate_MultipleIssues()
	{
		sitesConfiguration.getSites().add(createJiraSite());
		requests.addRule()
				.get()
				.urlStarts("http://localhost:2990/jira/")
				.urlEnds("links")
				.respond(200, ResponseBody.create("{\"DUMMY-1\": \"http://localhost:2990/jira/browse/DUMMY-1\"," +
						                                  "\"DUMMY-2\": \"http://localhost:2990/jira/browse/DUMMY-2\"," +
						                                  "\"DUMMY-3\": \"http://localhost:2990/jira/browse/DUMMY-3\"," +
						                                  "\"DUMMY-4\": \"http://localhost:2990/jira/browse/DUMMY-4\"}", JiraSite.JSON));
		MarkupText text = new MarkupText("DUMMY-1 Text DUMMY-2,DUMMY-3 DUMMY-4 NOTME-1!");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("<a href='http://localhost:2990/jira/browse/DUMMY-1'>DUMMY-1</a> Text " +
				                                    "<a href='http://localhost:2990/jira/browse/DUMMY-2'>DUMMY-2</a>," +
				                                    "<a href='http://localhost:2990/jira/browse/DUMMY-3'>DUMMY-3</a> " +
				                                    "<a href='http://localhost:2990/jira/browse/DUMMY-4'>DUMMY-4</a> NOTME-1!"));
	}

	@Test
	public void testAnnotate_WordBoundaries()
	{
		sitesConfiguration.getSites().add(createJiraSite());
		requests.addRule()
				.get()
				.urlStarts("http://localhost:2990/jira/")
				.urlEnds("links")
				.respond(200, ResponseBody.create("{\"DUMMY-1\": \"http://localhost:2990/jira/browse/DUMMY-1\"}", JiraSite.JSON));
		MarkupText text = new MarkupText("DUMMY-1 Text ");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("<a href='http://localhost:2990/jira/browse/DUMMY-1'>DUMMY-1</a> Text "));

		text = new MarkupText("DUMMY-1,comment");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("<a href='http://localhost:2990/jira/browse/DUMMY-1'>DUMMY-1</a>,comment"));

		text = new MarkupText("DUMMY-1.comment");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("<a href='http://localhost:2990/jira/browse/DUMMY-1'>DUMMY-1</a>.comment"));

		text = new MarkupText("DUMMY-1!comment");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("<a href='http://localhost:2990/jira/browse/DUMMY-1'>DUMMY-1</a>!comment"));

		text = new MarkupText("DUMMY-1\tcomment");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("<a href='http://localhost:2990/jira/browse/DUMMY-1'>DUMMY-1</a>\tcomment"));

		text = new MarkupText("DUMMY-1\ncomment");
		annotator.annotate(build, null, text);
		assertThat(text.toString(false), is("<a href='http://localhost:2990/jira/browse/DUMMY-1'>DUMMY-1</a><br>comment"));
	}

	private JiraSite createJiraSite()
	{
		return createJiraSite(URI.create("http://localhost:2990/jira/rest/jenkins/latest"), "Local Jira");
	}

	private JiraSite createJiraSite(
			URI uri,
			String name)
	{
		return new JiraSite(uri).withName(name)
				.withIdentifier(UUID.randomUUID().toString())
				.withSharedSecret(SharedSecretGenerator.generate());
	}
}
