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

public class AudioStream implements Closeable {
	public int audioFormat;
	public int numChannels;
	public int sampleRate;
	public int byteRate;
	private int blockAlign;
	public int bytesPerSample;

	public final DataInputStream input;
	public int sampleCount;

	public AudioStream() throws IOException {
		this.input = new DataInputStream(new InputStream() {
			@Override
			public int read() throws IOException {
				return 0;
			}

			@Override
			public int available() throws IOException {
				return 1;
			}
		});

		this.numChannels = 2;
		this.sampleRate = 44100;
		this.blockAlign = 4;
		this.bytesPerSample = 2;
		this.byteRate = sampleRate * numChannels * bytesPerSample;
	}

	public AudioStream(InputStream input) throws IOException {
		this.input = new DataInputStream(input);

		this.initWAV();
	}

	private void initWAV() throws IOException {
		while (true) {
			String chunkName = readString(this.input, 4);
			int chunkSize = swap32(this.input.readInt());

			// System.out.println("WAV chunk: [" + chunkName + "] size=" +
			// chunkSize);

			if (chunkName.equals("RIFF")) {
				if (!"WAVE".equals(readString(this.input, 4))) {
					throw new IllegalStateException();
				}
			} else if (chunkName.equals("fmt ")) {
				this.audioFormat = swap16(this.input.readUnsignedShort());
				this.numChannels = swap16(this.input.readUnsignedShort());
				this.sampleRate = swap32(this.input.readInt());
				this.byteRate = swap32(this.input.readInt());
				this.blockAlign = swap16(this.input.readUnsignedShort());
				this.bytesPerSample = swap16(this.input.readUnsignedShort()) / 8;

				for (int off = 16; off < chunkSize; off++) {
					this.input.readByte();
				}
			} else if (chunkName.equals("data")) {
				this.sampleCount = chunkSize / this.bytesPerSample / this.numChannels;
				break;
			} else {
				for (int off = 0; off < chunkSize; off++) {
					this.input.readByte();
				}
			}
		}

		if (this.audioFormat != 1) {
			if (input instanceof Closeable)
				((Closeable) input).close();
			throw new IllegalStateException("can only parse uncompressed wav files: " + audioFormat);
		}
	}

	public int bytesPerSample() {
		return this.bytesPerSample;
	}

	public int numChannels() {
		return this.numChannels;
	}

	public int sampleRate() {
		return this.sampleRate;
	}

	public int sampleCount() {
		return this.sampleCount;
	}

	public void readSamples(byte[] buf, int off, int len) throws IOException {
		if (len % bytesPerSample != 0) {
			throw new IllegalStateException();
		}
		((DataInputStream) input).readFully(buf, off, len);
	}

	public void close() throws IOException {
		input.close();
	}

	//

	private static String readString(DataInputStream raf, int len) throws IOException {
		char[] cs = new char[len];
		for (int i = 0; i < len; i++)
			cs[i] = (char) (raf.readByte() & 0xFF);
		return new String(cs);
	}

	private static int swap16(int i) {
		int b = 0;
		b |= ((i >> 8) & 0xFF) << 0;
		b |= ((i >> 0) & 0xFF) << 8;
		return b;
	}

	private static int swap32(int i) {
		int b = 0;
		b |= ((i >> 24) & 0xFF) << 0;
		b |= ((i >> 16) & 0xFF) << 8;
		b |= ((i >> 8) & 0xFF) << 16;
		b |= ((i >> 0) & 0xFF) << 24;
		return b;
	}
}