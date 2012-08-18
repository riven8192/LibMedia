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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import net.indiespot.media.Extractor;
import craterstudio.io.Streams;
import craterstudio.streams.NullOutputStream;
import craterstudio.text.RegexUtil;
import craterstudio.text.TextValues;

public class FFmpeg {
	public static String FFMPEG_PATH;
	public static boolean FFMPEG_VERBOSE = false;
	public static String JPEG_QUALITY = "1";

	static {
		String resourceName = "./bin/ffmpeg";
		if (Extractor.isMac) {
			resourceName += "-mac";
		} else {
			resourceName += Extractor.is64bit ? "64" : "32";
			if (Extractor.isWindows) {
				resourceName += ".exe";
			}
		}

		FFMPEG_PATH = resourceName;

		if (!new File(FFMPEG_PATH).exists()) {
			FFMPEG_PATH = "./res/ffmpeg32.exe";
		}
	}

	public static VideoMetadata extractMetadata(File srcMovieFile) throws IOException {
		Process process = new ProcessBuilder().command(//
		   FFMPEG_PATH, //
		   "-i", srcMovieFile.getAbsolutePath(),//
		   "-f", "null"//
		).start();
		Streams.asynchronousTransfer(process.getInputStream(), System.out, true, false);

		int width = -1;
		int height = -1;
		float framerate = -1;

		try {
			InputStream stderr = process.getErrorStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(stderr));
			for (String line; (line = br.readLine()) != null;) {
				System.out.println("ffmpeg: " + line);

				// Look for:
				// "	Stream #0:0: Video: vp6f, yuv420p, 320x240, 314 kb/s, 30 tbr, 1k tbn, 1k tbc"
				// ----------------------------------------------------------^

				if (line.trim().startsWith("Stream #") && line.contains("Video:")) {
					framerate = Float.parseFloat(RegexUtil.findFirst(line, Pattern.compile("\\s(\\d+(\\.\\d+)?)\\stbr,"), 1));
					int[] wh = TextValues.parseInts(RegexUtil.find(line, Pattern.compile("\\s(\\d+)x(\\d+)[\\s,]"), 1, 2));
					width = wh[0];
					height = wh[1];
				}
			}

			if (framerate == -1) {
				throw new IllegalStateException("failed to find framerate of video");
			}
			return new VideoMetadata(width, height, framerate);
		} finally {
			Streams.safeClose(process);
		}
	}

	public static InputStream extractVideoAsRGB24(File srcMovieFile) throws IOException {
		return streamData(new ProcessBuilder().command(//
		   FFMPEG_PATH, //
		   "-i", srcMovieFile.getAbsolutePath(), //
		   // "-ss", "00:05:00.00", //
		   "-f", "rawvideo", //
		   "-pix_fmt", "rgb24", //
		   "-" //
		));
	}

	//

	public static void extractAudioAsWAV(File srcMovieFile, File dstWavFile) throws IOException {
		await(new ProcessBuilder().command(//
		   FFMPEG_PATH, //
		   "-y", //
		   "-i", srcMovieFile.getAbsolutePath(), //
		   "-acodec", "pcm_s16le", //
		   "-ac", "2", //
		   // "-ss", "00:05:00.00", //
		   "-f", "wav", //
		   dstWavFile.getAbsolutePath() //
		   ));
	}

	public static InputStream extractAudioAsWAV(File srcMovieFile) throws IOException {
		return streamData(new ProcessBuilder().command(//
		   FFMPEG_PATH, //
		   "-i", srcMovieFile.getAbsolutePath(), //
		   "-acodec", "pcm_s16le", //
		   "-ac", "2", //
		   // "-ss", "00:05:00.00", //
		   "-f", "wav", //
		   "-" //
		));
	}

	//

	private static void await(ProcessBuilder pb) throws IOException {
		Process process = pb.start();
		Streams.asynchronousTransfer(process.getInputStream(), System.out, true, false);
		Streams.asynchronousTransfer(process.getErrorStream(), System.err, true, false);
		try {
			process.waitFor();
		} catch (InterruptedException exc) {
			throw new IllegalStateException(exc);
		}
	}

	private static InputStream streamData(ProcessBuilder pb) throws IOException {
		Process process = pb.start();
		Streams.asynchronousTransfer(process.getErrorStream(), FFMPEG_VERBOSE ? System.err : new NullOutputStream(), true, false);
		return process.getInputStream();
	}
}
