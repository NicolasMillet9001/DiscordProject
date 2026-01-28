package fr.unilasalle.chat.client.ui;

import fr.unilasalle.chat.client.Client;
import fr.unilasalle.chat.client.MessageListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ChatGUI extends JFrame implements MessageListener {
    private Client client;
    private JTextPane chatArea;
    private JTextField inputField;
    private DefaultListModel<String> userListModel;
    private DefaultListModel<String> channelListModel;
    private JList<String> channelList;

    // History storage
    private Map<String, StyledDocument> channelDocs = new HashMap<>();
    private String currentChannel = "general";

    private String username;

    public ChatGUI(String hostname, int port, String username) {
        this.username = username;
        System.out.println("Initializing ChatGUI Window...");
        setTitle("Discord (Java Edition) - " + username);
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Initialize default channel doc
        channelDocs.put("general", new DefaultStyledDocument());

        // Initialize Components
        createSidebar();
        createChatArea();
        createUserList();

        // Connect to Client
        System.out.println("Connecting to Server...");
        client = new Client(hostname, port, this);
        client.execute();

        System.out.println("Window set to visible.");
        setVisible(true);
    }

    private void createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(DiscordTheme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(200, 0));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(DiscordTheme.SIDEBAR);
        
        JLabel title = new JLabel("CHANNELS");
        title.setForeground(DiscordTheme.TEXT_MUTED);
        title.setBorder(new EmptyBorder(10, 10, 10, 10));
        titlePanel.add(title, BorderLayout.CENTER);

        JButton addBtn = new JButton("+");
        addBtn.setForeground(DiscordTheme.TEXT_MUTED);
        addBtn.setBackground(DiscordTheme.SIDEBAR);
        addBtn.setBorder(new EmptyBorder(10, 10, 10, 10));
        addBtn.setFocusPainted(false);
        addBtn.setContentAreaFilled(false);
        addBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        addBtn.addActionListener(e -> promptCreateChannel());
        titlePanel.add(addBtn, BorderLayout.EAST);
        
        sidebar.add(titlePanel, BorderLayout.NORTH);

        channelListModel = new DefaultListModel<>();
        // Channels will be populated by server

        channelList = new JList<>(channelListModel);
        channelList.setBackground(DiscordTheme.SIDEBAR);
        channelList.setForeground(DiscordTheme.TEXT_NORMAL);
        channelList.setSelectionBackground(DiscordTheme.ACTION_BG); // Assuming undefined, fixed below
        channelList.setSelectionForeground(Color.WHITE);
        channelList.setFont(new Font("SansSerif", Font.BOLD, 14));
        channelList.setFixedCellHeight(30);

        channelList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = channelList.getSelectedValue();
                if (selected != null) {
                    String cleanName = selected.substring(1); // Remove #
                    if (!cleanName.equals(currentChannel)) {
                        switchChannel(cleanName);
                    }
                }
            }
        });

        sidebar.add(channelList, BorderLayout.CENTER);
        add(sidebar, BorderLayout.WEST);
    }

    private void switchChannel(String newChannel) {
        client.sendMessage("/join " + newChannel);

        // Save current doc (already in map/model, just need to swap view)
        currentChannel = newChannel;

        StyledDocument doc = channelDocs.get(newChannel);
        if (doc == null) {
            doc = new DefaultStyledDocument();
            channelDocs.put(newChannel, doc);
        }

        chatArea.setDocument(doc);
        // appendToChat("Switched to #" + newChannel, Color.GRAY); // User requested to
        // hide this
    }

    private void promptCreateChannel() {
        String name = JOptionPane.showInputDialog(this, "Enter channel name:", "Create Channel", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            name = name.trim().replace("#", "").replace(" ", "_"); // Sanitize
            client.sendMessage("/join " + name);
        }
    }

    private void createChatArea() {
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(DiscordTheme.BACKGROUND);

        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(DiscordTheme.BACKGROUND);
        chatArea.setForeground(DiscordTheme.TEXT_NORMAL);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        chatArea.setDocument(channelDocs.get("general")); // Set initial doc

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(DiscordTheme.BACKGROUND);
        inputPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        inputField = new JTextField();
        inputField.setBackground(DiscordTheme.INPUT_BG);
        inputField.setForeground(DiscordTheme.TEXT_NORMAL);
        inputField.setCaretColor(Color.WHITE);
        inputField.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        inputField.addActionListener(e -> sendMessage());

        inputPanel.add(inputField, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        add(chatPanel, BorderLayout.CENTER);
    }

    private void createUserList() {
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBackground(DiscordTheme.SIDEBAR);
        userPanel.setPreferredSize(new Dimension(200, 0));

        JLabel title = new JLabel("MEMBERS");
        title.setForeground(DiscordTheme.TEXT_MUTED);
        title.setBorder(new EmptyBorder(10, 10, 10, 10));
        userPanel.add(title, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(userListModel);
        userList.setBackground(DiscordTheme.SIDEBAR);
        userList.setForeground(DiscordTheme.TEXT_NORMAL);
        userList.setFont(new Font("SansSerif", Font.PLAIN, 14));

        userPanel.add(userList, BorderLayout.CENTER);
        add(userPanel, BorderLayout.EAST);
    }

    private void sendMessage() {
        String text = inputField.getText();
        if (!text.isEmpty()) {
            client.sendMessage(text);
            // Optimistic rendering: Show my own message immediately
            // But don't show commands like /join as chat messages
            if (!text.startsWith("/")) {
                appendToChat("[" + username + "]: " + text, getUniqueColor(username));
            }
            inputField.setText("");
        }
    }

    // Helper to append colored text
    private void appendToChat(String msg, Color c) {
        StyledDocument doc = chatArea.getStyledDocument();

        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, c);

        // Add Timestamp
        String time = "[" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                + "] ";
        try {
            SimpleAttributeSet timeStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(timeStyle, DiscordTheme.TEXT_MUTED);
            StyleConstants.setFontSize(timeStyle, 12);
            doc.insertString(doc.getLength(), time, timeStyle);
        } catch (Exception e) {
        }

        try {
            // Check for Username pattern: [User]: Message
            if (msg.startsWith("[") && msg.contains("]: ")) {
                int splitIndex = msg.indexOf("]: ") + 3;
                String userPart = msg.substring(0, splitIndex);
                String contentPart = msg.substring(splitIndex);

                // Parse username from "[Name]: " to gen color
                String msgUser = userPart.substring(1, userPart.length() - 3);

                // Color for Username
                StyleConstants.setForeground(style, getUniqueColor(msgUser));
                StyleConstants.setBold(style, true);
                doc.insertString(doc.getLength(), userPart, style);

                // Color for Message Body (Normal Text)
                StyleConstants.setForeground(style, DiscordTheme.TEXT_NORMAL);
                StyleConstants.setBold(style, false);
                doc.insertString(doc.getLength(), contentPart + "\n", style);
            } else {
                // Normal system message or other
                doc.insertString(doc.getLength(), msg + "\n", style);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Color getUniqueColor(String name) {
        int hash = name.hashCode();
        // Generate bright pastel colors
        return Color.getHSBColor((Math.abs(hash) % 360) / 360f, 0.7f, 1.0f);
    }

    @Override
    public void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> {
            // Handle automatic login handshake
            if (message.trim().equalsIgnoreCase("Enter your username:")) {
                System.out.println("Server asked for username, sending: " + username);
                client.sendMessage(username);
                return;
            }

            // Handle User List updates
            if (message.startsWith("USERLIST " + currentChannel + " ")) {
                String users = message.substring(("USERLIST " + currentChannel + " ").length());
                userListModel.clear();
                for (String u : users.split(",")) {
                    if (!u.isEmpty())
                        userListModel.addElement(u);
                }
                return;
                // Ignore lists for other channels
                return;
            }
            
            // Handle Channel List updates
            if (message.startsWith("CHANNELLIST ")) {
                String channels = message.substring("CHANNELLIST ".length());
                channelListModel.clear();
                for (String c : channels.split(",")) {
                    if (!c.isEmpty())
                        channelListModel.addElement("#" + c);
                }
                
                // Restore selection
                if (channelList != null && currentChannel != null) {
                    int index = channelListModel.indexOf("#" + currentChannel);
                    if (index != -1) {
                         channelList.setSelectedIndex(index);
                    }
                }
                return;
            }

            // Handle Channel Messages (CHANMSG <channel> <content>)
            if (message.startsWith("CHANMSG ")) {
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String targetChannel = parts[1];
                    String content = parts[2];

                    // Fix: Check for USERLIST inside CHANMSG
                    if (content.startsWith("USERLIST ")) {
                        // Only update UI if it's for the current channel
                        if (targetChannel.equals(currentChannel)) {
                            String users = content.substring("USERLIST ".length()); // "general user1,user2" ? No,
                                                                                    // content is "USERLIST general
                                                                                    // user1,user2" presumably?
                            // Wait, Server sends: "USERLIST " + channel + " " + users
                            // So content is "USERLIST channel users..."
                            // Let's parse strictly.
                            String prefix = "USERLIST " + targetChannel + " ";
                            if (content.startsWith(prefix)) {
                                String userString = content.substring(prefix.length());
                                userListModel.clear();
                                for (String u : userString.split(",")) {
                                    if (!u.isEmpty())
                                        userListModel.addElement(u);
                                }
                            }
                        }
                        return; // Do NOT show in chat
                    }

                    // Ensure doc exists
                    if (!channelDocs.containsKey(targetChannel)) {
                        channelDocs.put(targetChannel, new DefaultStyledDocument());
                    }

                    appendMessageToDoc(targetChannel, content);
                }
                return;
            }

            // Global System Messages (LOG:)
            if (message.startsWith("LOG:")) {
                String logContent = message.substring(4);
                if (logContent.startsWith("You are in channel:"))
                    return;
                if (logContent.startsWith("You joined channel:"))
                    return;
                appendToChat(logContent, DiscordTheme.TEXT_MUTED);
                return;
            }

            // Other global messages
            appendToChat(message, DiscordTheme.TEXT_NORMAL);
        });
    }

    // Refactored helper to append to SPECIFIC channel document
    private void appendMessageToDoc(String channel, String msg) {
        StyledDocument doc = channelDocs.get(channel);
        if (doc == null)
            return; // Should created above

        SimpleAttributeSet style = new SimpleAttributeSet();

        // Add Timestamp
        String time = "[" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                + "] ";
        try {
            SimpleAttributeSet timeStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(timeStyle, DiscordTheme.TEXT_MUTED);
            StyleConstants.setFontSize(timeStyle, 12);
            doc.insertString(doc.getLength(), time, timeStyle);
        } catch (Exception e) {
        }

        // Check for internal LOG inside CHANMSG (e.g. joins/leaves in that channel)
        if (msg.startsWith("LOG:")) {
            String logContent = msg.substring(4);
            StyleConstants.setForeground(style, DiscordTheme.TEXT_MUTED);
            try {
                doc.insertString(doc.getLength(), logContent + "\n", style);
            } catch (Exception e) {
            }
            return;
        }

        try {
            if (msg.startsWith("[") && msg.contains("]: ")) {
                int splitIndex = msg.indexOf("]: ") + 3;
                String userPart = msg.substring(0, splitIndex);
                String contentPart = msg.substring(splitIndex);

                String msgUser = userPart.substring(1, userPart.length() - 3);

                StyleConstants.setForeground(style, getUniqueColor(msgUser));
                StyleConstants.setBold(style, true);
                doc.insertString(doc.getLength(), userPart, style);

                StyleConstants.setForeground(style, DiscordTheme.TEXT_NORMAL);
                StyleConstants.setBold(style, false);
                doc.insertString(doc.getLength(), contentPart + "\n", style);
            } else {
                StyleConstants.setForeground(style, DiscordTheme.TEXT_NORMAL);
                doc.insertString(doc.getLength(), msg + "\n", style);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("Launching ChatGUI...");
        SwingUtilities.invokeLater(() -> {
            try {
                // Show Login Dialog
                LoginDialog loginDlg = new LoginDialog(null);
                loginDlg.setVisible(true);

                if (loginDlg.isSucceeded()) {
                    String username = loginDlg.getUsername();
                    String host = loginDlg.getIP();
                    int port = loginDlg.getPort();

                    System.out.println("Creating GUI for " + username + " on " + host + ":" + port);
                    new ChatGUI(host, port, username);
                } else {
                    System.out.println("Login cancelled.");
                    System.exit(0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
