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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import craterstudio.util.concur.SimpleBlockingQueue;

import net.indiespot.media.impl.FFmpeg;
import net.indiespot.media.impl.VideoMetadata;

public class Movie implements Closeable {

	public static Movie open(File movieFile) throws IOException {
		VideoMetadata metadata = FFmpeg.extractMetadata(movieFile);

		InputStream rgb24Stream = FFmpeg.extractVideoAsRGB24(movieFile);
		InputStream wav16Stream = FFmpeg.extractAudioAsWAV(movieFile);

		AudioStream audioStream = new AudioStream(wav16Stream);
		VideoStream videoStream = new VideoStream(rgb24Stream, metadata);

		videoStream = new ThreadedVideoStream(videoStream, metadata, 3);

		return new Movie(metadata, videoStream, audioStream);
	}

	private Movie(VideoMetadata metadata, VideoStream videoStream, AudioStream audioStream) {
		this.metadata = metadata;
		this.videoStream = videoStream;
		this.audioStream = audioStream;
	}

	//

	private final VideoMetadata metadata;

	public int width() {
		return metadata.width;
	}

	public int height() {
		return metadata.height;
	}

	public float framerate() {
		return metadata.framerate;
	}

	//

	private final VideoStream videoStream;
	private final AudioStream audioStream;

	public VideoStream videoStream() {
		return videoStream;
	}

	public AudioStream audioStream() {
		return audioStream;
	}

	//

	private long initFrame;
	private long frameInterval;

	public void init() {
		initFrame = System.nanoTime();
		audioIndex = 0;
		videoIndex = 0;
		frameInterval = (long) (1000_000_000L / metadata.framerate);
	}

	//

	private static final int AUDIO_UNAVAILABLE = -1;
	private static final int AUDIO_TERMINATED = -2;

	private int audioIndex;
	private int videoIndex;

	public void onMissingAudio() {
		audioIndex = AUDIO_UNAVAILABLE;
	}

	public void onEndOfAudio() {
		audioIndex = AUDIO_TERMINATED;
	}

	public void onRenderedAudioBuffer() {
		this.audioIndex++;
	}

	public void onUpdatedVideoFrame() {
		this.videoIndex++;
	}

	public boolean isTimeForNextFrame() {

		switch (audioIndex) {
			case AUDIO_TERMINATED:
				// reached end of audio
				return true;

			case AUDIO_UNAVAILABLE:
				// sync video with clock
				return videoIndex * frameInterval <= System.nanoTime() - initFrame;

			default:
				// sync video with audio
				return videoIndex <= audioIndex;
		}
	}

	@Override
	public void close() throws IOException {
		audioStream().close();
		videoStream().close();
	}
}

class ThreadedVideoStream extends VideoStream {
	private final VideoStream backing;
	private final SimpleBlockingQueue<ByteBuffer> emptyQueue, filledQueue;

	public ThreadedVideoStream(final VideoStream backing, VideoMetadata metadata, int buffers) {
		super(backing.videoStream, metadata);

		this.backing = backing;
		this.emptyQueue = new SimpleBlockingQueue<>();
		this.filledQueue = new SimpleBlockingQueue<>();

		for (int i = 0; i < buffers; i++) {
			this.emptyQueue.put(ByteBuffer.allocateDirect(metadata.width * metadata.height * 3));
		}

		new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					ByteBuffer rgbBuffer = emptyQueue.take();

					rgbBuffer.clear();
					if (backing.readFrameInto(rgbBuffer) == null) {
						break;
					}
					rgbBuffer.flip();

					filledQueue.put(rgbBuffer);
				}

				filledQueue.put(null);
			}
		}).start();
	}

	public ByteBuffer readFrameInto(ByteBuffer rgbBuffer) {
		if (rgbBuffer != null) {
			this.emptyQueue.put(rgbBuffer);
		}
		return this.filledQueue.take();
	}

	@Override
	public void close() throws IOException {
		this.backing.close();
	}
}
