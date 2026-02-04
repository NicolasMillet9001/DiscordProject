package fr.unilasalle.chat.client.ui;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SoundManager {
    private static final String MEDIA_DIR = "media/";
    private Map<String, Clip> soundCache = new HashMap<>();
    private Random random = new Random();

    public SoundManager() {
        // Preload sounds to avoid playback delay
        loadSound("mouseClick.wav");
        loadSound("clavier1.wav");
        loadSound("clavier2.wav");
        loadSound("clavier3.wav");
        loadSound("clavierEspace.wav");
    }

    private void loadSound(String filename) {
        try {
            File file = new File(MEDIA_DIR + filename);
            if (!file.exists()) {
                System.err.println("Sound file not found: " + file.getAbsolutePath());
                return;
            }
            
            // Open the stream properly
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(file);
            
            // Get a clip specifically for this audio format
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
        if (isSpace) {
            playCachedSound("clavierEspace.wav");
        } else {
            int r = random.nextInt(3) + 1; // 1, 2, or 3
            playCachedSound("clavier" + r + ".wav");
        }
    }

    private void playCachedSound(String filename) {
        Clip clip = soundCache.get(filename);
        if (clip != null) {
            // Stop if currently playing to allow rapid re-triggering
            if (clip.isRunning()) {
                clip.stop();
            }
            // Rewind to the beginning
            clip.setFramePosition(0);
            // Play
            clip.start();
        }
    }
}
