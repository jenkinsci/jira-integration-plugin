package org.marvelution.jji.export;

import org.kohsuke.stapler.export.*;

/**
 * Holder for deployment environment details.
 *
 * @author Mark Rekveld
 * @since 3.8.0
 */
@ExportedBean
public class Environment
{

	private final String id;
	private final String name;
	private final Type type;

	public Environment(
			String id,
			String name,
			Type type)
	{
		this.id = id;
		this.name = name;
		this.type = type;
	}

	@Exported(visibility = 3)
	public String getId()
	{
		return id;
	}

	@Exported(visibility = 3)
	public String getName()
	{
		return name;
	}

	@Exported(visibility = 3)
	public Type getType()
	{
		return type;
	}

	@Override
	public String toString()
	{
		return type.name() + " environment " + name + " (" + id + ")";
	}

	public enum Type
	{
		unmapped,
		development,
		testing,
		staging,
		production;

		public static Type fromString(String string)
		{
			try
			{
				return valueOf(string);
			}
			catch (IllegalArgumentException e)
			{
				return unmapped;
			}
		}
	}
}
