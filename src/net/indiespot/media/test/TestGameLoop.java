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
import net.indiespot.media.MoviePlayer;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.openal.AL;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;

import craterstudio.math.EasyMath;
import craterstudio.text.Text;
import craterstudio.text.TextValues;

public class TestGameLoop {
	public static void main(String path) throws Exception {

		final File movieFile = new File(path);

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

		MoviePlayer player = new MoviePlayer(movieFile);

		// game loop

		long started = System.nanoTime();
		long startedLastSecond = System.nanoTime();
		int videoFramesLastSecond = 0;
		int renderFramesLastSecond = 0;

		boolean firstFrame = true;
		while (!Display.isCloseRequested()) {
			if (firstFrame || Display.wasResized()) {
				firstFrame = false;

				// setup projection matrix
				displayWidth = Display.getWidth();
				displayHeight = Display.getHeight();
				{
					glViewport(0, 0, displayWidth, displayHeight);
				}
			}

			// handle input
			{
				while (Keyboard.next()) {
					if (Keyboard.getEventKeyState()) { // on key press
						if (Keyboard.getEventKey() == Keyboard.KEY_SPACE) {
							if (player.isPlaying()) {
								player.pause();
							} else if (player.isPaused()) {
								player.resume();
							}
						} else if (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE) {
							player.stop();
						} else if (Keyboard.getEventKey() == Keyboard.KEY_LEFT) {
							player.relativeSeek(-10);
						} else if (Keyboard.getEventKey() == Keyboard.KEY_RIGHT) {
							player.relativeSeek(+10);
						}
					}
				}

				while (Mouse.next()) {
					int dwheel = Mouse.getDWheel();
					if (dwheel != 0) { // on scrollwheel rotate
						float volume = player.audio().getVolume();
						volume += (dwheel > 0) ? +0.1f : -0.1f;
						volume = EasyMath.clamp(volume, 0.0f, 1.0f);
						player.audio().setVolume(volume);
					}

					if (Mouse.getEventButtonState()) { // on mouse-button press
						if (player.isPlaying()) {
							player.pause();
						} else if (player.isPaused()) {
							player.resume();
						}
					}
				}
			}

			player.tick();

			glClearColor(0, 0, 0, 1);
			glClear(GL_COLOR_BUFFER_BIT);

			boolean is3D = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);

			// position camera, make it sway
			if (is3D) {
				glMatrixMode(GL_PROJECTION);
				glLoadIdentity();
				GLU.gluPerspective(60.0f, displayWidth / (float) displayHeight, 0.01f, 100.0f);

				glMatrixMode(GL_MODELVIEW);
				glLoadIdentity();

				long elapsed = (System.nanoTime() - started) / 1_000_000L;
				float angle = 90 + (float) Math.sin(elapsed * 0.001) * 15;

				// inverse camera transformations
				glRotatef(-angle, 0, 1, 0);
				glRotatef(-15f, 0, 0, 1); // look down
				glTranslatef(-3, -1.7f, -0);
			} else {
				glMatrixMode(GL_PROJECTION);
				glLoadIdentity();
				glOrtho(0, displayWidth, displayHeight, 0, -1.0f, +1.0f);

				glMatrixMode(GL_MODELVIEW);
				glLoadIdentity();
			}

			glEnable(GL_TEXTURE_2D);

			if (!player.syncTexture(5)) {
				break;
			}

			// render scene
			long textureRenderTook = System.nanoTime();
			{
				glEnable(GL_BLEND);
				glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

				float h = (float) player.movie.height() / player.movie.width() * 2;

				glPushMatrix();

				if (is3D) {
					glBegin(GL_QUADS);
					{
						glColor4f(1, 1, 1, 1);

						// render movie screen

						glTexCoord2f(0, 1);
						glVertex3f(0, 0.1f + h * 0, +1);

						glTexCoord2f(1, 1);
						glVertex3f(0, 0.1f + h * 0, -1);

						glTexCoord2f(1, 0);
						glVertex3f(0, 0.1f + h * 1, -1);

						glTexCoord2f(0, 0);
						glVertex3f(0, 0.1f + h * 1, +1);

						// radiosity / blur on the floor

						for (int i = -15; i <= +15; i++) {
							glColor4f(1, 1, 1, 0.025f);
							glTexCoord2f(0, 1);
							glVertex3f(0, 0, +1);

							glTexCoord2f(1, 1);
							glVertex3f(0, 0, -1);

							glColor4f(1, 1, 1, 0.0f);
							glTexCoord2f(1, 0);
							glVertex3f(0, -0.5f * h + Math.abs(i) * 0.01f, -1 + (i * 4) * 0.01f);

							glTexCoord2f(0, 0);
							glVertex3f(0, -0.5f * h + Math.abs(i) * 0.01f, +1 + (i * 4) * 0.01f);
						}
					}
					glEnd();
				} else {
					glBegin(GL_QUADS);

					glColor4f(1, 1, 1, 1);

					// render flat screen

					float wRatio = (float) displayWidth / player.movie.width();
					float hRatio = (float) displayHeight / player.movie.height();
					float minRatio = Math.min(wRatio, hRatio);

					float wMovie = player.movie.width() * minRatio;
					float hMovie = player.movie.height() * minRatio;
					float xMovie = (displayWidth - wMovie) * 0.5f;
					float yMovie = (displayHeight - hMovie) * 0.5f;

					glTexCoord2f(0, 0);
					glVertex3f(xMovie + 0 * wMovie, yMovie + 0 * hMovie, 0);

					glTexCoord2f(1, 0);
					glVertex3f(xMovie + 1 * wMovie, yMovie + 0 * hMovie, 0);

					glTexCoord2f(1, 1);
					glVertex3f(xMovie + 1 * wMovie, yMovie + 1 * hMovie, 0);

					glTexCoord2f(0, 1);
					glVertex3f(xMovie + 0 * wMovie, yMovie + 1 * hMovie, 0);

					glEnd();
				}
				glDisable(GL_BLEND);
				glDisable(GL_TEXTURE_2D);

				glPopMatrix();
			}
			glFlush();
			textureRenderTook = System.nanoTime() - textureRenderTook;

			renderFramesLastSecond++;
			if (System.nanoTime() > startedLastSecond + 1_000_000_000L) {
				startedLastSecond += 1_000_000_000L;

				String b1 = TextValues.formatNumber(player.textureUpdateTook.min() / 1_000_000.0, 1);
				String b2 = TextValues.formatNumber(player.textureUpdateTook.avg() / 1_000_000.0, 1);
				String b3 = TextValues.formatNumber(player.textureUpdateTook.max() / 1_000_000.0, 1);
				String c = TextValues.formatNumber(textureRenderTook / 1_000_000.0, 1);

				b1 = Text.replace(b1, ',', '.');
				b2 = Text.replace(b2, ',', '.');
				b3 = Text.replace(b3, ',', '.');
				c = Text.replace(c, ',', '.');

				Display.setTitle(//
				   "rendering " + renderFramesLastSecond + "fps, " + //
				      "video " + (player.textureUpdateTook.addCount() - videoFramesLastSecond) + "fps, " + //
				      "texture update: [min: " + b1 + ", avg: " + b2 + ", max: " + b3 + "ms] " + //
				      "rendering: " + c + "ms");

				renderFramesLastSecond = 0;
				videoFramesLastSecond = player.textureUpdateTook.addCount();
			}

			Display.update();

			Display.sync(60);
		}

		player.close();

		Display.destroy();
		AL.destroy();

		System.exit(0);
	}

	static int displayWidth;
	static int displayHeight;
}
