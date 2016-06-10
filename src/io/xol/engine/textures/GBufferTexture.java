package io.xol.engine.textures;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL14.*;

import java.nio.ByteBuffer;

import io.xol.chunkstories.client.FastConfig;

import static org.lwjgl.opengl.GL30.*;

//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class GBufferTexture extends Texture implements FBOAttachement
{
	public GBufferTexture(TextureType type, int w, int h)
	{
		super(type);
		glId = glGenTextures();
		resize(w, h);
	}

	public void resize(int w, int h)
	{
		glBindTexture(GL_TEXTURE_2D, glId);
		
		if(this.width == w && this.height == h && !(type == TextureType.RGB_HDR))
			return;
		
		this.width = w;
		this.height = h;
		if (type == TextureType.RGBA_8BPP)
		{
			//ChunkStoriesLogger.getInstance().log("Created " + w + "by" + h + " RGBA texture", ChunkStoriesLogger.LogType.RENDERING, ChunkStoriesLogger.LogLevel.INFO);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
		}
		else if (type == TextureType.RGB_HDR)
		{
			//ChunkStoriesLogger.getInstance().log("Created " + w + "by" + h + " tamer texture", ChunkStoriesLogger.LogType.RENDERING, ChunkStoriesLogger.LogLevel.INFO);
			// Optimization for OpenGL 3 cards
			if(!FastConfig.doBloom)
			//	glTexImage2D(GL_TEXTURE_2D, 0, GL_R9_G8_B9, w, h, 0, GL_RGB, GL_UNSIGNED_BYTE, (ByteBuffer) null);
				glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB, GL_UNSIGNED_BYTE, (ByteBuffer) null);
			else
			{
				if(FastConfig.openGL3Capable)
					glTexImage2D(GL_TEXTURE_2D, 0, GL_R11F_G11F_B10F, w, h, 0, GL_RGB, GL_FLOAT, (ByteBuffer) null);
				else
					glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
			}
			// GL_RGBA16F_ARB for GL3
		}
		else if (type == TextureType.DEPTH_SHADOWMAP)
		{
			//ChunkStoriesLogger.getInstance().log("Created " + w + "by" + h + " D16 texture", ChunkStoriesLogger.LogType.RENDERING, ChunkStoriesLogger.LogLevel.INFO);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, w, h, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
		}
		else if (type == TextureType.DEPTH_RENDERBUFFER)
		{
			//ChunkStoriesLogger.getInstance().log("Created " + w + "by" + h + " D32 texture", ChunkStoriesLogger.LogType.RENDERING, ChunkStoriesLogger.LogLevel.INFO);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32, w, h, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
		}
		if (type != TextureType.DEPTH_SHADOWMAP)
		{
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		}
		else
		{
			// For proper hardware linear filtering of textures :o
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_R_TO_TEXTURE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
		}
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	}

	@Override
	public void attachDepth()
	{
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, this.getID(), 0);
	}

	@Override
	public void attachColor(int colorAttachement)
	{
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + colorAttachement, GL_TEXTURE_2D, this.getID(), 0);
	}
}
