package fr.unilasalle.chat.client.ui;

import javax.sound.sampled.*;
import java.io.File;
import java.util.Random;

public class SoundManager {
    private static final String MEDIA_DIR = "media/";

    public void playClick() {
        playSound("mouseClick.wav");
    }

    public void playKey(boolean isSpace) {
        if (isSpace) {
            playSound("clavierEspace.wav");
        } else {
            int r = new Random().nextInt(3) + 1; // 1, 2, or 3
            playSound("clavier" + r + ".wav");
        }
    }

    private void playSound(String filename) {
        new Thread(() -> {
            try {
                File file = new File(MEDIA_DIR + filename);
                if (file.exists()) {
                    AudioInputStream audioIn = AudioSystem.getAudioInputStream(file);
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioIn);
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) {
                            clip.close();
                        }
                    });
                    clip.start();
                } else {
                    System.err.println("Sound file not found: " + file.getAbsolutePath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
