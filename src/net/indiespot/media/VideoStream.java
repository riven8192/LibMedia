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

package net.indiespot.media;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import craterstudio.util.concur.SimpleBlockingQueue;

import net.indiespot.media.impl.VideoMetadata;

public class VideoStream implements Closeable {
	final DataInputStream videoStream;
	private final VideoMetadata metadata;
	private final byte[] tmp1, tmp2;
	private final SimpleBlockingQueue<ByteBuffer> emptyQueue, filledQueue;

	public VideoStream(InputStream rgbStream, VideoMetadata metadata) {
		this.videoStream = new DataInputStream(rgbStream);
		this.metadata = metadata;

		this.tmp1 = new byte[64 * 1024];
		this.tmp2 = new byte[(metadata.width * metadata.height * 3) % tmp1.length];

		this.emptyQueue = new SimpleBlockingQueue<>();
		this.filledQueue = new SimpleBlockingQueue<>();

		for (int i = 0; i < 3; i++) {
			this.emptyQueue.put(ByteBuffer.allocateDirect(metadata.width * metadata.height * 3));
		}

		new Thread(new Runnable() {
			@Override
			public void run() {
				while (!closed) {
					if (!pumpFramesInto()) {
						break;
					}
				}

				filledQueue.put(EOF);
			}
		}).start();
	}

	public ByteBuffer pollFrameData() {
		return filledQueue.poll();
	}

	public void freeFrameData(ByteBuffer bb) {
		if (bb == null) {
			throw new IllegalArgumentException();
		}
		bb.clear();
		emptyQueue.put(bb);
	}

	public static final ByteBuffer EOF = ByteBuffer.allocateDirect(1);

	private boolean pumpFramesInto() {
		ByteBuffer rgbBuffer = emptyQueue.take();
		if (metadata.width * metadata.height * 3 != rgbBuffer.remaining()) {
			throw new IllegalArgumentException();
		}

		/*
		 * Using DataInputStream(ffmpeg.stdin).readFully(byte[64*1024]) instead of
		 * DataInputStream(BufferedInputStream(ffmpeg.stdin, 64*1024)) as it is
		 * about 50x slower... ~8ms vs. ~330ms per frame. wtf?!
		 */

		int cnt1 = rgbBuffer.remaining() / tmp1.length;
		int cnt2 = tmp2.length > 0 ? 1 : 0;

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
			rgbBuffer.flip();
			
			filledQueue.put(rgbBuffer);

			return true;
		} catch (IOException exc) {
			return false;
		}
	}

	volatile boolean closed;

	@Override
	public void close() throws IOException {
		this.closed = true;
		this.videoStream.close();
	}
}