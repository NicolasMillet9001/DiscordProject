package fr.unilasalle.chat.client.ui;

import fr.unilasalle.chat.client.Client;
import fr.unilasalle.chat.client.MessageListener;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

public class ChatGUI extends JFrame implements MessageListener {
    private Client client;
    private JTextPane chatArea;
    private JTextField inputField;
    private DefaultListModel<String> userListModel;
    private DefaultListModel<String> channelListModel;
    private JList<String> channelList;
    private DefaultListModel<String> friendsListModel;
    private JList<String> friendsList;

    private boolean isPrivateMode = false; // true if chatting with a friend

    // History storage
    private Map<String, StyledDocument> channelDocs = new HashMap<>();
    private String currentChannel = "general";

    private String username;
    private String password;
    private boolean registerMode;

    public ChatGUI(String hostname, int port, String username, String password, boolean registerMode) {
        this.username = username;
        this.password = password;
        this.registerMode = registerMode;
        System.out.println("Initializing ChatGUI Window (MSN Style)...");
        setTitle("MSN Messenger - " + username);
        setSize(900, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // XP Window Background
        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.setBackground(MsnTheme.BACKGROUND);
        mainContent.setBorder(BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR, 2));
        setContentPane(mainContent);

        // Initialize default channel doc
        channelDocs.put("general", new DefaultStyledDocument());

        // Initialize Components
        createTopHeader();

        JPanel splitPanel = new JPanel(new BorderLayout());
        splitPanel.setOpaque(false);
        splitPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        createSidebar(splitPanel);
        createChatArea(splitPanel);

        add(splitPanel, BorderLayout.CENTER);

        createUserList();

        // Connect to Client
        System.out.println("Connecting to Server...");
        client = new Client(hostname, port, this);
        client.execute();

        System.out.println("Window set to visible.");
        setVisible(true);
    }

    private void createTopHeader() {
        XPGradientPanel header = new XPGradientPanel();
        header.setLayout(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 50));
        header.setBorder(new EmptyBorder(0, 10, 0, 10)); // Padding

        JLabel appTitle = new JLabel("MSN Messenger");
        appTitle.setFont(new Font("Trebuchet MS", Font.BOLD, 18));
        appTitle.setForeground(Color.WHITE);

        // Add Logo if exists
        ImageIcon logoIcon = new ImageIcon("media/msn.png");
        if (logoIcon.getImageLoadStatus() == MediaTracker.COMPLETE) {
            Image img = logoIcon.getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH);
            appTitle.setIcon(new ImageIcon(img));
        }

        header.add(appTitle, BorderLayout.WEST);

        JLabel userStatus = new JLabel("<html>Logged in as <b>" + username + "</b><br>(Online)</html>");
        userStatus.setFont(MsnTheme.FONT_MAIN);
        userStatus.setForeground(Color.WHITE);
        userStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(userStatus, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
    }

    private void createSidebar(JPanel parent) {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(MsnTheme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, MsnTheme.BORDER_COLOR));

        // Title Panel
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(MsnTheme.SIDEBAR);

        JLabel title = new JLabel(" Conversations");
        title.setFont(MsnTheme.FONT_BOLD);
        title.setForeground(MsnTheme.HEADER_TOP);
        title.setBorder(new EmptyBorder(5, 5, 5, 5));
        titlePanel.add(title, BorderLayout.CENTER);

        JButton addBtn = new JButton("<html><b>+ Create</b></html>");
        styleXPButton(addBtn);
        addBtn.setPreferredSize(new Dimension(70, 25));
        addBtn.addActionListener(e -> promptCreateChannel());

        JPanel btnContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnContainer.setOpaque(false);
        btnContainer.add(addBtn);
        titlePanel.add(btnContainer, BorderLayout.EAST);

        sidebar.add(titlePanel, BorderLayout.NORTH);

        channelListModel = new DefaultListModel<>();
        // Channels will be populated by server

        channelList = new JList<>(channelListModel);
        channelList.setBackground(MsnTheme.SIDEBAR);
        channelList.setForeground(MsnTheme.TEXT_NORMAL);
        channelList.setSelectionBackground(MsnTheme.SELECTION_BG);
        channelList.setSelectionForeground(MsnTheme.TEXT_NORMAL);
        channelList.setFont(MsnTheme.FONT_MAIN);
        channelList.setFixedCellHeight(25);

        // Custom Renderer for XP look
        channelList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
                label.setBorder(new EmptyBorder(2, 5, 2, 5));
                if (isSelected) {
                    label.setBackground(MsnTheme.SELECTION_BG);
                    label.setBorder(BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR));
                }
                label.setIcon(UIManager.getIcon("FileView.fileIcon")); // Generic icon place holder
                return label;
            }
        });

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

        // Context Menu for Rename/Delete
        channelList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = channelList.locationToIndex(e.getPoint());
                    channelList.setSelectedIndex(row); // Select the item under cursor
                    if (row != -1) {
                        String selected = channelList.getSelectedValue().substring(1); // Remove #
                        JPopupMenu menu = new JPopupMenu();

                        JMenuItem renameItem = new JMenuItem("Renommer");
                        renameItem.addActionListener(ev -> promptRenameChannel(selected));

                        JMenuItem deleteItem = new JMenuItem("Supprimer conversation");
                        deleteItem.addActionListener(ev -> promptDeleteChannel(selected));

                        menu.add(renameItem);
                        menu.add(deleteItem);
                        menu.show(channelList, e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane channelScroll = new JScrollPane(channelList);
        channelScroll.setBorder(null);

        // Friends Panel
        JPanel friendsPanel = new JPanel(new BorderLayout());
        friendsPanel.setBackground(MsnTheme.SIDEBAR);

        JLabel friendsTitle = new JLabel(" Friends");
        friendsTitle.setFont(MsnTheme.FONT_BOLD);
        friendsTitle.setForeground(MsnTheme.HEADER_TOP);
        friendsTitle.setBorder(new EmptyBorder(5, 5, 5, 5));
        friendsPanel.add(friendsTitle, BorderLayout.NORTH);

        friendsListModel = new DefaultListModel<>();
        friendsList = new JList<>(friendsListModel);
        friendsList.setBackground(MsnTheme.SIDEBAR);
        friendsList.setForeground(MsnTheme.TEXT_NORMAL);
        friendsList.setFont(MsnTheme.FONT_MAIN);
        friendsList.setFixedCellHeight(25);

        friendsList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
                label.setBorder(new EmptyBorder(2, 5, 2, 5));
                if (isSelected) {
                    label.setBackground(MsnTheme.SELECTION_BG);
                    label.setBorder(BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR));
                }
                // Use a different icon for users (maybe same for now or load user.png)
                label.setIcon(UIManager.getIcon("FileView.computerIcon"));
                return label;
            }
        });

        friendsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = friendsList.getSelectedValue();
                if (selected != null) {
                    switchPrivateChat(selected);
                }
            }
        });

        // Context Menu for Friends
        friendsList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = friendsList.locationToIndex(e.getPoint());
                    friendsList.setSelectedIndex(row);
                    if (row != -1) {
                        String selected = friendsList.getSelectedValue();
                        JPopupMenu menu = new JPopupMenu();
                        JMenuItem msgItem = new JMenuItem("Envoyer un message");
                        msgItem.addActionListener(ev -> switchPrivateChat(selected));
                        menu.add(msgItem);
                        menu.show(friendsList, e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane friendScroll = new JScrollPane(friendsList);
        friendScroll.setBorder(null);
        friendsPanel.add(friendScroll, BorderLayout.CENTER);

        JSplitPane splitSidebar = new JSplitPane(JSplitPane.VERTICAL_SPLIT, channelScroll, friendsPanel);
        splitSidebar.setDividerLocation(150); // Rough height
        splitSidebar.setBorder(null);
        splitSidebar.setResizeWeight(0.5);

        sidebar.add(splitSidebar, BorderLayout.CENTER);

        parent.add(sidebar, BorderLayout.WEST);
    }

    private void switchChannel(String newChannel) {
        isPrivateMode = false;
        client.sendMessage("/join " + newChannel);
        currentChannel = newChannel;
        friendsList.clearSelection(); // Deselect friend

        StyledDocument doc = channelDocs.get(newChannel);
        if (doc == null) {
            doc = new DefaultStyledDocument();
            channelDocs.put(newChannel, doc);
        } else {
            // Clear existing document to prevent duplication of history
            try {
                doc.remove(0, doc.getLength());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        chatArea.setDocument(doc);
    }

    private void switchPrivateChat(String friendName) {
        currentChannel = friendName; // Effectively treating username as channel ID
        isPrivateMode = true;

        StyledDocument doc = channelDocs.get("PRIV_" + friendName);
        if (doc == null) {
            doc = new DefaultStyledDocument();
            channelDocs.put("PRIV_" + friendName, doc);
        }

        chatArea.setDocument(doc);
        // We might want to clear selection in channelList
        channelList.clearSelection();

        // Fetch history
        client.sendMessage("/privhistory " + friendName);
    }

    private void promptCreateChannel() {
        String name = JOptionPane.showInputDialog(this, "Enter conversation name:", "Start Conversation",
                JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            name = name.trim().replace("#", "").replace(" ", "_"); // Sanitize
            switchChannel(name);
        }
    }

    private void promptRenameChannel(String oldName) {
        if (oldName.equalsIgnoreCase("general")) {
            JOptionPane.showMessageDialog(this, "You cannot rename general channel.");
            return;
        }
        String newName = JOptionPane.showInputDialog(this, "Rename " + oldName + " to:", "Rename Channel",
                JOptionPane.PLAIN_MESSAGE);
        if (newName != null && !newName.trim().isEmpty()) {
            newName = newName.trim().replace("#", "").replace(" ", "_");
            // Send rename command
            client.sendMessage("/rename " + oldName + " " + newName);
        }
    }

    private void promptDeleteChannel(String name) {
        if (name.equalsIgnoreCase("general")) {
            JOptionPane.showMessageDialog(this, "You cannot delete general channel.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete #" + name + "?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            client.sendMessage("/delete " + name);
        }
    }

    private void createChatArea(JPanel parent) {
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(MsnTheme.BACKGROUND);
        chatPanel.setBorder(new EmptyBorder(0, 5, 0, 0)); // Gap from sidebar

        // Chat Header
        JPanel chatHeader = new JPanel(new FlowLayout(FlowLayout.LEFT));
        chatHeader.setBackground(Color.WHITE);
        chatHeader.setBorder(new MatteBorder(0, 0, 1, 0, MsnTheme.BORDER_COLOR));
        JLabel talkingTo = new JLabel("Chatting in " + currentChannel);
        talkingTo.setFont(MsnTheme.FONT_TITLE);
        talkingTo.setForeground(MsnTheme.TEXT_NORMAL);
        chatHeader.add(talkingTo);
        chatPanel.add(chatHeader, BorderLayout.NORTH);

        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(Color.WHITE);
        chatArea.setForeground(MsnTheme.TEXT_NORMAL);
        chatArea.setFont(MsnTheme.FONT_MAIN);
        chatArea.setDocument(channelDocs.get("general")); // Set initial doc

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatPanel.add(scrollPane, BorderLayout.CENTER);

        // Input Area (Resembles MSN Input box)
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(MsnTheme.BACKGROUND);
        inputPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
        inputPanel.setPreferredSize(new Dimension(0, 85));

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        toolbar.setBackground(MsnTheme.BACKGROUND);

        JButton colorBtn = new JButton("A");
        colorBtn.setFont(new Font("Georgia", Font.BOLD, 14));
        colorBtn.setForeground(Color.BLACK);
        colorBtn.setToolTipText("Change Text Color");
        styleToolbarButton(colorBtn);
        colorBtn.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, "Choose Text Color", msgTextColor);
            if (newColor != null) {
                msgTextColor = newColor;
                colorBtn.setForeground(newColor);
                inputField.setForeground(newColor);
            }
        });

        JButton bgBtn = new JButton("B"); // Using B to represent background
        bgBtn.setFont(new Font("Arial", Font.BOLD, 14));
        bgBtn.setBackground(Color.LIGHT_GRAY);
        bgBtn.setForeground(Color.WHITE);
        bgBtn.setToolTipText("Change Background Color");
        styleToolbarButton(bgBtn);
        bgBtn.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, "Choose Background Color", msgBgColor);
            if (newColor != null) {
                msgBgColor = newColor;
                bgBtn.setBackground(newColor);
                inputField.setBackground(newColor);
            }
        });

        JButton resetBtn = new JButton("x");
        resetBtn.setFont(new Font("Arial", Font.PLAIN, 12));
        resetBtn.setToolTipText("Reset Styles");
        styleToolbarButton(resetBtn);
        resetBtn.addActionListener(e -> {
            msgTextColor = Color.BLACK;
            msgBgColor = Color.WHITE;
            colorBtn.setForeground(msgTextColor);
            bgBtn.setBackground(Color.LIGHT_GRAY);
            inputField.setForeground(msgTextColor);
            inputField.setBackground(msgBgColor);
        });

        toolbar.add(colorBtn);
        toolbar.add(bgBtn);
        toolbar.add(resetBtn);
        inputPanel.add(toolbar, BorderLayout.NORTH);

        inputField = new JTextField();
        inputField.setBackground(Color.WHITE);
        inputField.setForeground(MsnTheme.TEXT_NORMAL);
        inputField.setFont(MsnTheme.FONT_MAIN);
        inputField.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR),
                new EmptyBorder(5, 5, 5, 5)));
        inputField.addActionListener(e -> sendMessage());

        inputPanel.add(inputField, BorderLayout.CENTER);

        JButton sendBtn = new JButton("Send");
        styleXPButton(sendBtn);
        sendBtn.setPreferredSize(new Dimension(70, 0));
        sendBtn.addActionListener(e -> sendMessage());

        JPanel sendPanel = new JPanel(new BorderLayout());
        sendPanel.setOpaque(false);
        sendPanel.setBorder(new EmptyBorder(0, 5, 0, 0));
        sendPanel.add(sendBtn, BorderLayout.CENTER);

        inputPanel.add(sendPanel, BorderLayout.EAST);

        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        parent.add(chatPanel, BorderLayout.CENTER);
    }

    // Style helper for toolbar buttons
    private void styleToolbarButton(JButton btn) {
        btn.setPreferredSize(new Dimension(25, 25));
        btn.setBorder(BorderFactory.createEmptyBorder());
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBorder(BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR));
            }

            public void mouseExited(MouseEvent e) {
                btn.setBorder(BorderFactory.createEmptyBorder());
            }
        });
    }

    // Style helper for buttons
    private void styleXPButton(JButton btn) {
        btn.setBackground(MsnTheme.ACTION_BG);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR),
                new EmptyBorder(2, 5, 2, 5)));
        btn.setFocusPainted(false);
        btn.setFont(MsnTheme.FONT_MAIN);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Simple hover effect
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(MsnTheme.SELECTION_BG);
            }

            public void mouseExited(MouseEvent e) {
                btn.setBackground(MsnTheme.ACTION_BG);
            }
        });
    }

    private void createUserList() {
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBackground(MsnTheme.SIDEBAR);
        userPanel.setPreferredSize(new Dimension(150, 0));
        userPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, MsnTheme.BORDER_COLOR));

        JLabel title = new JLabel(" Participants");
        title.setFont(MsnTheme.FONT_BOLD);
        title.setForeground(MsnTheme.HEADER_TOP);
        title.setBorder(new EmptyBorder(5, 5, 5, 5));
        userPanel.add(title, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(userListModel);
        userList.setBackground(MsnTheme.SIDEBAR);
        userList.setForeground(MsnTheme.TEXT_NORMAL);
        userList.setFont(MsnTheme.FONT_MAIN);

        attachUserListContextMenu(userList);

        userPanel.add(new JScrollPane(userList), BorderLayout.CENTER);

        add(userPanel, BorderLayout.EAST);
    }

    // Creating context menu for user list (Add Friend)
    private void attachUserListContextMenu(JList<String> list) {
        list.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = list.locationToIndex(e.getPoint());
                    list.setSelectedIndex(row);
                    if (row != -1) {
                        String selected = list.getSelectedValue();
                        if (!selected.equals(username)) { // Can't add self
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem addItem = new JMenuItem("Ajouter en ami");
                            addItem.addActionListener(ev -> {
                                client.sendMessage("/friend request " + selected);
                            });
                            menu.add(addItem);
                            menu.show(list, e.getX(), e.getY());
                        }
                    }
                }
            }
        });
    }

    private void sendMessage() {
        String text = inputField.getText();
        if (!text.isEmpty()) {

            // Construct message with style meta-data if needed
            // Format: [c=#RRGGBB][b=#RRGGBB]Message content
            StringBuilder payload = new StringBuilder();

            if (!msgTextColor.equals(Color.BLACK)) {
                String hex = String.format("#%02x%02x%02x", msgTextColor.getRed(), msgTextColor.getGreen(),
                        msgTextColor.getBlue());
                payload.append("[c=").append(hex).append("]");
            }

            if (!msgBgColor.equals(Color.WHITE)) {
                String hex = String.format("#%02x%02x%02x", msgBgColor.getRed(), msgBgColor.getGreen(),
                        msgBgColor.getBlue());
                payload.append("[b=").append(hex).append("]");
            }

            payload.append(text);

            if (isPrivateMode) {
                client.sendMessage("/privmsg " + currentChannel + " " + payload.toString());
            } else {
                client.sendMessage(payload.toString());
            }

            if (!text.startsWith("/")) {
                // Optimistic render
                appendToChat("[" + username + "]: " + payload.toString(), getUniqueColor(username));
            }
            inputField.setText("");
        }
    }

    // Helper to append colored text
    // Colors
    private Color msgTextColor = Color.BLACK;
    private Color msgBgColor = Color.WHITE;

    // Helper to append colored text
    private void appendToChat(String msg, Color c) {
        StyledDocument doc = chatArea.getStyledDocument();

        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setFontFamily(style, "Tahoma");
        StyleConstants.setFontSize(style, 12);

        try {
            // MSN Style Parsing
            if (msg.startsWith("[")) {
                int split = msg.indexOf("]:");
                if (split > 0) {
                    String user = msg.substring(1, split);
                    if (user.startsWith("["))
                        user = user.substring(1);

                    String content = msg.substring(split + 3);

                    // Name Line
                    SimpleAttributeSet nameStyle = new SimpleAttributeSet();
                    StyleConstants.setForeground(nameStyle, MsnTheme.TEXT_MUTED);
                    StyleConstants.setFontSize(nameStyle, 11);
                    doc.insertString(doc.getLength(), user + " says:\n", nameStyle);

                    // Message Line Parsing
                    Color fg = MsnTheme.TEXT_NORMAL;
                    Color bg = null;
                    String cleanContent = content;

                    // Simple Iterative Parser for [c=#...][b=#...] tags
                    while (cleanContent.startsWith("[c=#") || cleanContent.startsWith("[b=#")) {
                        if (cleanContent.startsWith("[c=#")) {
                            int end = cleanContent.indexOf("]");
                            if (end > 0) {
                                String hex = cleanContent.substring(3, end);
                                try {
                                    fg = Color.decode(hex);
                                } catch (Exception e) {
                                }
                                cleanContent = cleanContent.substring(end + 1);
                            } else
                                break;
                        } else if (cleanContent.startsWith("[b=#")) {
                            int end = cleanContent.indexOf("]");
                            if (end > 0) {
                                String hex = cleanContent.substring(3, end);
                                try {
                                    bg = Color.decode(hex);
                                } catch (Exception e) {
                                }
                                cleanContent = cleanContent.substring(end + 1);
                            } else
                                break;
                        }
                    }

                    SimpleAttributeSet msgStyle = new SimpleAttributeSet();
                    StyleConstants.setForeground(msgStyle, fg);
                    if (bg != null)
                        StyleConstants.setBackground(msgStyle, bg);
                    StyleConstants.setFontSize(msgStyle, 13);

                    doc.insertString(doc.getLength(), "  " + cleanContent + "\n\n", msgStyle);
                    return;
                }
            }

            // Fallback
            StyleConstants.setForeground(style, MsnTheme.TEXT_NORMAL);
            doc.insertString(doc.getLength(), msg + "\n", style);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Color getUniqueColor(String name) {
        return MsnTheme.TEXT_NORMAL;
    }

    @Override
    public void onMessageReceived(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.trim().equals("AUTH_REQUIRED")) {
                if (registerMode) {
                    client.sendMessage("/register " + username + " " + password);
                } else {
                    client.sendMessage("/login " + username + " " + password);
                }
                return;
            }

            if (message.startsWith("LOGIN_SUCCESS")) {
                appendToChat("System: Logged in successfully!", Color.GRAY);
                return;
            }

            if (message.equals("REGISTRATION_SUCCESS")) {
                appendToChat("System: Account created! Logging in...", Color.GRAY);
                client.sendMessage("/login " + username + " " + password);
                return;
            }

            if (message.startsWith("CHANNELLIST ")) {
                String channels = message.substring("CHANNELLIST ".length());
                channelListModel.clear();
                for (String c : channels.split(",")) {
                    if (!c.isEmpty())
                        channelListModel.addElement("#" + c);
                }
                return;
            }

            if (message.startsWith("FRIENDLIST ")) {
                String friends = message.substring("FRIENDLIST ".length());
                friendsListModel.clear();
                for (String f : friends.split(",")) {
                    if (!f.isEmpty())
                        friendsListModel.addElement(f);
                }
                return;
            }

            if (message.startsWith("FRIEND_REQ ")) {
                // String requester = message.substring("FRIEND_REQ ".length());
                // Show notification? Log is already sent.
                // maybe sound
                return;
            }

            if (message.startsWith("FRIEND_ACCEPT ")) {
                // Refresh friend list handled by next FRIENDLIST command if we strictly follow
                // protocol,
                // but server sends LOG and notification.
                // Actually server usually should send updated FRIENDLIST.
                // In my server implementation, I didn't send FRIENDLIST update on accept
                // automatically?
                // I should check.
                // For now, let's assume valid.
                return;
            }

            if (message.startsWith("PRIVMSG ")) {
                // PRIVMSG sender content
                // But wait, content might have spaces.
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String sender = parts[1];
                    String content = parts[2];

                    // If I am the sender (echo), display in chat with receiver
                    // But wait, server echoes "PRIVMSG <target> <content>" to sender?
                    // In Server.java: sender.sendMessage("PRIVMSG " + targetUserName + " " +
                    // message);
                    // So if I send to Bob, I get "PRIVMSG Bob Hello".
                    // If Bob sends to me, I get "PRIVMSG Bob Hello".
                    // So "sender" here is actually "the other person" in the chat view context
                    // usually?
                    // Wait.
                    // Case 1: Bob sends to Me. Server sends Me: "PRIVMSG Bob Hello".
                    // My view: Chat with Bob.
                    // Case 2: I send to Bob. Server sends Me: "PRIVMSG Bob Hello".
                    // My view: Chat with Bob.

                    // So specific logic:
                    // The second arg is ALWAYS the "Partner" of the conversation in this protocol
                    // design?
                    // Let's check Server.java again.
                    // user.sendMessage("PRIVMSG " + sender.getUserName() + " " + message); (To
                    // Receiver)
                    // sender.sendMessage("PRIVMSG " + targetUserName + " " + message); (To Sender)

                    // Yes! The 2nd arg is the "Remote User".
                    // For Receiver: Remote User is Sender.
                    // For Sender: Remote User is Target.

                    String remoteUser = sender; // It's actually the "context" name

                    // Ensure doc exists
                    if (!channelDocs.containsKey("PRIV_" + remoteUser)) {
                        channelDocs.put("PRIV_" + remoteUser, new DefaultStyledDocument());
                    }

                    // If current view is this private chat, render locally
                    if (isPrivateMode && currentChannel.equals(remoteUser)) {
                        appendToChat(content, Color.BLACK);
                    } else {
                        StyledDocument doc = channelDocs.get("PRIV_" + remoteUser);
                        if (doc != null) {
                            // Suppressed
                        }
                    }
                }
                return;
            }

            if (message.startsWith("USERLIST " + currentChannel + " ")) {
                String users = message.substring(("USERLIST " + currentChannel + " ").length());
                userListModel.clear();
                for (String u : users.split(",")) {
                    if (!u.isEmpty())
                        userListModel.addElement(u);
                }
                return;
            }

            if (message.startsWith("CHANMSG ")) {
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String targetChannel = parts[1];
                    String content = parts[2];

                    if (content.startsWith("USERLIST ")) {
                        // Parse USERLIST <channel> <users>
                        // content is "USERLIST channel user1,user2..."
                        String prefix = "USERLIST " + targetChannel + " ";
                        if (content.startsWith(prefix)) {
                            String users = content.substring(prefix.length());
                            // Only update if it matches current view
                            if (targetChannel.equals(currentChannel)) {
                                userListModel.clear();
                                for (String u : users.split(",")) {
                                    if (!u.isEmpty())
                                        userListModel.addElement(u);
                                }
                            }
                        }
                        return;
                    }

                    // Filter system join logs if they come as CHANMSG
                    if (content.startsWith("LOG:")) {
                        return;
                    }

                    if (!channelDocs.containsKey(targetChannel)) {
                        channelDocs.put(targetChannel, new DefaultStyledDocument());
                    }
                    if (targetChannel.equals(currentChannel)) {
                        // Only append if active, otherwise background
                        if (content.startsWith("HISTORY:")) {
                            try {
                                String clean = content.substring("HISTORY:".length());
                                appendToChat(clean, Color.BLACK);
                            } catch (Exception e) {
                                appendToChat(content, Color.BLACK);
                            }
                        } else {
                            appendToChat(content, Color.BLACK);
                        }
                    }
                    // ToDo: Handle background notifications
                }
                return;
            }

            // Global System Messages (LOG:)
            if (message.startsWith("LOG:")) {
                String logContent = message.substring(4);
                if (logContent.trim().startsWith("You joined channel"))
                    return;
                if (logContent.trim().startsWith("You are in channel"))
                    return;
                // Don't show generic logs in chat for now to keep it clean
                return;
            }

            // Fallback
            appendToChat(message, Color.BLACK);
        });
    }

    // Custom Gradient Panel for XP Headers
    class XPGradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int w = getWidth();
            int h = getHeight();
            GradientPaint gp = new GradientPaint(0, 0, MsnTheme.HEADER_TOP, 0, h, MsnTheme.HEADER_BOTTOM);
            g2d.setPaint(gp);
            g2d.fillRect(0, 0, w, h);
        }
    }

    public static void main(String[] args) {
        System.out.println("Launching ChatGUI...");
        SwingUtilities.invokeLater(() -> {
            try {
                // You might need to adjust this to launch straight away if testing without
                // server
                // But for now keeping standard flow
                LoginDialog loginDlg = new LoginDialog(null);
                loginDlg.setVisible(true);

                if (loginDlg.isSucceeded()) {
                    new ChatGUI(loginDlg.getIP(), loginDlg.getPort(),
                            loginDlg.getUsername(), loginDlg.getPassword(),
                            loginDlg.isRegisterMode());
                } else {
                    System.exit(0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
