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
import java.io.IOException;
import java.nio.ByteBuffer;

import org.lwjgl.openal.AL;

import craterstudio.io.Streams;
import craterstudio.util.HighLevel;

public abstract class VideoPlayback implements Closeable {
	public final VideoStream videoStream;
	public final AudioStream audioStream;

	public VideoPlayback(VideoStream videoStream, AudioStream audioStream) {
		this.videoStream = videoStream;
		this.audioStream = audioStream;
	}

	public void startVideo(VideoRenderer videoRenderer, AudioRenderer audioRenderer) {
		this.videoRenderer = videoRenderer;
		this.audioRenderer = null;

		if (audioRenderer != null && this.audioStream != null) {
			this.audioRenderer = audioRenderer;
			this.audioRenderer.init(this.audioStream, this.videoStream.metadata.framerate);
		}

		this.videoStream.startReadLoop();
		this.startDisplayLoop();
	}

	private void startDisplayLoop() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				runDisplayLoop();
			}
		}, "VideoPlayer-DisplayLoop").start();
	}

	private static final int AUDIO_UNAVAILABLE = -1;
	private static final int AUDIO_TERMINATED = -2;

	public int getAudioFrameIndex() {

		if (this.audioRenderer == null) {
			return AUDIO_UNAVAILABLE;
		}

		if (!this.audioRenderer.tick()) {
			return AUDIO_TERMINATED;
		}

		return this.audioRenderer.getAudioFrameIndex();
	}

	public void setVolume(float volume) {
		if (audioRenderer != null) {
			audioRenderer.setVolume(volume);
		} else {
			// ignore
		}
	}

	public void pause() {
		if (audioRenderer != null) {
			audioRenderer.pause();
		} else {
			// wait forever
			videoFrameIndex = Integer.MAX_VALUE;
		}
	}

	public void resume() {
		if (audioRenderer != null) {
			audioRenderer.resume();
		} else {
			// reset video timing
			initFrame = System.nanoTime();
			videoFrameIndex = 0;
		}
	}

	private long initFrame;
	private int videoFrameIndex;
	private long frameInterval;
	private VideoRenderer videoRenderer;
	private AudioRenderer audioRenderer;

	public void init() {
		initFrame = System.nanoTime();
		videoFrameIndex = 0;
		frameInterval = (long) (1000_000_000L / this.videoStream.metadata.framerate);
	}

	public void sync() {
		int audioFrameIndex = this.getAudioFrameIndex();

		while (videoRenderer.isVisible()) {

			boolean doWait;
			switch (audioFrameIndex) {
				case AUDIO_TERMINATED:
					// reached end of audio
					return;

				case AUDIO_UNAVAILABLE:
					// sync video with clock
					doWait = videoFrameIndex * frameInterval > System.nanoTime() - initFrame;
					break;

				default:
					// sync video with audio
					doWait = videoFrameIndex > audioFrameIndex;
					break;
			}

			if (!doWait) {
				break;
			}

			HighLevel.sleep(1);

			if (audioFrameIndex >= 0) {
				audioFrameIndex = this.getAudioFrameIndex();
			}
		}
	}

	public void runDisplayLoop() {

		System.out.println("Video buffering...");
		ByteBuffer rgb = this.videoStream.frameQueue.take();
		if (rgb == null) {
			System.out.println("no initial frame");
			return;
		}

		System.out.println("Video metadata: " + this.videoStream.metadata);

		long secondStarted = System.nanoTime();
		int videoFramerate = 0;

		this.init();

		videoRenderer.init(this.videoStream.metadata);

		do {
			this.sync();

			// render
			long frameRenderTime;
			{
				long t0 = System.nanoTime();
				this.videoRenderer.render(rgb);
				long t1 = System.nanoTime();
				frameRenderTime = t1 - t0;
			}

			videoFrameIndex++;
			videoFramerate++;

			// stats
			if (System.nanoTime() > secondStarted + 1000_000_000L) {
				videoRenderer.setStats(//
				   +videoFramerate + "fps " + //
				      "(receiving: " + (videoStream.frameReadingTime / 1000000L) + "ms, " + //
				      "rendering: " + (frameRenderTime / 1000000L) + "ms)");
				videoFramerate = 0;
				secondStarted += 1000_000_000L;
			}

			this.videoStream.framePool.release(rgb);
			rgb = this.videoStream.frameQueue.take();
		} while (videoRenderer.isVisible() && rgb != null);

		System.out.println("Image Queue: EOF");

		try {
			this.close();
		} catch (IOException exc) {
			throw new IllegalStateException();
		}

		AL.destroy();

		HighLevel.sleep(1000L);

		System.exit(0);
	}

	@Override
	public void close() throws IOException {
		Streams.safeClose(videoRenderer);
		Streams.safeClose(videoStream);
		Streams.safeClose(audioStream);
	}
}