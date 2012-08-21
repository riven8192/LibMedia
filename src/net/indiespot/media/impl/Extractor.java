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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import craterstudio.io.Streams;
import craterstudio.text.Text;

public class Extractor {

	public static final boolean isWindows, isMac, isLinux, is64bit;

	static {
		String osName = System.getProperty("os.name");
		String osArch = System.getProperty("os.arch");

		isWindows = osName.contains("Windows");
		isMac = osName.contains("Mac");
		isLinux = !isWindows && !isMac;

		String bits = System.getProperty("sun.arch.data.model");
		if (bits != null) {
			is64bit = Integer.parseInt(bits) == 64;
		} else {
			is64bit = osArch.equals("amd64") || osArch.equals("x86_64");
		}
	}

	public static void extractNativeLibrary(String resourceName) throws IOException {
		String[] paths = Text.split(System.getProperty("java.library.path"), File.pathSeparatorChar);
		String path = paths[paths.length - 1];
		path += '/' + Text.afterLast(resourceName, '/');
		extractResource(resourceName, new File(path));
	}

	public static void extractResource(String resourceName, File dst) throws IOException {
		if (dst.exists()) {
			return;
		}

		File dir = dst.getParentFile();

		if (!dir.exists() && !dir.mkdirs()) {
			throw new IllegalStateException("failed to create dir: " + dir.getAbsolutePath());
		}

		InputStream in = FFmpeg.class.getResourceAsStream(resourceName);
		if (in == null) {
			throw new IllegalStateException("failed to find resource: " + resourceName);
		}

		System.out.println("Extracting " + resourceName + " to " + dst.getAbsolutePath());
		Streams.copy(new BufferedInputStream(in), new BufferedOutputStream(new FileOutputStream(dst)));
	}
}
