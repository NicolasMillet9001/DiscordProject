package fr.unilasalle.chat.server;

import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {
    private Socket socket;
    private Server server;
    private PrintWriter writer;
    private String userName;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    private String channel = "general";

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void run() {
        try {
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            OutputStream output = socket.getOutputStream();
            writer = new PrintWriter(output, true);

            printUsers();

            String userName;
            String prompt = "AUTH_REQUIRED"; // Use strict protocol keyword
            writer.println(prompt);

            while (true) {
                String authLine = reader.readLine();
                if (authLine == null)
                    return;

                String[] parts = authLine.split(" ", 3);
                String command = parts[0].toLowerCase();

                if (command.equals("/register") && parts.length == 3) {
                    if (server.getDbService().register(parts[1], parts[2])) {
                        writer.println("REGISTRATION_SUCCESS");
                    } else {
                        writer.println("REGISTRATION_FAIL");
                    }
                } else if (command.equals("/login") && parts.length == 3) {
                    if (server.getDbService().authenticate(parts[1], parts[2])) {
                        // Check if already online
                        if (server.addUserName(parts[1])) {
                            this.userName = parts[1];
                            writer.println("LOGIN_SUCCESS " + this.userName);
                            writer.println("Welcome " + this.userName);
                            writer.println("You are in channel: " + channel);

                            // Send History
                            java.util.List<String> history = server.getDbService().getHistory(channel, 50);
                            for (String msg : history) {
                                // Protocol: CHANMSG channel content
                                writer.println("CHANMSG " + channel + " " + msg);
                            }

                            // Send channel list and user list directly to this client
                            writer.println("CHANNELLIST " + server.getChannelList());
                            writer.println("USERLIST " + channel + " " + server.getUsersInChannel(channel));

                            // Broadcast updated user list to all users in the channel
                            server.broadcastUserList(channel);
                            writer.println("CHANNELLIST " + server.getChannelList()); // Send initial channel list

                            // Send Friend List
                            java.util.List<String> friends = server.getDbService().getFriends(this.userName);
                            writer.println("FRIENDLIST " + String.join(",", friends));

                            // Send Pending Friend Requests
                            java.util.List<String> pending = server.getDbService().getPendingRequests(this.userName);
                            for (String requester : pending) {
                                writer.println("FRIEND_REQ " + requester);
                            }
                            break;
                        } else {
                            writer.println("LOGIN_FAIL_ALREADY_CONNECTED");
                        }
                    } else {
                        writer.println("LOGIN_FAIL_INVALID");
                    }
                } else {
                    writer.println("UNKNOWN_AUTH_COMMAND");
                }
            }

            String serverMessage = "LOG:New user connected: " + this.userName;
            server.broadcast(serverMessage, this);

            String clientMessage;

            do {
                clientMessage = reader.readLine();
                if (clientMessage != null) {
                    if (clientMessage.startsWith("/")) {
                        handleCommand(clientMessage);
                    } else {
                        serverMessage = "[" + this.userName + "]: " + clientMessage;
                        server.broadcastToChannel(channel, serverMessage, this);
                    }
                } else {
                    break;
                }
            } while (true);

        } catch (IOException ex) {
            System.out.println("Error in ClientHandler: " + ex.getMessage());
        } finally {
            try {
                server.removeUser(this, userName);
                socket.close();

                String serverMessage = "LOG:" + userName + " has quitted.";
                server.broadcast(serverMessage, this);
                server.broadcastUserList(channel); // Update list on leave
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleCommand(String command) {
        String[] parts = command.split(" ", 3);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "/msg":
                if (parts.length < 3) {
                    sendMessage("Syntax: /msg <user> <message>");
                } else {
                    server.sendPrivateMessage(parts[1], parts[2], this);
                }
                break;
            case "/join":
                if (parts.length < 2) {
                    sendMessage("Syntax: /join <channel>");
                } else {
                    String oldChannel = this.channel;
                    String newChannel = parts[1];
                    server.checkAndAddChannel(newChannel);
                    this.channel = newChannel;

                    // Broadcast to ALL users (null sender) so everyone sees the event history
                    // server.broadcastToChannel(oldChannel, "LOG:" + userName + " has left " +
                    // oldChannel, null);
                    // server.broadcastToChannel(newChannel, "LOG:" + userName + " has joined " +
                    // newChannel, null);

                    server.broadcastUserList(oldChannel); // Remove from old
                    server.broadcastUserList(newChannel); // Add to new

                    // Send History of new channel
                    java.util.List<String> history = server.getDbService().getHistory(newChannel, 50);
                    for (String msg : history) {
                        writer.println("CHANMSG " + newChannel + " " + msg);
                    }
                }
                break;
            case "/time":
                sendMessage("Server time: " + java.time.LocalDateTime.now());
                break;
            case "/list":
                sendMessage("Users in " + channel + ": " + server.getUsersInChannel(channel));
                break;
            case "/weather":
                if (parts.length < 2) {
                    sendMessage("Syntax: /weather <city>");
                } else {
                    sendMessage("Fetching weather for " + parts[1] + "...");
                    sendMessage(WeatherService.getWeather(parts[1]));
                }
                break;
            case "/delete":
                if (parts.length < 2) {
                    sendMessage("Syntax: /delete <channel>");
                } else {
                    server.deleteChannel(parts[1]);
                }
                break;
            case "/rename":
                if (parts.length < 3) {
                    sendMessage("Syntax: /rename <oldName> <newName>");
                } else {
                    server.renameChannel(parts[1], parts[2]);
                }
                break;
            case "/friend":
                if (parts.length < 2) {
                    sendMessage("Syntax: /friend <request|accept|list> [user]");
                } else {
                    String subCmd = parts[1].toLowerCase();
                    if (subCmd.startsWith("request")) {
                        // /friend request <user>
                        // parts[1] might be "request user" or split further?
                        // command split logic in handleCommand is split(" ", 3).
                        // So parts[1] is the first arg, parts[2] is the rest.
                        // Wait, logic is: cmd = parts[0] ("/friend"),
                        // If I typed "/friend request Bob", parts[1] is "request Bob" ?? No.
                        // split(" ", 3) on "/friend request Bob" gives: ["/friend", "request", "Bob"]

                        // BUT if I typed "/friend list", parts is ["/friend", "list"] length 2.

                        if (parts.length < 3) { // Need target
                            sendMessage("Syntax: /friend request <username>");
                        } else {
                            String target = parts[2];
                            if (server.getDbService().requestFriend(this.userName, target)) {
                                sendMessage("Friend request sent to " + target);
                                server.sendFriendRequestNotification(target, this.userName);
                            } else {
                                sendMessage("Could not send request (already friends or pending, or error).");
                            }
                        }
                    } else if (subCmd.startsWith("accept")) {
                        if (parts.length < 3) {
                            sendMessage("Syntax: /friend accept <username>");
                        } else {
                            String target = parts[2];
                            if (server.getDbService().acceptFriend(this.userName, target)) {
                                sendMessage("You are now friends with " + target);
                                server.sendFriendAcceptNotification(target, this.userName);
                                // Refresh lists for both? The client needs to know.
                                // We can send FRIENDLIST update command to both
                                // Ideally we should have a method to resync friend list
                            } else {
                                sendMessage("Could not accept request (no pending request found).");
                            }
                        }
                    } else if (subCmd.startsWith("list")) {
                        java.util.List<String> friends = server.getDbService().getFriends(this.userName);
                        sendMessage("FRIENDLIST " + String.join(",", friends));
                    }
                }
                break;
            case "/privmsg":
                if (parts.length < 3) {
                    sendMessage("Syntax: /privmsg <user> <message>");
                } else {
                    server.sendPrivateMessage(parts[1], parts[2], this);
                }
                break;
            case "/privhistory":
                if (parts.length < 2) {
                    sendMessage("Syntax: /privhistory <user>");
                } else {
                    String target = parts[1];
                    java.util.List<String> history = server.getDbService().getPrivateHistory(this.userName, target, 50);
                    for (String msg : history) {
                        sendMessage("PRIVMSG " + target + " " + msg);
                    }
                }
                break;
            default:
                sendMessage("Unknown command: " + cmd);
                break;
        }
    }

    /**
     * Sends a list of online users to the newly connected user.
     */
    void printUsers() {
        if (server.hasUsers()) {
            writer.println("Connected users: " + server.getUserNames());
        } else {
            writer.println("No other users connected");
        }
    }

    /**
     * Sends a message to the client.
     */
    void sendMessage(String message) {
        writer.println(message);
    }

    public String getUserName() {
        return this.userName;
    }
}
