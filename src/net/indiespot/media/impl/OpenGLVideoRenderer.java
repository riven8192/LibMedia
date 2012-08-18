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
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.PixelFormat;

import craterstudio.math.EasyMath;
import craterstudio.text.TextValues;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.*;

public class OpenGLVideoRenderer implements VideoRenderer {
	private final String windowTitle;

	public OpenGLVideoRenderer(String windowTitle) {
		this.windowTitle = windowTitle;
	}

	//

	private boolean fullscreen;

	public void setFullscreen(boolean fullscreen) {
		this.fullscreen = fullscreen;
	}

	//

	private boolean vsync;

	public void setVSync(boolean vsync) {
		this.vsync = vsync;
	}
	
	//

	private boolean renderRotatingQuad;

	public void setRenderRotatingQuad(boolean renderRotatingQuad) {
		this.renderRotatingQuad = renderRotatingQuad;
	}

	//

	private int videoWidth, videoHeight;
	private int displayWidth, displayHeight;

	@Override
	public void init(VideoMetadata metadata) {
		this.videoWidth = metadata.width;
		this.videoHeight = metadata.height;

		try {
			if (!fullscreen) {
				Display.setDisplayMode(new DisplayMode(videoWidth, videoHeight));
			}
			Display.setResizable(true);
			Display.setTitle(windowTitle);
			Display.setVSyncEnabled(this.vsync);
			Display.setFullscreen(this.fullscreen);
			try {
				Display.create(new PixelFormat(/* bpp */32, /* alpha */8, /* depth */24,/* stencil */8,/* samples */4));
			} catch (LWJGLException exc) {
				System.out.println("Failed to create Display with 4 samples");
				Display.create(new PixelFormat());
			}
		} catch (LWJGLException exc) {
			throw new IllegalStateException(exc);
		}

		//

		textures = new int[2];
		for (int i = 0; i < textures.length; i++) {
			textures[i] = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, textures[i]);

			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

			int wPot = EasyMath.fitInPowerOfTwo(videoWidth);
			int hPot = EasyMath.fitInPowerOfTwo(videoHeight);
			texWidthUsedRatio = (float) videoWidth / wPot;
			texHeightUsedRatio = (float) videoHeight / hPot;

			// 'tmpbuf' should be null, but some drivers are too buggy
			ByteBuffer tmpbuf = BufferUtils.createByteBuffer(wPot * hPot * 3);
			glTexImage2D(GL_TEXTURE_2D, 0/* level */, GL_RGB, wPot, hPot, 0/* border */, GL_RGB, GL_UNSIGNED_BYTE, tmpbuf);
		}

		this.pboAllowed = GLContext.getCapabilities().OpenGL21;
		System.out.println("PBO allowed: " + (this.pboAllowed ? "yes" : "no"));
		
		this.enablePBO();
	}

	@Override
	public boolean isVisible() {
		return !Display.isCloseRequested();
	}

	@Override
	public void setStats(String stats) {
		Display.setTitle(windowTitle + " - OpenGL - " + stats);
	}

	private float texWidthUsedRatio, texHeightUsedRatio;
	private int[] textures = null;
	private int textureIndex = 0;

	void enablePBO() {
		if (!this.pboAllowed) {
			return;
		}

		if (pboHandle != 0) {
			throw new IllegalStateException();
		}

		pboHandle = glGenBuffers();
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboHandle);
		glBufferData(GL_PIXEL_UNPACK_BUFFER, videoWidth * videoHeight * 3, GL_STREAM_DRAW);
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);

		System.out.println("Enabled PBO.");
	}

	boolean isUsingPBO() {
		return this.pboHandle != 0;
	}

	void disablePBO() {
		if (!this.pboAllowed) {
			return;
		}

		if (pboHandle == 0) {
			throw new IllegalStateException();
		}

		glDeleteBuffers(pboHandle);
		pboHandle = 0;
		System.out.println("Disabled PBO.");
	}

	private static final int PBO_SELF_MEASURE_INTERVAL = 100000000;
	private long texTook, pboTook;
	private boolean pboAllowed;
	private int pboIndex;
	private int pboHandle;
	private ByteBuffer pboBuffer;
	private int currentFrameTexture;

	private void updateTexture(ByteBuffer rgb) {
		if (rgb.remaining() != videoWidth * videoHeight * 3) {
			throw new IllegalStateException(rgb.remaining() + " <> " + (videoWidth * videoHeight * 3));
		}

		long t0 = System.nanoTime();
		if (pboHandle == 0) {

			glBindTexture(GL_TEXTURE_2D, textures[textureIndex & 1]);
			glTexSubImage2D(GL_TEXTURE_2D, 0/* level */, 0, 0, videoWidth, videoHeight, GL_RGB, GL_UNSIGNED_BYTE, rgb);

		} else {
			glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboHandle);

			pboBuffer = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY, pboBuffer);
			//System.out.println(NativeHacks.getBufferAddress(pboBuffer));
			pboBuffer.put(rgb);
			pboBuffer.flip();
			rgb.flip();
			glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);

			glBindTexture(GL_TEXTURE_2D, textures[textureIndex & 1]);
			glTexSubImage2D(GL_TEXTURE_2D, 0/* level */, 0, 0, videoWidth, videoHeight, GL_RGB, GL_UNSIGNED_BYTE, 0);
			glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
		}
		long t1 = System.nanoTime();

		if (pboHandle == 0) {
			texTook += t1 - t0;
		} else {
			pboTook += t1 - t0;
		}

		if (++pboIndex == 1 * PBO_SELF_MEASURE_INTERVAL) {
			this.enablePBO();
		} else if (pboIndex == 2 * PBO_SELF_MEASURE_INTERVAL) {
			System.out.println("\ttex updates took: " + TextValues.formatNumber(texTook / 1000000.0 / PBO_SELF_MEASURE_INTERVAL, 2) + " ms");
			System.out.println("\tpbo updates took: " + TextValues.formatNumber(pboTook / 1000000.0 / PBO_SELF_MEASURE_INTERVAL, 2) + " ms");
			if (texTook < pboTook) {
				this.disablePBO();
			} else {
				System.out.println("Keeping PBO.");
			}
		}

		// bind the texture, that has been filled in the previous frame (if any)
		if (textureIndex++ == 0) {
			currentFrameTexture = textures[0];
		} else {
			currentFrameTexture = textures[textureIndex & 1];
		}
	}

	@Override
	public void render(ByteBuffer rgb) {

		if (displayWidth == 0 || Display.wasResized()) {

			displayWidth = Display.getWidth();
			displayHeight = Display.getHeight();

			glMatrixMode(GL_PROJECTION);
			glLoadIdentity();
			{
				// coordinate system a la Java2D
				glScalef(2.0f, -2.0f, 1.0f);
				glTranslatef(-0.5f, -0.5f, 0);
				glScalef(1.0f / displayWidth, 1.0f / displayHeight, 1.0f);
			}

			glMatrixMode(GL_MODELVIEW);
			glLoadIdentity();

			glViewport(0, 0, displayWidth, displayHeight);
		}

		glClearColor(0, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT);

		if (rgb != null) {
			this.updateTexture(rgb);
		}

		// render video
		{
			glEnable(GL_TEXTURE_2D);
			glBindTexture(GL_TEXTURE_2D, this.currentFrameTexture);

			glColor3f(1, 1, 1);
			glBegin(GL_QUADS);
			{
				int wQuad = displayWidth;
				int hQuad = Math.round(wQuad * ((float) videoHeight / videoWidth));

				if (hQuad > displayHeight) {
					hQuad = displayHeight;
					wQuad = Math.round(hQuad * ((float) videoWidth / videoHeight));
				}

				if (wQuad > displayWidth) {
					wQuad = displayWidth;
					hQuad = Math.round(wQuad * ((float) videoHeight / videoWidth));
				}

				int xQuad = (displayWidth - wQuad) / 2;
				int yQuad = (displayHeight - hQuad) / 2;
				this.renderQuad(xQuad, yQuad, wQuad, hQuad);
			}
			glEnd();
			glDisable(GL_TEXTURE_2D);
		}

		if (renderRotatingQuad) {
			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

			glPushMatrix();
			glScalef(32, 32, 1);
			glTranslatef((float) Math.sqrt(2.0), (float) Math.sqrt(2.0), 0);
			glRotatef((float) (System.nanoTime() / 1_000_000_000.0 * 360 * 0.1), 0, 0, 1);

			glColor4f(1, 1, 1, 0.25f);
			glBegin(GL_QUADS);
			glVertex2f(-1, -1);
			glVertex2f(+1, -1);
			glVertex2f(+1, +1);
			glVertex2f(-1, +1);
			glEnd();

			glPopMatrix();

			glDisable(GL_BLEND);
		}
	}

	private void renderQuad(int x, int y, int w, int h) {
		glTexCoord2f(0 * texWidthUsedRatio, 0 * texHeightUsedRatio);
		glVertex2f(x + 0, y + 0);

		glTexCoord2f(1 * texWidthUsedRatio, 0 * texHeightUsedRatio);
		glVertex2f(x + w, y + 0);

		glTexCoord2f(1 * texWidthUsedRatio, 1 * texHeightUsedRatio);
		glVertex2f(x + w, y + h);

		glTexCoord2f(0 * texWidthUsedRatio, 1 * texHeightUsedRatio);
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
