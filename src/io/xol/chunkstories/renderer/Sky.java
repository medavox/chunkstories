package io.xol.chunkstories.renderer;

import io.xol.engine.base.ObjectRenderer;
import io.xol.engine.base.XolioWindow;
import io.xol.engine.model.RenderingContext;
import io.xol.engine.shaders.ShaderProgram;
import io.xol.engine.shaders.ShadersLibrary;
import io.xol.engine.textures.Texture;
import io.xol.engine.textures.TexturesHandler;
import io.xol.chunkstories.api.world.World;
import io.xol.chunkstories.client.FastConfig;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

import io.xol.engine.math.lalgb.Vector3f;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class Sky
{
	public float time = 0;
	float distance = 1500;
	float height = -500;

	World world;
	WorldRenderer worldRenderer;
	
	public Sky(World world, WorldRenderer worldRenderer)
	{
		this.world = world;
		this.worldRenderer = worldRenderer;
	}

	public Vector3f getSunPosition()
	{
		float sunloc = (float) (time * Math.PI * 2 / 1.6 - 0.5);
		float sunangle = 0;
		float sundistance = 1000;
		return new Vector3f((float) (400 + sundistance * Math.sin(rad(sunangle)) * Math.cos(sunloc)), (float) (height + sundistance * Math.sin(sunloc)), (float) (sundistance * Math.cos(rad(sunangle)) * Math.cos(sunloc))).normalise();
	}
	
	public void render(RenderingContext renderingContext)
	{
		//setupFog();

		glDisable(GL_ALPHA_TEST);
		glDisable(GL_DEPTH_TEST);
		glDisable(GL_CULL_FACE);
		glDisable(GL_BLEND);
		glDepthMask(false);

		Vector3f sunPosVector = getSunPosition();
		double[] sunpos = { sunPosVector.x, sunPosVector.y, sunPosVector.z };

		ShaderProgram skyShader = ShadersLibrary.getShaderProgram("sky");
		renderingContext.setCurrentShader(skyShader);
		
		// TexturesHandler.bindTexture("res/textures/environement/sky.png");
		XolioWindow.getInstance().getRenderingContext().setCurrentShader(skyShader);
		//skyShader.use(true);
		skyShader.setUniformSampler(9, "cloudsNoise", TexturesHandler.getTexture("environement/cloudsStatic.png"));
		Texture glowTexture = TexturesHandler.getTexture("environement/glow.png");
		skyShader.setUniformSampler(1, "glowSampler", glowTexture);
		glowTexture.setLinearFiltering(true);
		glowTexture.setTextureWrapping(false);
		glowTexture.setTextureWrapping(false);

		Texture skyTexture = TexturesHandler.getTexture(world.isRaining() ? "environement/sky_rain.png" : "environement/sky.png");
		skyShader.setUniformSampler(0, "colorSampler", skyTexture);
		skyShader.setUniformFloat("isRaining", world.isRaining() ? 1f : 0f);
		skyTexture.setLinearFiltering(true);
		skyTexture.setMipMapping(false);
		skyTexture.setTextureWrapping(false);

		//skyShader.setUniformSamplerCube(2, "skybox", TexturesHandler.idCubemap("res/textures/skybox"));
		skyShader.setUniformFloat3("camPos", renderingContext.getCamera().pos.castToSP());
		skyShader.setUniformFloat3("sunPos", (float) sunpos[0], (float) sunpos[1], (float) sunpos[2]);
		skyShader.setUniformFloat("time", time);
		renderingContext.getCamera().setupShader(skyShader);

		ObjectRenderer.drawFSQuad(skyShader.getVertexAttributeLocation("vertexIn"));

		//skyShader.use(false);
		
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glPointSize(1f);

		ShaderProgram starsShader = skyShader = ShadersLibrary.getShaderProgram("stars");
		
		renderingContext.setCurrentShader(starsShader);
		//starsShader.use(true);
		starsShader.setUniformFloat3("sunPos", (float) sunpos[0], (float) sunpos[1], (float) sunpos[2]);
		starsShader.setUniformFloat3("color", 1f, 1f, 1f);
		renderingContext.getCamera().setupShader(starsShader);
		int NB_STARS = 500;
		if (stars == null)
		{
			stars = BufferUtils.createFloatBuffer(NB_STARS * 3);
			for (int i = 0; i < NB_STARS; i++)
			{
				Vector3f star = new Vector3f((float) Math.random() * 2f - 1f, (float) Math.random(), (float) Math.random() * 2f - 1f);
				star.normalise();
				star.scale(100f);
				stars.put(new float[] { star.x, star.y, star.z });
			}
		}
		stars.rewind();
		int vertexIn = starsShader.getVertexAttributeLocation("vertexIn");
		if (vertexIn >= 0)
		{
			renderingContext.enableVertexAttribute(vertexIn);
			glBindBuffer(GL_ARRAY_BUFFER, 0);
			glVertexAttribPointer(vertexIn, 3, false, 0, stars);
			glDrawArrays(GL_POINTS, 0, NB_STARS);
			renderingContext.disableVertexAttribute(vertexIn);
			//starsShader.use(false);
		}
		glDisable(GL_BLEND);
		glDepthMask(true);
		glEnable(GL_DEPTH_TEST);
		//stars = null;

	}

	private FloatBuffer stars = null;

	private double rad(float h)
	{
		return h / 180 * Math.PI;
	}

	public void setupShader(ShaderProgram shader)
	{
		shader.setUniformFloat("fogStartDistance", world.isRaining() ? 32 : FastConfig.viewDistance);
		shader.setUniformFloat("fogEndDistance", world.isRaining() ? 384 : 1024);
	}
}