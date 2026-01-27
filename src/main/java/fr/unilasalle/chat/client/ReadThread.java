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
                    if (client.getListener() != null)
                        client.getListener().onMessageReceived("Server connection was closed");
                    break;
                }

                if (client.getListener() != null) {
                    client.getListener().onMessageReceived(response);
                }

                // Reprint the prompt (visual polish) check handled in WriteThread
                // Prompt handling is tricky with GUI vs Console.
                // For GUI, we don't need to reprint prompt.
                // For Console, we might lose this feature or move it to ConsoleListener.
                // Let's rely on simple message passing for now.

            } catch (IOException ex) {
                if (socket.isClosed()) {
                    break;
                }
                if (client.getListener() != null)
                    client.getListener().onMessageReceived("Error reading from server: " + ex.getMessage());
                break;
            }
        }
    }
}
