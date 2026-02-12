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
            while (running) {
                if (sendingScreen) {
                    // Capture all monitors
                    Rectangle allScreensBounds = new Rectangle();
                    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    GraphicsDevice[] gd = ge.getScreenDevices();
                    for (GraphicsDevice device : gd) {
                        allScreensBounds = allScreensBounds.union(device.getDefaultConfiguration().getBounds());
                    }

                    BufferedImage capture = robot.createScreenCapture(allScreensBounds);
                    
                    // Resize to a fixed size for UDP transmission
                    // 800x600 is a good compromise for speed vs quality
                    int targetW = 800;
                    int targetH = (int) (allScreensBounds.height * (800.0 / allScreensBounds.width));
                    
                    Image resized = capture.getScaledInstance(targetW, targetH, Image.SCALE_FAST);
                    BufferedImage outputImage = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = outputImage.createGraphics();
                    g2.drawImage(resized, 0, 0, null);
                    g2.dispose();
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(outputImage, "jpg", baos);
                    byte[] imageBytes = baos.toByteArray();
                    
                    if (imageBytes.length < 65000) {
                        InetAddress serverAddr = InetAddress.getByName(serverHostname);
                        DatagramPacket packet = new DatagramPacket(imageBytes, imageBytes.length, serverAddr, serverPort);
                        socket.send(packet);
                    }
                    
                    Thread.sleep(150); // ~6-7 FPS for stability
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
