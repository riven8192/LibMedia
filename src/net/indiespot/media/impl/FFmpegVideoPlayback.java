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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import net.indiespot.media.AudioStream;
import net.indiespot.media.VideoPlayback;
import net.indiespot.media.VideoStream;

public class FFmpegVideoPlayback extends VideoPlayback {
	public FFmpegVideoPlayback(File movieFile) throws IOException {
		super(//
		   video(movieFile),//
		   audio(movieFile)//
		);
	}

	private static final ThreadLocal<VideoMetadata> metadata = new ThreadLocal<>();

	private static VideoMetadata metadata(File movieFile) throws IOException {
		VideoMetadata metadata = FFmpeg.extractMetadata(movieFile);
		FFmpegVideoPlayback.metadata.set(metadata);
		return metadata;
	}

	private static VideoStream video(File movieFile) throws IOException {
		InputStream rgbStream = FFmpeg.extractVideoAsRGB24(movieFile);
		return new Rgb24VideoStream(rgbStream, metadata(movieFile));
	}

	private static AudioStream audio(File movieFile) throws IOException {
		InputStream wavStream = FFmpeg.extractAudioAsWAV(movieFile);
		try {
			return new AudioStream(new DataInputStream(new BufferedInputStream(wavStream)));
		} catch (IOException exc) {
			return null;
		}
	}
}
