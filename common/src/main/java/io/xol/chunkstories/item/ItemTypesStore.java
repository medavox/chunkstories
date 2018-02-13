package io.xol.chunkstories.item;

import io.xol.chunkstories.api.content.Content;
import io.xol.chunkstories.api.exceptions.content.IllegalItemDeclarationException;
import io.xol.chunkstories.api.item.Item;
import io.xol.chunkstories.api.item.ItemDefinition;
import io.xol.chunkstories.api.content.Asset;
import io.xol.chunkstories.api.content.mods.ModsManager;
import io.xol.chunkstories.content.GameContentStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//(c) 2015-2017 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class ItemTypesStore implements Content.ItemsTypes
{
	Map<Short, Constructor<? extends Item>> itemsTypes = new HashMap<Short, Constructor<? extends Item>>();
	public Map<String, ItemDefinition> dictionary = new HashMap<String, ItemDefinition>();
	public int itemTypes = 0;
	public int lastAllocatedId;

	private final Content content;
	private final ModsManager modsManager;

	private static final Logger logger = LoggerFactory.getLogger("content.items");
	public Logger logger() {
		return logger;
	}
	
	public ItemTypesStore(GameContentStore gameContentStore)
	{
		this.content = gameContentStore;
		this.modsManager = gameContentStore.modsManager();

		//reload();
	}

	public void reload()
	{
		dictionary.clear();

		Iterator<Asset> i = modsManager.getAllAssetsByExtension("items");
		while (i.hasNext())
		{
			Asset f = i.next();
			logger().debug("Reading items definitions in : " + f);
			readitemsDefinitions(f);
		}
	}

	private void readitemsDefinitions(Asset f)
	{
		if (f == null)
			return;
		try
		{
			BufferedReader reader = new BufferedReader(f.reader());
			String line = "";

			//ItemTypeImpl currentItemType = null;
			while ((line = reader.readLine()) != null)
			{
				line = line.replace("\t", "");
				if (line.startsWith("#"))
				{
					// It's a comment, ignore.
				}
				else if (line.startsWith("end"))
				{
					//if (currentItemType == null)
					{
						logger().warn("Syntax error in file : " + f + " : ");
						continue;
					}
				}
				else if (line.startsWith("item"))
				{
					if (line.contains(" "))
					{
						String[] split = line.split(" ");
						String itemName = split[1];

						try
						{
							ItemTypeImpl itemType = new ItemTypeImpl(this, itemName, reader);

							dictionary.put(itemType.getInternalName(), itemType);
						}
						catch (IllegalItemDeclarationException e)
						{
							e.printStackTrace();
						}
					}
				}
			}
			reader.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public ItemDefinition getItemTypeByName(String itemName)// throws UndefinedItemTypeException
	{
		if (dictionary.containsKey(itemName))
			return dictionary.get(itemName);
		return null;
	}

	@Override
	public Iterator<ItemDefinition> all()
	{
		return dictionary.values().iterator();
	}

	@Override
	public Content parent()
	{
		return content;
	}
}
