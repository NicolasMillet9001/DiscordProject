package fr.unilasalle.chat.server;

import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class Server {
    private int port;
    private Set<ClientHandler> userThreads = ConcurrentHashMap.newKeySet();
    private Set<String> knownChannels = ConcurrentHashMap.newKeySet();

    public Server(int port) {
        this.port = port;
        knownChannels.add("general");
        knownChannels.add("room1");
        knownChannels.add("random");
    }

    public void execute() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat Server is listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New user connected");

                ClientHandler newUser = new ClientHandler(socket, this);
                userThreads.add(newUser);
                newUser.start();
            }

        } catch (IOException ex) {
            System.out.println("Error in the server: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Syntax: java Server <port-number>");
            System.exit(0);
        }

        int port = Integer.parseInt(args[0]);
        Server server = new Server(port);
        server.execute();
    }

    /**
     * Delivers a message from one user to others (broadcasting)
     */
    void broadcast(String message, ClientHandler excludeUser) {
        Logger.log(message);
        for (ClientHandler aUser : userThreads) {
            if (aUser != excludeUser) {
                aUser.sendMessage(message);
            }
        }
    }

    void broadcastToChannel(String channel, String message, ClientHandler sender) {
        Logger.log("[" + channel + "] " + message);
        String taggedMessage = "CHANMSG " + channel + " " + message;
        for (ClientHandler user : userThreads) {
            if (user != sender) {
                // Send to ALL connected users so they can update their background history
                user.sendMessage(taggedMessage);
            }
        }
    }

    void sendPrivateMessage(String targetUserName, String message, ClientHandler sender) {
        for (ClientHandler user : userThreads) {
            if (user.getUserName().equalsIgnoreCase(targetUserName)) {
                user.sendMessage("[Private from " + sender.getUserName() + "]: " + message);
                sender.sendMessage("[Private to " + targetUserName + "]: " + message);
                return;
            }
        }
        sender.sendMessage("User " + targetUserName + " not found.");
    }

    String getUsersInChannel(String channel) {
        StringBuilder sb = new StringBuilder();
        for (ClientHandler user : userThreads) {
            if (user.getChannel().equalsIgnoreCase(channel)) {
                sb.append(user.getUserName()).append(",");
            }
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1); // remove last comma
        }
        return sb.toString();
    }

    void broadcastUserList(String channel) {
        String users = getUsersInChannel(channel);
        String message = "USERLIST " + channel + " " + users;
        broadcastToChannel(channel, message, null); // null sender means system message
    }

    void broadcastChannelList() {
        String channels = String.join(",", knownChannels);
        String message = "CHANNELLIST " + channels;
        broadcast(message, null);
    }
    
    public void checkAndAddChannel(String channelName) {
        if (!knownChannels.contains(channelName)) {
            knownChannels.add(channelName);
            broadcastChannelList(); 
        }
    }

    public String getChannelList() {
        return String.join(",", knownChannels);
    }

    /**
     * Stores username of the newly connected client.
     * Returns true if username is added, false if it already exists or is invalid.
     */
    synchronized boolean addUserName(String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            return false;
        }

        for (ClientHandler user : userThreads) {
            String existingName = user.getUserName();
            if (existingName != null && existingName.equalsIgnoreCase(userName)) {
                return false;
            }
        }

        System.out.println(userName + " has connected");
        return true;
    }

    /**
     * When a client is disconnected, removes the associated username and UserThread
     */
    void removeUser(ClientHandler user, String userName) {
        boolean removed = userThreads.remove(user);
        if (removed) {
            System.out.println("The user " + userName + " quitted");
        }
    }

    Set<String> getUserNames() {
        Set<String> userNames = new HashSet<>();
        for (ClientHandler user : userThreads) {
            if (user.getUserName() != null) {
                userNames.add(user.getUserName());
            }
        }
        return userNames;
    }

    boolean hasUsers() {
        return !this.userThreads.isEmpty();
    }
}
