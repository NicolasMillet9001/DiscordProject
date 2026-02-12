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
import java.awt.event.KeyAdapter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.net.InetAddress;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.HyperlinkEvent;
import fr.unilasalle.chat.audio.AudioClient; // Import AudioClient

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
    private Map<String, String> channelUserStatus = new HashMap<>();
    private Map<String, String> channelUserMsg = new HashMap<>();

    private boolean isPrivateMode = false; // true if chatting with a friend

    // History storage
    private Map<String, StyledDocument> channelDocs = new HashMap<>();
    private String currentChannel = "general";

    private String username;
    private String password;
    private boolean registerMode;
    private HTMLEditorKit kit; // Helper for inserting HTML
    private JLabel headerAvatar;
    private AudioClient audioClient;
    private boolean inCall = false;
    private Map<String, Image> userAvatars = new HashMap<>();
    private JLabel partnerAvatarLabel;
    private JLabel talkingTo;

    private Image ownAvatarImage;
    private String ownStatusMessage = "";
    private String currentOnlineStatusCode = "online";

    public void setOwnStatusMessage(String msg) { this.ownStatusMessage = msg; }
    public void setCurrentOnlineStatusCode(String code) { this.currentOnlineStatusCode = code; }
    private Set<String> requestedAvatars = new HashSet<>();

    private JPanel rightSidebarContainer;
    private JPanel userPanel;
    private JPanel avatarSidebar;
    private JLabel myAvatarLabel;
    private JPanel msnTopActionBar;
    private CardLayout rightSidebarLayout;
    private JPanel inputPanel;
    private JPanel topMsnHeader;
    private JLabel partnerAvatarLabelFrame; // New avatar label in sidebar
    private JLabel partnerMoodLabel;
    private JLabel myMoodLabel;
    private JButton sidebarCallBtn;

    private static class Friend {
        String name;
        String status; // online, busy, away, offline
        String statusMessage;

        Friend(String name, String status, String msg) {
            this.name = name;
            this.status = status;
            this.statusMessage = msg;
        }
    }

    private SoundManager soundManager;
    private String serverHost;
    private int serverPort;

    public ChatGUI(String hostname, int port, String username, String password, boolean registerMode) {
        this.serverHost = hostname;
        this.serverPort = port;
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
        styleSheet.addRule("body { font-family: Tahoma; font-size: 11px; margin: 0; padding: 0; }");
        styleSheet.addRule(".msg-table { width: 100%; border-collapse: collapse; margin-bottom: 5px; }");
        styleSheet.addRule(".header { color: #888; font-size: 10px; margin-bottom: 2px; }");
        styleSheet.addRule(".content { font-size: 12px; }");

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

        JLabel avatarLabel = new JLabel();
        avatarLabel.setPreferredSize(new Dimension(50, 50));
        // avatarLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        avatarLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        avatarLabel.setToolTipText("Cliquez pour changer votre photo");
        avatarLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                new ProfileDialog(ChatGUI.this, username, client, ownAvatarImage, ownStatusMessage, currentOnlineStatusCode).setVisible(true);
            }
        });

        // Expose avatarLabel to update it later
        this.headerAvatar = avatarLabel;
        header.add(avatarLabel, BorderLayout.CENTER); // Center or East?
        // Layout tweak:
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.setOpaque(false);
        left.add(avatarLabel);
        left.add(appTitle);

        header.add(left, BorderLayout.WEST);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        statusPanel.setOpaque(false);

        JLabel userStatus = new JLabel("<html>Connecté(e) en tant que <b>" + username + "</b></html>");
        userStatus.setFont(MsnTheme.FONT_MAIN);
        userStatus.setForeground(Color.WHITE);

        statusPanel.add(userStatus);

        header.add(statusPanel, BorderLayout.EAST);

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

                                JMenuItem callItem = new JMenuItem("Appeler");
                                callItem.addActionListener(ev -> initiateCall(f.name));
                                menu.add(callItem);

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
        friendsList.clearSelection();

        talkingTo.setText(" À : #" + newChannel);
        rightSidebarLayout.show(rightSidebarContainer, "LIST");

        channelUsers.clear();
        if (userList != null)
            userList.repaint();

        StyledDocument doc = channelDocs.get(newChannel);
        if (doc == null) {
            doc = (StyledDocument) kit.createDefaultDocument();
            channelDocs.put(newChannel, doc);
            client.sendMessage("/history " + newChannel);
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
        currentChannel = friendName;
        isPrivateMode = true;

        talkingTo.setText(" À : " + friendName);
        rightSidebarLayout.show(rightSidebarContainer, "AVATARS");

        // Update side avatars
        if (userAvatars.containsKey(friendName)) {
            partnerAvatarLabelFrame.setIcon(new ImageIcon(userAvatars.get(friendName).getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
        } else {
            partnerAvatarLabelFrame.setIcon(null);
            client.sendMessage("/getavatar " + friendName);
        }
        
        if (ownAvatarImage != null) {
            myAvatarLabel.setIcon(new ImageIcon(ownAvatarImage.getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
        }
        
        // Populate Mood Labels
        updateMoodLabels(friendName);

        // Reset sidebar call button
        sidebarCallBtn.setText("Appeler");
        sidebarCallBtn.setForeground(Color.BLACK);

        String roomName = getPrivateRoomName(username, friendName);
        client.sendMessage("/join " + roomName);

        channelUsers.clear();
        if (userList != null)
            userList.repaint();

        StyledDocument doc = channelDocs.get("PRIV_" + friendName);
        if (doc == null) {
            doc = (StyledDocument) kit.createDefaultDocument();
            channelDocs.put("PRIV_" + friendName, doc);
            client.sendMessage("/privhistory " + friendName);
        }

        chatArea.setDocument(doc);
        scrollToBottom();
        channelList.clearSelection();
    }

    private void updateMoodLabels(String friendName) {
        String myStatus = "online"; // Could be dynamically fetched
        String myMsg = ownStatusMessage.isEmpty() ? "Pas de message perso" : ownStatusMessage;
        myMoodLabel.setText("<html><center><b>" + username + "</b> ("+myStatus+")<br><font color='gray'>" + myMsg + "</font></center></html>");

        if (friendName != null) {
            String pStatus = channelUserStatus.getOrDefault(friendName, "offline");
            String pMsg = channelUserMsg.getOrDefault(friendName, "");
            if (pMsg.isEmpty()) pMsg = "Pas de message perso";
            partnerMoodLabel.setText("<html><center><b>" + friendName + "</b> ("+pStatus+")<br><font color='gray'>" + pMsg + "</font></center></html>");
        }
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
        chatPanel.setBorder(new EmptyBorder(0, 5, 0, 0));

        // Center Content
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);

        // Chat Header (The blue "À :" bar)
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(new Color(230, 235, 245));
        chatHeader.setBorder(new MatteBorder(1, 1, 1, 1, MsnTheme.BORDER_COLOR));
        chatHeader.setPreferredSize(new Dimension(0, 30));

        talkingTo = new JLabel(" À : " + currentChannel);
        talkingTo.setFont(MsnTheme.FONT_MAIN);
        talkingTo.setForeground(MsnTheme.TEXT_NORMAL);
        chatHeader.add(talkingTo, BorderLayout.WEST);
        
        centerPanel.add(chatHeader, BorderLayout.NORTH);

        chatArea = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        chatArea.setEditorKit(kit);
        chatArea.setEditable(false);
        chatArea.setBackground(Color.WHITE);
        chatArea.setMargin(new Insets(5, 5, 5, 25));
        chatArea.setFont(MsnTheme.FONT_MAIN);
        
        chatArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String desc = e.getDescription();
                if (desc.startsWith("download:")) {
                    String fileId = desc.substring("download:".length());
                    int confirm = JOptionPane.showConfirmDialog(this, "Télécharger " + fileId + " ?", "Téléchargement", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) client.sendMessage("/download " + fileId);
                }
            }
        });

        chatArea.setDocument(channelDocs.get("general"));
        JScrollPane scrollPane = new JScrollPane(chatArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR));
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // Input Area
        inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(MsnTheme.BACKGROUND);
        inputPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
        inputPanel.setPreferredSize(new Dimension(0, 85));

        // Toolbar with old buttons
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
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR),
                new EmptyBorder(5, 5, 5, 5)));
        inputField.addActionListener(e -> sendMessage());
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                soundManager.playKey(e.getKeyChar() == ' ');
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

        centerPanel.add(inputPanel, BorderLayout.SOUTH);
        chatPanel.add(centerPanel, BorderLayout.CENTER);

        parent.add(chatPanel, BorderLayout.CENTER);
    }

    private JLabel createActionIcon(String text, String tooltip) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Tahoma", Font.PLAIN, 11));
        label.setForeground(new Color(0, 51, 153));
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setToolTipText(tooltip);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalTextPosition(SwingConstants.BOTTOM);
        label.setHorizontalTextPosition(SwingConstants.CENTER);
        return label;
    }

    private JLabel createSmallLink(String text) {
        JLabel l = new JLabel("<html><u>" + text + "</u></html>");
        l.setFont(new Font("Tahoma", Font.PLAIN, 10));
        l.setForeground(Color.BLUE);
        l.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return l;
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
        rightSidebarLayout = new CardLayout();
        rightSidebarContainer = new JPanel(rightSidebarLayout);
        rightSidebarContainer.setPreferredSize(new Dimension(150, 0));
        rightSidebarContainer.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, MsnTheme.BORDER_COLOR));

        // Card 1: User List (Public)
        userPanel = new JPanel(new BorderLayout());
        userPanel.setBackground(MsnTheme.SIDEBAR);

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

        JButton groupWizzBtn = new WindowsXPButton("Wizz!");
        groupWizzBtn.setForeground(Color.RED);
        groupWizzBtn.addActionListener(e -> client.sendMessage("/wizz"));
        JPanel groupWizzContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        groupWizzContainer.setOpaque(false);
        groupWizzContainer.add(groupWizzBtn);
        userPanel.add(groupWizzContainer, BorderLayout.SOUTH);

        // Card 2: Avatar Sidebar (Private)
        avatarSidebar = new JPanel();
        avatarSidebar.setLayout(new BoxLayout(avatarSidebar, BoxLayout.Y_AXIS));
        avatarSidebar.setBackground(MsnTheme.SIDEBAR);
        avatarSidebar.setBorder(new EmptyBorder(10, 5, 10, 5));
        
        // Partner Section
        JPanel pTop = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pTop.setOpaque(false);
        partnerAvatarLabelFrame = new JLabel();
        pTop.add(new MsnPhotoFrame(partnerAvatarLabelFrame, 96));
        
        partnerMoodLabel = new JLabel("<html><center><b>...</b><br><font color='gray'>...</font></center></html>");
        partnerMoodLabel.setFont(new Font("Tahoma", Font.PLAIN, 11));
        partnerMoodLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        partnerMoodLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        sidebarCallBtn = new WindowsXPButton("Appeler");
        sidebarCallBtn.setMaximumSize(new Dimension(100, 30));
        sidebarCallBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebarCallBtn.addActionListener(e -> {
            if (inCall) {
                client.sendMessage("/hangup");
                endCall();
            } else {
                if (isPrivateMode && currentChannel != null) {
                    initiateCall(currentChannel);
                }
            }
        });

        JButton sidebarWizzBtn = new WindowsXPButton("Wizz!");
        sidebarWizzBtn.setMaximumSize(new Dimension(100, 30));
        sidebarWizzBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebarWizzBtn.setForeground(Color.RED);
        sidebarWizzBtn.addActionListener(e -> client.sendMessage("/wizz"));

        // My Section
        JPanel pBottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pBottom.setOpaque(false);
        myAvatarLabel = new JLabel();
        pBottom.add(new MsnPhotoFrame(myAvatarLabel, 96));
        
        myMoodLabel = new JLabel("<html><center><b>" + username + "</b><br><font color='gray'>...</font></center></html>");
        myMoodLabel.setFont(new Font("Tahoma", Font.PLAIN, 11));
        myMoodLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        myMoodLabel.setHorizontalAlignment(SwingConstants.CENTER);

        avatarSidebar.add(pTop);
        avatarSidebar.add(partnerMoodLabel);
        avatarSidebar.add(Box.createVerticalStrut(5));
        avatarSidebar.add(sidebarCallBtn);
        avatarSidebar.add(Box.createVerticalStrut(5));
        avatarSidebar.add(sidebarWizzBtn);
        avatarSidebar.add(Box.createVerticalGlue());
        avatarSidebar.add(myMoodLabel);
        avatarSidebar.add(pBottom);

        rightSidebarContainer.add(userPanel, "LIST");
        rightSidebarContainer.add(avatarSidebar, "AVATARS");

        add(rightSidebarContainer, BorderLayout.EAST);
    }

    private class UserListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String) {
                String user = (String) value;

                // Avatar icon
                if (userAvatars.containsKey(user)) {
                    Image img = userAvatars.get(user).getScaledInstance(15, 15, Image.SCALE_SMOOTH);
                    label.setIcon(new ImageIcon(img));
                } else {
                    label.setIcon(null);
                    if (!requestedAvatars.contains(user)) {
                        requestedAvatars.add(user);
                        client.sendMessage("/getavatar " + user);
                    }
                }

                if (channelUsers.contains(user)) {
                    String st = channelUserStatus.getOrDefault(user, "online");
                    Color c2 = Color.BLACK;
                    String suffix = "";

                    if (st.equals("online")) {
                        c2 = new Color(0, 128, 0);
                    } else if (st.equals("busy")) {
                        c2 = Color.RED;
                        suffix = " (Occupé)";
                    } else if (st.equals("away")) {
                        c2 = Color.ORANGE;
                        suffix = " (Absent)";
                    }

                    String msg = channelUserMsg.getOrDefault(user, "");
                    if (!msg.isEmpty())
                        label.setToolTipText(msg);
                    else
                        label.setToolTipText(null);

                    label.setForeground(c2);
                    label.setFont(MsnTheme.FONT_BOLD);
                    label.setText(user + suffix);
                } else {
                    label.setForeground(MsnTheme.TEXT_NORMAL);
                    label.setFont(MsnTheme.FONT_MAIN);
                    label.setToolTipText(null);
                }
            }
            return label;
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

                            JMenuItem callItem = new JMenuItem("Appeler");
                            callItem.addActionListener(ev -> initiateCall(selected));
                            menu.add(callItem);

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

            if (text.startsWith("/setmsg ")) {
            ownStatusMessage = text.substring(8);
            if (isPrivateMode) updateMoodLabels(currentChannel);
        }

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
            if (msg.startsWith("RAW_HTML:")) {
                kit.insertHTML(doc, doc.getLength(), msg.substring("RAW_HTML:".length()), 0, 0, null);
                scrollToBottom();
                return;
            }

            StringBuilder html = new StringBuilder();

            // Handle private messages specially
            if (msg.startsWith("[Private from ") || msg.startsWith("[Private to ")) {
                int split = msg.indexOf("]:");
                if (split > 0) {
                    String header = msg.substring(1, split);
                    String content = wrapLongWords(msg.substring(split + 3));
                    boolean isMe = header.contains(username != null ? username : "");
                    
                    html.append("<table class='msg-table' width='100%'><tr>");
                    if (isMe) {
                        html.append("<td width='40'></td>");
                        html.append("<td style='background-color:#f0e6ff; border-right: 3px solid #800080; padding: 5px; text-align: right;'>");
                    } else {
                        html.append("<td style='background-color:#f0e6ff; border-left: 3px solid #800080; padding: 5px; text-align: left;'>");
                    }
                    
                    html.append("<div class='header' style='color:#800080; font-weight:bold;'>").append(header).append(":</div>");
                    html.append("<div class='content' style='font-style:italic;'>").append(content).append("</div>");
                    html.append("</td>");
                    
                    if (!isMe) {
                        html.append("<td width='40'></td>");
                    }
                    html.append("</tr></table>");

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
            kit.insertHTML(doc, doc.getLength(), "<div style='color:gray; font-style:italic;'>" + wrapLongWords(safeMsg) + "</div>", 0,
                    0, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String wrapLongWords(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        int count = 0;
        boolean inTag = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') inTag = true;
            if (c == '>') {
                inTag = false;
                sb.append(c);
                continue;
            }
            if (inTag) {
                sb.append(c);
                continue;
            }

            if (Character.isWhitespace(c)) {
                count = 0;
            } else {
                count++;
                if (count > 20) { 
                    sb.append("&#8203;"); 
                    count = 0;
                }
            }
            sb.append(c);
        }
        return sb.toString();
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
        cleanContent = wrapLongWords(cleanContent);

        boolean isMe = user.equalsIgnoreCase(username != null ? username : "");
        StringBuilder html = new StringBuilder();
        
        html.append("<table class='msg-table' width='100%'><tr>");
        
        String style = "color:" + fgHex + ";";
        if (bgHex != null) style += " background-color:" + bgHex + ";";

        if (isMe) {
            html.append("<td width='50'></td>");
            html.append("<td style='").append(style).append(" text-align: right; padding: 2px;'>");
        } else {
            html.append("<td style='").append(style).append(" text-align: left; padding: 2px;'>");
        }

        html.append("<div class='header' style='color:#999;'>").append(timestamp).append(" - ").append(user).append(":</div>");
        html.append("<div class='content'>").append(cleanContent).append("</div>");
        html.append("</td>");
        
        if (!isMe) {
            html.append("<td width='50'></td>");
        }
        html.append("</tr></table>");

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
                String[] parts = message.split(" ");
                if (parts.length >= 3 && !parts[2].equals("null")) {
                    client.sendMessage("/getavatar " + username);
                }
                if (parts.length >= 4) {
                    try {
                        byte[] decoded = Base64.getDecoder().decode(parts[3]);
                        ownStatusMessage = new String(decoded);
                    } catch (Exception e) {}
                }
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

                // Log and refresh list (it should show in Pending section now)
                appendToChat("<b>Demande d'ami reçue de " + requester + "</b> (Voir liste d'amis)", Color.BLUE);
                soundManager.playMessageReceived(); // Or a specific sound if available
                client.sendMessage("/friend list");
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
                        String raw = content.substring("HISTORY:".length());
                        // raw: "[12/02/26 08:53:44] [Jeomin]: WIZZ Jeomin"

                        // Parse regex: [timestamp] [sender]: msg
                        java.util.regex.Pattern p = java.util.regex.Pattern.compile("^\\[(.*?)\\] \\[(.*?)\\]: (.*)$");
                        java.util.regex.Matcher m = p.matcher(raw);
                        if (m.find()) {
                            String ts = m.group(1);
                            String msgSender = m.group(2); // Should match sender in []
                            String msgContent = m.group(3);

                            if (msgContent.startsWith("WIZZ ")) {
                                String wizzHtml;
                                if (msgSender.equals(username)) {
                                    wizzHtml = "<div style='color:#FF0000;font-weight:bold;font-size:14px;text-align:center;'>--- Vous avez envoyé un Wizz ! ---</div>";
                                } else {
                                    wizzHtml = "<div style='color:#FF0000;font-weight:bold;font-size:14px;text-align:center;'>--- "
                                            + msgSender + " vous a envoyé un Wizz ! ---</div>";
                                }
                                // Include timestamp as requested by user
                                formatted = "RAW_HTML:<div style='color:gray;font-size:10px;text-align:left;'>" + ts
                                        + "</div>" + wizzHtml;
                            } else {
                                formatted = raw;
                            }
                        } else {
                            formatted = raw;
                        }
                    } else if (content.startsWith("WIZZ ")) {
                        String wizzSender = content.substring("WIZZ ".length());
                        if (wizzSender.equals(username)) {
                            formatted = "RAW_HTML:<div style='color:#FF0000;font-weight:bold;font-size:14px;text-align:center;'>--- Vous avez envoyé un Wizz ! ---</div>";
                        } else {
                            formatted = "RAW_HTML:<div style='color:#FF0000;font-weight:bold;font-size:14px;text-align:center;'>--- "
                                    + wizzSender + " vous a envoyé un Wizz ! ---</div>";
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
                channelUserStatus.clear();
                channelUserMsg.clear();
                for (String u : users.split(",")) {
                    if (!u.isEmpty()) {
                        String[] parts = u.split(":");
                        String name = parts[0];
                        String st = parts.length > 1 ? parts[1] : "online";
                        String m = parts.length > 2 ? parts[2] : "";
                        channelUsers.add(name);
                        channelUserStatus.put(name, st);
                        channelUserMsg.put(name, m);
                    }
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

                    // Resolve the correct document key (handle private chat mapping)
                    String docKey = targetChannel;
                    if (targetChannel.startsWith("!PRIVATE_")) {
                        String[] p = targetChannel.substring(9).split("_");
                        if (p.length >= 2) {
                            if (p[0].equals(username))
                                docKey = "PRIV_" + p[1];
                            else
                                docKey = "PRIV_" + p[0];
                        }
                    }

                    if (content.startsWith("WIZZ ")) {
                        String sender = content.substring("WIZZ ".length());
                        // GLOBAL EFFECT: Always play sound and shake
                        soundManager.playWizz();
                        shakeWindow();

                        String displayHtml;
                        if (sender.equals(username)) {
                            displayHtml = "RAW_HTML:<div style='color:#FF0000;font-weight:bold;font-size:14px;text-align:center;'>--- Vous avez envoyé un Wizz ! ---</div>";
                        } else {
                            displayHtml = "RAW_HTML:<div style='color:#FF0000;font-weight:bold;font-size:14px;text-align:center;'>--- "
                                    + sender + " vous a envoyé un Wizz ! ---</div>";
                        }

                        boolean match = false;
                        if (targetChannel.equals(currentChannel)) {
                            match = true;
                        } else if (isPrivateMode
                                && targetChannel.equals(getPrivateRoomName(username, currentChannel))) {
                            match = true;
                        }

                        System.out.println("DEBUG WIZZ: target=" + targetChannel + ", current=" + currentChannel
                                + ", isPrivate=" + isPrivateMode + ", match=" + match);

                        if (match) {
                            appendToChat(displayHtml, null); // Color ignored for raw HTML
                        } else {
                            // Append to background buffer using RESOLVED key
                            if (!channelDocs.containsKey(docKey)) {
                                channelDocs.put(docKey, (HTMLDocument) chatArea.getEditorKit().createDefaultDocument());
                            }
                            StyledDocument doc = channelDocs.get(docKey);
                            if (doc instanceof HTMLDocument) {
                                appendMessageToDoc((HTMLDocument) doc, displayHtml);
                            }
                        }
                        return;
                    }

                    if (!channelDocs.containsKey(docKey)) {
                        // Create HTML doc!
                        channelDocs.put(docKey, (HTMLDocument) chatArea.getEditorKit().createDefaultDocument());
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
                                StyledDocument doc = channelDocs.get(docKey);
                                if (doc instanceof HTMLDocument) {
                                    appendMessageToDoc((HTMLDocument) doc, clean);
                                }
                            } catch (Exception e) {
                            }
                        } else {
                            StyledDocument doc = channelDocs.get(docKey);
                            if (doc instanceof HTMLDocument) {
                                appendMessageToDoc((HTMLDocument) doc, content);
                                // Play sound if it's not me sending the message
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

            if (message.startsWith("SEARCH_START ")) {
                // Switch to search view
                String query = message.substring("SEARCH_START ".length());
                String searchChan = "!Recherche";

                if (!channelDocs.containsKey(searchChan)) {
                    channelDocs.put(searchChan, (HTMLDocument) kit.createDefaultDocument());
                }
                // Clear previous search? HTMLDocument doesn't have clear().
                channelDocs.put(searchChan, (HTMLDocument) kit.createDefaultDocument());

                currentChannel = searchChan;
                chatArea.setDocument(channelDocs.get(searchChan));

                appendToChat("<b>Résultats de la recherche pour : " + query + "</b><br><hr>", Color.BLUE);
                return;
            }

            if (message.startsWith("SEARCH_RESULT ")) {
                String data = message.substring("SEARCH_RESULT ".length());
                // Format: CHANNEL:channel:user:ts:content OR PRIVATE:other:sender:ts:content
                String[] parts = data.split(":", 5);
                if (parts.length >= 5) {
                    String type = parts[0];
                    String location = parts[1];
                    String user = parts[2];
                    String ts = parts[3];
                    // ts is YYYY-MM-DD HH:MM:SS from sqlite datetime(..., localtime)
                    // Parse it to make it nicer?
                    String content = parts[4];

                    String displayRaw = "[" + ts + "] [" + location + "] <b>" + user + "</b>: " + content;
                    appendToChat(displayRaw, Color.DARK_GRAY);
                }
                return;
            }

            if (message.equals("SEARCH_END")) {
                appendToChat("<hr><i>Fin de la recherche.</i>", Color.GRAY);
                scrollToBottom();
                return;
            }

            if (message.startsWith("AVATAR_SET ")) {
                String filename = message.substring("AVATAR_SET ".length());
                // We just set it, request data back to display?
                // Or just assume successful. To display, we need the image.
                client.sendMessage("/getavatar " + username);
                return;
            }

            if (message.startsWith("AVATAR_DATA ")) {
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String user = parts[1];
                    String b64 = parts[2];
                    try {
                        byte[] data = Base64.getDecoder().decode(b64);
                        ImageIcon icon = new ImageIcon(data);
                        Image rawImg = icon.getImage();
                        userAvatars.put(user, rawImg);

                        if (user.equals(username)) {
                            ownAvatarImage = rawImg;
                            if (headerAvatar != null) {
                                Image scaled = rawImg.getScaledInstance(50, 50, Image.SCALE_SMOOTH);
                                headerAvatar.setIcon(new ImageIcon(scaled));
                            }
                        }

                        // If we are in call with this user, update call window
                        if (inCall && callWindow != null) {
                            callWindow.setPartnerAvatar(rawImg);
                        }

                        // Update side avatars in private mode
                        if (isPrivateMode) {
                            if (user.equals(currentChannel)) {
                                if (partnerAvatarLabelFrame != null) {
                                    partnerAvatarLabelFrame.setIcon(new ImageIcon(rawImg.getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
                                }
                            } else if (user.equals(username)) {
                                if (myAvatarLabel != null) {
                                    myAvatarLabel.setIcon(new ImageIcon(rawImg.getScaledInstance(96, 96, Image.SCALE_SMOOTH)));
                                }
                            }
                        }

                        // Trigger repaint of lists
                        if (userList != null)
                            userList.repaint();
                        if (friendsList != null)
                            friendsList.repaint();
                    } catch (Exception e) {
                    }
                }
                return;
            }

            if (message.startsWith("AVATAR_UPDATE ")) {
                String user = message.substring("AVATAR_UPDATE ".length());
                requestedAvatars.remove(user);
                userAvatars.remove(user); // Force reload
                client.sendMessage("/getavatar " + user);
                return;
            }

            if (message.startsWith("FILE ")) {
                // FILE <uniqueID> <originalName>
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String fileId = parts[1];
                    String fileName = parts[2];

                    String html = "<div style='background-color:#eef; border:1px solid #ccf; padding:5px; margin:5px;'>"
                            +
                            "<b>Fichier partagé : </b>" + fileName + "<br>" +
                            "<a href='download:" + fileId + "'>Cliquez ici pour télécharger</a>" +
                            "</div>";

                    StyledDocument doc = chatArea.getStyledDocument(); // Assume current channel context for
                                                                       // simplicity/broadcast
                    try {
                        kit.insertHTML((HTMLDocument) doc, doc.getLength(), html, 0, 0, null);
                        scrollToBottom();
                    } catch (Exception e) {
                    }
                }
                return;
            }

            if (message.startsWith("FILEDOWNLOAD ")) {
                String[] parts = message.split(" ", 3);
                if (parts.length >= 3) {
                    String fileId = parts[1];
                    String b64 = parts[2];

                    JFileChooser saver = new JFileChooser();
                    saver.setSelectedFile(new File(fileId)); // Suggest ID as name
                    int res = saver.showSaveDialog(this);
                    if (res == JFileChooser.APPROVE_OPTION) {
                        try {
                            byte[] data = Base64.getDecoder().decode(b64);
                            Files.write(saver.getSelectedFile().toPath(), data);
                            JOptionPane.showMessageDialog(this, "Fichier enregistré avec succès !");
                        } catch (Exception e) {
                            JOptionPane.showMessageDialog(this, "Erreur lors de l'enregistrement : " + e.getMessage());
                        }
                    }
                }
                return;
            }

            if (message.startsWith("CALL_INCOMING ")) {
                String caller = message.substring("CALL_INCOMING ".length());
                // Play ringtone?
                int resp = JOptionPane.showConfirmDialog(this, "Appel entrant de " + caller + ". Accepter ?",
                        "Appel Entrant", JOptionPane.YES_NO_OPTION);
                if (resp == JOptionPane.YES_OPTION) {
                    client.sendMessage("/accept " + caller);
                    startCall(caller);
                } else {
                    client.sendMessage("/deny " + caller);
                }
                return;
            }

            if (message.startsWith("CALL_ACCEPTED ")) {
                String partner = message.substring("CALL_ACCEPTED ".length());
                appendToChat("Appel accepté par " + partner + ". Connexion Audio...", Color.GREEN);
                startCall(partner);
                return;
            }

            if (message.startsWith("CALL_DENIED ")) {
                appendToChat("Appel refusé.", Color.RED);
                // Reset UI state if needed
                return;
            }

            if (message.startsWith("HANGUP ")) {
                String partner = message.substring("HANGUP ".length());
                appendToChat("Appel terminé par " + partner, Color.RED);
                endCall();
                return;
            }

            // Fallback
            appendToChat(message, Color.BLACK);
        });
    }

    private fr.unilasalle.chat.video.VideoClient videoClient;
    private CallWindow callWindow;

    private void startCall(String partner) {
        if (inCall)
            return;
        inCall = true;
        // Start Audio Client on Port (Server Port + 1 usually)

        audioClient = new AudioClient(serverHost, serverPort + 1, username);
        audioClient.startCall();

        // Open Call Window
        SwingUtilities.invokeLater(() -> {
            callWindow = new CallWindow(partner,
                    e -> {
                        // Hangup action
                        client.sendMessage("/hangup");
                        endCall();
                    },
                    e -> {
                        // Screen Share action
                        if (videoClient != null) {
                            if (videoClient.isSendingScreen()) {
                                videoClient.stopScreenShare();
                                callWindow.clearVideo();
                                appendToChat("Partage d'écran désactivé.", Color.BLUE);
                            } else {
                                videoClient.startScreenShare();
                                appendToChat("Partage d'écran activé.", Color.BLUE);
                            }
                        }
                    },
                    e -> {
                        // Camera action
                        // TODO: Implement Camera
                        JOptionPane.showMessageDialog(callWindow, "Aucune caméra détectée (Bibliothèque manquante).",
                                "Erreur Caméra", JOptionPane.WARNING_MESSAGE);
                    });

            // Set Avatar if we have it
            if (userAvatars.containsKey(partner)) {
                callWindow.setPartnerAvatar(userAvatars.get(partner));
            } else {
                client.sendMessage("/getavatar " + partner);
            }

            // Start Video Client (Port + 2)
            videoClient = new fr.unilasalle.chat.video.VideoClient(serverHost, serverPort + 2, username, img -> {
                if (callWindow != null)
                    callWindow.updateVideo(img);
            });
            videoClient.start();
        });

        appendToChat("Appel vocal actif.", Color.BLUE);
    }

    private void endCall() {
        if (audioClient != null) {
            audioClient.stopCall();
            audioClient = null;
        }
        if (videoClient != null) {
            videoClient.stop();
            videoClient = null;
        }
        if (callWindow != null) {
            callWindow.dispose();
            callWindow = null;
        }
        inCall = false;
        sidebarCallBtn.setText("Appeler");
        sidebarCallBtn.setForeground(MsnTheme.TEXT_NORMAL);
        appendToChat("Appel terminé.", Color.GRAY);
    }

    private void initiateCall(String target) {
        if (inCall) return;
        
        String status = channelUserStatus.getOrDefault(target, "offline");
        if (status.equals("offline") || status.equals("busy")) {
            String reason = status.equals("offline") ? "est hors ligne" : "est occupé(e)";
            JOptionPane.showMessageDialog(this, target + " " + reason + " et ne peut pas être appelé(e) pour le moment.", "Appel Impossible", JOptionPane.WARNING_MESSAGE);
            return;
        }

        client.sendMessage("/call " + target);
        sidebarCallBtn.setText("Fin d'appel");
        sidebarCallBtn.setForeground(Color.RED);
        appendToChat("Appel de " + target + " en cours...", Color.BLUE);
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

    // MSN Photo Frame (The Polaroid look)
    class MsnPhotoFrame extends JPanel {
        private JLabel imgLabel;
        private int size;

        public MsnPhotoFrame(JLabel label, int size) {
            this.imgLabel = label;
            this.size = size;
            setLayout(new BorderLayout());
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                BorderFactory.createEmptyBorder(5, 5, 20, 5) // Bottom space for name/info
            ));
            setPreferredSize(new Dimension(size + 10, size + 25));
            add(label, BorderLayout.CENTER);
            
            // Subtle "rounded" corners effect with border? 
            // XP era didn't have much rounding, mostly sharp but soft gradients.
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
                String status = data.length > 1 ? data[1] : "offline";
                String msg = data.length > 2 ? data[2] : "";

                // Update maps for real-time mood/status tracking
                channelUserStatus.put(name, status);
                channelUserMsg.put(name, msg);

                if (status.equals("pending")) {
                    pending.add(new Friend(name, "pending", msg));
                } else if (!status.equals("offline")) {
                    online.add(new Friend(name, status, msg));
                } else {
                    offline.add(new Friend(name, "offline", msg));
                }
            }
        }

        System.out.println("DEBUG CLIENT: Parsed " + online.size() + " online, " + offline.size() + " offline, "
                + pending.size() + " pending.");

        SwingUtilities.invokeLater(() -> {
            friendsListModel.clear();

            if (!pending.isEmpty()) {
                friendsListModel.addElement("En attente (" + pending.size() + ")");
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
            if (isPrivateMode) updateMoodLabels(currentChannel);
        });
    }

    private void shakeWindow() {
        // Force window to front and restore if minimized
        if (getExtendedState() == JFrame.ICONIFIED) {
            setExtendedState(JFrame.NORMAL);
        }
        toFront();
        requestFocus();

        final Point original = getLocation();
        final int shakeAmplitude = 10;
        final int shakeDuration = 500; // ms
        final long startTime = System.currentTimeMillis();

        Timer shakeTimer = new Timer(50, null);
        shakeTimer.addActionListener(e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > shakeDuration) {
                setLocation(original);
                shakeTimer.stop();
            } else {
                int xOffset = (int) (Math.random() * shakeAmplitude * 2 - shakeAmplitude);
                int yOffset = (int) (Math.random() * shakeAmplitude * 2 - shakeAmplitude);
                setLocation(original.x + xOffset, original.y + yOffset);
            }
        });
        shakeTimer.start();
    }

    private class FriendListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {

            // Use super to set up basic properties (bg, fg, font, selection)
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            // For Headers (String)
            if (value instanceof String) {
                lbl.setOpaque(true);
                lbl.setBackground(new Color(220, 230, 244)); // Light blue header
                lbl.setForeground(new Color(40, 60, 100));
                lbl.setFont(MsnTheme.FONT_BOLD);
                lbl.setBorder(new EmptyBorder(2, 5, 2, 5));
                lbl.setText((String) value);
                return lbl;
            }

            // For Friends (Friend object)
            if (value instanceof Friend) {
                Friend f = (Friend) value;
                lbl.setBorder(new EmptyBorder(2, 5, 2, 5)); // Reduced indent as we use icon

                // Avatar icon
                if (userAvatars.containsKey(f.name)) {
                    Image img = userAvatars.get(f.name).getScaledInstance(20, 20, Image.SCALE_SMOOTH);
                    lbl.setIcon(new ImageIcon(img));
                } else {
                    lbl.setIcon(null);
                    if (!requestedAvatars.contains(f.name)) {
                        requestedAvatars.add(f.name);
                        client.sendMessage("/getavatar " + f.name);
                    }
                }

                String displayStatus = "";
                Color c2 = Color.GRAY;

                if (f.status.equals("online")) {
                    displayStatus = "(En ligne)";
                    c2 = new Color(0, 128, 0);
                } else if (f.status.equals("busy")) {
                    displayStatus = "(Occupé)";
                    c2 = new Color(200, 0, 0); // Red
                } else if (f.status.equals("away")) {
                    displayStatus = "(Absent)";
                    c2 = new Color(220, 150, 0); // Orange
                } else if (f.status.equals("pending")) {
                    displayStatus = "(En attente)";
                    c2 = new Color(100, 100, 255); // Blue-ish
                }

                if (!f.status.equals("offline")) {
                    lbl.setForeground(c2);
                    String txt = "<html><b>" + f.name + "</b> " + displayStatus;
                    // Sanitize potential HTML in status message or name?
                    // Assume name is safe-ish, but message might have chars.
                    // For now, keep it simple but functional.
                    if (f.statusMessage != null && !f.statusMessage.isEmpty()) {
                        String safeMsg = f.statusMessage.replace("<", "&lt;").replace(">", "&gt;");
                        txt += "<br><span style='color:gray;font-size:9px'> - " + safeMsg + "</span>";
                    }
                    txt += "</html>";
                    lbl.setText(txt);
                } else {
                    lbl.setForeground(Color.GRAY);
                    lbl.setText(f.name);
                }
                return lbl;
            }

            return lbl;
        }
    }
}
