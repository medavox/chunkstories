package io.xol.chunkstories.core.entity.voxel;

import io.xol.chunkstories.api.entity.Inventory;
import io.xol.chunkstories.api.entity.EntityVoxel;
import io.xol.chunkstories.api.entity.interfaces.EntityWithInventory;
import io.xol.chunkstories.api.world.World;
import io.xol.chunkstories.core.entity.components.EntityComponentPublicInventory;
import io.xol.chunkstories.entity.EntityImplementation;
import io.xol.chunkstories.physics.CollisionBox;

//(c) 2015-2017 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class EntityChest extends EntityImplementation implements EntityWithInventory, EntityVoxel
{
	EntityComponentPublicInventory inventoryComponent;
	
	public EntityChest(World w, double x, double y, double z)
	{
		super(w, x, y, z);
		inventoryComponent = new EntityComponentPublicInventory(this, 10, 6);
	}
	
	@Override
	public CollisionBox[] getCollisionBoxes()
	{
		return new CollisionBox[] { new CollisionBox(1.0, 1.0, 1.0).translate(0.5, 0, 0.5) };
	}

	@Override
	public Inventory getInventory()
	{
		return inventoryComponent;
	}

}
