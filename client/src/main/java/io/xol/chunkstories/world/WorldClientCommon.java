//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.world;

import io.xol.chunkstories.api.content.ContentTranslator;
import io.xol.chunkstories.api.plugin.ClientPluginManager;
import io.xol.chunkstories.api.world.WorldClient;
import io.xol.chunkstories.client.ClientImplementation;
import io.xol.chunkstories.renderer.debug.WorldLogicTimeRenderer;
import io.xol.chunkstories.renderer.decals.DecalsRendererImplementation;
import io.xol.chunkstories.renderer.particles.ClientParticlesRenderer;
import io.xol.chunkstories.renderer.world.WorldRendererImplementation;

/**
 * Mostly the common methods of WorldClientRemote and WorldClientLocal
 */
public abstract class WorldClientCommon extends WorldImplementation implements WorldClient {
	protected WorldRendererImplementation renderer;

	public WorldClientCommon(ClientImplementation client, WorldInfoImplementation info) throws WorldLoadingException {
		this(client, info, null);
	}

	public WorldClientCommon(ClientImplementation client, WorldInfoImplementation info, ContentTranslator translator)
			throws WorldLoadingException {
		super(client, info, translator);

		this.renderer = new WorldRendererImplementation(this, client);
	}

	public ClientPluginManager getPluginManager() {
		return ClientImplementation.getInstance().getPluginManager();
	}

	@Override
	public ClientImplementation getClient() {
		return ClientImplementation.getInstance();
	}

	public ClientImplementation getGameContext() {
		return getClient();
	}

	@Override
	public WorldRendererImplementation getWorldRenderer() {
		return renderer;
	}

	@Override
	public DecalsRendererImplementation getDecalsManager() {
		return renderer.getDecalsRenderer();
	}

	@Override
	public ClientParticlesRenderer getParticlesManager() {
		return renderer.getParticlesRenderer();
	}

	@Override
	public void tick() {
		super.tick();

		// Update used map bits
		getClient().getPlayer().loadingAgent.updateUsedWorldBits();

		// Update world timing graph
		WorldLogicTimeRenderer.tickWorld();

		// Update world effects
		getWorldRenderer().getWorldEffectsRenderer().tick();

		// Update particles subsystem if it exists
		if (getParticlesManager() != null && getParticlesManager() instanceof ClientParticlesRenderer)
			((ClientParticlesRenderer) getParticlesManager()).updatePhysics();
	}
}
