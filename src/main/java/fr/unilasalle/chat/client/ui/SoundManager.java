package fr.unilasalle.chat.client.ui;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class SoundManager {
    private static final String MEDIA_DIR = "media/";
    private Map<String, Clip> soundCache = new HashMap<>();
    private Timer timer = new Timer();
    private TimerTask currentStopTask;

    public SoundManager() {
        // Preload sounds
        loadSound("mouseClick.wav");
        loadSound("clavierFull.wav");
        loadSound("messageSent.wav");
        loadSound("messageReceived.wav");
        loadSound("wizz.wav");
    }

    private void loadSound(String filename) {
        try {
            File file = new File(MEDIA_DIR + filename);
            if (!file.exists()) {
                System.err.println("Sound file not found: " + file.getAbsolutePath());
                return;
            }

            AudioInputStream audioIn = AudioSystem.getAudioInputStream(file);
            DataLine.Info info = new DataLine.Info(Clip.class, audioIn.getFormat());
            Clip clip = (Clip) AudioSystem.getLine(info);

            clip.open(audioIn);
            soundCache.put(filename, clip);

        } catch (LineUnavailableException | IOException | UnsupportedAudioFileException e) {
            System.err.println("Error loading sound " + filename + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void playClick() {
        playCachedSound("mouseClick.wav");
    }

    public void playKey(boolean isSpace) {
        // Argument ignored, we now play a continuous loop for any key
        startTypingSound();
    }

    public void playMessageSent() {
        playCachedSound("messageSent.wav");
    }

    public void playMessageReceived() {
        playCachedSound("messageReceived.wav");
    }

    public void playWizz() {
        playCachedSound("wizz.wav");
    }

    private synchronized void startTypingSound() {
        Clip clip = soundCache.get("clavierFull.wav");
        if (clip == null)
            return;

        // Reset the inactivity timer
        if (currentStopTask != null) {
            currentStopTask.cancel();
        }

        // Start looping if not already playing
        if (!clip.isRunning()) {
            clip.setFramePosition(0);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        }

        // Schedule stop after 1 second of inactivity
        currentStopTask = new TimerTask() {
            @Override
            public void run() {
                if (clip.isRunning()) {
                    clip.stop();
                }
            }
        };
        timer.schedule(currentStopTask, 200);
    }

    private void playCachedSound(String filename) {
        Clip clip = soundCache.get(filename);
        if (clip != null) {
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.setFramePosition(0);
            clip.start();
        }
    }
}
