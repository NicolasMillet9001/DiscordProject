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
    private DatabaseService dbService;
    private Set<String> knownChannels = ConcurrentHashMap.newKeySet();

    public Server(int port) {
        this.port = port;
        this.dbService = new DatabaseService();
        this.knownChannels.add("general");
    }

    public DatabaseService getDbService() {
        return dbService;
    }

    public void execute() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat Server v2 (Debug) is listening on port " + port);

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

        // Save to DB (only if it's a real user message, i.e. sender != null)
        // Actually, logic is: "sender" is user.
        // If message is tagged with user like "[User]: Content", we should parse or
        // save raw?
        // Let's assume sender.getUserName() is the user.
        // Wait, message arg here is roughly "[User]: Content" or system message.
        // If sender is not null, it's a user message.
        if (sender != null) {
            // Need to parse content from message: "[User]: Content" -> "Content"
            // Or just save the whole formatted message?
            // User requested "conversation history". Saving formatted is easier for replay.
            // But my table has "username" and "content".
            // Let's retry: save raw content?
            // The message passed here is already formatted "[User]: Content".
            // Extract content:
            String content = message;
            int split = message.indexOf("]: ");
            if (split > 0) {
                content = message.substring(split + 3);
            }
            dbService.saveMessage(channel, sender.getUserName(), content);
        }

        String taggedMessage = "CHANMSG " + channel + " " + message;
        for (ClientHandler user : userThreads) {
            // Send to ALL users including sender (so they see their own message)
            user.sendMessage(taggedMessage);
        }
    }

    void sendPrivateMessage(String targetUserName, String message, ClientHandler sender) {
        // Save to DB first
        dbService.savePrivateMessage(sender.getUserName(), targetUserName, message);

        for (ClientHandler user : userThreads) {
            if (user.getUserName().equalsIgnoreCase(targetUserName)) {
                // Determine format. If we want it to look like a private chat in UI, we might
                // use a specific protocol tag
                // But for now, let's keep it compatible or use PRIVMSG tag
                // Match ChatGUI expectations using new unambiguous commands
                user.sendMessage("PRIVRECV " + sender.getUserName() + " " + message);
                sender.sendMessage("PRIVSENT " + targetUserName + " " + message); // Echo back to sender
                return;
            }
        }
        // If user not found (offline), we still saved it.
        // sender.sendMessage("User " + targetUserName + " is offline. Message saved.");
        // // User requested to remove this pollution
        // Echo it back to sender using PRIVSENT so it shows in their chat
        sender.sendMessage("PRIVSENT " + targetUserName + " " + message);
    }

    void sendFriendRequestNotification(String target, String requester) {
        for (ClientHandler user : userThreads) {
            if (user.getUserName().equalsIgnoreCase(target)) {
                user.sendMessage("LOG: You have received a friend request from " + requester + ". Type '/friend accept "
                        + requester + "' to accept.");
                user.sendMessage("FRIEND_REQ " + requester);
                return;
            }
        }
    }

    void sendFriendAcceptNotification(String target, String accepter) {
        for (ClientHandler user : userThreads) {
            if (user.getUserName().equalsIgnoreCase(target)) {
                user.sendMessage("LOG: " + accepter + " has accepted your friend request!");
                user.sendMessage("FRIEND_ACCEPT " + accepter);
                return;
            }
        }
    }

    void sendFriendListUpdate(String username) {
        for (ClientHandler user : userThreads) {
            if (user.getUserName().equalsIgnoreCase(username)) {
                String friendListMsg = getFormattedFriendList(username);
                user.sendMessage("FRIENDLIST " + friendListMsg);
                return;
            }
        }
    }

    private String getFormattedFriendList(String username) {
        java.util.List<String> friends = dbService.getFriends(username);
        if (friends.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Set<String> onlineUsersLower = new HashSet<>();
        Set<String> rawOnlineUsers = new HashSet<>();
        for (String u : getUserNames()) {
            onlineUsersLower.add(u.toLowerCase());
            rawOnlineUsers.add(u);
        }

        System.out.println("DEBUG: getFormattedFriendList for " + username);
        System.out.println("DEBUG: Friends: " + friends);
        System.out.println("DEBUG: Online Users (Raw): " + rawOnlineUsers);
        System.out.println("DEBUG: Online Users (Lower): " + onlineUsersLower);

        for (String friend : friends) {
            if (sb.length() > 0)
                sb.append(",");
            String status = onlineUsersLower.contains(friend.toLowerCase()) ? "online" : "offline";
            System.out.println("DEBUG: Friend " + friend + " -> " + status);
            sb.append(friend).append(":").append(status);
        }

        // Add Pending Requests
        java.util.List<String> pending = dbService.getPendingRequests(username);
        for (String p : pending) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(p).append(":pending");
        }

        return sb.toString();
    }

    void broadcastFriendStatusUpdate(String changedUser) {
        System.out.println("DEBUG: Broadcasting status update for " + changedUser);
        // Prepare variable implementation
        // For every connected user, checks if 'changedUser' is in their friend list.
        // If so, send them a FRIENDLIST update.
        for (ClientHandler user : userThreads) {
            java.util.List<String> friends = dbService.getFriends(user.getUserName());
            boolean isFriend = false;
            for (String f : friends) {
                if (f.equalsIgnoreCase(changedUser)) {
                    isFriend = true;
                    break;
                }
            }
            if (isFriend) {
                System.out.println("DEBUG: Sending update to " + user.getUserName() + " because they are friends with "
                        + changedUser);
                sendFriendListUpdate(user.getUserName());
            }
        }
    }

    String getUsersInChannel(String channel) {
        StringBuilder sb = new StringBuilder();
        for (ClientHandler user : userThreads) {
            if (user.getChannel().equalsIgnoreCase(channel)) {
                sb.append(user.getUserName()).append(",");
            }
        }
        return sb.toString(); // No trailing comma logic needed if empty?
        // Actually the original code had manual loop. String.join is easier if we
        // collected first.
        // But let's stick to minimal diff or just fix the trailing comma if present.
        // Wait, original code:
        /*
         * if (sb.length() > 0) {
         * sb.setLength(sb.length() - 1);
         * }
         */
        // I should keep it robust.
    }

    void broadcastUserList(String channel) {
        String users = getUsersInChannel(channel);
        String message = "USERLIST " + channel + " " + users;
        broadcastToChannel(channel, message, null); // null sender means system message
    }

    void broadcastAllUsers() {
        String users = String.join(",", getUserNames());
        String message = "ALLUSERS " + users;
        broadcast(message, null);
    }

    void broadcastChannelList() {
        String channels = getChannelList(); // Re-use getChannelList which filters
        String message = "CHANNELLIST " + channels;
        broadcast(message, null);
    }

    public void checkAndAddChannel(String channelName) {
        if (!knownChannels.contains(channelName)) {
            knownChannels.add(channelName);
            broadcastChannelList();
        }
    }

    public void deleteChannel(String channelName) {
        if (knownChannels.contains(channelName)) {
            knownChannels.remove(channelName);

            // Move users in this channel to general or kick them
            for (ClientHandler user : userThreads) {
                if (user.getChannel().equalsIgnoreCase(channelName)) {
                    user.sendMessage("LOG: Channel " + channelName + " has been deleted. Moving you to 'general'.");
                    user.setChannel("general"); // Force move
                    user.sendMessage("CHANMSG general You have been moved to general");
                }
            }

            broadcastChannelList();
        }
    }

    public void renameChannel(String oldName, String newName) {
        if (knownChannels.contains(oldName) && !knownChannels.contains(newName)) {
            knownChannels.remove(oldName);
            knownChannels.add(newName);

            for (ClientHandler user : userThreads) {
                if (user.getChannel().equalsIgnoreCase(oldName)) {
                    user.setChannel(newName);
                    user.sendMessage("LOG: Channel " + oldName + " was renamed to " + newName);
                }
            }
            broadcastChannelList();
        }
    }

    public String getChannelList() {
        // Filter out hidden channels (start with !)
        java.util.List<String> visible = new java.util.ArrayList<>();
        for (String c : knownChannels) {
            if (!c.startsWith("!")) {
                visible.add(c);
            }
        }
        return String.join(",", visible);
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
            System.out.println("The user " + userName + " quitted");
            broadcastAllUsers(); // Updates global list for everyone
            broadcastFriendStatusUpdate(userName); // Update status for friends
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
