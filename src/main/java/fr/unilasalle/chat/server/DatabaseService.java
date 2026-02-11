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
            initFriendTables();
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
                    // Parse to dd/MM/yy HH:mm:ss format
                    if (ts.contains(" ")) {
                        String[] parts = ts.split(" ");
                        String datePart = parts[0]; // YYYY-MM-DD
                        String timePortion = parts[1]; // HH:MM:SS
                        
                        // Parse date
                        String[] dateComponents = datePart.split("-");
                        if (dateComponents.length == 3) {
                            String year = dateComponents[0].substring(2); // Get last 2 digits of year
                            String month = dateComponents[1];
                            String day = dateComponents[2];
                            
                            // Remove milliseconds from time if any
                            if (timePortion.contains("."))
                                timePortion = timePortion.split("\\.")[0];
                            
                            timePart = day + "/" + month + "/" + year + " " + timePortion;
                        }
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
    // --- Friends System ---

    private void createFriendsTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS friends (" +
                "user1 TEXT, " +
                "user2 TEXT, " +
                "status TEXT, " + // PENDING, ACCEPTED
                "PRIMARY KEY (user1, user2)" +
                ");";
        try (Connection conn = connect();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void createPrivateMessageTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS private_messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT, " +
                "receiver TEXT, " +
                "content TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";
        try (Connection conn = connect();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void initFriendTables() {
        try {
            createFriendsTable();
            createPrivateMessageTable();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Returns true if request sent, false if already exists
    public boolean requestFriend(String requester, String target) {
        // Check if exists in either direction
        if (areFriendsOrPending(requester, target))
            return false;

        String sql = "INSERT INTO friends(user1, user2, status) VALUES(?, ?, 'PENDING')";
        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, requester);
            pstmt.setString(2, target);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Error requesting friend: " + e.getMessage());
            return false;
        }
    }

    public boolean acceptFriend(String requester, String target) {
        // The requester here is the one ACCEPTING the request (so they are user2 in DB
        // usually, or just update)
        // Check if there is a pending request from target to requester
        String sql = "UPDATE friends SET status = 'ACCEPTED' WHERE user1 = ? AND user2 = ? AND status = 'PENDING'";
        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, target); // The original requester
            pstmt.setString(2, requester); // The one accepting
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.out.println("Error accepting friend: " + e.getMessage());
            return false;
        }
    }

    public boolean areFriendsOrPending(String u1, String u2) {
        String sql = "SELECT 1 FROM friends WHERE (user1 = ? AND user2 = ?) OR (user1 = ? AND user2 = ?)";
        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, u1);
            pstmt.setString(2, u2);
            pstmt.setString(3, u2);
            pstmt.setString(4, u1);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    public java.util.List<String> getFriends(String user) {
        java.util.List<String> friends = new java.util.ArrayList<>();
        String sql = "SELECT user1, user2 FROM friends WHERE (user1 = ? OR user2 = ?) AND status = 'ACCEPTED'";
        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user);
            pstmt.setString(2, user);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String u1 = rs.getString("user1");
                String u2 = rs.getString("user2");
                if (u1.equals(user))
                    friends.add(u2);
                else
                    friends.add(u1);
            }
        } catch (SQLException e) {
            System.out.println("Error getting friends: " + e.getMessage());
        }
        return friends;
    }

    public java.util.List<String> getPendingRequests(String user) {
        java.util.List<String> requests = new java.util.ArrayList<>();
        String sql = "SELECT user1 FROM friends WHERE user2 = ? AND status = 'PENDING'";
        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                requests.add(rs.getString("user1"));
            }
        } catch (SQLException e) {
            System.out.println("Error getting requests: " + e.getMessage());
        }
        return requests;
    }

    // --- Private Messaging ---

    public void savePrivateMessage(String sender, String receiver, String content) {
        String sql = "INSERT INTO private_messages(sender, receiver, content) VALUES(?, ?, ?)";
        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, content);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error saving private message: " + e.getMessage());
        }
    }

    public java.util.List<String> getPrivateHistory(String user1, String user2, int limit) {
        java.util.List<String> history = new java.util.ArrayList<>();
        // Select messages between u1 and u2 (either direction)
        String sql = "SELECT sender, content, datetime(timestamp, 'localtime') as timestamp FROM private_messages " +
                "WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) " +
                "ORDER BY id DESC LIMIT ?";

        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2);
            pstmt.setString(4, user1);
            pstmt.setInt(5, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String sender = rs.getString("sender");
                String msg = rs.getString("content");
                String ts = rs.getString("timestamp");

                String timePart = ts;
                try {
                    if (ts.contains(" ")) {
                        timePart = ts.split(" ")[1];
                        if (timePart.contains("."))
                            timePart = timePart.split("\\.")[0];
                    }
                } catch (Exception e) {
                }

                history.add("HISTORY:[" + timePart + "] [" + sender + "]: " + msg);
            }
        } catch (SQLException e) {
            System.out.println("Error loading pm history: " + e.getMessage());
        }
        java.util.Collections.reverse(history);
        return history;
    }
    public java.util.List<String> searchMessages(String query, String username) {
        java.util.List<String> results = new java.util.ArrayList<>();
        String sqlPublic = "SELECT channel, username, content, datetime(timestamp, 'localtime') as ts FROM messages WHERE content LIKE ?";
        String sqlPrivate = "SELECT sender, receiver, content, datetime(timestamp, 'localtime') as ts FROM private_messages WHERE (sender = ? OR receiver = ?) AND content LIKE ?";
        
        try (Connection conn = connect()) {
            // Public
            try (PreparedStatement pstmt = conn.prepareStatement(sqlPublic)) {
                pstmt.setString(1, "%" + query + "%");
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add("CHANNEL:" + rs.getString("channel") + ":" + rs.getString("username") + ":" + rs.getString("ts") + ":" + rs.getString("content"));
                }
            }
            
            // Private
            try (PreparedStatement pstmt = conn.prepareStatement(sqlPrivate)) {
                pstmt.setString(1, username);
                pstmt.setString(2, username);
                pstmt.setString(3, "%" + query + "%");
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String other = rs.getString("sender").equals(username) ? rs.getString("receiver") : rs.getString("sender");
                    results.add("PRIVATE:" + other + ":" + rs.getString("sender") + ":" + rs.getString("ts") + ":" + rs.getString("content"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }
}
