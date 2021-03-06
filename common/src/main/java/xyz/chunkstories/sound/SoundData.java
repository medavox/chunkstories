//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.sound;

public abstract class SoundData {
	public abstract long getLengthMs();

	public abstract boolean loadedOk();

	public abstract int getBuffer();

	public abstract void destroy();

	public abstract String getName();
}
