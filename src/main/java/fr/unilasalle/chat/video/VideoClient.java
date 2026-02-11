package fr.unilasalle.chat.video;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.util.Arrays;
import java.util.function.Consumer;

public class VideoClient {
    private String serverHostname;
    private int serverPort;
    private DatagramSocket socket;
    private boolean running = false;
    private String username;
    private Consumer<Image> onImageReceived;
    private boolean sendingScreen = false;

    public VideoClient(String serverHostname, int serverPort, String username, Consumer<Image> onImageReceived) {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;
        this.username = username;
        this.onImageReceived = onImageReceived;
    }

    public void start() {
        if (running) return;
        running = true;
        
        try {
            socket = new DatagramSocket();
            // Link
            byte[] linkData = ("LINK:" + username).getBytes();
            DatagramPacket linkPacket = new DatagramPacket(linkData, linkData.length, InetAddress.getByName(serverHostname), serverPort);
            socket.send(linkPacket);
            
            new Thread(this::receiveLoop).start();
            new Thread(this::sendLoop).start();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void startScreenShare() {
        sendingScreen = true;
    }
    
    public void stopScreenShare() {
        sendingScreen = false;
    }

    public boolean isSendingScreen() {
        return sendingScreen;
    }
    
    public void stop() {
        running = false;
        sendingScreen = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }
    
    private void sendLoop() {
        try {
            Robot robot = new Robot();
            InetAddress serverAddr = InetAddress.getByName(serverHostname);
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            
            while (running) {
                if (sendingScreen) {
                    BufferedImage capture = robot.createScreenCapture(screenRect);
                    // Resize to reduce bandwidth? 
                    // Let's resize to 800x600 or similar to keep UDP happy
                    Image resized = capture.getScaledInstance(800, 600, Image.SCALE_SMOOTH);
                    BufferedImage outputImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
                    outputImage.getGraphics().drawImage(resized, 0, 0, null);
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(outputImage, "jpg", baos);
                    byte[] imageBytes = baos.toByteArray();
                    
                    // Chunking would be needed for UDP > 64k. 
                    // 800x600 JPG low quality might fit in 60k?
                    // If not, we need chunking or TCP.
                    // For simplest implementation, let's try to send if small enough, else discard.
                    
                    if (imageBytes.length < 60000) {
                        DatagramPacket packet = new DatagramPacket(imageBytes, imageBytes.length, serverAddr, serverPort);
                        socket.send(packet);
                    } else {
                         // System.out.println("Frame too big: " + imageBytes.length);
                         // Ideally implement chunking here.
                         // Or reduce quality/resolution.
                    }
                    
                    Thread.sleep(100); // 10 FPS
                } else {
                    Thread.sleep(1000);
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }
    private void receiveLoop() {
        try {
            socket.setSoTimeout(2000); // 2s timeout
            byte[] buffer = new byte[65535];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException te) {
                    if (onImageReceived != null) onImageReceived.accept(null);
                    continue;
                }
                
                if (packet.getLength() > 0) {
                    ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                    BufferedImage img = ImageIO.read(bais);
                    if (img != null && onImageReceived != null) {
                        onImageReceived.accept(img);
                    }
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }
}
