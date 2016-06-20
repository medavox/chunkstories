package io.xol.chunkstories.world;

import java.io.File;
import java.util.Iterator;

import io.xol.chunkstories.api.Location;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.entity.components.Subscriber;
import io.xol.chunkstories.api.world.ChunksIterator;
import io.xol.chunkstories.api.world.WorldMaster;
import io.xol.chunkstories.core.events.PlayerSpawnEvent;
import io.xol.chunkstories.net.packets.PacketPlaySound;
import io.xol.chunkstories.net.packets.PacketTime;
import io.xol.chunkstories.net.packets.PacketVoxelUpdate;
import io.xol.chunkstories.net.packets.PacketsProcessor.PendingSynchPacket;
import io.xol.chunkstories.physics.particules.Particle;
import io.xol.chunkstories.server.Server;
import io.xol.chunkstories.server.net.ServerClient;
import io.xol.chunkstories.world.chunk.ChunkHolder;
import io.xol.chunkstories.world.chunk.CubicChunk;
import io.xol.chunkstories.world.io.IOTasksMultiplayerServer;
import io.xol.engine.math.LoopingMathHelper;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class WorldServer extends WorldImplementation implements WorldMaster, WorldNetworked
{
	public WorldServer(String worldDir)
	{
		super(new WorldInfo(new File(worldDir + "/info.txt"), new File(worldDir).getName()));

		ioHandler = new IOTasksMultiplayerServer(this);
		ioHandler.start();
	}

	@Override
	public void tick()
	{
		this.trimRemovableChunks();
		//Update client tracking
		for (ServerClient client : Server.getInstance().handler.getAuthentificatedClients())
		{
			//System.out.println("client: "+client);
			if (client.getProfile().hasSpawned())
			{
				//System.out.println(client.getProfile().hasSpawned());
				//Load 8x4x8 chunks arround player
				Location loc = client.getProfile().getLocation();
				int chunkX = (int) (loc.getX() / 32f);
				int chunkY = (int) (loc.getY() / 32f);
				int chunkZ = (int) (loc.getZ() / 32f);
				for(int cx = chunkX - 4 ; cx < chunkX + 4; cx ++)
					for(int cy = chunkY - 2 ; cy < chunkY + 2; cy ++)
						for(int cz = chunkZ - 4 ; cz < chunkZ + 4; cz ++)
							this.getChunk(chunkX, chunkY, chunkZ, true);
				
				//System.out.println("chunk:"+this.getChunk(chunkX, chunkY, chunkZ, true));
				//System.out.println("holder:"+client.getProfile().getControlledEntity().getChunkHolder());
				//Update whatever he controls
				client.getProfile().updateTrackedEntities();
			}
			PacketTime packetTime = new PacketTime(false);
			packetTime.time = this.worldTime;
			client.sendPacket(packetTime);
		}
		super.tick();
	}

	public void handleWorldMessage(ServerClient sender, String message)
	{
		if (message.equals("info"))
		{
			//Sends the construction info for the world, and then the player entity
			worldInfo.sendInfo(sender);

			PlayerSpawnEvent playerSpawnEvent = new PlayerSpawnEvent(sender.getProfile(), this);
			Server.getInstance().getPluginsManager().fireEvent(playerSpawnEvent);

		}
		else if(message.equals("respawn"))
		{
			//TODO respawn request
			
		}
		if (message.startsWith("getChunkCompressed"))
		{
			//System.out.println(message);
			String[] split = message.split(":");
			int x = Integer.parseInt(split[1]);
			int y = Integer.parseInt(split[2]);
			int z = Integer.parseInt(split[3]);
			((IOTasksMultiplayerServer) ioHandler).requestCompressedChunkSend(x, y, z, sender);
		}
		if (message.startsWith("getChunkSummary"))
		{
			String[] split = message.split(":");
			int x = Integer.parseInt(split[1]);
			int z = Integer.parseInt(split[2]);
			((IOTasksMultiplayerServer) ioHandler).requestChunkSummary(x, z, sender);
		}
	}

	@Override
	public void trimRemovableChunks()
	{
		int chunksViewDistance = 256 / 32;
		int sizeInChunks = getWorldInfo().getSize().sizeInChunks;

		//Chunks pruner
		ChunksIterator i = this.getAllLoadedChunks();
		CubicChunk c;
		while (i.hasNext())
		{
			c = i.next();
			boolean neededBySomeone = false;
			for (ServerClient client : Server.getInstance().handler.clients)
			{
				if (client.isAuthentificated())
				{
					Entity clientEntity = client.getProfile().getControlledEntity();
					if (clientEntity == null)
						continue;
					Location loc = clientEntity.getLocation();
					int pCX = (int) loc.x / 32;
					int pCY = (int) loc.y / 32;
					int pCZ = (int) loc.z / 32;
					//TODO use proper configurable values for this
					if (((LoopingMathHelper.moduloDistance(c.chunkX, pCX, sizeInChunks) < chunksViewDistance + 1) || (LoopingMathHelper.moduloDistance(c.chunkZ, pCZ, sizeInChunks) < chunksViewDistance + 1) || (Math.abs(c.chunkY - pCY) < 4)))
					{
						neededBySomeone = true;
					}
				}
			}

			if (!neededBySomeone)
			{
				removeChunk(c, true);
			}
		}
		
		//4 of margin bc we need to be far enought of the center of the holder
		chunksViewDistance += 4;
		
		Iterator<ChunkHolder> chunksHoldersIterator = this.getChunksHolder().getLoadedChunkHolders();
		while(chunksHoldersIterator.hasNext())
		{
			ChunkHolder chunkHolder = chunksHoldersIterator.next();
			int chunkHolderCenterX = chunkHolder.regionX * 8 + 4;
			int chunkHolderCenterY = chunkHolder.regionY * 8 + 4;
			int chunkHolderCenterZ = chunkHolder.regionZ * 8 + 4;
			
			boolean neededBySomeone = false;
			for (ServerClient client : Server.getInstance().handler.clients)
			{
				if (client.isAuthentificated())
				{
					Entity clientEntity = client.getProfile().getControlledEntity();
					if (clientEntity == null)
						continue;
					Location loc = clientEntity.getLocation();
					int pCX = (int) loc.x / 32;
					int pCY = (int) loc.y / 32;
					int pCZ = (int) loc.z / 32;
					//TODO use proper configurable values for this
					if (((LoopingMathHelper.moduloDistance(chunkHolderCenterX, pCX, sizeInChunks) < chunksViewDistance + 2)
							&& (LoopingMathHelper.moduloDistance(chunkHolderCenterZ, pCZ, sizeInChunks) < chunksViewDistance + 2)
							&& (Math.abs(chunkHolderCenterY - pCY) < 4+4)))
					{
						neededBySomeone = true;
					}
				}
			}
			
			if(chunkHolder.getNumberOfLoadedChunks() == 0 && !neededBySomeone)
			{
				//TODO saves
				chunkHolder.unload();
				chunksHoldersIterator.remove();
			}
		}
	}

	@Override
	public void setDataAt(int x, int y, int z, int i, boolean load)
	{
		int blocksViewDistance = 256;
		int sizeInBlocks = getWorldInfo().getSize().sizeInChunks * 32;
		super.setDataAt(x, y, z, i, load);
		PacketVoxelUpdate packet = new PacketVoxelUpdate(false);
		packet.x = x;
		packet.y = y;
		packet.z = z;
		packet.data = i;
		for (ServerClient client : Server.getInstance().handler.clients)
		{
			if (client.isAuthentificated())
			{
				Entity clientEntity = client.getProfile().getControlledEntity();
				if (clientEntity == null)
					continue;
				Location loc = clientEntity.getLocation();
				int plocx = (int) loc.x;
				int plocy = (int) loc.y;
				int plocz = (int) loc.z;
				//TODO use proper configurable values for this
				if (!((LoopingMathHelper.moduloDistance(x, plocx, sizeInBlocks) > blocksViewDistance + 2) || (LoopingMathHelper.moduloDistance(z, plocz, sizeInBlocks) > blocksViewDistance + 2) || (y - plocy) > 4 * 32))
				{
					client.sendPacket(packet);
				}
			}
		}
	}

	@Override
	public void processIncommingPackets()
	{
		for (ServerClient client : Server.getInstance().handler.clients)
		{
			if(client.isAuthentificated())
			{
				//System.out.println("processing queued packets of "+client);
				PendingSynchPacket packet = client.getPacketsProcessor().getPendingSynchPacket();
				while(packet != null)
				{
					packet.process(client, client.getPacketsProcessor());
					packet = client.getPacketsProcessor().getPendingSynchPacket();
				}
			}
		}
	}
	
	public void addParticle(Particle particle)
	{
		
	}

	@Override
	public void playSoundEffect(String soundEffect, Location location, float pitch, float gain)
	{
		this.playSoundEffectExcluding(soundEffect, location, pitch, gain, null);
	}
	
	@Override
	public void playSoundEffectExcluding(String soundEffect, Location location, float pitch, float gain, Subscriber subscriber)
	{
		PacketPlaySound packetSound = new PacketPlaySound(false);
		packetSound.soundName = soundEffect;
		packetSound.position = location;
		packetSound.gain = gain;
		packetSound.pitch = pitch;
		
		for (ServerClient client : Server.getInstance().handler.clients)
		{
			if (client.isAuthentificated())
			{
				Entity clientEntity = client.getProfile().getControlledEntity();
				if (clientEntity == null)
					continue;
				if(subscriber != null && subscriber.equals(client.getProfile()))
					continue;
				
				client.sendPacket(packetSound);
			}
		}
	}
}