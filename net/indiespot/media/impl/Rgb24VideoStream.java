/*
 * Copyright (c) 2012, Riven
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Riven nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.indiespot.media.impl;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import net.indiespot.media.VideoStream;

import craterstudio.util.Pool;
import craterstudio.util.concur.SimpleBlockingQueue;

public class Rgb24VideoStream extends VideoStream {
	private final DataInputStream videoStream;

	public Rgb24VideoStream(InputStream rgbStream, VideoMetadata metadata) {
		super(metadata);

		this.videoStream = new DataInputStream(rgbStream);
	}

	@Override
	protected void runReadLoop(Pool<ByteBuffer> pool, SimpleBlockingQueue<ByteBuffer> frameQueue) {

		/*
		 * Using DataInputStream(ffmpeg.stdin, 64*1024) instead of
		 * DataInputStream(BufferedInputStream(ffmpeg.stdin, 64*1024)) as it is
		 * about 50x slower... ~8ms vs. ~330ms per frame. wtf?!
		 */

		ByteBuffer rgbBuffer = pool.aquire();

		byte[] tmp1 = new byte[64 * 1024];
		byte[] tmp2 = new byte[rgbBuffer.capacity() % tmp1.length];

		int cnt1 = rgbBuffer.capacity() / tmp1.length;
		int cnt2 = tmp2.length > 0 ? 1 : 0;

		while (true) {

			long t0 = System.nanoTime();
			try {
				/*
				 * Wouldn't that be easy...
				 * 
				 * videoStream.readFully(rgbArray);
				 */

				for (int i = 0; i < cnt1; i++) {
					videoStream.readFully(tmp1);
					rgbBuffer.put(tmp1);
				}
				for (int i = 0; i < cnt2; i++) {
					videoStream.readFully(tmp2);
					rgbBuffer.put(tmp2);
				}

				if (rgbBuffer.hasRemaining()) {
					throw new IllegalStateException();
				}
			} catch (EOFException exc) {
				break;
			} catch (IOException exc) {
				throw new IllegalStateException("terminated video provider", exc);
			}
			long t1 = System.nanoTime();
			this.frameReadingTime = t1 - t0;

			rgbBuffer.flip();
			frameQueue.put(rgbBuffer);
			rgbBuffer = pool.aquire();
		}

		pool.release(rgbBuffer);
		frameQueue.put(null);
		System.out.println("Byte Stream: EOF");
	}

	@Override
	public void close() throws IOException {
		videoStream.close();
	}
}