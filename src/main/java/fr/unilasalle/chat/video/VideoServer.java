package fr.unilasalle.chat.video;

import java.net.*;
import java.util.concurrent.*;
import java.io.*;
import java.util.*;

public class VideoServer extends Thread {
    private DatagramSocket socket;
    private int port;
    private ConcurrentHashMap<String, InetSocketAddress> userAddresses = new ConcurrentHashMap<>();
    private ConcurrentHashMap<InetSocketAddress, String> addressToUser = new ConcurrentHashMap<>();
    
    // Use activeCalls from AudioServer or separate?
    // Let's duplicate logic for simplicity as Video might be optional
    private ConcurrentHashMap<String, String> activeCalls = new ConcurrentHashMap<>();

    public VideoServer(int port) throws SocketException {
        this.port = port;
        this.socket = new DatagramSocket(port);
        System.out.println("Video Server listening on UDP port " + port);
    }

    public void registerCall(String user1, String user2) {
        activeCalls.put(user1, user2);
        activeCalls.put(user2, user1);
        System.out.println("Video: Registered call between " + user1 + " and " + user2);
    }
    
    public void endCall(String user) {
        String partner = activeCalls.remove(user);
        if (partner != null) {
            activeCalls.remove(partner);
            System.out.println("Video: Ended call between " + user + " and " + partner);
        }
    }

    @Override
    public void run() {
        // Video Packets are larger: 64KB max UDP packet size.
        byte[] buffer = new byte[65535];
        
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                InetSocketAddress senderAddr = (InetSocketAddress) packet.getSocketAddress();
                
                if (packet.getLength() < 100) {
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    if (msg.startsWith("LINK:")) {
                        String username = msg.substring(5).trim();
                        userAddresses.put(username, senderAddr);
                        addressToUser.put(senderAddr, username);
                        continue;
                    }
                }
                
                String sender = addressToUser.get(senderAddr);
                if (sender != null) {
                    String recipient = activeCalls.get(sender);
                    if (recipient != null) {
                        InetSocketAddress recipientAddr = userAddresses.get(recipient);
                        if (recipientAddr != null) {
                            DatagramPacket forward = new DatagramPacket(
                                packet.getData(), 
                                packet.getLength(), 
                                recipientAddr.getAddress(), 
                                recipientAddr.getPort()
                            );
                            socket.send(forward);
                        }
                    }
                }
                
            } catch (IOException e) {
                if (!socket.isClosed()) e.printStackTrace();
            }
        }
    }
    
    public void close() {
        socket.close();
    }
}
