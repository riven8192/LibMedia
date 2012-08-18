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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import net.indiespot.media.impl.FFmpegVideoPlayback;
import net.indiespot.media.impl.OpenALAudioRenderer;
import net.indiespot.media.impl.OpenGLVideoRenderer;

class VideoPlaybackTest {
	public static void main(String[] args) throws Exception {

		File movieFile = new File(args[0]);

		boolean audioEnabled = true;

		VideoRenderer videoRenderer = new OpenGLVideoRenderer(movieFile.getName());
		AudioRenderer audioRenderer = audioEnabled ? new OpenALAudioRenderer() : null;

		if (videoRenderer instanceof OpenGLVideoRenderer) {
			OpenGLVideoRenderer opengl = (OpenGLVideoRenderer) videoRenderer;
			opengl.setFullscreen(false);
			opengl.setVSync(false);
			opengl.setRenderRotatingQuad(true);
		}

		VideoPlayback playback = new FFmpegVideoPlayback(movieFile);
		playback.setCoupleFramerateToVideo(false);
		playback.startVideo(videoRenderer, audioRenderer);

		/**
		 * oldskool controls!!
		 */

		BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(System.in)));
		while (true) {
			String line = br.readLine();

			if (line.equals("mute")) {
				playback.setVolume(0.0f);
			} else if (line.equals("half")) {
				playback.setVolume(0.5f);
			} else if (line.equals("full")) {
				playback.setVolume(1.0f);
			} else if (line.equals("pause")) {
				playback.pause();
			} else if (line.equals("resume")) {
				playback.resume();
			} else {
				System.out.println("wait what?");
			}
		}
	}
}