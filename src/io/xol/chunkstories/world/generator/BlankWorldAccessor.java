package io.xol.chunkstories.world.generator;

import io.xol.chunkstories.api.world.WorldGenerator;
import io.xol.chunkstories.world.CubicChunk;

//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class BlankWorldAccessor extends WorldGenerator
{

	@Override
	public CubicChunk generateChunk(int cx, int cy, int cz)
	{
		CubicChunk c = new CubicChunk(world, cx, cy, cz);
		// c.dataPointer = world.chunksData.malloc();
		/*
		 * for(int a = 0; a < 32; a++) for(int b = 0; b < 32; b++) { for(int i =
		 * 0; i < 32; i++) { if(Math.random() > 0.999) c.setDataAt(a, i, b, 1);
		 * } }
		 */
		// System.out.println("Loading chunk "+cx+":"+cy+":"+cz+" set dp"+c.dataPointer);
		return c;
	}

	@Override
	public int getDataAt(int x, int y)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getHeightAt(int x, int z)
	{
		// TODO Auto-generated method stub
		return 0;
	}
}
