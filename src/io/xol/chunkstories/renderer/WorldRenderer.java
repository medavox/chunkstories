package io.xol.chunkstories.renderer;

import static io.xol.engine.graphics.textures.TextureType.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;

import io.xol.engine.math.lalgb.Matrix3f;
import io.xol.engine.math.lalgb.Matrix4f;
import io.xol.engine.math.lalgb.Vector3f;

import io.xol.engine.base.InputAbstractor;
import io.xol.engine.base.GameWindowOpenGL;
import io.xol.engine.graphics.fbo.FBO;
import io.xol.engine.graphics.shaders.ShaderProgram;
import io.xol.engine.graphics.shaders.ShadersLibrary;
import io.xol.engine.graphics.textures.Cubemap;
import io.xol.engine.graphics.textures.GBufferTexture;
import io.xol.engine.graphics.textures.Texture2D;
import io.xol.engine.graphics.textures.TexturesHandler;
import io.xol.engine.graphics.util.ObjectRenderer;
import io.xol.engine.graphics.util.PBOPacker;
import io.xol.engine.math.LoopingMathHelper;
import io.xol.engine.math.Math2;
import io.xol.engine.math.MatrixHelper;
import io.xol.engine.math.lalgb.Vector3d;
import io.xol.engine.model.RenderingContext;
import io.xol.chunkstories.client.Client;
import io.xol.chunkstories.client.FastConfig;
import io.xol.chunkstories.content.GameDirectory;
import io.xol.chunkstories.renderer.chunks.ChunkRenderData;
import io.xol.chunkstories.renderer.chunks.ChunksRenderer;
import io.xol.chunkstories.renderer.debug.OverlayRenderer;
import io.xol.chunkstories.renderer.lights.LightsRenderer;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.entity.interfaces.EntityHUD;
import io.xol.chunkstories.api.voxel.Voxel;
import io.xol.chunkstories.api.world.Chunk;
import io.xol.chunkstories.api.world.ChunksIterator;
import io.xol.chunkstories.voxel.VoxelTypes;
import io.xol.chunkstories.world.WorldClientCommon;
import io.xol.chunkstories.world.chunk.ChunkRenderable;
import io.xol.chunkstories.world.chunk.CubicChunk;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class WorldRenderer
{
	// World pointer
	WorldClientCommon world;

	// Worker thread
	public ChunksRenderer chunksRenderer;

	//Chunk space position
	public int currentChunkX, currentChunkY, currentChunkZ;

	// camera object ( we need it so much)
	private Camera camera;

	private int sizeInChunks; // cache from world

	// Chunks to render
	private List<ChunkRenderable> renderList = new ArrayList<ChunkRenderable>();

	// Wheter to update the renderlist or not.
	private boolean chunksChanged = true;

	// Screen width & height
	private int scrW, scrH;
	
	// Shader programs
	private ShaderProgram terrainShader;
	private ShaderProgram opaqueBlocksShader;
	private ShaderProgram liquidBlocksShader;
	private ShaderProgram entitiesShader;
	private ShaderProgram shadowsPassShader;
	private ShaderProgram applyShadowsShader;
	private ShaderProgram lightShader;
	private ShaderProgram postProcess;

	private ShaderProgram bloomShader;
	private ShaderProgram ssaoShader;

	private ShaderProgram blurH;
	private ShaderProgram blurV;

	//Rendering context
	private RenderingContext renderingContext;

	// Main Rendertarget (HDR)
	private GBufferTexture shadedBuffer = new GBufferTexture(RGB_HDR, GameWindowOpenGL.windowWidth, GameWindowOpenGL.windowHeight);
	private int illDownIndex = 0;
	private int illDownBuffers = 1;
	private long lastIllCalc = 8;
	private PBOPacker illuminationDownloader[] = new PBOPacker[illDownBuffers];
	
	// G-Buffers
	private GBufferTexture albedoBuffer = new GBufferTexture(RGBA_8BPP, GameWindowOpenGL.windowWidth, GameWindowOpenGL.windowHeight);
	private GBufferTexture zBuffer = new GBufferTexture(DEPTH_RENDERBUFFER, GameWindowOpenGL.windowWidth, GameWindowOpenGL.windowHeight);
	private GBufferTexture normalBuffer = new GBufferTexture(RGBA_8BPP, GameWindowOpenGL.windowWidth, GameWindowOpenGL.windowHeight);
	private GBufferTexture materialBuffer = new GBufferTexture(RGBA_8BPP, GameWindowOpenGL.windowWidth, GameWindowOpenGL.windowHeight);

	// Bloom texture
	private GBufferTexture bloomBuffer = new GBufferTexture(RGB_HDR, GameWindowOpenGL.windowWidth / 2, GameWindowOpenGL.windowHeight / 2);
	private GBufferTexture ssaoBuffer = new GBufferTexture(RGBA_8BPP, GameWindowOpenGL.windowWidth, GameWindowOpenGL.windowHeight);

	// FBOs
	private FBO fboGBuffers = new FBO(zBuffer, albedoBuffer, normalBuffer, materialBuffer);

	private FBO fboShadedBuffer = new FBO(zBuffer, shadedBuffer);
	private FBO fboBloom = new FBO(null, bloomBuffer);
	private FBO fboSSAO = new FBO(null, ssaoBuffer);

	private GBufferTexture blurIntermediateBuffer = new GBufferTexture(RGB_HDR, GameWindowOpenGL.windowWidth / 2, GameWindowOpenGL.windowHeight / 2);
	private FBO fboBlur = new FBO(null, blurIntermediateBuffer);

	// 64x64 texture used to cull distant mesh
	private GBufferTexture loadedChunksMapTop = new GBufferTexture(DEPTH_RENDERBUFFER, 64, 64);
	private FBO fboLoadedChunksTop = new FBO(loadedChunksMapTop);
	private GBufferTexture loadedChunksMapBot = new GBufferTexture(DEPTH_RENDERBUFFER, 64, 64);
	private FBO fboLoadedChunksBot = new FBO(loadedChunksMapBot);

	// Shadow maps
	private int shadowMapResolution = 0;
	private GBufferTexture shadowMapBuffer = new GBufferTexture(DEPTH_SHADOWMAP, 256, 256);
	private FBO shadowMapFBO = new FBO(shadowMapBuffer);

	//Environment map
	private Cubemap environmentMap = new Cubemap(Cubemap.CubemapType.RGBA_8BPP);
	private FBO environmentMapFBO = new FBO(null, environmentMap.getFace(0));

	// Shadow transformation matrix
	private Matrix4f depthMatrix = new Matrix4f();

	// Sky
	private SkyRenderer sky;

	// Debug
	// private DebugProfiler updateProfiler = new DebugProfiler();

	//Far terrain mesher
	public FarTerrainRenderer farTerrainRenderer;
	
	//Rain snow etc
	public WeatherEffectsRenderer weatherEffectsRenderer;

	//For shaders animations
	float animationTimer = 0.0f;

	//Counters
	public int renderedVertices = 0;
	public int renderedVerticesShadow = 0;
	public int renderedChunks = 0;
	
	//Bloom avg color buffer
	ByteBuffer shadedMipmapZeroLevelColor = null;//BufferUtils.createByteBuffer(4 * 3);
	//Bloom aperture
	float apertureModifier = 1f;

	//Sky stuff
	Texture2D sunGlowTexture = TexturesHandler.getTexture("environement/glow.png");
	Texture2D skyTextureSunny = TexturesHandler.getTexture("environement/sky.png");
	Texture2D skyTextureRaining = TexturesHandler.getTexture("environement/sky_rain.png");
	
	Texture2D lightmapTexture = TexturesHandler.getTexture("environement/light.png");
	Texture2D waterNormalTexture = TexturesHandler.getTexture("water/shallow.png");

	Texture2D blocksDiffuseTexture = TexturesHandler.getTexture("tiles_merged_diffuse.png");
	Texture2D blocksNormalTexture = TexturesHandler.getTexture("tiles_merged_normal.png");
	Texture2D blocksMaterialTexture = TexturesHandler.getTexture("tiles_merged_material.png");

	//SSAO (disabled)
	Vector3f ssao_kernel[];
	int ssao_kernel_size;
	int ssao_noiseTex = -1;

	//8K buffer of 2D coordinates to draw the local map
	ByteBuffer localMapCommands = BufferUtils.createByteBuffer(8192);

	long lastEnvmapRender = 0L;
	
	//Constructor and modificators
	
	public WorldRenderer(WorldClientCommon w)
	{
		// Link world
		world = w;
		world.linkWorldRenderer(this);
		farTerrainRenderer = new FarTerrainRenderer(world);
		weatherEffectsRenderer = new WeatherEffectsRenderer(world, this);
		sky = new SkyRenderer(world, this);
		sizeInChunks = world.getSizeInChunks();
		resizeShadowMaps();

		renderingContext = GameWindowOpenGL.getInstance().getRenderingContext();
		GameWindowOpenGL.instance.renderingContext = renderingContext;

		//Pre-load shaders
		opaqueBlocksShader = ShadersLibrary.getShaderProgram("blocks_opaque");
		entitiesShader = ShadersLibrary.getShaderProgram("entities");
		shadowsPassShader = ShadersLibrary.getShaderProgram("shadows");
		applyShadowsShader = ShadersLibrary.getShaderProgram("shadows_apply");
		lightShader = ShadersLibrary.getShaderProgram("light");
		postProcess = ShadersLibrary.getShaderProgram("postprocess");
		terrainShader = ShadersLibrary.getShaderProgram("terrain");

		bloomShader = ShadersLibrary.getShaderProgram("bloom");
		ssaoShader = ShadersLibrary.getShaderProgram("ssao");
		blurH = ShadersLibrary.getShaderProgram("blurH");
		blurV = ShadersLibrary.getShaderProgram("blurV");

		for(int i = 0; i < illDownBuffers; i++)
			illuminationDownloader[i] = new PBOPacker();
		
		chunksRenderer = new ChunksRenderer(world);
		chunksRenderer.start();
	}
	
	public void setupRenderSize(int width, int height)
	{
		scrW = width;
		scrH = height;
		this.fboGBuffers.resizeFBO(width, height);
		this.fboShadedBuffer.resizeFBO(width, height);
		// this.composite_pass_gbuffers_waterfp.resizeFBO(width, height);
		// Resize bloom components
		fboBlur.resizeFBO(width / 2, height / 2);
		fboBloom.resizeFBO(width / 2, height / 2);
		fboSSAO.resizeFBO(width, height);
	}
	
	public void resizeShadowMaps()
	{
		// Only if necessary
		if (shadowMapResolution == FastConfig.shadowMapResolutions)
			return;
		shadowMapResolution = FastConfig.shadowMapResolutions;
		shadowMapBuffer.resize(shadowMapResolution, shadowMapResolution);
	}
	
	public void flagModified()
	{
		chunksChanged = true;
	}
	
	public RenderingContext getRenderingContext()
	{
		return renderingContext;
	}
	
	//Rendering main calls
	
	public void renderWorldAtCamera(Camera camera)
	{
		Client.profiler.startSection("kekupdates");
		this.camera = camera;
		if (FastConfig.doDynamicCubemaps && (System.currentTimeMillis() - lastEnvmapRender) > 2000L)// * Math.pow(30.0f / XolioWindow.getFPS(), 1.0f))
			screenCubeMap(256, environmentMap);
		renderWorldAtCameraInternal(camera, -1);
	}

	public void renderWorldAtCameraInternal(Camera camera, int chunksToRenderLimit)
	{
		// Set camera
		this.camera = camera;
		OverlayRenderer.setCamera(camera);
		// Debug/Dev : reset vertex counts
		renderedChunks = 0;
		renderedVertices = 0;
		renderedVerticesShadow = 0;

		Client.profiler.startSection("updates");
		// Load/Unload required parts of the world, update the display list etc
		updateRender(camera);

		Client.profiler.startSection("next");
		// Shadows pre-pass
		if (FastConfig.doShadows && chunksToRenderLimit == -1)
			shadowPass();
		// Prepare matrices
		camera.justSetup(scrW, scrH);
		camera.translate();

		// Clear G-Buffers and bind shaded HDR rendertarget
		fboGBuffers.bind();
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		fboShadedBuffer.bind();

		// Draw sky
		if (FastConfig.debugGBuffers)
			glFinish();
		long t = System.nanoTime();
		sky.time = (world.worldTime % 10000) / 10000f;
		//sky.skyShader.use(true);
		//sky.skyShader.setUniformSamplerCubemap(7, "environmentCubemap", environmentMap);
		glViewport(0, 0, scrW, scrH);
		sky.render(renderingContext);

		if (FastConfig.debugGBuffers)
			glFinish();
		if (FastConfig.debugGBuffers)
			System.out.println("sky took " + (System.nanoTime() - t) / 1000000.0 + "ms");

		// Move camera to relevant position
		fboGBuffers.setEnabledRenderTargets();

		// Render world
		renderWorld(false, chunksToRenderLimit);

		// Render weather
		fboShadedBuffer.bind();
		fboShadedBuffer.setEnabledRenderTargets();
		weatherEffectsRenderer.renderEffects(renderingContext);

		// Debug
		if (FastConfig.debugGBuffers)
			glFinish();
		if (FastConfig.debugGBuffers)
			System.out.println("total took " + (System.nanoTime() - t) / 1000000.0 + "ms ( " + 1 / ((System.nanoTime() - t) / 1000000000.0) + " fps)");

		//Disable depth check
		glDisable(GL_DEPTH_TEST);

		// Do bloom
		if (FastConfig.doBloom)
			renderBloom();

		//Bind shaded buffer in case some other rendering is to be done
		fboShadedBuffer.bind();
		fboShadedBuffer.setEnabledRenderTargets();

		Client.profiler.startSection("done");
	}

	//Rendering passes
	
	/**
	 * Pre-rendering function : Uploads the finished meshes from ChunksRenderer; Grabs all the loaded chunks and add them to the rendering queue Updates far terrain meshes if needed Draws the chunksLoadedMap
	 * 
	 * @param camera
	 */
	public void updateRender(Camera camera)
	{
		Vector3d pos = new Vector3d(camera.pos).negate();
		// Called every frame, this method takes care of updating the world :
		// It will keep up to date the camera position, as well as a list of
		// to-render chunks in order to fill empty VBO space
		// Upload generated chunks data to GPU
		//updateProfiler.reset("vbo upload");
		ChunkRenderData chunkRenderData = chunksRenderer.getNextRenderedChunkData();
		int loadLimit = 16;
		while (chunkRenderData != null)
		{
			//CubicChunk c = world.getChunk(toload.x, toload.y, toload.z, false);
			CubicChunk c = chunkRenderData.chunk;
			if (c != null && c instanceof ChunkRenderable)
			{
				((ChunkRenderable)c).setChunkRenderData(chunkRenderData);
				
				//Upload data
				chunkRenderData.upload();
				chunksChanged = true;
			}
			else
			{
				if (FastConfig.debugGBuffers)
					System.out.println("ChunkRenderer outputted a chunk render for a not loaded chunk : ");
				//if (FastConfig.debugGBuffers)
				//	System.out.println("Chunks coordinates : X=" + toload.x + " Y=" + toload.y + " Z=" + toload.z);
				//if (FastConfig.debugGBuffers)
				//	System.out.println("Render information : vbo size =" + toload.s_normal + " and water size =" + toload.s_water);
				chunkRenderData.free();
			}
			loadLimit--;
			if (loadLimit > 0)
				chunkRenderData = chunksRenderer.getNextRenderedChunkData();
			else
				chunkRenderData = null;
		}
		// Update view
		//viewRotH = view_rotx;
		//viewRotV = view_roty;
		int newCX = Math2.floor((pos.x) / 32);
		int newCY = Math2.floor((pos.y) / 32);
		int newCZ = Math2.floor((pos.z) / 32);
		// Fill the VBO array with chunks VBO ids if the player changed chunk
		if (currentChunkX != newCX || currentChunkY != newCY || currentChunkZ != newCZ || chunksChanged)
		{
			if (newCX != currentChunkX || newCZ != currentChunkZ)
				farTerrainRenderer.startAsynchSummaryRegeneration(camera);
			//Updates current chunk location
			currentChunkX = newCX;
			currentChunkY = newCY;
			currentChunkZ = newCZ;
			int chunksViewDistance = (int) (FastConfig.viewDistance / 32);

			// Unload too far chunks
			//updateProfiler.startSection("unloadFar");
			//long usageBefore = Runtime.getRuntime().freeMemory();
			world.trimRemovableChunks();
			renderList.clear();

			//Iterates over all loaded chunks to generate list and map
			fboLoadedChunksBot.bind();
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			
			fboLoadedChunksTop.bind();
			glViewport(0, 0, 64, 64);
			glClearDepth(0f);
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			glClearDepth(1f);
			this.loadedChunksMapTop.setLinearFiltering(false);
			this.loadedChunksMapTop.setMipMapping(false);
			this.loadedChunksMapTop.setTextureWrapping(false);
			//
			this.loadedChunksMapBot.setLinearFiltering(false);
			this.loadedChunksMapBot.setMipMapping(false);
			this.loadedChunksMapBot.setTextureWrapping(false);
			//fboLoadedChunks.resizeFBO(32, 32);
			renderingContext.setCurrentShader(ShadersLibrary.getShaderProgram("loaded_map"));
			localMapCommands.clear();

			int vertexIn = renderingContext.getCurrentShader().getVertexAttributeLocation("vertexIn");
			//System.out.println("vertexIn"+vertexIn);
			//glDepthMask(true);
			glEnable(GL_DEPTH_TEST);
			glDisable(GL_CULL_FACE);
			glDisable(GL_ALPHA_TEST);
			glDepthFunc(GL_GEQUAL);
			//glEnable(GL_BLEND);
			//glBlendFunc(GL_ONE, GL_ONE);

			ChunksIterator it = Client.world.getAllLoadedChunks();
			Chunk chunk;
			int localMapElements = 0;
			//int i = 0;
			while (it.hasNext())
			{
				//i++;
				chunk = it.next();
				if(chunk == null || !(chunk instanceof ChunkRenderable))
					continue;

				ChunkRenderable renderableChunk = (ChunkRenderable)chunk;
				
				if (LoopingMathHelper.moduloDistance(chunk.getChunkX(), currentChunkX, world.getSizeInChunks()) <= chunksViewDistance)
					if (LoopingMathHelper.moduloDistance(chunk.getChunkZ(), currentChunkZ, world.getSizeInChunks()) <= chunksViewDistance)
					{
						if (LoopingMathHelper.moduloDistance(chunk.getChunkX(), currentChunkX, world.getSizeInChunks()) < chunksViewDistance)
							if (LoopingMathHelper.moduloDistance(chunk.getChunkZ(), currentChunkZ, world.getSizeInChunks()) < chunksViewDistance)
							{
								if ((renderableChunk.getChunkRenderData() != null && renderableChunk.getChunkRenderData().isUploaded))
								{
									// chunkY = 5 height = 8
									//float yDistance = chunk.chunkY*32f + 16f - (world.regionSummaries.getMinChunkHeightAt(chunk.chunkX*32 + 16, chunk.chunkZ*32 + 16)-1);
									//System.out.println(chunk.chunkY+":"+);
									//if (Math.abs(yDistance) <= 16f)
									{
										int ajustedChunkX = chunk.getChunkX();
										int ajustedChunkZ = chunk.getChunkZ();
										if (chunk.getChunkX() - currentChunkX > chunksViewDistance)
											ajustedChunkX += -sizeInChunks;
										if (chunk.getChunkX() - currentChunkX < -chunksViewDistance)
											ajustedChunkX += sizeInChunks;
										if (chunk.getChunkZ() - currentChunkZ > chunksViewDistance)
											ajustedChunkZ += -sizeInChunks;
										if (chunk.getChunkZ() - currentChunkZ < -chunksViewDistance)
											ajustedChunkZ += sizeInChunks;
										
										localMapCommands.put((byte) (ajustedChunkX - currentChunkX));
										localMapCommands.put((byte) (ajustedChunkZ - currentChunkZ));
										localMapCommands.put((byte) (chunk.getChunkY()));
										//System.out.println(chunk.chunkY);
										localMapCommands.put((byte) 0x00);

										localMapElements++;
									}
								}
							}

						if (renderableChunk.isMarkedForReRender() && !chunk.isAirChunk())
						{
							//chunksRenderer.requestChunkRender(chunk);
							//chunksRenderer.addTask(a, b, c, chunk.need_render_fast);
						}
						renderList.add(renderableChunk);
					}
			}

			renderingContext.clearVertexAttributes();
			renderingContext.enableVertexAttribute(vertexIn);
			glBindBuffer(GL_ARRAY_BUFFER, 0);
			localMapCommands.flip();

			glVertexAttribPointer(vertexIn, 3, GL_BYTE, false, 4, localMapCommands);
			glDrawArrays(GL_POINTS, 0, localMapElements);
			//Two maps
			glDepthFunc(GL_LEQUAL);
			fboLoadedChunksBot.bind();

			glDrawArrays(GL_POINTS, 0, localMapElements);
			//glDepthFunc(GL_LEQUAL);
			//glDrawArrays(GL_TRIANGLES, 0, 3);

			renderingContext.disableVertexAttribute(vertexIn);

			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
			FBO.unbind();

			//Sort 
			renderList.sort(new Comparator<ChunkRenderable>()
			{
				@Override
				public int compare(ChunkRenderable a, ChunkRenderable b)
				{
					int distanceA = LoopingMathHelper.moduloDistance(a.getChunkX(), currentChunkX, world.getSizeInChunks()) + LoopingMathHelper.moduloDistance(a.getChunkZ(), currentChunkZ, world.getSizeInChunks());
					int distanceB = LoopingMathHelper.moduloDistance(b.getChunkX(), currentChunkX, world.getSizeInChunks()) + LoopingMathHelper.moduloDistance(b.getChunkZ(), currentChunkZ, world.getSizeInChunks());
					return distanceA - distanceB;
				}
			});

			// Now delete from the worker threads what we won't need anymore
			chunksRenderer.purgeUselessWork(currentChunkX, currentChunkY, currentChunkZ, sizeInChunks, chunksViewDistance);
			world.ioHandler.requestChunksUnload(currentChunkX, currentChunkY, currentChunkZ, sizeInChunks, chunksViewDistance);

			farTerrainRenderer.updateData();
			world.getRegionSummaries().removeFurther(currentChunkX, currentChunkZ, 33);

			chunksChanged = false;
			// Load nearby chunks
			for (int t = (currentChunkX - chunksViewDistance - 1); t < currentChunkX + chunksViewDistance + 1; t++)
			{
				for (int g = (currentChunkZ - chunksViewDistance - 1); g < currentChunkZ + chunksViewDistance + 1; g++)
					for (int b = currentChunkY - 3; b < currentChunkY + 3; b++)
					{
						chunk = world.getChunk(t, b, g, true);
					}
			}
		}
		// Cleans free vbos
		ChunkRenderData.deleteUselessVBOs();
	}

	public void shadowPass()
	{
		Client.profiler.startSection("shadows");
		// float worldTime = (world.worldTime%1000+1000)%1000;
		if (this.getShadowVisibility() == 0f)
			return; // No shadows at night :)
		glCullFace(GL_BACK);
		glEnable(GL_CULL_FACE);
		//glDisable(GL_CULL_FACE);
		glEnable(GL_ALPHA_TEST);
		glDisable(GL_BLEND);
		glEnable(GL_DEPTH_TEST);
		
		int size = (shadowMapBuffer).getWidth();
		glViewport(0, 0, size, size);

		shadowMapFBO.bind();
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		renderingContext.setCurrentShader(shadowsPassShader);
		//shadowsPassShader.use(true);
		int fun = 10;// / hdPass ? 3 : 8;
		if (size > 1024)
			fun = 15;
		else if (size > 2048)
			fun = 20;

		int fun2 = 200;// hdPass ? 100 : 200;
		Matrix4f depthProjectionMatrix = MatrixHelper.getOrthographicMatrix(-fun * 10, fun * 10, -fun * 10, fun * 10, -fun2, fun2);
		Matrix4f depthViewMatrix = MatrixHelper.getLookAtMatrix(sky.getSunPosition(), new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));

		Matrix4f.mul(depthProjectionMatrix, depthViewMatrix, depthMatrix);
		Matrix4f shadowMVP = new Matrix4f(depthMatrix);

		//System.out.println(depthViewMatrix);
		//depthMatrix.translate(new Vector3f((float) Math.floor(camera.pos.x), (float) Math.floor(camera.pos.y), (float) Math.floor(camera.pos.z)));
		//shadowMVP.translate(new Vector3f((float) Math.floor(camera.pos.x), (float) Math.floor(camera.pos.y), (float) Math.floor(camera.pos.z)));
		shadowMVP.translate(new Vector3f((float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z));

		shadowsPassShader.setUniformMatrix4f("depthMVP", shadowMVP);
		shadowsPassShader.setUniformMatrix4f("localTransform", new Matrix4f());
		shadowsPassShader.setUniformFloat("entity", 0);
		renderWorld(true, -1);
		glViewport(0, 0, scrW, scrH);
	}

	public void renderTerrain(boolean ignoreWorldCulling)
	{
		// Terrain
		Client.profiler.startSection("terrain");
		glDisable(GL_BLEND);

		renderingContext.setCurrentShader(terrainShader);
		//terrainShader.use(true);
		camera.setupShader(terrainShader);
		sky.setupShader(terrainShader);
		
		//terrainShader.setUniformFloat3("vegetationColor", vegetationColor[0] / 255f, vegetationColor[1] / 255f, vegetationColor[2] / 255f);
		terrainShader.setUniformFloat3("sunPos", sky.getSunPosition());
		terrainShader.setUniformFloat("time", animationTimer);
		terrainShader.setUniformFloat("terrainHeight", world.getRegionSummaries().getHeightAtWorldCoordinates((int) camera.pos.x, (int) camera.pos.z));
		terrainShader.setUniformFloat("viewDistance", FastConfig.viewDistance);
		terrainShader.setUniformFloat("shadowVisiblity", getShadowVisibility());
		waterNormalTexture.setLinearFiltering(true);
		waterNormalTexture.setMipMapping(true);
		terrainShader.setUniformSamplerCubemap(9, "environmentCubemap", environmentMap);
		terrainShader.setUniformSampler(8, "glowSampler", sunGlowTexture);
		terrainShader.setUniformSampler(7, "skyTextureSunny", skyTextureSunny);
		terrainShader.setUniformSampler(12, "skyTextureRaining", skyTextureRaining);
		terrainShader.setUniformSampler(6, "blockLightmap", lightmapTexture);
		Texture2D lightColors = TexturesHandler.getTexture("./res/textures/environement/lightcolors.png");
		terrainShader.setUniformSampler(11, "lightColors", lightColors);
		terrainShader.setUniformSampler(10, "normalTexture", waterNormalTexture);
		setupShadowColors(terrainShader);
		terrainShader.setUniformFloat("time", sky.time);
		
		//terrainShader.setUniformFloat("isRaining", world.isRaining() ? 1f : 0f);

		terrainShader.setUniformSampler(3, "vegetationColorTexture", getGrassTexture());
		terrainShader.setUniformFloat("mapSize", sizeInChunks * 32);
		terrainShader.setUniformSampler(4, "loadedChunksMapTop", loadedChunksMapTop);
		terrainShader.setUniformSampler(5, "loadedChunksMapBot", loadedChunksMapBot);
		terrainShader.setUniformFloat2("playerCurrentChunk", this.currentChunkX, this.currentChunkY);
		terrainShader.setUniformFloat("ignoreWorldCulling", ignoreWorldCulling ? 1f : 0f);

		if(Keyboard.isKeyDown(Keyboard.KEY_F7))
			glPolygonMode( GL_FRONT_AND_BACK, GL_LINE );
		
		if (FastConfig.debugGBuffers)
			glFinish();
		long t = System.nanoTime();
		if (!InputAbstractor.isKeyDown(org.lwjgl.input.Keyboard.KEY_F9))
			renderedVertices += farTerrainRenderer.draw(renderingContext, terrainShader);

		glPolygonMode( GL_FRONT_AND_BACK, GL_FILL );
		
		if (FastConfig.debugGBuffers)
			glFinish();
		if (FastConfig.debugGBuffers)
			System.out.println("terrain took " + (System.nanoTime() - t) / 1000000.0 + "ms");
	}

	public void renderWorld(boolean isShadowPass, int chunksToRenderLimit)
	{
		long t;
		animationTimer = (float) (((System.currentTimeMillis() % 100000) / 200f) % 100.0);

		int chunksViewDistance = (int) (FastConfig.viewDistance / 32);

		//skyTextureSunny = TexturesHandler.getTexture(world.isRaining() ? "environement/sky_rain.png" : "environement/sky.png");

		Vector3f sunPos = sky.getSunPosition();
		float shadowVisiblity = getShadowVisibility();
		chunksViewDistance = sizeInChunks / 2;

		if (!isShadowPass)
		{
			this.fboShadedBuffer.bind();

			Client.profiler.startSection("blocks");
			this.fboGBuffers.setEnabledRenderTargets();

			renderingContext.setCurrentShader(opaqueBlocksShader);
			//opaqueBlocksShader.use(true);

			//Set materials
			opaqueBlocksShader.setUniformSampler(0, "diffuseTexture", blocksDiffuseTexture);
			opaqueBlocksShader.setUniformSampler(1, "normalTexture", blocksNormalTexture);
			opaqueBlocksShader.setUniformSampler(2, "materialTexture", blocksMaterialTexture);
			opaqueBlocksShader.setUniformSampler(3, "lightColors", lightmapTexture);
			opaqueBlocksShader.setUniformSampler(4, "vegetationColorTexture", getGrassTexture());

			//Set texturing arguments
			blocksDiffuseTexture.setTextureWrapping(false);
			blocksDiffuseTexture.setLinearFiltering(false);
			blocksDiffuseTexture.setMipMapping(false);
			blocksDiffuseTexture.setMipmapLevelsRange(0, 4);

			blocksNormalTexture.setTextureWrapping(false);
			blocksNormalTexture.setLinearFiltering(false);
			blocksNormalTexture.setMipMapping(false);
			blocksNormalTexture.setMipmapLevelsRange(0, 4);

			blocksMaterialTexture.setTextureWrapping(false);
			blocksMaterialTexture.setLinearFiltering(false);
			blocksMaterialTexture.setMipMapping(false);
			blocksMaterialTexture.setMipmapLevelsRange(0, 4);

			//Shadows parameters
			opaqueBlocksShader.setUniformFloat("shadowVisiblity", getShadowVisibility());

			//Camera-related stuff
			opaqueBlocksShader.setUniformFloat2("screenSize", scrW, scrH);

			//World stuff
			opaqueBlocksShader.setUniformFloat("mapSize", sizeInChunks * 32);
			opaqueBlocksShader.setUniformFloat("time", animationTimer);
			
			opaqueBlocksShader.setUniformFloat("overcastFactor", world.getWeather());
			opaqueBlocksShader.setUniformFloat("wetness", getWorldWetness());
			//opaqueBlocksShader.setUniformFloat("wetness", world.isRaining() ? 0.5f : 0.0f);

			camera.setupShader(opaqueBlocksShader);

			// Prepare for gbuffer pass
			glEnable(GL_CULL_FACE);
			glCullFace(GL_BACK);
			glDisable(GL_BLEND);
		}
		else
		{
			renderingContext.setCurrentShader(shadowsPassShader);
			//shadowsPassShader.use(true);
			shadowsPassShader.setUniformFloat("time", animationTimer);
			opaqueBlocksShader.setUniformSampler(0, "albedoTexture", blocksDiffuseTexture);
		}

		// Alpha blending is disabled because certain G-Buffer rendertargets can output a 0 for alpha
		glAlphaFunc(GL_GREATER, 0.0f);
		glDisable(GL_ALPHA_TEST);
		int vertexIn = 0, texCoordIn = 0, colorIn = 0, normalIn = 0;
		// Init vertex attribute locations
		if (!isShadowPass)
		{
			vertexIn = opaqueBlocksShader.getVertexAttributeLocation("vertexIn");
			texCoordIn = opaqueBlocksShader.getVertexAttributeLocation("texCoordIn");
			colorIn = opaqueBlocksShader.getVertexAttributeLocation("colorIn");
			normalIn = opaqueBlocksShader.getVertexAttributeLocation("normalIn");
			//glEnablezVertexAttribArray(colorIn);
			//renderingContext.setupVertexInputs(vertexIn, texCoordIn, colorIn, normalIn);
			renderingContext.setCurrentShader(opaqueBlocksShader);
			renderingContext.enableVertexAttribute(colorIn);
		}
		else
		{
			vertexIn = shadowsPassShader.getVertexAttributeLocation("vertexIn");
			texCoordIn = shadowsPassShader.getVertexAttributeLocation("texCoordIn");
			normalIn = shadowsPassShader.getVertexAttributeLocation("normalIn");
			//renderingContext.setupVertexInputs(vertexIn, texCoordIn, colorIn, normalIn);
			renderingContext.setCurrentShader(shadowsPassShader);
		}
		renderingContext.setIsShadowPass(isShadowPass);

		renderingContext.enableVertexAttribute(normalIn);
		renderingContext.enableVertexAttribute(vertexIn);
		renderingContext.enableVertexAttribute(texCoordIn);

		// Culling vectors
		//viewerPosVector = camera.pos.castToSP();//new Vector3f((float) viewX, (float) viewY, (float) viewZ);
		//float transformedViewH = (float) ((camera.rotationX) / 180 * Math.PI);
		// if(FastConfig.debugGBuffers ) System.out.println(Math.sin(transformedViewV)+"f");
		//viewerCamDirVector = new Vector3f((float) (Math.sin((180 + camera.rotationY) / 180 * Math.PI) * Math.cos(transformedViewH)), (float) (Math.sin(transformedViewH)), (float) (Math.cos((180 + camera.rotationY) / 180 * Math.PI) * Math.cos(transformedViewH)));

		if (FastConfig.debugGBuffers)
			glFinish();
		t = System.nanoTime();

		// renderList.clear();
		glDisable(GL_BLEND);
		int chunksRendered = 0;
		for (ChunkRenderable chunk : renderList)
		{
			
			if(Keyboard.isKeyDown(Keyboard.KEY_F6))
				break;
			ChunkRenderData chunkRenderData = chunk.getChunkRenderData();
			chunksRendered++;
			if (chunksToRenderLimit != -1 && chunksRendered > chunksToRenderLimit)
				break;
			int vboDekalX = 0;
			int vboDekalZ = 0;
			// Adjustements so border chunks show at the correct place.
			vboDekalX = chunk.getChunkX() * 32;
			vboDekalZ = chunk.getChunkZ() * 32;
			if (chunk.getChunkX() - currentChunkX > chunksViewDistance)
				vboDekalX += -sizeInChunks * 32;
			if (chunk.getChunkX() - currentChunkX < -chunksViewDistance)
				vboDekalX += sizeInChunks * 32;
			if (chunk.getChunkZ() - currentChunkZ > chunksViewDistance)
				vboDekalZ += -sizeInChunks * 32;
			if (chunk.getChunkZ() - currentChunkZ < -chunksViewDistance)
				vboDekalZ += sizeInChunks * 32;
			// Update if chunk was modified
			if ((chunk.isMarkedForReRender() || chunk.needsLightningUpdates()) && !chunk.isAirChunk())
				chunksRenderer.requestChunkRender(chunk);
			// Don't bother if it don't render anything
			if (chunkRenderData == null || chunkRenderData.vboSizeFullBlocks + chunkRenderData.vboSizeCustomBlocks == 0)
				continue;
			
			// If we're doing shadows
			if (isShadowPass)
			{
				// TODO : make proper orthogonal view checks etc
				float distanceX = LoopingMathHelper.moduloDistance(currentChunkX, chunk.getChunkX(), sizeInChunks);
				float distanceZ = LoopingMathHelper.moduloDistance(currentChunkZ, chunk.getChunkZ(), sizeInChunks);

				int maxShadowDistance = 4;
				if (shadowMapResolution >= 2048)
					maxShadowDistance = 5;
				if (shadowMapResolution >= 4096)
					maxShadowDistance = 6;

				
				if (distanceX > maxShadowDistance || distanceZ > maxShadowDistance)
					continue;
			}
			else
			{
				// Cone occlusion checking !
				int correctedCX = vboDekalX / 32;
				int correctedCY = chunk.getChunkY();
				int correctedCZ = vboDekalZ / 32;
				//Always show the chunk we're standing in no matter what
				boolean shouldShowChunk = ((int) (camera.pos.x / 32) == chunk.getChunkX()) && ((int) (camera.pos.y / 32) == correctedCY) && ((int) (camera.pos.z / 32) == correctedCZ);
				if (!shouldShowChunk)
					shouldShowChunk = checkChunkOcclusion(chunk, correctedCX, correctedCY, correctedCZ);
				if (!shouldShowChunk)
					continue;
			}
			if (!isShadowPass)
			{
				int camIntPartX = (int) Math.floor(camera.pos.x);
				int camIntPartY = (int) Math.floor(camera.pos.y);
				int camIntPartZ = (int) Math.floor(camera.pos.z);
				double fractPartX = camera.pos.x - Math.floor(camera.pos.x);
				double fractPartY = camera.pos.y - Math.floor(camera.pos.y);
				double fractPartZ = camera.pos.z - Math.floor(camera.pos.z);
				double diffChunkX = vboDekalX + camIntPartX;
				double diffChunkY = chunk.getChunkY() * 32 + camIntPartY;
				double diffChunkZ = vboDekalZ + camIntPartZ;
				opaqueBlocksShader.setUniformFloat3("objectPosition", vboDekalX + camera.pos.x, chunk.getChunkY() * 32f + camera.pos.y, vboDekalZ + camera.pos.z);
				opaqueBlocksShader.setUniformFloat3("objectPosition", diffChunkX + fractPartX, diffChunkY + fractPartY, diffChunkZ + fractPartZ);
			}
			else
				shadowsPassShader.setUniformFloat3("objectPosition", vboDekalX, chunk.getChunkY() * 32f, vboDekalZ);

			//glBindBuffer(GL_ARRAY_BUFFER, chunkRenderData.vboId);

			if (isShadowPass)
				renderedVerticesShadow += chunkRenderData.renderCubeSolidBlocks(renderingContext);
			else
			{
				renderedChunks++;
				renderedVertices += chunkRenderData.renderCubeSolidBlocks(renderingContext);
			}

			if (isShadowPass)
				renderedVerticesShadow += chunkRenderData.renderCustomSolidBlocks(renderingContext);
			else
			{
				renderedChunks++;
				renderedVertices += chunkRenderData.renderCustomSolidBlocks(renderingContext);
			}
		}
		// Done looping chunks, now entities
		if (!isShadowPass)
		{
			if (FastConfig.debugGBuffers)
				glFinish();
			if (FastConfig.debugGBuffers)
				System.out.println("blocks took " + (System.nanoTime() - t) / 1000000.0 + "ms");

			renderingContext.disableVertexAttribute(vertexIn);
			renderingContext.disableVertexAttribute(texCoordIn);
			renderingContext.disableVertexAttribute(colorIn);
			renderingContext.disableVertexAttribute(normalIn);

			// Select shader
			renderingContext.setCurrentShader(entitiesShader);
			//entitiesShader.use(true);

			vertexIn = entitiesShader.getVertexAttributeLocation("vertexIn");
			texCoordIn = entitiesShader.getVertexAttributeLocation("texCoordIn");
			normalIn = entitiesShader.getVertexAttributeLocation("normalIn");

			renderingContext.enableVertexAttribute(vertexIn);
			renderingContext.enableVertexAttribute(texCoordIn);
			renderingContext.enableVertexAttribute(normalIn);

			//renderingContext.setupVertexInputs(vertexIn, texCoordIn, -1, normalIn);

			entitiesShader.setUniformMatrix4f("localTansform", new Matrix4f());
			entitiesShader.setUniformMatrix3f("localTransformNormal", new Matrix3f());

			entitiesShader.setUniformFloat("viewDistance", FastConfig.viewDistance);
			entitiesShader.setUniformFloat("shadowVisiblity", shadowVisiblity);
			entitiesShader.setUniformSampler(4, "lightColors", lightmapTexture);
			lightmapTexture.setTextureWrapping(false);
			entitiesShader.setUniformFloat2("screenSize", scrW, scrH);
			entitiesShader.setUniformFloat3("sunPos", sunPos.x, sunPos.y, sunPos.z);
			entitiesShader.setUniformFloat3("blockColor", 1f, 1f, 1f);
			entitiesShader.setUniformFloat("time", animationTimer);


			entitiesShader.setUniformFloat("overcastFactor", world.getWeather());
			entitiesShader.setUniformFloat("wetness", getWorldWetness());
			//entitiesShader.setUniformFloat("wetness", world.isRaining() ? 0.5f : 0.0f);

			camera.setupShader(entitiesShader);

			//TexturesHandler.bindTexture("res/textures/normal.png");
		}
		else
		{
			shadowsPassShader.setUniformFloat("entity", 1);
		}

		//Client.world.particlesHolder.render(renderingContext);
		glEnable(GL_CULL_FACE);

		
		glDisable(GL_CULL_FACE);
		// Render entities
		Iterator<Entity> ie = world.getAllLoadedEntities();
		Entity entity;
		while (ie.hasNext())
		//for (Entity e : getAllLoadedEntities())
		{
			entity = ie.next();

			//Set the context
			renderingContext.getCurrentShader().setUniformFloat3("objectPosition", entity.getLocation());
			renderingContext.getCurrentShader().setUniformFloat2("worldLight", world.getBlocklightLevel(entity.getLocation()), world.getSunlightLevel(entity.getLocation()));
			
			//Reset animations transformations
			renderingContext.getCurrentShader().setUniformMatrix4f("localTansform", new Matrix4f());
			renderingContext.getCurrentShader().setUniformMatrix3f("localTransformNormal", new Matrix3f());
			
			if (entity != null)
				entity.render(renderingContext);
		}

		renderingContext.disableVertexAttribute(normalIn);
		renderingContext.disableVertexAttribute(vertexIn);
		renderingContext.disableVertexAttribute(texCoordIn);
		
		if (isShadowPass)
			return;

		// Solid blocks done, now render water & lights
		glDisable(GL_CULL_FACE);
		glDisable(GL_ALPHA_TEST);

		// We do water in two passes : one for computing the refracted color and putting it in shaded buffer, and another one
		// to read it back and blend it
		glDepthFunc(GL_LEQUAL);
		for (int pass = 1; pass < 3; pass++)
		{
			liquidBlocksShader = ShadersLibrary.getShaderProgram("blocks_liquid_pass" + (pass));
			renderingContext.setCurrentShader(liquidBlocksShader);
			//liquidBlocksShader.use(true);

			liquidBlocksShader.setUniformFloat("viewDistance", FastConfig.viewDistance);

			liquidBlocksShader.setUniformFloat("yAngle", (float) (camera.rotationY * Math.PI / 180f));
			liquidBlocksShader.setUniformFloat("shadowVisiblity", shadowVisiblity);
			// liquidBlocksShader.setUniformSamplerCube(3, "skybox",
			// TexturesHandler.idCubemap("textures/skybox"));
			liquidBlocksShader.setUniformSampler(1, "normalTextureDeep", TexturesHandler.getTexture("water/deep.png"));
			liquidBlocksShader.setUniformSampler(2, "normalTextureShallow", waterNormalTexture);
			liquidBlocksShader.setUniformSampler(3, "lightColors", lightmapTexture);
			liquidBlocksShader.setUniformSampler(0, "diffuseTexture", blocksDiffuseTexture);
			liquidBlocksShader.setUniformFloat2("screenSize", scrW, scrH);
			liquidBlocksShader.setUniformFloat3("sunPos", sunPos.x, sunPos.y, sunPos.z);
			liquidBlocksShader.setUniformFloat("time", animationTimer);

			camera.setupShader(liquidBlocksShader);

			// Vertex attributes setup
			vertexIn = liquidBlocksShader.getVertexAttributeLocation("vertexIn");
			texCoordIn = liquidBlocksShader.getVertexAttributeLocation("texCoordIn");
			colorIn = liquidBlocksShader.getVertexAttributeLocation("colorIn");
			normalIn = liquidBlocksShader.getVertexAttributeLocation("normalIn");

			renderingContext.setCurrentShader(liquidBlocksShader);
			renderingContext.enableVertexAttribute(vertexIn);
			if (texCoordIn != -1)
				renderingContext.enableVertexAttribute(texCoordIn);
			if (colorIn != -1)
				renderingContext.enableVertexAttribute(colorIn);
			if (normalIn != -1)
				renderingContext.enableVertexAttribute(normalIn);

			// Set rendering context.
			//renderingContext.setupVertexInputs(vertexIn, texCoordIn, colorIn, normalIn);

			Voxel vox = VoxelTypes.get(world.getVoxelData((int) -camera.pos.x, (int) (-camera.pos.y + 0), (int) -camera.pos.z, false));
			liquidBlocksShader.setUniformFloat("underwater", vox.isVoxelLiquid() ? 1 : 0);

			//liquidBlocksShader.setUniformInt("pass", pass-1);
			if (pass == 1)
			{
				fboShadedBuffer.bind();
				fboShadedBuffer.setEnabledRenderTargets(true);
				liquidBlocksShader.setUniformSampler(4, "readbackAlbedoBufferTemp", this.albedoBuffer);
				liquidBlocksShader.setUniformSampler(5, "readbackMetaBufferTemp", this.materialBuffer);
				liquidBlocksShader.setUniformSampler(6, "readbackDepthBufferTemp", this.zBuffer);
				//glEnable(GL_ALPHA_TEST);
				glDisable(GL_ALPHA_TEST);
				glDepthMask(false);
			}
			else if (pass == 2)
			{
				//composite_pass_gbuffers_waterfp.unbind();
				fboGBuffers.bind();
				fboGBuffers.setEnabledRenderTargets();
				//System.out.println("Race (tm)");
				liquidBlocksShader.setUniformSampler(4, "readbackShadedBufferTemp", this.shadedBuffer);
				liquidBlocksShader.setUniformSampler(6, "readbackDepthBufferTemp", this.zBuffer);
				glDepthMask(true);
			}
			for (ChunkRenderable chunk : renderList)
			{
				ChunkRenderData chunkRenderData = chunk.getChunkRenderData();
				if (chunkRenderData == null || chunkRenderData.vboSizeWaterBlocks == 0)
					continue;

				int vboDekalX = chunk.getChunkX() * 32;
				int vboDekalZ = chunk.getChunkZ() * 32;

				if (chunk.getChunkX() - currentChunkX > chunksViewDistance)
					vboDekalX += -sizeInChunks * 32;// (int) (Math.random()*50);//-sizeInChunks;
				if (chunk.getChunkX() - currentChunkX < -chunksViewDistance)
					vboDekalX += sizeInChunks * 32;
				if (chunk.getChunkZ() - currentChunkZ > chunksViewDistance)
					vboDekalZ += -sizeInChunks * 32;// (int) (Math.random()*50);//-sizeInChunks;
				if (chunk.getChunkZ() - currentChunkZ < -chunksViewDistance)
					vboDekalZ += sizeInChunks * 32;

				// Cone occlusion checking !
				int correctedCX = vboDekalX / 32;
				int correctedCY = chunk.getChunkY();
				int correctedCZ = vboDekalZ / 32;

				boolean shouldShowChunk = ((int) (camera.pos.x / 32) == chunk.getChunkX()) && ((int) (camera.pos.y / 32) == correctedCY) && ((int) (camera.pos.z / 32) == correctedCZ);
				if (!shouldShowChunk)
					shouldShowChunk = checkChunkOcclusion(chunk, correctedCX, correctedCY, correctedCZ);
				if (!shouldShowChunk)
					continue;

				liquidBlocksShader.setUniformFloat3("objectPosition", vboDekalX, chunk.getChunkY() * 32, vboDekalZ);

				//glBindBuffer(GL_ARRAY_BUFFER, chunkRenderData.vboId);
				renderedVertices += chunkRenderData.renderWaterBlocks(renderingContext);
			}

			// Disable vertex attributes
			renderingContext.disableVertexAttribute(vertexIn);
			if (texCoordIn != -1)
				renderingContext.disableVertexAttribute(texCoordIn);
			if (colorIn != -1)
				renderingContext.disableVertexAttribute(colorIn);
			if (normalIn != -1)
				renderingContext.disableVertexAttribute(normalIn);
			//renderingContext.doneWithVertexInputs();
		}

		// Particles rendering
		this.world.getParticlesHolder().render(renderingContext);
		
		// Draw world shaded with sunlight and vertex light
		glDepthMask(false);
		renderShadedBlocks();
		glDepthMask(true);

		// Compute SSAO
		if (FastConfig.ssaoQuality > 0)
			this.SSAO(FastConfig.ssaoQuality);
		
		renderLightsDeffered();
		renderTerrain(chunksToRenderLimit != -1);
		
		//Draws chunks lines
		/*if(FastConfig.showDebugInfo)
		{
			OverlayRenderer.glColor4f(4, 0, 0, 1);
			ChunksIterator it = world.getAllLoadedChunks();
			Chunk chunk;
			while(it.hasNext())
			{
				chunk = it.next();
				if(chunk instanceof ChunkRenderable)
				{
					ChunkRenderData crd = ((ChunkRenderable) chunk).getChunkRenderData();
					if(crd != null)
						crd.renderChunkBounds(renderingContext);
				}
			}
				
		}*/
	}

	private void renderLightsDeffered()
	{
		Client.profiler.startSection("lights");

		//We work on the shaded buffer
		this.fboShadedBuffer.bind();
		// Deffered lightning
		// Disable depth read/write
		glDisable(GL_DEPTH_TEST);
		glDepthMask(false);
		renderingContext.setCurrentShader(lightShader);
		//lightShader.use(true);

		//Required info
		lightShader.setUniformSampler(0, "albedoBuffer", this.albedoBuffer);
		lightShader.setUniformSampler(1, "metaBuffer", this.materialBuffer);
		lightShader.setUniformSampler(2, "comp_depth", this.zBuffer);
		lightShader.setUniformSampler(3, "comp_normal", this.normalBuffer);

		//Parameters
		lightShader.setUniformFloat("powFactor", 5f);
		camera.setupShader(lightShader);
		//Blend parameters
		glEnable(GL_BLEND);
		glEnable(GL_ALPHA_TEST);
		glDisable(GL_DEPTH_TEST);
		glBlendFunc(GL_ONE, GL_ONE);

		LightsRenderer.renderPendingLights(renderingContext);
		//Cleanup
		glDepthMask(true);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glDisable(GL_BLEND);
		glEnable(GL_DEPTH_TEST);
		//renderingContext.lights.clear();
	}

	/**
	 * Uses G-Buffers data to spit out shaded solid blocks ( shadows etc )
	 */
	public void renderShadedBlocks()
	{
		if (FastConfig.debugGBuffers)

			if (FastConfig.debugGBuffers)
				glFinish();
		long t = System.nanoTime();

		renderingContext.setCurrentShader(applyShadowsShader);
		//applyShadowsShader.use(true);
		setupShadowColors(applyShadowsShader);
		

		applyShadowsShader.setUniformFloat("overcastFactor", world.getWeather());
		applyShadowsShader.setUniformFloat("wetness", getWorldWetness());
		//applyShadowsShader.setUniformFloat("isRaining", world.isRaining() ? 1f : 0f);
		// Sun position
		// if(FastConfig.debugGBuffers ) glFinish();
		// glFlush();

		//glEnable(GL_BLEND);
		glEnable(GL_ALPHA_TEST);
		glDisable(GL_DEPTH_TEST);
		//glBlendFunc(GL_DST_COLOR, GL_ZERO);
		//glBlendFunc(GL_ONE, GL_ONE);

		Vector3f sunPos = sky.getSunPosition();

		fboShadedBuffer.bind();

		//composite_pass_gbuffers.bind();
		//composite_pass_gbuffers.setEnabledRenderTargets(false, false, true);

		float lightMultiplier = 1.0f;
		// int Bdata = world.getDataAt((int)viewX, (int)viewY, (int)viewZ);
		// int llWherePlayerIs = VoxelFormat.sunlight(Bdata);
		// lightMultiplier += (15-llWherePlayerIs)/15.0f/2f;

		applyShadowsShader.setUniformFloat("brightnessMultiplier", lightMultiplier);

		applyShadowsShader.setUniformSampler(0, "albedoBuffer", albedoBuffer);
		applyShadowsShader.setUniformSampler(1, "depthBuffer", zBuffer);
		applyShadowsShader.setUniformSampler(2, "normalBuffer", normalBuffer);
		applyShadowsShader.setUniformSampler(3, "metaBuffer", materialBuffer);
		applyShadowsShader.setUniformSampler(4, "blockLightmap", lightmapTexture);
		applyShadowsShader.setUniformSampler(5, "shadowMap", shadowMapBuffer);
		applyShadowsShader.setUniformSampler(6, "glowSampler", sunGlowTexture);
		//applyShadowsShader.setUniformSampler(7, "colorSampler", skyTextureSunny);
		
		applyShadowsShader.setUniformSampler(7, "skyTextureSunny", skyTextureSunny);
		applyShadowsShader.setUniformSampler(11, "skyTextureRaining", skyTextureRaining);
		
		Texture2D lightColors = TexturesHandler.getTexture("./res/textures/environement/lightcolors.png");
		applyShadowsShader.setUniformSampler(8, "lightColors", lightColors);
		
		//TODO if SSAO
		applyShadowsShader.setUniformSampler(9, "ssaoBuffer", ssaoBuffer);
		
		applyShadowsShader.setUniformSamplerCubemap(10, "environmentCubemap", environmentMap);

		applyShadowsShader.setUniformFloat("time", sky.time);

		applyShadowsShader.setUniformFloat("shadowMapResolution", shadowMapResolution);
		applyShadowsShader.setUniformFloat("shadowVisiblity", getShadowVisibility());
		applyShadowsShader.setUniformMatrix4f("shadowMatrix", depthMatrix);
		applyShadowsShader.setUniformFloat3("sunPos", sunPos);

		// Matrices for screen-space transformations
		camera.setupShader(applyShadowsShader);
		sky.setupShader(applyShadowsShader);

		ObjectRenderer.drawFSQuad(applyShadowsShader.getVertexAttributeLocation("vertexIn"));
		//drawFSQuad();

		if (FastConfig.debugGBuffers)
			glFinish();

		if (FastConfig.debugGBuffers)
			System.out.println("shadows pass took " + (System.nanoTime() - t) / 1000000.0 + "ms");

		glDisable(GL_BLEND);
		glEnable(GL_DEPTH_TEST);
	}

	//Post-process effects
	
	/**
	 * Renders the final image to the screen
	 */
	public void blitScreen()
	{
		if (FastConfig.debugGBuffers)
			glFinish();
		long t = System.nanoTime();

		
		// We render to the screen.
		FBO.unbind();
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_BLEND);
		glDisable(GL_CULL_FACE);
		renderingContext.setCurrentShader(postProcess);
		//postProcess.use(true);

		postProcess.setUniformSampler(0, "shadedBuffer", this.shadedBuffer);
		postProcess.setUniformSampler(1, "albedoBuffer", this.albedoBuffer);
		postProcess.setUniformSampler(2, "depthBuffer", this.zBuffer);
		postProcess.setUniformSampler(3, "normalBuffer", this.normalBuffer);
		postProcess.setUniformSampler(4, "metaBuffer", this.materialBuffer);
		postProcess.setUniformSampler(5, "shadowMap", this.shadowMapBuffer);
		postProcess.setUniformSampler(6, "bloomBuffer", this.bloomBuffer);
		postProcess.setUniformSampler(7, "ssaoBuffer", this.ssaoBuffer);
		//postProcess.setUniformSampler(8, "debugBuffer", (System.currentTimeMillis() % 1000 < 500 ) ? this.loadedChunksMapD : this.loadedChunksMap);
		postProcess.setUniformSampler(8, "debugBuffer", (System.currentTimeMillis() % 1000 < 500 ) ? this.loadedChunksMapTop : this.loadedChunksMapBot);

		Voxel vox = VoxelTypes.get(world.getDataAt(camera.pos.negate(), false));
		postProcess.setUniformFloat("underwater", vox.isVoxelLiquid() ? 1 : 0);
		postProcess.setUniformFloat("time", animationTimer);

		Vector3f sunPos = sky.getSunPosition();
		postProcess.setUniformFloat3("sunPos", sunPos.x, sunPos.y, sunPos.z);

		camera.setupShader(postProcess);

		postProcess.setUniformFloat("viewWidth", scrW);
		postProcess.setUniformFloat("viewHeight", scrH);

		postProcess.setUniformFloat("apertureModifier", apertureModifier);

		ObjectRenderer.drawFSQuad(postProcess.getVertexAttributeLocation("vertexIn"));
		//drawFSQuad();

		if (FastConfig.debugGBuffers)
			glFinish();

		if (FastConfig.debugGBuffers)
			System.out.println("final blit took " + (System.nanoTime() - t) / 1000000.0 + "ms");

		if (FastConfig.doBloom)
		{
			glBindTexture(GL_TEXTURE_2D, shadedBuffer.getId());
			shadedBuffer.setMipMapping(true);
			int max_mipmap = (int) (Math.ceil(Math.log(Math.max(scrH, scrW)) / Math.log(2))) - 1;
			shadedBuffer.setMipmapLevelsRange(0, max_mipmap);
			try
			{
				//int max_mipmap = (int) (Math.floor(Math.log(Math.max(scrH, scrW)) / Math.log(2)));
				//System.out.println(fBuffer + " " + max_mipmap);
				//shadedMipmapZeroLevelColor.rewind();
				
				//illDownIndex++;
				//if (illDownIndex % 50 == 0)
				if(System.currentTimeMillis() - lastIllCalc > 1000)
				{
					lastIllCalc = System.currentTimeMillis();
					//shadedMipmapZeroLevelColor = BufferUtils.createByteBuffer(12);
					//if(!shadedMipmapZeroLevelColor.hasRemaining())
					//	shadedMipmapZeroLevelColor.rewind();
					//glGetTexImage(GL_TEXTURE_2D, max_mipmap, GL_RGB, GL_FLOAT, shadedMipmapZeroLevelColor);
					//System.out.println("ill");
					illDownBuffers = 1;
					//long nanoC = System.nanoTime();
					illuminationDownloader[(illDownIndex/50 + illDownBuffers - 1 ) % illDownBuffers].copyTexure(shadedBuffer, max_mipmap);
					//long nanoR = System.nanoTime();
					//System.out.println("copy took "+Math.floor((nanoR-nanoC)/10f)/100f+"�s ");
					if(illDownIndex / 10 >= illDownBuffers)
					{
						//ByteBuffer tmpBuffer = illuminationDownloader[illDownIndex/10 % illDownBuffers].readPBO();
						//shadedMipmapZeroLevelColor = BufferUtils.createByteBuffer(tmpBuffer.capacity());
						//shadedMipmapZeroLevelColor.put(tmpBuffer);
						shadedMipmapZeroLevelColor = illuminationDownloader[illDownIndex/50 % illDownBuffers].readPBO();
						//System.out.println("read took "+Math.floor((System.nanoTime()-nanoR)/10f)/100f+"�s ");
						//System.out.println("Read "+shadedMipmapZeroLevelColor.capacity() + "bytes.");
						//System.out.println("glError : "+glGetError());
						illuminationDownloader[illDownIndex/10 % illDownBuffers].doneWithReading();
						//shadedMipmapZeroLevelColor.rewind();
						
						//float luma = shadedMipmapZeroLevelColor.getFloat() * 0.2125f + shadedMipmapZeroLevelColor.getFloat() * 0.7154f + shadedMipmapZeroLevelColor.getFloat() * 0.0721f;
						//System.out.println("read luma : "+luma);
					}
				}
				else if(shadedMipmapZeroLevelColor != null)
					shadedMipmapZeroLevelColor.rewind();
				
				this.shadedBuffer.computeMipmaps();
				
				if(shadedMipmapZeroLevelColor != null)
				{
					if(!shadedMipmapZeroLevelColor.hasRemaining())
						shadedMipmapZeroLevelColor.rewind();
					//System.out.println(shadedMipmapZeroLevelColor);
					float luma = 0.0f;
					for(int i = 0; i < 1; i++)
						luma += shadedMipmapZeroLevelColor.getFloat() * 0.2125f + shadedMipmapZeroLevelColor.getFloat() * 0.7154f + shadedMipmapZeroLevelColor.getFloat() * 0.0721f;
	
					//System.out.println(luma);
					//luma /= 4;
					luma *= apertureModifier;
					luma = (float) Math.pow(luma, 1d / 2.2);
					//System.out.println("luma:"+luma + " aperture:"+ this.apertureModifier);
	
					float targetLuma = 0.55f;
					float lumaMargin = 0.15f;
	
					if (luma < targetLuma - lumaMargin)
					{
						if (apertureModifier < 2.0)
							apertureModifier *= 1.001;
					}
					else if (luma > targetLuma + lumaMargin)
					{
						if (apertureModifier > 1.0)
							apertureModifier *= 0.999;
					}
					else
					{
						float clamped = (float) Math.min(Math.max(1 / apertureModifier, 0.998), 1.002);
						apertureModifier *= clamped;
					}
				}
			}
			catch (Throwable th)
			{
				th.printStackTrace();
			}
		}
		else
			apertureModifier = 1.0f;

		//Draw entities Huds
		Iterator<Entity> ei = world.getAllLoadedEntities();
		Entity e;
		while (ei.hasNext())
		{
			e = ei.next();
			if (e instanceof EntityHUD)
				((EntityHUD) e).drawHUD(camera);
		}
	}

	private void SSAO(int quality)
	{
		fboSSAO.bind();
		fboSSAO.setEnabledRenderTargets();

		renderingContext.setCurrentShader(ssaoShader);
		//ssaoShader.use(true);

		ssaoShader.setUniformSampler(1, "normalTexture", this.normalBuffer);
		ssaoShader.setUniformSampler(0, "depthTexture", this.zBuffer);

		ssaoShader.setUniformFloat("viewWidth", scrW);
		ssaoShader.setUniformFloat("viewHeight", scrH);

		ssaoShader.setUniformInt("kernelsPerFragment", quality * 8);

		camera.setupShader(ssaoShader);

		if (ssao_kernel == null)
		{
			ssao_kernel_size = 16;//applyShadowsShader.getConstantInt("KERNEL_SIZE"); nvm too much work
			ssao_kernel = new Vector3f[ssao_kernel_size];
			for (int i = 0; i < ssao_kernel_size; i++)
			{
				Vector3f vec = new Vector3f((float) Math.random() * 2f - 1f, (float) Math.random() * 2f - 1f, (float) Math.random());
				vec.normalise(vec);
				float scale = ((float) i) / ssao_kernel_size;
				scale = Math2.mix(0.1f, 1.0f, scale * scale);
				vec.scale(scale);
				ssao_kernel[i] = vec;
				if (FastConfig.debugGBuffers)
					System.out.println("lerp " + scale + "x " + vec.x);
			}
		}

		for (int i = 0; i < ssao_kernel_size; i++)
		{
			ssaoShader.setUniformFloat3("ssaoKernel[" + i + "]", ssao_kernel[i].x, ssao_kernel[i].y, ssao_kernel[i].z);
		}

		ObjectRenderer.drawFSQuad(ssaoShader.getVertexAttributeLocation("vertexIn"));

		// Blur the thing

		// Vertical pass
		fboBlur.bind();
		renderingContext.setCurrentShader(blurV);
		//blurV.use(true);
		blurV.setUniformFloat2("screenSize", scrW, scrH );
		blurV.setUniformFloat("lookupScale", 2);
		blurV.setUniformSampler(0, "inputTexture", this.ssaoBuffer);
		ObjectRenderer.drawFSQuad(blurV.getVertexAttributeLocation("vertexIn"));
		//drawFSQuad();

		// Horizontal pass
		this.fboSSAO.bind();
		renderingContext.setCurrentShader(blurH);
		//blurH.use(true);
		blurH.setUniformFloat2("screenSize", scrW , scrH);
		blurH.setUniformSampler(0, "inputTexture", blurIntermediateBuffer);
		ObjectRenderer.drawFSQuad(blurH.getVertexAttributeLocation("vertexIn"));
		//drawFSQuad();

	}
	
	private void renderBloom()
	{
		this.shadedBuffer.setLinearFiltering(true);
		this.bloomBuffer.setLinearFiltering(true);
		this.blurIntermediateBuffer.setLinearFiltering(true);

		renderingContext.setCurrentShader(bloomShader);
		//bloomShader.use(true);
		bloomShader.setUniformSampler(0, "shadedBuffer", this.shadedBuffer);
		bloomShader.setUniformFloat("apertureModifier", apertureModifier);
		bloomShader.setUniformFloat2("screenSize", scrW / 2f, scrH / 2f);

		int max_mipmap = (int) (Math.ceil(Math.log(Math.max(scrH, scrW)) / Math.log(2)));
		bloomShader.setUniformFloat("max_mipmap", max_mipmap);

		this.fboBloom.bind();
		this.fboBloom.setEnabledRenderTargets();
		glViewport(0, 0, scrW / 2, scrH / 2);
		ObjectRenderer.drawFSQuad(bloomShader.getVertexAttributeLocation("vertexIn"));

		// Blur bloom

		// Vertical pass
		fboBlur.bind();
		renderingContext.setCurrentShader(blurV);
		//blurV.use(true);
		blurV.setUniformFloat2("screenSize", scrW / 2f, scrH / 2f);
		blurV.setUniformFloat("lookupScale", 1);
		blurV.setUniformSampler(0, "inputTexture", this.bloomBuffer);
		//drawFSQuad();
		ObjectRenderer.drawFSQuad(blurV.getVertexAttributeLocation("vertexIn"));

		// Horizontal pass
		this.fboBloom.bind();
		renderingContext.setCurrentShader(blurH);
		//blurH.use(true);
		blurH.setUniformFloat2("screenSize", scrW / 2f, scrH / 2f);
		blurH.setUniformSampler(0, "inputTexture", blurIntermediateBuffer);
		//drawFSQuad();
		ObjectRenderer.drawFSQuad(blurH.getVertexAttributeLocation("vertexIn"));
		
		fboBlur.bind();
		renderingContext.setCurrentShader(blurV);
		//blurV.use(true);
		blurV.setUniformFloat2("screenSize", scrW / 4f, scrH / 4f);
		blurV.setUniformFloat("lookupScale", 1);
		blurV.setUniformSampler(0, "inputTexture", this.bloomBuffer);
		//drawFSQuad();
		ObjectRenderer.drawFSQuad(blurV.getVertexAttributeLocation("vertexIn"));

		// Horizontal pass
		this.fboBloom.bind();
		renderingContext.setCurrentShader(blurH);
		//blurH.use(true);
		blurH.setUniformFloat2("screenSize", scrW / 4f, scrH / 4f);
		blurH.setUniformSampler(0, "inputTexture", blurIntermediateBuffer);
		//drawFSQuad();
		ObjectRenderer.drawFSQuad(blurH.getVertexAttributeLocation("vertexIn"));

		// Done blooming
		glViewport(0, 0, scrW, scrH);
	}

	//Capture functions
	
	/**
	 * Renders the whole scene into either a cubemap or saved on disk
	 * 
	 * @param resolution
	 * @param cubemap
	 *            the cubemap to render to, or null to save to disk
	 */
	public void screenCubeMap(int resolution, Cubemap cubemap)
	{
		lastEnvmapRender = System.currentTimeMillis();
		// Save state
		boolean oldBloom = FastConfig.doBloom;
		float oldViewDistance = FastConfig.viewDistance;
		//System.out.println(this.view);
		//FastConfig.viewDistance = 0;
		FastConfig.doBloom = false;
		int oldW = scrW;
		int oldH = scrH;
		float camX = camera.rotationX;
		float camY = camera.rotationY;
		float camZ = camera.rotationZ;
		float fov = camera.fov;
		camera.fov = 45;
		// Setup cubemap resolution
		this.setupRenderSize(resolution, resolution);
		String[] names = { "front", "back", "top", "bottom", "right", "left" };
		// Minecraft names
		// String[] names = {"right", "left", "top", "bottom", "front", "back"};
		// String[]{"panorama_0","panorama_2","panorama_4","panorama_5","panorama_1","panorama_3"};
		// old 0 0 0 180 -90 0 90 0 0 90 0 270
		//
		// names = new
		// String[]{"panorama_1","panorama_3","panorama_4","panorama_5","panorama_0","panorama_2"};
		// PX NX PY NY PZ NZ

		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("YYYY.MM.dd HH.mm.ss");
		String time = sdf.format(cal.getTime());

		// byte[] buf = new byte[1024 * 1024 * 4];

		for (int z = 0; z < 6; z++)
		{
			// Camera location
			switch (z)
			{
			case 0:
				camera.rotationX = 0.0f;
				camera.rotationY = 0f;
				break;
			case 1:
				camera.rotationX = 0;
				camera.rotationY = 180;
				break;
			case 2:
				camera.rotationX = -90;
				camera.rotationY = 0;
				break;
			case 3:
				camera.rotationX = 90;
				camera.rotationY = 0;
				break;
			case 4:
				camera.rotationX = 0;
				camera.rotationY = 90;
				break;
			case 5:
				camera.rotationX = 0;
				camera.rotationY = 270;
				break;
			}
			//this.viewRotH = camera.rotationX;
			//this.viewRotV = camera.view_roty;

			//float transformedViewH = (float) ((camera.rotationX) / 180 * Math.PI);
			//viewerCamDirVector = new Vector3f((float) (Math.sin((180 + camera.rotationY) / 180 * Math.PI) * Math.cos(transformedViewH)), (float) (Math.sin(transformedViewH)), (float) (Math.cos((180 + camera.rotationY) / 180 * Math.PI) * Math.cos(transformedViewH)));

			this.fboShadedBuffer.bind();
			//glDisable(GL_DEPTH_TEST);
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			// Scene rendering
			this.renderWorldAtCameraInternal(camera, cubemap == null ? -1 : 0);

			// GL access
			glBindTexture(GL_TEXTURE_2D, shadedBuffer.getId());

			if (cubemap != null)
			{
				glBindTexture(GL_TEXTURE_CUBE_MAP, cubemap.getID());

				//String[] names = { "front", "back", "top", "bottom", "right", "left" };
				//String[] names = { "right", "left", "top", "bottom", "front", "back" };
				int t[] = new int[] { 4, 5, 3, 2, 0, 1 };
				int f = t[z];
				
				//System.out.println("face"+f);
				
				//ByteBuffer bbuf = ByteBuffer.allocateDirect(resolution * resolution * 4).order(ByteOrder.nativeOrder());
				
				//glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, bbuf);
				
				glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + f, 0, GL_RGBA, resolution, resolution, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer)null);
				
				//glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + f, 0, GL_RGBA, resolution, resolution, 0, GL_RGBA, GL_UNSIGNED_BYTE, bbuf);
				glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
				glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
				// Anti seam
				glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
				glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
				
				renderingContext.setCurrentShader(ShadersLibrary.getShaderProgram("blit"));
				
				this.environmentMapFBO.bind();
				//glDisable(GL_DEPTH_TEST);
				//glDisable(GL_ALPHA_TEST);
				//glDisable(GL_CULL_FACE);
				this.environmentMapFBO.setColorAttachement(0, cubemap.getFace(f));
				
				renderingContext.getCurrentShader().setUniformSampler(0, "diffuseTexture", shadedBuffer);
				renderingContext.getCurrentShader().setUniformFloat2("screenSize", resolution, resolution);
				
				renderingContext.enableVertexAttribute(renderingContext.getCurrentShader().getVertexAttributeLocation("texCoord"));
				ObjectRenderer.drawFSQuad(renderingContext.getCurrentShader().getVertexAttributeLocation("vertexIn"));
				//glFinish();
			}
			else
			{
				// File access
				File image = new File(GameDirectory.getGameFolderPath() + "/skyboxscreens/" + time + "/" + names[z] + ".png");
				image.mkdirs();

				// Saving
				// bbuf.get(buf);
				ByteBuffer bbuf = ByteBuffer.allocateDirect(resolution * resolution * 4).order(ByteOrder.nativeOrder());
				
				glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, bbuf);
				
				BufferedImage pixels = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_RGB);
				for (int x = 0; x < resolution; x++)
					for (int y = 0; y < resolution; y++)
					{
						int i = 4 * (x + resolution * y);
						int r = (int) (Math.pow((bbuf.get(i) & 0xFF) / 255d, 1d / 2.2d) * 255d);
						int g = (int) (Math.pow((bbuf.get(i + 1) & 0xFF) / 255d, 1d / 2.2d) * 255d);
						int b = (int) (Math.pow((bbuf.get(i + 2) & 0xFF) / 255d, 1d / 2.2d) * 255d);
						pixels.setRGB(x, resolution - 1 - y, (0xFF << 24) | (r << 16) | (g << 8) | b);
					}
				try
				{
					ImageIO.write(pixels, "PNG", image);
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}

		}
		// Revert correct data

		FastConfig.viewDistance = oldViewDistance;
		FastConfig.doBloom = oldBloom;
		camera.rotationX = camX;
		camera.rotationY = camY;
		camera.rotationZ = camZ;
		camera.fov = fov;
		camera.justSetup(oldW, oldH);
		this.setupRenderSize(oldW, oldH);
	}

	/**
	 * Takes a screenshot and saves it.
	 * 
	 * @return
	 */
	public String screenShot()
	{
		ByteBuffer bbuf = ByteBuffer.allocateDirect(scrW * scrH * 4).order(ByteOrder.nativeOrder());

		glReadBuffer(GL_FRONT);
		glReadPixels(0, 0, scrW, scrH, GL_RGBA, GL_UNSIGNED_BYTE, bbuf);

		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("YYYY.MM.dd HH.mm.ss");
		String time = sdf.format(cal.getTime());

		File image = new File(GameDirectory.getGameFolderPath() + "/screenshots/" + time + ".png");

		image.mkdirs();

		BufferedImage pixels = new BufferedImage(scrW, scrH, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < scrW; x++)
			for (int y = 0; y < scrH; y++)
			{
				int i = 4 * (x + scrW * y);
				int r = bbuf.get(i) & 0xFF;
				int g = bbuf.get(i + 1) & 0xFF;
				int b = bbuf.get(i + 2) & 0xFF;
				pixels.setRGB(x, scrH - 1 - y, (0xFF << 24) | (r << 16) | (g << 8) | b);
			}
		try
		{
			ImageIO.write(pixels, "PNG", image);
			return "#FFFF00Saved screenshot as " + time + ".png";
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return "#FF0000Failed to take screenshot ! (" + e.toString() + ")";
		}
	}

	//Visual properties functions
	public Texture2D getGrassTexture()
	{
		Texture2D vegetationTexture = null;
		if(world.getFolderPath() != null)
			vegetationTexture = TexturesHandler.getTexture(world.getFolderPath() + "/grassColor.png");
		if (vegetationTexture == null || vegetationTexture.getId() == -1)
			vegetationTexture = TexturesHandler.getTexture("./res/textures/environement/grassColor.png");
		vegetationTexture.setMipMapping(true);
		vegetationTexture.setLinearFiltering(true);
		return vegetationTexture;
	}
	
	private void setupShadowColors(ShaderProgram shader)
	{
		float sunLightFactor = Math.min(Math.max(0.0f, world.getWeather() - 0.0f) / 1.0f, 1.0f);
		
		shader.setUniformFloat("shadowStrength", 1.0f);
		float x = 1.5f;
		shader.setUniformFloat3("sunColor", Math2.mix( new Vector3f(x * 255 / 255f, x * 255 / 255f, x * 255 / 255f),  new Vector3f(0.5f), sunLightFactor));
		shader.setUniformFloat3("shadowColor", new Vector3f(0.50f, 0.50f, 0.50f));
		
		
		/*if (world.isRaining())
		{
			shader.setUniformFloat("shadowStrength", 1.0f);
			shader.setUniformFloat3("sunColor", new Vector3f(1.0f, 1.0f, 1.0f));
			shader.setUniformFloat3("shadowColor", new Vector3f(0.50f, 0.50f, 0.50f));
		}
		else
		{
			shader.setUniformFloat("shadowStrength", 1.0f);
			float x = 1.5f;
			shader.setUniformFloat3("sunColor", x * 255 / 255f, x * 255 / 255f, x * 255 / 255f);
			
			Vector3f shadowColor = new Vector3f(0, 0, 0);
			
			float b = 0.50f;
			shadowColor.add(new Vector3f(b, b, b));
			shader.setUniformFloat3("shadowColor", shadowColor);
		}*/
	}

	private float getShadowVisibility()
	{
		float worldTime = (world.worldTime % 10000 + 10000) % 10000;
		int start = 2500;
		int end = 7500;
		if (worldTime < start || worldTime > end + 500)
			return 0;
		else if (worldTime < start + 500)
			return (worldTime - start) / 500f;
		else if (worldTime > end)
			return 1 - (worldTime - end) / 500f;
		else
			return 1;
	}
	
	//Math helper functions
	
	private boolean checkChunkOcclusion(Chunk chunk, int correctedCX, int correctedCY, int correctedCZ)
	{
		Vector3f center = new Vector3f(correctedCX * 32 + 16, correctedCY * 32 + 15, correctedCZ * 32 + 16);
		return camera.isBoxInFrustrum(center, new Vector3f(32, 32, 32));
	}
	
	private float getWorldWetness()
	{
		float wetFactor = Math.min(Math.max(0.0f, world.getWeather() - 0.5f) / 0.3f, 1.0f);
		return wetFactor;
	}

	public void destroy()
	{
		sky.destroy();
		chunksRenderer.killThread();
		farTerrainRenderer.destroy();
	}
}
