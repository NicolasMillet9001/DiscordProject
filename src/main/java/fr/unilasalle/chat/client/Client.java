package fr.unilasalle.chat.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {
    private String hostname;
    private int port;
    private String userName;
    private MessageListener listener;
    private Socket socket;
    private ReadThread readThread;
    private PrintWriter writer;

    public Client(String hostname, int port, MessageListener listener) {
        this.hostname = hostname;
        this.port = port;
        this.listener = listener;
    }

    public void execute() {
        try {
            socket = new Socket(hostname, port);
            if (listener != null)
                listener.onMessageReceived("Connected to the chat server");

            // Handle reading in a separate thread that notifies the listener
            readThread = new ReadThread(socket, this);
            readThread.start();

            // Output stream for sending messages
            writer = new PrintWriter(socket.getOutputStream(), true);

        } catch (UnknownHostException ex) {
            if (listener != null)
                listener.onMessageReceived("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            if (listener != null)
                listener.onMessageReceived("I/O Error: " + ex.getMessage());
        }
    }

    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return this.userName;
    }

    public MessageListener getListener() {
        return listener;
    }

    public static void main(String[] args) {
        // Console mode legacy support or removed?
        // Let's keep it simple: Main entry point will be GUI or Console based on args?
        // For now, this Main is for Console.
        if (args.length < 2) {
            System.out.println("Syntax: java Client <hostname> <port>");
            return;
        }

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);

        // Simple console listener
        MessageListener consoleListener = message -> System.out.println(message);

        Client client = new Client(hostname, port, consoleListener);
        client.execute();

        // Block main thread to keep running for console input (simulated)
        // usage of WriteThread is now slightly different, we need to adapt it.
        new WriteThread(client.socket, client).start();
    }
}
