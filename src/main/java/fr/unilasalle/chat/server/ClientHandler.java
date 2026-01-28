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
            while (true) {
                writer.println("Enter your username:");
                userName = reader.readLine();
                if (userName == null) {
                    return;
                }

                if (server.addUserName(userName)) {
                    this.userName = userName;
                    writer.println("Welcome " + userName);
                    writer.println("You are in channel: " + channel);
                    server.broadcastUserList(channel); // Update list for everyone in default channel
                    break;
                } else {
                    writer.println("Error: Username taken or invalid. Try again.");
                }
            }

            String serverMessage = "LOG:New user connected: " + userName;
            server.broadcast(serverMessage, this);

            String clientMessage;

            do {
                clientMessage = reader.readLine();
                if (clientMessage != null) {
                    if (clientMessage.startsWith("/")) {
                        handleCommand(clientMessage);
                    } else {
                        serverMessage = "[" + userName + "]: " + clientMessage;
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
                    this.channel = newChannel;

                    // Broadcast to ALL users (null sender) so everyone sees the event history
                    server.broadcastToChannel(oldChannel, "LOG:" + userName + " has left " + oldChannel, null);
                    server.broadcastToChannel(newChannel, "LOG:" + userName + " has joined " + newChannel, null);

                    server.broadcastUserList(oldChannel); // Remove from old
                    server.broadcastUserList(newChannel); // Add to new
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
