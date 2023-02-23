package org.marvelution.jji;

import java.io.*;
import java.util.*;

import hudson.model.*;
import net.sf.json.*;
import org.apache.commons.io.*;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.export.*;

import static org.apache.commons.codec.digest.DigestUtils.*;
import static org.apache.commons.lang.StringUtils.*;

public class JiraUtils
{

	private static final ModelBuilder MODEL_BUILDER = new ModelBuilder();

	public static String getJobHash(Run run)
	{
		return getJobHash(run.getParent());
	}

	public static String getJobHash(Item item)
	{
		return getJobHash(item.getUrl());
	}

	public static String getJobHash(String item)
	{
		String url = stripStart(stripEnd(item, "/"), "/");
		if (url.startsWith("job/"))
		{
			url = url.substring(4);
		}
		return sha1Hex(url);
	}

	public static JSONObject getJsonFromRequest(StaplerRequest request)
			throws IOException
	{
		return JSONObject.fromObject(IOUtils.toString(request.getReader()));
	}

	public static List<String> getAllParentHashes(Run run)
	{
		List<String> hashes = new ArrayList<>();
		Object parent = run.getParent();
		while (parent instanceof Item)
		{
			hashes.add(getJobHash((Item) parent));
			parent = ((Item) parent).getParent();
		}
		return hashes;
	}

	@SuppressWarnings("unchecked")
	public static <T> String asJson(
			T item,
			List<String> fields)
	{
		try
		{
			StringWriter writer = new StringWriter();
			Model<T> model = (Model<T>) MODEL_BUILDER.get(item.getClass());
			TreePruner pruner;
			if (fields.isEmpty())
			{
				pruner = new TreePruner.ByDepth(1);
			}
			else
			{
				pruner = new NamedPathPruner(String.join(",", fields));
			}
			model.writeTo(item, pruner, Flavor.JSON.createDataWriter(item, writer, new ExportConfig()));
			return writer.toString();
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
}
