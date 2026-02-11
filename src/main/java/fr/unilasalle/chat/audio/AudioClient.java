package fr.unilasalle.chat.audio;

import javax.sound.sampled.*;
import java.net.*;
import java.io.*;

public class AudioClient {
    private String serverHostname;
    private int serverPort;
    private DatagramSocket socket;
    private boolean running = false;
    private String username;

    private TargetDataLine microphone;
    private SourceDataLine speakers;

    public AudioClient(String serverHostname, int serverPort, String username) {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;
        this.username = username;
    }
    
    public void startCall() {
        if (running) return;
        running = true;
        
        try {
            socket = new DatagramSocket();
            
            // Send LINK packet
            byte[] linkData = ("LINK:" + username).getBytes();
            DatagramPacket linkPacket = new DatagramPacket(linkData, linkData.length, InetAddress.getByName(serverHostname), serverPort);
            socket.send(linkPacket);
            
            // Audio Format: 8000Hz, 16-bit, Mono (standard VoIP)
            AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);
            
            // Setup Microphone
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(micInfo)) {
                System.err.println("Microphone not supported");
                return;
            }
            microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphone.open(format);
            microphone.start();
            
            // Setup Speakers
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(speakerInfo)) {
                System.err.println("Speakers not supported");
                return;
            }
            speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speakers.open(format);
            speakers.start();
            
            // Start Threads
            new Thread(this::captureAndSend).start();
            new Thread(this::receiveAndPlay).start();
            
        } catch (Exception e) {
            e.printStackTrace();
            stopCall();
        }
    }
    
    public void stopCall() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        if (microphone != null) { microphone.stop(); microphone.close(); }
        if (speakers != null) { speakers.stop(); speakers.close(); }
    }
    
    private void captureAndSend() {
        try {
            byte[] buffer = new byte[1024]; // 1024 bytes buffer
            InetAddress serverAddr = InetAddress.getByName(serverHostname);
            
            while (running) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    DatagramPacket packet = new DatagramPacket(buffer, bytesRead, serverAddr, serverPort);
                    socket.send(packet);
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace(); // Only print if not intentionally stopped
        }
    }
    
    private void receiveAndPlay() {
        try {
            byte[] buffer = new byte[1024];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                speakers.write(packet.getData(), 0, packet.getLength());
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }
}
