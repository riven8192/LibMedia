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

import craterstudio.data.ByteList;
import craterstudio.text.TextValues;

public abstract class AudioRenderer implements Closeable {
	public static enum State {
		INIT, BUFFERING, PLAYING, PAUSED, CLOSED;
	}

	protected AudioStream audioStream;
	protected float frameRate;
	private byte[] largest;
	private final int[] samplesInBuffers = new int[2];
	protected final ByteBuffer[] bufferDuo = new ByteBuffer[2];
	private final ByteList bufferIndexList = new ByteList();

	public void init(AudioStream audioStream, float frameRate) {
		this.audioStream = audioStream;
		this.frameRate = frameRate;

		if (this.audioStream.numChannels != 2) {
			throw new IllegalStateException();
		}
		if (this.audioStream.bytesPerSample != 2) {
			throw new IllegalStateException();
		}

		samplesInBuffers[0] = (int) Math.floor(this.audioStream.sampleRate / frameRate);
		samplesInBuffers[1] = (int) Math.ceil(this.audioStream.sampleRate / frameRate);
		double samplesPerSecond = this.audioStream.sampleRate / (double) frameRate;

		this.calcSyncPattern(samplesPerSecond);

		for (int i = 0; i < bufferDuo.length; i++) {
			bufferDuo[i] = ByteBuffer.allocateDirect(samplesInBuffers[i] * (this.audioStream.numChannels + this.audioStream.bytesPerSample));
		}

		if (false) {
			System.out.println("Audio metadata: " + this.audioStream.sampleRate() + "Hz, " //
			   + this.audioStream.numChannels + " channels, " //
			   + (this.audioStream.bytesPerSample * 8) + " bit / sample, " + this.audioStream.sampleCount + " samples");
		}

		largest = new byte[Math.max(bufferDuo[0].capacity(), bufferDuo[1].capacity())];
	}

	private void calcSyncPattern(double samplesPerSecond) {
		double bestHourlyError = Integer.MAX_VALUE;
		int bestHourlyErrorIndex = -1;

		double prevErr = 1.0;
		for (int i = 0; i < 10_000; i++) {
			double currErr = (samplesPerSecond * (i + 1)) % 1.0;
			int picked = ((currErr > prevErr) ? 0 : 1);
			prevErr = currErr;

			bufferIndexList.add((byte) picked);

			double hourlyError = this.calcHourlyError();
			if (hourlyError < bestHourlyError) {
				bestHourlyError = hourlyError;
				bestHourlyErrorIndex = i;
			}
			if (hourlyError < 0.01) {
				break;
			}
		}

		while (bufferIndexList.size() > bestHourlyErrorIndex + 1) {
			bufferIndexList.removeLast();
		}

		if (false) {
			System.out.println("Audio hourly sync error: " + TextValues.formatNumber(calcHourlyError(), 2) + "sec, using pattern of " + bufferIndexList.size() + " switches");
		}
	}

	private double calcHourlyError() {
		int totalSamples = 0;
		for (int i = 0; i < bufferIndexList.size(); i++) {
			totalSamples += samplesInBuffers[bufferIndexList.get(i)];
		}

		double desired = (audioStream.sampleRate / frameRate);
		double actual = (double) totalSamples / bufferIndexList.size();
		return (actual - desired) * 3600.0 / audioStream.sampleRate;
	}

	private int loadIndex = 0;

	public abstract State getState();

	//

	public abstract void pause();

	public abstract void resume();

	public abstract void stop();

	//

	public abstract float getVolume();

	public abstract void setVolume(float volume);

	//

	public abstract boolean tick(Movie sync);

	public ByteBuffer loadNextSamples() {
		try {
			// switch between big and small buffer
			ByteBuffer buffer = bufferDuo[bufferIndexList.get(loadIndex++ % bufferIndexList.size())];
			audioStream.readSamples(largest, 0, buffer.capacity());

			buffer.clear();
			buffer.put(largest, 0, buffer.capacity());
			buffer.flip();

			return buffer;
		} catch (IOException exc) {
			return null;
		}
	}

	public void close() throws IOException {
		this.audioStream.close();
	}
}