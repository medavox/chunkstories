package io.xol.chunkstories.core.entity;

import java.util.HashSet;
import java.util.Set;

import io.xol.chunkstories.api.ai.AI;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.rendering.RenderingInterface;
import io.xol.chunkstories.api.rendering.entity.EntityRenderable;
import io.xol.chunkstories.api.rendering.entity.EntityRenderer;
import io.xol.chunkstories.api.world.WorldAuthority;
import io.xol.chunkstories.core.entity.ai.AggressiveHumanoidAI;
import io.xol.chunkstories.world.WorldImplementation;
import io.xol.engine.graphics.textures.Texture2D;
import io.xol.engine.graphics.textures.TexturesHandler;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class EntityZombie extends EntityHumanoid
{
	int i = 0;
	AI<?> zombieAi;

	static Set<Class<? extends Entity>> zombieTargets = new HashSet<Class<? extends Entity>>();
	
	static {
		zombieTargets.add(EntityPlayer.class);
	}
	
	public EntityZombie(WorldImplementation w, double x, double y, double z)
	{
		super(w, x, y, z);
		zombieAi = new AggressiveHumanoidAI(this, 10, zombieTargets);
	}

	@Override
	public float getStartHealth()
	{
		return 50f;
	}

	public boolean renderable()
	{
		return true;
	}
	
	@Override
	public void tick(WorldAuthority authority)
	{
		//AI works on master
		if(authority.isMaster())
			zombieAi.tick();
		
		//Ticks the entity
		super.tick(authority);
		
		//Anti-glitch
		if(Double.isNaN(this.getEntityRotationComponent().getHorizontalRotation()))
		{
			System.out.println("nan !" + this);
			this.getEntityRotationComponent().setRotation(0.0, 0.0);
		}
	}

	class EntityZombieRenderer<H extends EntityHumanoid> extends EntityHumanoidRenderer<H> {
		
		@Override
		public void setupRender(RenderingInterface renderingContext)
		{
			super.setupRender(renderingContext);
			
			//Player textures
			Texture2D playerTexture = TexturesHandler.getTexture("./models/zombie_s3.png");
			playerTexture.setLinearFiltering(false);
			
			renderingContext.bindAlbedoTexture(playerTexture);
		}
	}
	
	@Override
	public EntityRenderer<? extends EntityRenderable> getEntityRenderer()
	{
		return new EntityZombieRenderer<EntityZombie>();
	}
}
