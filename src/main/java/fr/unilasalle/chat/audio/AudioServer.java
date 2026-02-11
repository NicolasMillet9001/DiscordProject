package fr.unilasalle.chat.audio;

import java.net.*;
import java.util.concurrent.*;
import java.io.*;
import java.util.*;

public class AudioServer extends Thread {
    private DatagramSocket socket;
    private int port;
    private ConcurrentHashMap<String, InetSocketAddress> userAddresses = new ConcurrentHashMap<>();
    private ConcurrentHashMap<InetSocketAddress, String> addressToUser = new ConcurrentHashMap<>();
    
    // Maps a user to the user they are currently in a call with
    private ConcurrentHashMap<String, String> activeCalls = new ConcurrentHashMap<>();

    public AudioServer(int port) throws SocketException {
        this.port = port;
        this.socket = new DatagramSocket(port);
        System.out.println("Audio Server listening on UDP port " + port);
    }

    // Called by Main Server (TCP) when a call is accepted
    public void registerCall(String user1, String user2) {
        activeCalls.put(user1, user2);
        activeCalls.put(user2, user1);
        System.out.println("Audio: Registered call between " + user1 + " and " + user2);
    }

    public void endCall(String user) {
        String partner = activeCalls.remove(user);
        if (partner != null) {
            activeCalls.remove(partner);
            System.out.println("Audio: Ended call between " + user + " and " + partner);
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[4096];
        
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                InetSocketAddress senderAddr = (InetSocketAddress) packet.getSocketAddress();
                
                // Packet Format:
                // If starts with "LINK:", it's a registration packet: "LINK:username"
                // Else it's audio data
                
                // Check if it's a control packet (first few bytes)
                // We'll use a simple heuristic: if length < 100 and starts with "LINK:", it's control.
                // Audio packets are usually 500-1000 bytes.
                
                if (packet.getLength() < 100) {
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    if (msg.startsWith("LINK:")) {
                        String username = msg.substring(5).trim();
                        userAddresses.put(username, senderAddr);
                        addressToUser.put(senderAddr, username);
                        // System.out.println("Audio: Linked " + username + " to " + senderAddr);
                        continue;
                    }
                }
                
                // It's an audio packet
                String sender = addressToUser.get(senderAddr);
                if (sender != null) {
                    String recipient = activeCalls.get(sender);
                    if (recipient != null) {
                        InetSocketAddress recipientAddr = userAddresses.get(recipient);
                        if (recipientAddr != null) {
                            // Forward packet
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
