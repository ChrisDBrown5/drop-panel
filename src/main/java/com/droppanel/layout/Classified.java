package com.droppanel.layout;

import com.droppanel.data.Drop;
import lombok.Value;

@Value
public class Classified
{
	Drop drop;
	Section section;
	boolean collectionLogItem;
}
