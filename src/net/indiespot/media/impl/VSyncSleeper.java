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

import org.lwjgl.opengl.Display;

import craterstudio.text.TextValues;
import craterstudio.util.HighLevel;

class VSyncSleeper {
	private static final long INTERVAL = 1_000_000_000L / 60;

	private long nextPostSync;
	private long errorMargin;

	public void setSyncErrorMargin(int millis) {
		this.errorMargin = (millis * 1_000_000L);
	}

	public void measureSyncTimestamp(int frames) {
		// ensure GPU cannot use async updates
		nextPostSync = System.nanoTime();
		for (int i = 0; i < frames; i++) {
			Display.update();
			long now = System.nanoTime();
			System.out.println("VSyncSleeper: " + TextValues.formatNumber((now - nextPostSync) / 1_000_000.0, 2) + "ms");
			nextPostSync = now;
		}
	}

	private void debugMeasureSyncPredictionError() {
		/**
		 * result: even over thousands of frames, the error is within 1ms
		 */
		for (int i = 0; true; i++) {
			Display.update();

			System.out.println("vsync prediction error: " + TextValues.formatNumber(//
			   (System.nanoTime() - (nextPostSync + (i + 1) * INTERVAL)) / 1_000_000.0, 2) + "ms");
		}
	}

	public void sleepUntilBeforeVsync() {
		this.calcNextPostVync(System.nanoTime());

		long wakeupAt = this.nextPostSync - this.errorMargin;
		if (wakeupAt < System.nanoTime()) {
			return;
		}

		// System.out.println("VSyncSleeper: wait for " +
		// TextValues.formatNumber((wakeupAt - System.nanoTime()) / 1_000_000.0,
		// 2) + "ms");

		while (System.nanoTime() < wakeupAt) {
			HighLevel.sleep(1);
		}
	}

	private void calcNextPostVync(long now) {
		long next = nextPostSync;
		while (next < now) {
			next += INTERVAL;
		}
		nextPostSync = next;
	}
}
