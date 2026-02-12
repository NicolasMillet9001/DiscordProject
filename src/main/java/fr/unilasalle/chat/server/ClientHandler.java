package fr.unilasalle.chat.server;


import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Base64;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;


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
    private String status = "online";
    private String statusMessage = "";
    private String avatar = null;

    public void setAvatar(String path) {
        this.avatar = path;
    }
    
    public String getAvatar() {
        return avatar;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String msg) {
        this.statusMessage = msg;
    }

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
                            this.avatar = server.getDbService().getAvatar(this.userName); // Load avatar
                            
                            server.broadcastAllUsers(); // Update global list now that name is set
                            System.out.println("DEBUG: triggering friend status update for " + this.userName);
                            server.broadcastFriendStatusUpdate(this.userName);
                            
                            // Send login success with avatar info?
                            writer.println("LOGIN_SUCCESS " + this.userName + " " + (this.avatar != null ? this.avatar : "default.png"));
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
                            writer.println("ALLUSERS " + String.join(",", server.getUserNames()));
                            server.sendFriendListUpdate(this.userName);

                            // Broadcast updated user list to all users in the channel
                            server.broadcastUserList(channel);
                            writer.println("CHANNELLIST " + server.getChannelList()); // Send initial channel list

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
                        String time = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                                .format(java.time.LocalDateTime.now());
                        serverMessage = "[" + time + "] [" + this.userName + "]: " + clientMessage;
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
                                server.sendFriendListUpdate(target);
                                server.sendFriendListUpdate(this.userName);
                            } else {
                                sendMessage("Could not accept request (no pending request found).");
                            }
                        }
                    } else if (subCmd.startsWith("deny")) {
                        if (parts.length < 3) {
                            sendMessage("Syntax: /friend deny <username>");
                        } else {
                            String target = parts[2];
                            if (server.getDbService().rejectRequest(this.userName, target)) {
                                sendMessage("You denied friend request from " + target);
                                // Optional: notify target they were denied? Usually silent or "Request denied"
                                // server.sendFriendDenyNotification(target, this.userName);
                            } else {
                                sendMessage("Could not deny request (no pending request found).");
                            }
                        }
                    } else if (subCmd.startsWith("list")) {
                        server.sendFriendListUpdate(this.userName);
                    }
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
            case "/search":
                if (parts.length < 2) {
                    sendMessage("Syntax: /search <query>");
                } else {
                    String query = parts[1];
                    // parts is split by " ", 3. If query has spaces, it might be in parts[2] too?
                    // Actually handleCommand uses split(" ", 3).
                    // /search query words
                    // parts[0]=/search, parts[1]=query, parts[2]=words
                    if (parts.length > 2) query += " " + parts[2];
                    
                    sendMessage("SEARCH_START " + query);
                    java.util.List<String> results = server.getDbService().searchMessages(query, this.userName);
                    for (String r : results) {
                        sendMessage("SEARCH_RESULT " + r);
                    }
                    sendMessage("SEARCH_END");
                }
                break;
            case "/privmsg":
                if (parts.length < 3) {
                    sendMessage("Syntax: /privmsg <user> <message>");
                } else {
                    server.sendPrivateMessage(parts[1], parts[2], this);
                }
                break;
            // case "/privhistory":
            //     if (parts.length < 2) {
            //         sendMessage("Syntax: /privhistory <user>");
            //     } else {
            //         String target = parts[1];
            //         java.util.List<String> history = server.getDbService().getPrivateHistory(this.userName, target, 50);
            //         for (String msg : history) {
            //             sendMessage("PRIVMSG " + target + " " + msg);
            //         }
            //     }
            //     break;
            case "/status":
                if (parts.length < 2) {
                    sendMessage("Syntax: /status <online|busy|away>");
                } else {
                    this.status = parts[1].toLowerCase();
                    sendMessage("Status set to " + this.status);
                    server.broadcastFriendStatusUpdate(this.userName);
                    server.broadcastUserList(this.channel);
                }
                break;
            case "/setmsg":
                if (parts.length < 2) {
                    this.statusMessage = "";
                } else {
                    // Sanitize: remove colons and commas to preserve protocol integrity
                    String msg = parts.length == 3 ? parts[1] + " " + parts[2] : parts[1]; // handle partial split?
                    // Actually parts is split by " ", 3. So parts[1] + parts[2] covers it roughly but logic in handleCommand is specific.
                    // Let's rely on full command string or reconstruction.
                    // handleCommand splits by " ", 3.
                    // if I type /setmsg Hello World
                    // parts[0]=/setmsg, parts[1]=Hello, parts[2]=World
                    // if I type /setmsg Hello
                    // parts[0]=/setmsg, parts[1]=Hello
                    
                    String raw = "";
                    if (parts.length >= 2) raw += parts[1];
                    if (parts.length >= 3) raw += " " + parts[2];
                    
                    this.statusMessage = raw.replace(",", " ").replace(":", " ");
                }
                server.broadcastFriendStatusUpdate(this.userName);
                server.broadcastUserList(this.channel);
                break;
            case "/upload":
                if (parts.length < 3) {
                    sendMessage("Syntax: /upload <filename> <base64_data>");
                } else {
                    String filename = parts[1];
                    String b64 = parts[2];
                    try {
                        byte[] data = Base64.getDecoder().decode(b64);
                        // Ensure transfer directory exists
                        File transferDir = new File("transfer");
                        if (!transferDir.exists()) transferDir.mkdir();
                        
                        // Create unique name
                        String uniqueName = System.currentTimeMillis() + "_" + filename;
                        Files.write(Paths.get("transfer", uniqueName), data, StandardOpenOption.CREATE);
                        
                        // Broadcast file link
                        // Format: FILE <uniqueID> <originalName>
                        String fileMsg = "FILE " + uniqueName + " " + filename;
                        server.broadcastToChannel(channel, fileMsg, this);
                        sendMessage("File uploaded successfully.");
                    } catch (IllegalArgumentException e) {
                         sendMessage("Error: Invalid Base64 data.");
                    } catch (IOException e) {
                         sendMessage("Error saving file: " + e.getMessage());
                    }
                }
                break;
            case "/download":
                if (parts.length < 2) {
                    sendMessage("Syntax: /download <file_id>");
                } else {
                    String fileId = parts[1];
                    // Security check: simple path traversal prevention
                    if (fileId.contains("..") || fileId.contains("/") || fileId.contains("\\")) {
                        sendMessage("Error: Invalid filename.");
                        return;
                    }
                    
                    File f = new File("transfer", fileId);
                    if (f.exists()) {
                        try {
                            byte[] data = Files.readAllBytes(f.toPath());
                            String b64 = Base64.getEncoder().encodeToString(data);
                            // Protocol: FILEDOWNLOAD <file_id> <base64>
                            // We need to send original name too? client might not know it if they just requested ID.
                            // But usually client clicked a link that had both ID and Name.
                            // Let's send just content for now, client saves as fileId (or we change protocol).
                            // Better: FILEDOWNLOAD <file_id> <base64>
                            sendMessage("FILEDOWNLOAD " + fileId + " " + b64);
                        } catch (IOException e) {
                            sendMessage("Error reading file: " + e.getMessage());
                        }
                    } else {
                        sendMessage("Error: File not found.");
                    }
                }
                break;
            case "/setavatar":
                if (parts.length < 2) {
                    sendMessage("Syntax: /setavatar <base64>");
                } else {
                    String b64 = parts[1];
                    try {
                         byte[] data = Base64.getDecoder().decode(b64);
                         // Save to avatars/username.png
                         String filename = this.userName + "_" + System.currentTimeMillis() + ".png";
                         File avatarDir = new File("avatars");
                         if (!avatarDir.exists()) avatarDir.mkdir();
                         
                         Files.write(Paths.get("avatars", filename), data, StandardOpenOption.CREATE);
                         
                         // Update DB
                         server.getDbService().updateAvatar(this.userName, filename);
                         this.avatar = filename;
                         
                         sendMessage("AVATAR_SET " + filename);
                         server.broadcastAvatarUpdate(this.userName); 
                    } catch (Exception e) {
                        sendMessage("Error setting avatar: " + e.getMessage());
                    }
                }
                break;
            case "/getavatar":
                if (parts.length < 2) {
                    sendMessage("Syntax: /getavatar <username>");
                } else {
                    String target = parts[1];
                    String av = server.getDbService().getAvatar(target);
                    if (av == null || av.trim().isEmpty()) av = "default.png";
                    
                    File f = new File("avatars", av);
                    if (!f.exists()) f = new File("avatars", "default.png");
                    
                    if (f.exists()) {
                        try {
                            byte[] dataBytes = Files.readAllBytes(f.toPath());
                            String b64 = Base64.getEncoder().encodeToString(dataBytes);
                            sendMessage("AVATAR_DATA " + target + " " + b64);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            case "/call":
                if (parts.length < 2) {
                    sendMessage("Syntax: /call <username>");
                } else {
                    String target = parts[1];
                    // Check if online
                    boolean found = false;
                    for (ClientHandler h : server.getUserThreads()) {
                        if (h.getUserName().equalsIgnoreCase(target)) {
                            h.sendMessage("CALL_INCOMING " + this.userName);
                            found = true;
                            break;
                        }
                    }
                    if (found) sendMessage("Calling " + target + "...");
                    else sendMessage("User " + target + " not found or offline.");
                }
                break;
            case "/accept":
                if (parts.length < 2) {
                     sendMessage("Syntax: /accept <username>");
                } else {
                    String caller = parts[1];
                    // Notify caller
                    boolean found = false;
                     for (ClientHandler h : server.getUserThreads()) {
                        if (h.getUserName().equalsIgnoreCase(caller)) {
                            h.sendMessage("CALL_ACCEPTED " + this.userName);
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        sendMessage("Call accepted. Connecting audio...");
                        // Register call in UDP server
                        server.getAudioServer().registerCall(this.userName, caller);
                        server.getVideoServer().registerCall(this.userName, caller);
                    }
                }
                break;
            case "/deny":
                if (parts.length < 2) {
                     sendMessage("Syntax: /deny <username>");
                } else {
                    String caller = parts[1];
                     for (ClientHandler h : server.getUserThreads()) {
                        if (h.getUserName().equalsIgnoreCase(caller)) {
                            h.sendMessage("CALL_DENIED " + this.userName);
                            break;
                        }
                    }
                }
                break;
            case "/hangup":
                // End current call
                String partner = server.getAudioServer().endCall(this.userName);
                server.getVideoServer().endCall(this.userName);
                
                sendMessage("Call ended.");
                
                if (partner != null) {
                    server.getVideoServer().endCall(partner); // Should be redundant if symmetric but cleans up
                    // Notify partner
                     for (ClientHandler h : server.getUserThreads()) {
                        if (h.getUserName().equalsIgnoreCase(partner)) {
                            h.sendMessage("HANGUP " + this.userName);
                            break;
                        }
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
