package net.indiespot.media.impl;

import java.nio.ByteBuffer;

import craterstudio.util.Pool;
import craterstudio.util.PoolHandler;

public class VideoMetadata {
	public final int width, height;
	public final float framerate;
	public final Pool<ByteBuffer> pool;

	public VideoMetadata(int width, int height, float framerate) {
		this.width = width;
		this.height = height;
		this.framerate = framerate;

		this.pool = new Pool<>(new PoolHandler<ByteBuffer>() {
			@Override
			public ByteBuffer create() {
				// heap buffer!
				return ByteBuffer.allocateDirect(VideoMetadata.this.width * VideoMetadata.this.height * 3);
			}

			public void clean(ByteBuffer buffer) {
				buffer.clear();
			}
		}).asThreadSafePool();
	}

	@Override
	public String toString() {
		return "VideoMetadata[" + width + "x" + height + " @ " + framerate + "fps]";
	}
}