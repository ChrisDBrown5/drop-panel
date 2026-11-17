package com.droppanel.layout;

/**
 * Panel sections, in display order.
 */
public enum Section
{
	UNIQUE("Uniques"),
	TERTIARY("Tertiary"),
	RARE("Rare"),
	HIGH_VALUE("High value"),
	OTHER("Other drops"),
	UNIFORM("Drops");

	private final String label;

	Section(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}
}
