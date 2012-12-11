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

package net.indiespot.media.test;

import static org.lwjgl.opengl.GL11.*;

import java.io.File;
import java.nio.ByteBuffer;

import net.indiespot.media.AudioRenderer;
import net.indiespot.media.Movie;
import net.indiespot.media.impl.OpenALAudioRenderer;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.openal.AL;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;

import craterstudio.math.EasyMath;
import craterstudio.text.TextValues;

public class TestGameLoop {
	public static void main(String path) throws Exception {

		final File movieFile = new File(path);
		Movie movie = Movie.open(movieFile);

		OpenALAudioRenderer audioRenderer = new OpenALAudioRenderer();
		audioRenderer.init(movie.audioStream(), movie.framerate());

		//

		Display.setDisplayMode(new DisplayMode(800, 600));
		Display.setResizable(true);
		Display.setTitle("TestGame");
		Display.setVSyncEnabled(false);
		AL.create();

		// create display
		{
			for (int samples = 8; samples >= 0; samples--) {
				try {
					Display.create(new PixelFormat(/* bpp */32, /* alpha */8, /* depth */24,/* stencil */8,/* samples */4));
					break;
				} catch (LWJGLException exc) {
					System.out.println("Failed to create Display with " + samples + " samples");
				}
			}
		}

		// setup projection matrix
		{
			displayWidth = Display.getWidth();
			displayHeight = Display.getHeight();
			{
				glMatrixMode(GL_PROJECTION);
				glLoadIdentity();
				GLU.gluPerspective(60.0f, displayWidth / (float) displayHeight, 0.01f, 100.0f);

				glMatrixMode(GL_MODELVIEW);
				glLoadIdentity();

				glViewport(0, 0, displayWidth, displayHeight);
			}
		}

		// create texture holding video frame
		{
			textureHandle = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, textureHandle);

			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

			int wPot = EasyMath.fitInPowerOfTwo(movie.width());
			int hPot = EasyMath.fitInPowerOfTwo(movie.height());
			texWidthUsedRatio = (float) movie.width() / wPot;
			texHeightUsedRatio = (float) movie.height() / hPot;

			// 'tmpbuf' should be null, but some drivers are too buggy
			ByteBuffer tmpbuf = BufferUtils.createByteBuffer(wPot * hPot * 3);
			glTexImage2D(GL_TEXTURE_2D, 0/* level */, GL_RGB, wPot, hPot, 0/* border */, GL_RGB, GL_UNSIGNED_BYTE, tmpbuf);
			tmpbuf = null;
		}

		// game loop

		long textureReceiveTook = 0;
		long textureUpdateTook = 0;
		long textureRenderTook = 0;

		long started = System.nanoTime();
		long startedLastSecond = System.nanoTime();
		int videoFramesLastSecond = 0;
		int renderFramesLastSecond = 0;

		while (!Display.isCloseRequested()) {

			// handle input
			{
				while (Keyboard.next()) {
					if (Keyboard.getEventKeyState()) { // on key press
						if (Keyboard.getEventKey() == Keyboard.KEY_SPACE) {
							if (audioRenderer.getState() == AudioRenderer.State.PLAYING) {
								audioRenderer.pause();
							} else if (audioRenderer.getState() == AudioRenderer.State.PAUSED) {
								audioRenderer.resume();
							}
						} else if (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE) {
							audioRenderer.stop();
						}
					}
				}

				while (Mouse.next()) {
					if (Mouse.getDWheel() != 0) { // on scrollwheel rotate
						float volume = audioRenderer.getVolume();
						volume += (Mouse.getDWheel() > 0) ? +0.1f : -0.1f;
						volume = EasyMath.clamp(volume, 0.0f, 1.0f);
						audioRenderer.setVolume(volume);
					}

					if (Mouse.getEventButtonState()) { // on mouse-button press
						if (audioRenderer.getState() == AudioRenderer.State.PLAYING) {
							audioRenderer.pause();
						} else if (audioRenderer.getState() == AudioRenderer.State.PAUSED) {
							audioRenderer.resume();
						}
					}
				}
			}

			audioRenderer.tick(movie);

			glClearColor(0, 0, 0, 1);
			glClear(GL_COLOR_BUFFER_BIT);

			// position camera, make it sway
			{
				glMatrixMode(GL_MODELVIEW);
				glLoadIdentity();

				long elapsed = (System.nanoTime() - started) / 1_000_000L;
				float angle = 90 + (float) Math.sin(elapsed * 0.001) * 15;

				// inverse camera transformations
				glRotatef(-angle, 0, 1, 0);
				glRotatef(-15f, 0, 0, 1); // look down
				glTranslatef(-3, -1.7f, -0);
			}

			glEnable(GL_TEXTURE_2D);
			glBindTexture(GL_TEXTURE_2D, textureHandle);

			if (movie.isTimeForNextFrame()) {
				// grab the next frame from the video stream
				textureReceiveTook = System.nanoTime();
				textureBuffer = movie.videoStream().readFrameInto(textureBuffer);
				textureReceiveTook = System.nanoTime() - textureReceiveTook;

				if (textureBuffer == null) {
					break;
				}

				textureUpdateTook = System.nanoTime();
				glTexSubImage2D(GL_TEXTURE_2D, 0/* level */, 0, 0, movie.width(), movie.height(), GL_RGB, GL_UNSIGNED_BYTE, textureBuffer);
				textureUpdateTook = System.nanoTime() - textureUpdateTook;

				// signal the AV-sync that we processed a frame
				movie.onUpdatedVideoFrame();

				videoFramesLastSecond++;
			}

			// render scene
			textureRenderTook = System.nanoTime();
			{
				glEnable(GL_BLEND);
				glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

				float h = (float) movie.height() / movie.width() * 2;

				glPushMatrix();

				glBegin(GL_QUADS);
				{
					glColor4f(1, 1, 1, 1);

					// render movie screen

					glTexCoord2f(0 * texWidthUsedRatio, 1 * texHeightUsedRatio);
					glVertex3f(0, 0.1f + h * 0, +1);

					glTexCoord2f(1 * texWidthUsedRatio, 1 * texHeightUsedRatio);
					glVertex3f(0, 0.1f + h * 0, -1);

					glTexCoord2f(1 * texWidthUsedRatio, 0 * texHeightUsedRatio);
					glVertex3f(0, 0.1f + h * 1, -1);

					glTexCoord2f(0 * texWidthUsedRatio, 0 * texHeightUsedRatio);
					glVertex3f(0, 0.1f + h * 1, +1);

					// radiosity / blur on the floor

					for (int i = -15; i <= +15; i++) {
						glColor4f(1, 1, 1, 0.025f);
						glTexCoord2f(0 * texWidthUsedRatio, 1 * texHeightUsedRatio);
						glVertex3f(0, 0, +1);

						glTexCoord2f(1 * texWidthUsedRatio, 1 * texHeightUsedRatio);
						glVertex3f(0, 0, -1);

						glColor4f(1, 1, 1, 0.0f);
						glTexCoord2f(1 * texWidthUsedRatio, 0 * texHeightUsedRatio);
						glVertex3f(0, -0.5f * h + Math.abs(i) * 0.01f, -1 + (i * 4) * 0.01f);

						glTexCoord2f(0 * texWidthUsedRatio, 0 * texHeightUsedRatio);
						glVertex3f(0, -0.5f * h + Math.abs(i) * 0.01f, +1 + (i * 4) * 0.01f);
					}
				}
				glEnd();
				glDisable(GL_BLEND);
				glDisable(GL_TEXTURE_2D);

				glPopMatrix();
			}
			glFlush();
			textureRenderTook = System.nanoTime() - textureRenderTook;

			renderFramesLastSecond++;
			if (System.nanoTime() > startedLastSecond + 1_000_000_000L) {
				startedLastSecond += 1_000_000_000L;

				String a = TextValues.formatNumber(textureReceiveTook / 1_000_000.0, 1);
				String b = TextValues.formatNumber(textureUpdateTook / 1_000_000.0, 1);
				String c = TextValues.formatNumber(textureRenderTook / 1_000_000.0, 1);
				Display.setTitle(//
				   "rendering " + renderFramesLastSecond + "fps, " + //
				      "video " + videoFramesLastSecond + "fps, " + //
				      "read blocking: " + a + "ms, " + //
				      "texture update: " + b + "ms, " + //
				      "rendering: " + c + "ms");

				renderFramesLastSecond = 0;
				videoFramesLastSecond = 0;
			}

			Display.update();
		}

		movie.close();
		audioRenderer.close();

		Display.destroy();
		AL.destroy();

		System.exit(0);
	}

	static int displayWidth;
	static int displayHeight;

	static int textureHandle;
	static ByteBuffer textureBuffer;

	static float texWidthUsedRatio;
	static float texHeightUsedRatio;
}
