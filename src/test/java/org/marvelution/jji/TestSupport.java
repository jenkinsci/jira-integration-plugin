package org.marvelution.jji;

import org.junit.*;
import org.mockito.*;

public abstract class TestSupport
{

	private AutoCloseable mocks;

	@Before
	public void setUpMocks()
	{
		mocks = MockitoAnnotations.openMocks(this);
	}

	@After
	public void tearDown()
			throws Exception
	{
		mocks.close();
	}
}
