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

import java.io.IOException;
import java.nio.ByteBuffer;

import net.indiespot.media.VideoRenderer;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import craterstudio.math.EasyMath;

import static org.lwjgl.opengl.GL11.*;

public class OpenGLVideoRenderer implements VideoRenderer {
	private final String windowTitle;

	public OpenGLVideoRenderer(String windowTitle) {
		this.windowTitle = windowTitle;
	}

	private int w, h;
	private int dw, dh;

	@Override
	public void init(VideoMetadata metadata) {
		this.w = metadata.width;
		this.h = metadata.height;

		try {
			Display.setDisplayMode(new DisplayMode(w, h));
			Display.setResizable(true);
			Display.setTitle(windowTitle);
			Display.create();
		} catch (LWJGLException exc) {
			throw new IllegalStateException(exc);
		}
	}

	@Override
	public boolean isVisible() {
		return !Display.isCloseRequested();
	}

	@Override
	public void setStats(String stats) {
		Display.setTitle(windowTitle + " - OpenGL - " + stats);
	}

	float wRatio, hRatio;
	private int[] textures = null;
	private int textureIndex = 0;

	@Override
	public void render(ByteBuffer rgb) {

		if (rgb.remaining() != w * h * 3) {
			throw new IllegalStateException(rgb.remaining() + " <> " + (w * h * 3));
		}

		if (textures == null || Display.wasResized()) {

			dw = Display.getWidth();
			dh = Display.getHeight();

			glMatrixMode(GL_PROJECTION);
			glLoadIdentity();
			{
				// coordinate system a la Java2D
				glScalef(2.0f, -2.0f, 1.0f);
				glTranslatef(-0.5f, -0.5f, 0);
				glScalef(1.0f / dw, 1.0f / dh, 1.0f);
			}

			glMatrixMode(GL_MODELVIEW);
			glLoadIdentity();

			glViewport(0, 0, dw, dh);
		}

		glClearColor(0, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT);

		glEnable(GL_TEXTURE_2D);

		if (textures == null) {
			textures = new int[2];

			for (int i = 0; i < textures.length; i++) {
				textures[i] = glGenTextures();
				glBindTexture(GL_TEXTURE_2D, textures[i]);

				glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
				glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
				glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
				glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

				int wPot = EasyMath.fitInPowerOfTwo(w);
				int hPot = EasyMath.fitInPowerOfTwo(h);
				wRatio = (float) w / wPot;
				hRatio = (float) h / hPot;

				// 'tmpbuf' should be null, but some drivers are too buggy
				ByteBuffer tmpbuf = BufferUtils.createByteBuffer(wPot * hPot * 3);
				glTexImage2D(GL_TEXTURE_2D, 0/* level */, GL_RGB, wPot, hPot, 0/* border */, GL_RGB, GL_UNSIGNED_BYTE, tmpbuf);
			}
		}

		{
			glBindTexture(GL_TEXTURE_2D, textures[textureIndex]);

			glTexSubImage2D(GL_TEXTURE_2D, 0/* level */, 0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, rgb);
		}

		textureIndex += 1;
		textureIndex %= textures.length;
		glBindTexture(GL_TEXTURE_2D, textures[textureIndex]);

		glColor3f(1, 1, 1);
		glBegin(GL_QUADS);
		{
			int wQuad = dw;
			int hQuad = Math.round(wQuad * ((float) h / w));

			if (hQuad > dh) {
				hQuad = dh;
				wQuad = Math.round(hQuad * ((float) w / h));
			}

			if (wQuad > dw) {
				wQuad = dw;
				hQuad = Math.round(wQuad * ((float) h / w));
			}

			int xQuad = (dw - wQuad) / 2;
			int yQuad = (dh - hQuad) / 2;
			this.renderQuad(xQuad, yQuad, wQuad, hQuad);
		}
		glEnd();

		Display.update();
	}

	private void renderQuad(int x, int y, int w, int h) {
		glTexCoord2f(0 * wRatio, 0 * hRatio);
		glVertex2f(x + 0, y + 0);

		glTexCoord2f(1 * wRatio, 0 * hRatio);
		glVertex2f(x + w, y + 0);

		glTexCoord2f(1 * wRatio, 1 * hRatio);
		glVertex2f(x + w, y + h);

		glTexCoord2f(0 * wRatio, 1 * hRatio);
		glVertex2f(x + 0, y + h);
	}

	@Override
	public void close() throws IOException {

		for (int texture : textures) {
			glDeleteTextures(texture);
		}

		Display.destroy();
	}
}
