package fr.unilasalle.chat.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseService {

    private static final String URL = "jdbc:sqlite:users.db";

    public DatabaseService() {
        try {
            // Load driver manually just in case
            Class.forName("org.sqlite.JDBC");
            createTable();
            createMessageTable();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                "username TEXT PRIMARY KEY, " +
                "password TEXT NOT NULL" +
                ");";

        try (Connection conn = connect();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public synchronized boolean register(String username, String password) {
        String sql = "INSERT INTO users(username, password) VALUES(?, ?)";
        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Registration failed (User likely exists): " + e.getMessage());
            return false;
        }
    }

    public boolean authenticate(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String storedPwd = rs.getString("password");
                return storedPwd.equals(password);
            }
        } catch (SQLException e) {
            System.out.println("Auth error: " + e.getMessage());
        }
        return false;
    }

    public boolean userExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // --- Message Persistence ---

    private void createMessageTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "channel TEXT, " +
                "username TEXT, " +
                "content TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";
        try (Connection conn = connect();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void saveMessage(String channel, String username, String content) {
        String sql = "INSERT INTO messages(channel, username, content) VALUES(?, ?, ?)";
        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, channel);
            pstmt.setString(2, username);
            pstmt.setString(3, content);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error saving message: " + e.getMessage());
        }
    }

    public java.util.List<String> getHistory(String channel, int limit) {
        java.util.List<String> history = new java.util.ArrayList<>();
        String sql = "SELECT username, content, datetime(timestamp, 'localtime') as timestamp FROM messages WHERE channel = ? ORDER BY id DESC LIMIT ?";

        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, channel);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();

            // Result is reverse order (DESC), we want chronological for chat
            while (rs.next()) {
                String user = rs.getString("username");
                String msg = rs.getString("content");
                // Timestamp format from SQLite is usually YYYY-MM-DD HH:MM:SS
                // We just want HH:mm:ss ideally, or just raw string
                String ts = rs.getString("timestamp");

                // Format: "HISTORY:[<Time>] [<User>]: <Msg>"
                // We will implement a HISTORY protocol handling in client or just send as text
                // Let's format it nicely here.
                // Assuming SQLite returns "YYYY-MM-DD HH:MM:SS"
                String timePart = ts;
                try {
                    // Quick parse to just get HH:mm:ss if possible
                    if (ts.contains(" ")) {
                        timePart = ts.split(" ")[1]; // Get HH:mm:ss
                        // Remove milliseconds if any
                        if (timePart.contains("."))
                            timePart = timePart.split("\\.")[0];
                    }
                } catch (Exception e) {
                }

                history.add("HISTORY:[" + timePart + "] [" + user + "]: " + msg);
            }
        } catch (SQLException e) {
            System.out.println("Error loading history: " + e.getMessage());
        }

        // Reverse back to chronological
        java.util.Collections.reverse(history);
        return history;
    }
}
