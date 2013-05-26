package net.indiespot.media;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL11.GL_RGB;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import net.indiespot.media.AudioRenderer.State;
import net.indiespot.media.impl.OpenALAudioRenderer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;

import craterstudio.util.RunningAvg;
import craterstudio.util.concur.SimpleBlockingQueue;

public class MoviePlayer {

	public File movieFile;
	public Movie movie;
	public int textureHandle;
	public OpenALAudioRenderer audioRenderer;

	public MoviePlayer(File movieFile) throws IOException {
		this.movieFile = movieFile;

		movie = Movie.open(movieFile);

		this.init();
	}

	private void init() {
		audioRenderer = new OpenALAudioRenderer();
		audioRenderer.init(movie.audioStream(), movie.framerate());

		// create texture holding video frame
		textureHandle = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, textureHandle);

		glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

		glTexImage2D(GL_TEXTURE_2D, 0/* level */, GL_RGB, movie.width(), movie.height(), 0/* border */, GL_RGB, GL_UNSIGNED_BYTE, (ByteBuffer) null);
	}

	private int offsetInSeconds;

	public void relativeSeek(int seconds) throws IOException {
		seconds = offsetInSeconds + (int) movie.getPlayingTime() + seconds;
		this.absoluteSeek(Math.max(0, seconds));
	}

	public void absoluteSeek(int seconds) throws IOException {
		this.offsetInSeconds = seconds;

		// audioRenderer.await();
		audioRenderer.close();
		audioRenderer = null;

		movie.close();
		movie = null;
		movie = Movie.open(movieFile, seconds);

		this.init();
	}

	public void tick() {
		audioRenderer.tick(movie);
	}

	public AudioRenderer audio() {
		return audioRenderer;
	}

	public boolean isPlaying() {
		return audioRenderer.getState() == State.PLAYING;
	}

	public boolean isPaused() {
		return audioRenderer.getState() == State.PAUSED;
	}

	public void pause() {
		audioRenderer.pause();
	}

	public void resume() {
		audioRenderer.resume();
	}

	public void stop() {
		audioRenderer.stop();
	}

	public void close() throws IOException {
		glDeleteTextures(textureHandle);
		textureHandle = 0;

		audioRenderer.close();
		movie.close();
	}

	public RunningAvg textureUpdateTook = new RunningAvg(20);

	public boolean syncTexture(int maxFramesBacklog) {
		glBindTexture(GL_TEXTURE_2D, textureHandle);

		ByteBuffer texBuffer = null;

		if (movie.isTimeForNextFrame()) {
			int framesRead = 0;
			do {
				if (framesRead > 0) {
					movie.videoStream().freeFrameData(texBuffer);
					texBuffer = null;

					// signal the AV-sync that we processed a frame
					movie.onUpdatedVideoFrame();
				}

				// grab the next frame from the video stream
				texBuffer = movie.videoStream().pollFrameData();
				if (texBuffer == VideoStream.EOF) {
					return false;
				}
				if (texBuffer == null) {
					return true;
				}

				framesRead++;
			} while (movie.hasVideoBacklogOver(maxFramesBacklog));

			if (framesRead > 1) {
				System.out.println("video frames skipped: " + (framesRead - 1));
			}

			{
				long tStart = System.nanoTime();
				glTexSubImage2D(GL_TEXTURE_2D, 0/* level */, 0, 0, movie.width(), movie.height(), GL_RGB, GL_UNSIGNED_BYTE, texBuffer);
				movie.videoStream().freeFrameData(texBuffer);
				textureUpdateTook.add(System.nanoTime() - tStart);
			}

			// signal the AV-sync that we processed a frame
			movie.onUpdatedVideoFrame();
		}

		return true;
	}
}
