package fr.unilasalle.chat.client.ui;

import fr.unilasalle.chat.client.Client;
import fr.unilasalle.chat.client.MessageListener;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class ChatGUI extends JFrame implements MessageListener {
    private Client client;
    private JTextPane chatArea;
    private JTextField inputField;
    private DefaultListModel<String> userListModel;
    private DefaultListModel<String> channelListModel;
    private JList<String> channelList;
    private DefaultListModel<Object> friendsListModel;
    private JList<Object> friendsList;
    private JList<String> userList;
    private Set<String> channelUsers = new HashSet<>();

    private boolean isPrivateMode = false; // true if chatting with a friend

    // History storage
    private Map<String, StyledDocument> channelDocs = new HashMap<>();
    private String currentChannel = "general";

    private String username;
    private String password;
    private boolean registerMode;
    private HTMLEditorKit kit; // Helper for inserting HTML

    private static class Friend {
        String name;
        String status; // "online", "offline", "pending"

        Friend(String name, String status) {
            this.name = name;
            this.status = status;
        }
    }

    private SoundManager soundManager;

    public ChatGUI(String hostname, int port, String username, String password, boolean registerMode) {
        this.username = username;
        this.password = password;
        this.registerMode = registerMode;
        System.out.println("Initializing ChatGUI Window (MSN Style HTML)...");
        setTitle("MSN Messenger - " + username);
        setSize(900, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // XP Window Background
        JPanel mainContent = new JPanel(new BorderLayout());
        mainContent.setBackground(MsnTheme.BACKGROUND);
        mainContent.setBorder(BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR, 2));
        setContentPane(mainContent);

        // Initialize Sound Manager
        soundManager = new SoundManager();

        // Initialize Kit
        kit = new HTMLEditorKit();
        // Set basic font via CSS
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: Tahoma; font-size: 12px; margin: 0; padding: 0; }");
        styleSheet.addRule(".msg-block { margin-bottom: 5px; padding: 5px; }");
        styleSheet.addRule(".header { color: #777777; font-size: 10px; margin-bottom: 2px; }");
        styleSheet.addRule(".content { font-size: 13px; margin-left: 10px; }");

        // Initialize default channel doc
        channelDocs.put("general", (HTMLDocument) kit.createDefaultDocument());

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

        // Global Mouse Listener for Click Sounds
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == MouseEvent.MOUSE_PRESSED) {
                soundManager.playClick();
            }
        }, AWTEvent.MOUSE_EVENT_MASK);

        System.out.println("Window set to visible.");
        setVisible(true);
    }

    // ... [Header creation]
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

        JLabel userStatus = new JLabel("<html>Connecté(e) en tant que <b>" + username + "</b><br>(En ligne)</html>");
        userStatus.setFont(MsnTheme.FONT_MAIN);
        userStatus.setForeground(Color.WHITE);
        userStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(userStatus, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
    }

    private void processPrivateMessage(String remoteUser, String formattedContent) {
        // Ensure doc exists
        if (!channelDocs.containsKey("PRIV_" + remoteUser)) {
            channelDocs.put("PRIV_" + remoteUser, (StyledDocument) kit.createDefaultDocument());
        }

        // If current view is this private chat, render locally
        if (isPrivateMode && currentChannel.equals(remoteUser)) {
            // Calling appendToChat handles scrolling and parsing/rendering
            appendToChat(formattedContent, Color.BLACK);
        } else {
            // Append to background doc
            StyledDocument doc = channelDocs.get("PRIV_" + remoteUser);
            if (doc instanceof HTMLDocument) {
                appendMessageToDoc((HTMLDocument) doc, formattedContent);
            }
        }
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

        JButton addBtn = new WindowsXPButton("<html><b>Créer</b></html>");
        addBtn.setPreferredSize(new Dimension(70, 25));
        addBtn.addActionListener(e -> promptCreateChannel());

        JPanel btnContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnContainer.setOpaque(false);
        btnContainer.add(addBtn);
        titlePanel.add(btnContainer, BorderLayout.EAST);

        sidebar.add(titlePanel, BorderLayout.NORTH);

        channelListModel = new DefaultListModel<>();

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
                label.setIcon(UIManager.getIcon("FileView.fileIcon"));
                return label;
            }
        });

        channelList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = channelList.getSelectedValue();
                if (selected != null) {
                    String cleanName = selected.substring(1);
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

        JLabel friendsTitle = new JLabel(" Amis");
        friendsTitle.setFont(MsnTheme.FONT_BOLD);
        friendsTitle.setForeground(MsnTheme.HEADER_TOP);
        friendsTitle.setBorder(new EmptyBorder(5, 5, 5, 5));
        friendsPanel.add(friendsTitle, BorderLayout.NORTH);

        friendsListModel = new DefaultListModel<>();
        friendsList = new JList<>(friendsListModel);
        friendsList.setBackground(MsnTheme.SIDEBAR); // e.g. white or light gradient
        friendsList.setForeground(MsnTheme.TEXT_NORMAL);
        friendsList.setFont(MsnTheme.FONT_MAIN);
        friendsList.setFixedCellHeight(25);

        friendsList.setCellRenderer(new FriendListRenderer());

        friendsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Object selected = friendsList.getSelectedValue();
                if (selected instanceof Friend) {
                    switchPrivateChat(((Friend) selected).name);
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
                        Object val = friendsListModel.getElementAt(row);
                        if (val instanceof Friend) {
                            Friend f = (Friend) val;
                            JPopupMenu menu = new JPopupMenu();

                            if ("pending".equals(f.status)) {
                                JMenuItem acceptItem = new JMenuItem("Accepter");
                                acceptItem.addActionListener(ev -> client.sendMessage("/friend accept " + f.name));
                                menu.add(acceptItem);

                                JMenuItem denyItem = new JMenuItem("Refuser");
                                denyItem.addActionListener(ev -> client.sendMessage("/friend deny " + f.name));
                                menu.add(denyItem);
                            } else {
                                JMenuItem msgItem = new JMenuItem("Envoyer un message");
                                msgItem.addActionListener(ev -> switchPrivateChat(f.name));
                                menu.add(msgItem);

                                // Optional: Remove friend
                                // JMenuItem removeItem = new JMenuItem("Supprimer");
                                // menu.add(removeItem);
                            }
                            menu.show(friendsList, e.getX(), e.getY());
                        }
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
        client.sendMessage("/join " + newChannel);
        currentChannel = newChannel;
        friendsList.clearSelection(); // Deselect friend

        // Clear channel users locally while waiting for server update
        channelUsers.clear();
        if (userList != null)
            userList.repaint();

        StyledDocument doc = channelDocs.get(newChannel);
        if (doc == null) {
            doc = (StyledDocument) kit.createDefaultDocument();
            channelDocs.put(newChannel, doc);
        }
        chatArea.setDocument(doc);
        scrollToBottom();
    }

    private String getPrivateRoomName(String u1, String u2) {
        if (u1.compareTo(u2) < 0) {
            return "!PRIVATE_" + u1 + "_" + u2;
        } else {
            return "!PRIVATE_" + u2 + "_" + u1;
        }
    }

    private void switchPrivateChat(String friendName) {
        currentChannel = friendName; // Effectively treating username as channel ID
        isPrivateMode = true;

        // Join the hidden presence room
        String roomName = getPrivateRoomName(username, friendName);
        client.sendMessage("/join " + roomName);

        // Highlight only this friend
        channelUsers.clear();
        // channelUsers.add(friendName); // Waiting for server update instead
        if (userList != null)
            userList.repaint();

        StyledDocument doc = channelDocs.get("PRIV_" + friendName);
        if (doc == null) {
            doc = (StyledDocument) kit.createDefaultDocument();
            channelDocs.put("PRIV_" + friendName, doc);
            // Fetch history only on first load
            client.sendMessage("/privhistory " + friendName);
        }

        chatArea.setDocument(doc);
        scrollToBottom();
        // We might want to clear selection in channelList
        channelList.clearSelection();
    }

    private void promptCreateChannel() {
        String name = JOptionPane.showInputDialog(this, "Nom de la conversation :", "Nouvelle conversation",
                JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            name = name.trim().replace("#", "").replace(" ", "_"); // Sanitize
            switchChannel(name);
        }
    }

    private void promptRenameChannel(String oldName) {
        if (oldName.equalsIgnoreCase("general")) {
            JOptionPane.showMessageDialog(this, "Vous ne pouvez pas renommer le canal général.");
            return;
        }
        String newName = JOptionPane.showInputDialog(this, "Renommer " + oldName + " en :", "Renommer la conversation",
                JOptionPane.PLAIN_MESSAGE);
        if (newName != null && !newName.trim().isEmpty()) {
            newName = newName.trim().replace("#", "").replace(" ", "_");
            // Send rename command
            client.sendMessage("/rename " + oldName + " " + newName);
        }
    }

    private void promptDeleteChannel(String name) {
        if (name.equalsIgnoreCase("general")) {
            JOptionPane.showMessageDialog(this, "Vous ne pouvez pas supprimer le canal général.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Êtes-vous sûr de vouloir supprimer la conversation #" + name + " ?",
                "Confirmer la suppression", JOptionPane.YES_NO_OPTION);
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
        JLabel talkingTo = new JLabel("Discussion dans " + currentChannel);
        talkingTo.setFont(MsnTheme.FONT_TITLE);
        talkingTo.setForeground(MsnTheme.TEXT_NORMAL);
        chatHeader.add(talkingTo);
        chatPanel.add(chatHeader, BorderLayout.NORTH);

        chatArea = new JTextPane();
        chatArea.setEditorKit(kit); // Use HTML Kit
        // chatArea.setContentType("text/html"); // Handled by setEditorKit
        chatArea.setEditable(false);
        chatArea.setBackground(Color.WHITE);
        // Force font style
        chatArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        chatArea.setFont(MsnTheme.FONT_MAIN);

        chatArea.setDocument(channelDocs.get("general")); // Set initial doc

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatPanel.add(scrollPane, BorderLayout.CENTER);

        // Input Area
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
        colorBtn.setToolTipText("Choisir la couleur du texte");
        styleToolbarButton(colorBtn);
        colorBtn.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, "Choisir la couleur du texte", msgTextColor);
            if (newColor != null) {
                msgTextColor = newColor;
                colorBtn.setForeground(newColor);
                inputField.setForeground(newColor);
            }
        });

        JButton bgBtn = new JButton("B");
        bgBtn.setFont(new Font("Arial", Font.BOLD, 14));
        bgBtn.setBackground(Color.LIGHT_GRAY);
        bgBtn.setForeground(Color.WHITE);
        bgBtn.setToolTipText("Choisir la couleur de fond");
        styleToolbarButton(bgBtn);
        bgBtn.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, "Choisir la couleur de fond", msgBgColor);
            if (newColor != null) {
                msgBgColor = newColor;
                bgBtn.setBackground(newColor);
                inputField.setBackground(newColor);
            }
        });

        JButton resetBtn = new JButton("x");
        resetBtn.setFont(new Font("Arial", Font.PLAIN, 12));
        resetBtn.setToolTipText("Réinitialiser le style");
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
        inputField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                int code = e.getKeyCode();
                // Ignore modifiers (Shift, Ctrl, Alt) and Function/Action keys (F1-F12, Arrows)
                if (code == java.awt.event.KeyEvent.VK_SHIFT ||
                        code == java.awt.event.KeyEvent.VK_CONTROL ||
                        code == java.awt.event.KeyEvent.VK_ALT ||
                        code == java.awt.event.KeyEvent.VK_ALT_GRAPH ||
                        code == java.awt.event.KeyEvent.VK_META ||
                        code == java.awt.event.KeyEvent.VK_CAPS_LOCK ||
                        code == java.awt.event.KeyEvent.VK_ENTER ||
                        e.isActionKey()) {
                    return;
                }
                soundManager.playKey(code == java.awt.event.KeyEvent.VK_SPACE);
            }
        });

        inputPanel.add(inputField, BorderLayout.CENTER);

        JButton sendBtn = new WindowsXPButton("Envoyer");
        sendBtn.setPreferredSize(new Dimension(90, 0));
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
        userList = new JList<>(userListModel);
        userList.setCellRenderer(new UserListRenderer());
        userList.setBackground(MsnTheme.SIDEBAR);
        userList.setForeground(MsnTheme.TEXT_NORMAL);
        userList.setFont(MsnTheme.FONT_MAIN);

        attachUserListContextMenu(userList);

        userPanel.add(new JScrollPane(userList), BorderLayout.CENTER);

        add(userPanel, BorderLayout.EAST);
    }

    private class UserListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String) {
                String user = (String) value;
                if (channelUsers.contains(user)) {
                    setForeground(new Color(0, 180, 0)); // Green for same channel
                    setFont(MsnTheme.FONT_BOLD);
                } else {
                    setForeground(MsnTheme.TEXT_NORMAL);
                    setFont(MsnTheme.FONT_MAIN);
                }
            }
            return c;
        }
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

                            JMenuItem msgItem = new JMenuItem("Envoyer un message");
                            msgItem.addActionListener(ev -> switchPrivateChat(selected));
                            menu.add(msgItem);

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

            soundManager.playMessageSent();

            if (!text.startsWith("/")) {
                // appendToChat("[" + username + "]: " + payload.toString(),
                // getUniqueColor(username));
            }
            inputField.setText("");
        }
    }

    // Helper to append colored text
    private Color msgTextColor = Color.BLACK;
    private Color msgBgColor = Color.WHITE;

    // Helper to append colored text using HTML
    private void appendToChat(String msg, Color c) {
        StyledDocument doc = chatArea.getStyledDocument();
        if (doc instanceof HTMLDocument) {
            appendMessageToDoc((HTMLDocument) doc, msg);
            scrollToBottom();
        }
        return;
        /*
         * HTMLDocument doc = (HTMLDocument) chatArea.getDocument();
         * // Ensure we handle current channel doc correctly if we are in background?
         * // Actually chatArea.getDocument() is current channel.
         * // If msg is for current channel we use chatArea, else we get from map.
         * // But for simplicity in this method provided, we assume it's for current doc
         * or
         * // we fetch from map?
         * // The original logic fetched 'doc = chatArea.getStyledDocument()'.
         * // Logic in onMessageReceived handles 'if target==current append else...'
         * // So we strictly append to 'doc' which is current.
         * 
         * try {
         * StringBuilder html = new StringBuilder();
         * 
         * // Handle private messages specially
         * if (msg.startsWith("[Private from ") || msg.startsWith("[Private to ")) {
         * int split = msg.indexOf("]:");
         * if (split > 0) {
         * String header = msg.substring(1, split); // "Private from User" or
         * "Private to User"
         * String content = msg.substring(split + 3);
         * 
         * String style =
         * "background-color:#f0e6ff; border-left: 3px solid #800080; padding: 5px;";
         * // Right alignment for own messages (assuming username variable holds current
         * // user)
         * boolean isMe = header.contains(username);
         * if (isMe) {
         * style += " margin-left: 50px; text-align: right;";
         * } else {
         * style += " margin-right: 50px;";
         * }
         * 
         * html.append("<div class='msg-block' style='" + style + "'>");
         * html.append("<div class='header' style='color:#800080; font-weight:bold;'>").
         * append(header)
         * .append(":</div>");
         * html.append("<div class='content' style='font-style:italic;'>").append(
         * content).append("</div>");
         * html.append("</div>");
         * 
         * kit.insertHTML(doc, doc.getLength(), html.toString(), 0, 0, null);
         * scrollToBottom();
         * return;
         * }
         * }
         * 
         * if (msg.startsWith("[")) {
         * // Check if this is a history message with timestamp: [dd/MM/yy HH:MM:SS]
         * // [user]: message
         * 
         * // Check if msg is already fully formatted with date (History check 1)
         * if
         * (msg.matches("^\\[\\d{2}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}\\] \\[.+?\\]: .+"
         * )) {
         * // Extract timestamp, user, and content
         * int firstClose = msg.indexOf("]");
         * String timestamp = msg.substring(1, firstClose); // dd/MM/yy HH:MM:SS
         * 
         * int secondOpen = msg.indexOf("[", firstClose);
         * int secondClose = msg.indexOf("]:", secondOpen);
         * String user = msg.substring(secondOpen + 1, secondClose);
         * String content = msg.substring(secondClose + 3);
         * 
         * // Parser for color codes
         * String fgHex = "#000000";
         * String bgHex = null;
         * String cleanContent = content;
         * 
         * while (cleanContent.startsWith("[c=#") || cleanContent.startsWith("[b=#")) {
         * if (cleanContent.startsWith("[c=#")) {
         * int end = cleanContent.indexOf("]");
         * if (end > 0) {
         * fgHex = cleanContent.substring(3, end);
         * cleanContent = cleanContent.substring(end + 1);
         * } else
         * break;
         * } else if (cleanContent.startsWith("[b=#")) {
         * int end = cleanContent.indexOf("]");
         * if (end > 0) {
         * bgHex = cleanContent.substring(3, end);
         * cleanContent = cleanContent.substring(end + 1);
         * } else
         * break;
         * }
         * }
         * 
         * String divStyle = "class='msg-block'";
         * String styleAttr = "color:" + fgHex + ";";
         * if (bgHex != null) {
         * styleAttr += " background-color:" + bgHex + ";";
         * }
         * 
         * // Check for own message
         * boolean isMe = user.equalsIgnoreCase(username);
         * if (isMe) {
         * styleAttr += " text-align: right; margin-left: 50px;";
         * } else {
         * styleAttr += " margin-right: 50px;";
         * }
         * 
         * html.append("<div ").append(divStyle).append(" style='").append(styleAttr).
         * append("'>");
         * html.append("<div class='header' style='color:#999;'>").append(timestamp).
         * append(" - ").append(user)
         * .append(":</div>");
         * html.append("<div class='content'>").append(cleanContent).append("</div>");
         * html.append("</div>");
         * 
         * kit.insertHTML(doc, doc.getLength(), html.toString(), 0, 0, null);
         * scrollToBottom();
         * return;
         * }
         * 
         * // Check for History format variant: [HH:mm:ss] [User]: ...
         * if (msg.matches("^\\[\\d{2}:\\d{2}:\\d{2}\\] \\[.+?\\]: .+")) {
         * // Extract timestamp (HH:mm:ss only), user, content
         * int firstClose = msg.indexOf("]");
         * String timePart = msg.substring(1, firstClose); // HH:mm:ss
         * // We might want to prepend today's date or just use it as is?
         * // Let's use it as the timestamp text.
         * 
         * int secondOpen = msg.indexOf("[", firstClose);
         * int secondClose = msg.indexOf("]:", secondOpen);
         * String user = msg.substring(secondOpen + 1, secondClose);
         * String content = msg.substring(secondClose + 3);
         * 
         * // Assume same color parsing logic as above?
         * // Or simplify. Copy-paste logic for styling:
         * String fgHex = "#000000";
         * String bgHex = null;
         * String cleanContent = content;
         * 
         * while (cleanContent.startsWith("[c=#") || cleanContent.startsWith("[b=#")) {
         * if (cleanContent.startsWith("[c=#")) {
         * int end = cleanContent.indexOf("]");
         * if (end > 0) {
         * fgHex = cleanContent.substring(3, end);
         * cleanContent = cleanContent.substring(end + 1);
         * } else
         * break;
         * } else if (cleanContent.startsWith("[b=#")) {
         * int end = cleanContent.indexOf("]");
         * if (end > 0) {
         * bgHex = cleanContent.substring(3, end);
         * cleanContent = cleanContent.substring(end + 1);
         * } else
         * break;
         * }
         * }
         * 
         * String divStyle = "class='msg-block'";
         * String styleAttr = "color:" + fgHex + ";";
         * if (bgHex != null) {
         * styleAttr += " background-color:" + bgHex + ";";
         * }
         * 
         * boolean isMe = user.equalsIgnoreCase(username);
         * if (isMe) {
         * styleAttr += " text-align: right; margin-left: 50px;";
         * } else {
         * styleAttr += " margin-right: 50px;";
         * }
         * 
         * html.append("<div ").append(divStyle).append(" style='").append(styleAttr).
         * append("'>");
         * html.append("<div class='header' style='color:#999;'>").append(timePart).
         * append(" - ").append(user)
         * .append(":</div>");
         * html.append("<div class='content'>").append(cleanContent).append("</div>");
         * html.append("</div>");
         * 
         * kit.insertHTML(doc, doc.getLength(), html.toString(), 0, 0, null);
         * scrollToBottom();
         * return;
         * }
         * 
         * // Regular message format: [user]: message
         * int split = msg.indexOf("]:");
         * if (split > 0) {
         * String user = msg.substring(1, split);
         * if (user.startsWith("["))
         * user = user.substring(1);
         * String content = msg.substring(split + 3);
         * 
         * // Generate current timestamp
         * DateTimeFormatter formatter =
         * DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss");
         * String timestamp = LocalDateTime.now().format(formatter);
         * 
         * // Parser
         * String fgHex = "#000000";
         * String bgHex = null;
         * String cleanContent = content;
         * 
         * while (cleanContent.startsWith("[c=#") || cleanContent.startsWith("[b=#")) {
         * if (cleanContent.startsWith("[c=#")) {
         * int end = cleanContent.indexOf("]");
         * if (end > 0) {
         * fgHex = cleanContent.substring(3, end);
         * cleanContent = cleanContent.substring(end + 1);
         * } else
         * break;
         * } else if (cleanContent.startsWith("[b=#")) {
         * int end = cleanContent.indexOf("]");
         * if (end > 0) {
         * bgHex = cleanContent.substring(3, end);
         * cleanContent = cleanContent.substring(end + 1);
         * } else
         * break;
         * }
         * }
         * 
         * String divStyle = "class='msg-block' style='color:" + fgHex + ";";
         * if (bgHex != null) {
         * divStyle += " background-color:" + bgHex + ";";
         * }
         * divStyle += "'";
         * 
         * html.append("<div ").append(divStyle).append(">");
         * html.append("<div class='header' style='color:#999;'>").append(timestamp).
         * append(" - ").append(user)
         * .append(":</div>");
         * html.append("<div class='content'>").append(cleanContent).append("</div>");
         * html.append("</div>");
         * 
         * kit.insertHTML(doc, doc.getLength(), html.toString(), 0, 0, null);
         * scrollToBottom();
         * return;
         * }
         * }
         * 
         * // Fallback for simple messages (System logs etc)
         * String safeMsg = msg.replace("<", "&lt;").replace(">", "&gt;");
         * kit.insertHTML(doc, doc.getLength(),
         * "<div style='color:gray; font-style:italic;'>" + safeMsg + "</div>", 0,
         * 0, null);
         * 
         * } catch (Exception e) {
         * e.printStackTrace();
         * }
         * }
         */
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> chatArea.setCaretPosition(chatArea.getDocument().getLength()));
    }

    private Color getUniqueColor(String name) {
        return MsnTheme.TEXT_NORMAL;
    }

    private void appendMessageToDoc(HTMLDocument doc, String msg) {
        try {
            StringBuilder html = new StringBuilder();

            // Handle private messages specially
            if (msg.startsWith("[Private from ") || msg.startsWith("[Private to ")) {
                int split = msg.indexOf("]:");
                if (split > 0) {
                    String header = msg.substring(1, split);
                    String content = msg.substring(split + 3);

                    String style = "background-color:#f0e6ff; border-left: 3px solid #800080; padding: 5px;";
                    boolean isMe = header.contains(username != null ? username : "");
                    if (isMe) {
                        style += " margin-left: 50px; text-align: right;";
                    } else {
                        style += " margin-right: 50px;";
                    }

                    html.append("<div class='msg-block' style='" + style + "'>");
                    html.append("<div class='header' style='color:#800080; font-weight:bold;'>").append(header)
                            .append(":</div>");
                    html.append("<div class='content' style='font-style:italic;'>").append(content).append("</div>");
                    html.append("</div>");

                    kit.insertHTML(doc, doc.getLength(), html.toString(), 0, 0, null);
                    return;
                }
            }

            if (msg.startsWith("[")) {
                // Check for dd/MM/yyyy HH:mm:ss format
                if (msg.matches("^\\[\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}\\] \\[.+?\\]: .+")) {
                    int firstClose = msg.indexOf("]");
                    String timestamp = msg.substring(1, firstClose);

                    int secondOpen = msg.indexOf("[", firstClose);
                    int secondClose = msg.indexOf("]:", secondOpen);
                    String user = msg.substring(secondOpen + 1, secondClose);
                    String content = msg.substring(secondClose + 3);

                    appendFormattedBlock(doc, timestamp, user, content);
                    return;
                }

                // Check for dd/MM/yy HH:mm:ss format (2-digit year)
                if (msg.matches("^\\[\\d{2}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}\\] \\[.+?\\]: .+")) {
                    int firstClose = msg.indexOf("]");
                    String timestamp = msg.substring(1, firstClose);

                    int secondOpen = msg.indexOf("[", firstClose);
                    int secondClose = msg.indexOf("]:", secondOpen);
                    String user = msg.substring(secondOpen + 1, secondClose);
                    String content = msg.substring(secondClose + 3);

                    appendFormattedBlock(doc, timestamp, user, content);
                    return;
                }

                // Check for HH:mm:ss format (History/Legacy)
                if (msg.matches("^\\[\\d{2}:\\d{2}:\\d{2}\\] \\[.+?\\]: .+")) {
                    int firstClose = msg.indexOf("]");
                    String timePart = msg.substring(1, firstClose);

                    int secondOpen = msg.indexOf("[", firstClose);
                    int secondClose = msg.indexOf("]:", secondOpen);
                    String user = msg.substring(secondOpen + 1, secondClose);
                    String content = msg.substring(secondClose + 3);

                    appendFormattedBlock(doc, timePart, user, content);
                    return;
                }

                // Regular message format fallback
                int split = msg.indexOf("]:");
                if (split > 0) {
                    String user = msg.substring(1, split);
                    if (user.startsWith("["))
                        user = user.substring(1);
                    String content = msg.substring(split + 3);

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                    String timestamp = LocalDateTime.now().format(formatter);

                    appendFormattedBlock(doc, timestamp, user, content);
                    return;
                }
            }

            // Fallback for simple messages
            String safeMsg = msg.replace("<", "&lt;").replace(">", "&gt;");
            kit.insertHTML(doc, doc.getLength(), "<div style='color:gray; font-style:italic;'>" + safeMsg + "</div>", 0,
                    0, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void appendFormattedBlock(HTMLDocument doc, String timestamp, String user, String content)
            throws Exception {
        String fgHex = "#000000";
        String bgHex = null;
        String cleanContent = content;

        while (cleanContent.startsWith("[c=#") || cleanContent.startsWith("[b=#")) {
            if (cleanContent.startsWith("[c=#")) {
                int end = cleanContent.indexOf("]");
                if (end > 0) {
                    fgHex = cleanContent.substring(3, end);
                    cleanContent = cleanContent.substring(end + 1);
                } else
                    break;
            } else if (cleanContent.startsWith("[b=#")) {
                int end = cleanContent.indexOf("]");
                if (end > 0) {
                    bgHex = cleanContent.substring(3, end);
                    cleanContent = cleanContent.substring(end + 1);
                } else
                    break;
            }
        }

        String divStyle = "class='msg-block'";
        String styleAttr = "color:" + fgHex + ";";
        if (bgHex != null) {
            styleAttr += " background-color:" + bgHex + ";";
        }

        boolean isMe = user.equalsIgnoreCase(username != null ? username : "");
        if (isMe) {
            styleAttr += " text-align: right; margin-left: 50px;";
        } else {
            styleAttr += " margin-right: 50px;";
        }

        StringBuilder html = new StringBuilder();
        html.append("<div ").append(divStyle).append(" style='").append(styleAttr).append("'>");
        html.append("<div class='header' style='color:#999;'>").append(timestamp).append(" - ").append(user)
                .append(":</div>");
        html.append("<div class='content'>").append(cleanContent).append("</div>");
        html.append("</div>");

        kit.insertHTML(doc, doc.getLength(), html.toString(), 0, 0, null);
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
                appendToChat("Système : Connexion réussie !", Color.GRAY);
                return;
            }

            if (message.equals("REGISTRATION_SUCCESS")) {
                appendToChat("Système : Compte créé ! Connexion en cours...", Color.GRAY);
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
                String content = message.substring("FRIENDLIST ".length());
                rebuildFriendList(content);
                return;
            }

            if (message.startsWith("FRIEND_REQ ")) {
                System.out.println("DEBUG: Received FRIEND_REQ: " + message);
                String requester = message.substring("FRIEND_REQ ".length());
                int response = JOptionPane.showConfirmDialog(this,
                        "Vous avez une demande d'ami de " + requester + ".\nVoulez-vous accepter ?",
                        "Demande d'ami",
                        JOptionPane.YES_NO_OPTION);

                if (response == JOptionPane.YES_OPTION) {
                    client.sendMessage("/friend accept " + requester);
                } else { // NO or CLOSED
                    client.sendMessage("/friend deny " + requester);
                }
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

            if (message.startsWith("PRIVRECV ")) {
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String sender = parts[1]; // The person sending to me
                    String content = parts[2];

                    String timestamp = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                            .format(new java.util.Date());
                    String formatted = "[" + timestamp + "] [" + sender + "]: " + content;

                    String remoteUser = sender;
                    processPrivateMessage(remoteUser, formatted);
                }
                return;
            }

            if (message.startsWith("PRIVSENT ")) {
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String target = parts[1]; // The person I sent to
                    String content = parts[2];

                    String timestamp = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                            .format(new java.util.Date());
                    String formatted = "[" + timestamp + "] [" + username + "]: " + content; // Using my username

                    String remoteUser = target;
                    processPrivateMessage(remoteUser, formatted);
                }
                return;
            }

            if (message.startsWith("PRIVMSG ")) {
                // Maintained for HISTORY
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String sender = parts[1];
                    String content = parts[2];

                    // Legacy logic...
                    // Yes! The 2nd arg is the "Remote User".
                    // For Receiver: Remote User is Sender.
                    // For Sender: Remote User is Target.

                    String remoteUser = sender; // It's actually the "context" name

                    // For history, content usually has [timestamp] [user]: ...
                    // Just use content as is if it looks formatted, else format it
                    String formatted = content;
                    if (content.startsWith("HISTORY:")) {
                        content = content.substring("HISTORY:".length());
                        if (content.matches("^\\[\\d{2}:\\d{2}:\\d{2}\\] \\[.+?\\]: .+")) {
                            formatted = content;
                        } else {
                            formatted = content;
                        }
                    } else {
                        // Fallback for standard PRIVMSG if any
                        String timestamp = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                                .format(new java.util.Date());
                        formatted = "[" + timestamp + "] [" + sender + "]: " + content;
                    }

                    processPrivateMessage(remoteUser, formatted);
                }
                return;
            }

            if (message.startsWith("USERLIST " + currentChannel + " ")) {
                // Standard Channel List
                String users = message.substring(("USERLIST " + currentChannel + " ").length());
                channelUsers.clear();
                for (String u : users.split(",")) {
                    if (!u.isEmpty())
                        channelUsers.add(u);
                }
                userList.repaint();
                return;
            }

            // Handle Private Room User List
            if (message.startsWith("USERLIST !PRIVATE_")) {
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String roomName = parts[1];
                    String users = parts[2];

                    // Check if this room corresponds to our current private chat
                    if (isPrivateMode && roomName.equals(getPrivateRoomName(username, currentChannel))) {
                        channelUsers.clear();
                        for (String u : users.split(",")) {
                            if (!u.isEmpty())
                                channelUsers.add(u);
                        }
                        userList.repaint();
                    }
                }
                return;
            }

            if (message.startsWith("ALLUSERS ")) {
                String users = message.substring("ALLUSERS ".length());
                userListModel.clear();
                for (String u : users.split(",")) {
                    if (!u.isEmpty())
                        userListModel.addElement(u);
                }
                userList.repaint();
                return;
            }

            if (message.startsWith("CHANMSG ")) {
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String targetChannel = parts[1];
                    String content = parts[2];

                    if (content.startsWith("USERLIST ")) {
                        String prefix = "USERLIST " + targetChannel + " ";
                        if (content.startsWith(prefix)) {
                            String users = content.substring(prefix.length());
                            // Only update if it matches current view
                            boolean match = false;
                            if (targetChannel.equals(currentChannel)) {
                                match = true;
                            } else if (isPrivateMode
                                    && targetChannel.equals(getPrivateRoomName(username, currentChannel))) {
                                match = true;
                            }

                            if (match) {
                                channelUsers.clear();
                                for (String u : users.split(",")) {
                                    if (!u.isEmpty())
                                        channelUsers.add(u);
                                }
                                userList.repaint();
                            }
                        }
                        return;
                    }

                    // Filter system join logs if they come as CHANMSG
                    if (content.startsWith("LOG:")) {
                        return;
                    }

                    if (!channelDocs.containsKey(targetChannel)) {
                        // Create HTML doc!
                        channelDocs.put(targetChannel, (HTMLDocument) chatArea.getEditorKit().createDefaultDocument());
                    }
                    if (targetChannel.equals(currentChannel)) {
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
                    } else {
                        // Background update
                        if (content.startsWith("HISTORY:")) {
                            try {
                                String clean = content.substring("HISTORY:".length());
                                StyledDocument doc = channelDocs.get(targetChannel);
                                if (doc instanceof HTMLDocument) {
                                    appendMessageToDoc((HTMLDocument) doc, clean);
                                }
                            } catch (Exception e) {
                            }
                        } else {
                            StyledDocument doc = channelDocs.get(targetChannel);
                            if (doc instanceof HTMLDocument) {
                                appendMessageToDoc((HTMLDocument) doc, content);
                                // Play sound if it's not me sending the message (basic check)
                                // The server sends back my own messages too, but usually formatted.
                                // If I want to avoid double sound (sent + received), I check sender name inside
                                // content?
                                // Content format: [timestamp] [User]: msg...
                                if (!content.contains("[" + username + "]:")) {
                                    soundManager.playMessageReceived();
                                }
                            }
                        }
                    }
                }
                return;
            }

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

    private void rebuildFriendList(String diff) {
        System.out.println("DEBUG CLIENT: Received FRIENDLIST payload: " + diff);
        java.util.List<Friend> online = new java.util.ArrayList<>();
        java.util.List<Friend> offline = new java.util.ArrayList<>();
        java.util.List<Friend> pending = new java.util.ArrayList<>();

        if (!diff.isEmpty()) {
            for (String part : diff.split(",")) {
                String[] data = part.split(":");
                String name = data[0];
                String status = (data.length > 1) ? data[1] : "offline";

                if (status.equals("online")) {
                    online.add(new Friend(name, "online"));
                } else if (status.equals("pending")) {
                    pending.add(new Friend(name, "pending"));
                } else {
                    offline.add(new Friend(name, "offline"));
                }
            }
        }

        System.out.println("DEBUG CLIENT: Parsed " + online.size() + " online, " + offline.size() + " offline, "
                + pending.size() + " pending.");

        SwingUtilities.invokeLater(() -> {
            friendsListModel.clear();

            if (!pending.isEmpty()) {
                friendsListModel.addElement("Demandes (" + pending.size() + ")");
                for (Friend f : pending)
                    friendsListModel.addElement(f);
            }

            friendsListModel.addElement("En ligne (" + online.size() + ")");
            for (Friend f : online)
                friendsListModel.addElement(f);

            friendsListModel.addElement("Hors ligne (" + offline.size() + ")");
            for (Friend f : offline)
                friendsListModel.addElement(f);

            friendsList.repaint(); // Force repaint just in case
        });
    }

    private class FriendListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            // For Headers
            if (value instanceof String) {
                JLabel lbl = new JLabel((String) value);
                lbl.setOpaque(true);
                lbl.setBackground(new Color(220, 230, 244)); // Light blue header
                lbl.setForeground(new Color(40, 60, 100));
                lbl.setFont(MsnTheme.FONT_BOLD);
                lbl.setBorder(new EmptyBorder(2, 5, 2, 5));
                return lbl;
            }

            // For Friends
            if (value instanceof Friend) {
                Friend f = (Friend) value;
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, f.name, index, isSelected, cellHasFocus);
                lbl.setBorder(new EmptyBorder(2, 20, 2, 5)); // Indent

                if ("online".equals(f.status)) {
                    lbl.setForeground(new Color(0, 128, 0)); // Green
                    lbl.setText("<html><b>" + f.name + "</b> (En ligne)</html>");
                } else if ("pending".equals(f.status)) {
                    lbl.setForeground(new Color(255, 140, 0)); // Dark Orange
                    lbl.setText("<html>" + f.name + " (En attente)</html>");
                } else {
                    lbl.setForeground(Color.GRAY);
                    lbl.setText(f.name);
                }

                return lbl;
            }
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
    }
}
