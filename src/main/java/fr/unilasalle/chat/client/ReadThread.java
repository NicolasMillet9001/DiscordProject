package fr.unilasalle.chat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;

public class ReadThread extends Thread {
    private BufferedReader reader;
    private Socket socket;
    private Client client;

    public ReadThread(Socket socket, Client client) {
        this.socket = socket;
        this.client = client;

        try {
            InputStream input = socket.getInputStream();
            reader = new BufferedReader(new InputStreamReader(input));
        } catch (IOException ex) {
            System.out.println("Error getting input stream: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void run() {
        while (true) {
            try {
                String response = reader.readLine();
                if (response == null) {
                    System.out.println("\nServer connection was closed");
                    break;
                }
                System.out.println("\n" + response); // Print new line for better formatting with prompt

                // Reprint the prompt (visual polish) check handled in WriteThread
                if (client.getUserName() != null) {
                    System.out.print("[" + client.getUserName() + "]: ");
                }

            } catch (IOException ex) {
                if (socket.isClosed()) {
                    // Socket closed by WriteThread on exit, expected behavior
                    break;
                }
                System.out.println("Error reading from server: " + ex.getMessage());
                ex.printStackTrace();
                break;
            }
        }
    }
}
