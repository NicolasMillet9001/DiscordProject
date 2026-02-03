package fr.unilasalle.chat.client.ui;

import fr.unilasalle.chat.client.Client;
import fr.unilasalle.chat.client.MessageListener;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private Map<String, HTMLDocument> channelDocs = new HashMap<>(); // Switched to HTMLDocument
    private String currentChannel = "general";

    private String username;
    private String password;
    private boolean registerMode;
    private HTMLEditorKit kit; // Helper for inserting HTML

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
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
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

        JScrollPane scroll = new JScrollPane(channelList);
        scroll.setBorder(null);
        sidebar.add(scroll, BorderLayout.CENTER);
        
        parent.add(sidebar, BorderLayout.WEST);
    }

    private void switchChannel(String newChannel) {
        client.sendMessage("/join " + newChannel);
        currentChannel = newChannel;

        HTMLDocument doc = channelDocs.get(newChannel);
        if (doc == null) {
            doc = (HTMLDocument) kit.createDefaultDocument();
            channelDocs.put(newChannel, doc);
        }

        chatArea.setDocument(doc);
    }

    private void promptCreateChannel() {
        String name = JOptionPane.showInputDialog(this, "Enter conversation name:", "Start Conversation", JOptionPane.PLAIN_MESSAGE);
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

        JButton bgBtn = new JButton("B"); 
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
                new EmptyBorder(5, 5, 5, 5)
        ));
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
                new EmptyBorder(2, 5, 2, 5)
        ));
        btn.setFocusPainted(false);
        btn.setFont(MsnTheme.FONT_MAIN);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
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
        
        // Add right-click context menu for private messages
        userList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = userList.locationToIndex(e.getPoint());
                    userList.setSelectedIndex(row);
                    if (row != -1) {
                        String selectedUser = userList.getSelectedValue();
                        // Don't allow messaging yourself
                        if (selectedUser.equals(username)) {
                            return;
                        }
                        
                        JPopupMenu menu = new JPopupMenu();
                        JMenuItem msgItem = new JMenuItem("Send Private Message");
                        msgItem.addActionListener(ev -> promptPrivateMessage(selectedUser));
                        menu.add(msgItem);
                        menu.show(userList, e.getX(), e.getY());
                    }
                }
            }
        });
        
        userPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        
        add(userPanel, BorderLayout.EAST);
    }

    private void promptPrivateMessage(String targetUser) {
        String message = JOptionPane.showInputDialog(this, 
            "Enter your private message to " + targetUser + ":", 
            "Private Message", 
            JOptionPane.PLAIN_MESSAGE);
        
        if (message != null && !message.trim().isEmpty()) {
            // Send private message using /msg command
            client.sendMessage("/msg " + targetUser + " " + message);
        }
    }

    private void sendMessage() {
        String text = inputField.getText();
        if (!text.isEmpty()) {
            StringBuilder payload = new StringBuilder();
            
            if (!msgTextColor.equals(Color.BLACK)) {
                String hex = String.format("#%02x%02x%02x", msgTextColor.getRed(), msgTextColor.getGreen(), msgTextColor.getBlue());
                payload.append("[c=").append(hex).append("]");
            }
            
            if (!msgBgColor.equals(Color.WHITE)) {
                 String hex = String.format("#%02x%02x%02x", msgBgColor.getRed(), msgBgColor.getGreen(), msgBgColor.getBlue());
                 payload.append("[b=").append(hex).append("]");
            }
            
            payload.append(text);
            
            client.sendMessage(payload.toString());
            
            if (!text.startsWith("/")) {
                appendToChat("[" + username + "]: " + payload.toString(), getUniqueColor(username));
            }
            inputField.setText("");
        }
    }

    // Helper to append colored text
    private Color msgTextColor = Color.BLACK;
    private Color msgBgColor = Color.WHITE;

    // Helper to append colored text using HTML
    private void appendToChat(String msg, Color c) {
        HTMLDocument doc = (HTMLDocument) chatArea.getDocument();
        // Ensure we handle current channel doc correctly if we are in background?
        // Actually chatArea.getDocument() is current channel. 
        // If msg is for current channel we use chatArea, else we get from map.
        // But for simplicity in this method provided, we assume it's for current doc or we fetch from map?
        // The original logic fetched 'doc = chatArea.getStyledDocument()'. 
        // Logic in onMessageReceived handles 'if target==current append else...'
        // So we strictly append to 'doc' which is current.
        
        try {
            StringBuilder html = new StringBuilder();
            
            // Handle private messages specially
            if (msg.startsWith("[Private from ") || msg.startsWith("[Private to ")) {
                int split = msg.indexOf("]:");
                if (split > 0) {
                    String header = msg.substring(1, split); // "Private from User" or "Private to User"
                    String content = msg.substring(split + 3);
                    
                    html.append("<div class='msg-block' style='background-color:#f0e6ff; border-left: 3px solid #800080; padding: 5px;'>");
                    html.append("<div class='header' style='color:#800080; font-weight:bold;'>").append(header).append(":</div>");
                    html.append("<div class='content' style='font-style:italic;'>").append(content).append("</div>");
                    html.append("</div>");
                    
                    kit.insertHTML(doc, doc.getLength(), html.toString(), 0, 0, null);
                    scrollToBottom();
                    return;
                }
            }
            
            if (msg.startsWith("[")) {
                 // Check if this is a history message with timestamp: [dd/MM/yy HH:MM:SS] [user]: message
                 if (msg.matches("^\\[\\d{2}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}\\] \\[.+?\\]: .+")) {
                     // Extract timestamp, user, and content
                     int firstClose = msg.indexOf("]");
                     String timestamp = msg.substring(1, firstClose); // dd/MM/yy HH:MM:SS
                     
                     int secondOpen = msg.indexOf("[", firstClose);
                     int secondClose = msg.indexOf("]:", secondOpen);
                     String user = msg.substring(secondOpen + 1, secondClose);
                     String content = msg.substring(secondClose + 3);
                     
                     // Parser for color codes
                     String fgHex = "#000000";
                     String bgHex = null;
                     String cleanContent = content;
                     
                     while(cleanContent.startsWith("[c=#") || cleanContent.startsWith("[b=#")) {
                         if (cleanContent.startsWith("[c=#")) {
                             int end = cleanContent.indexOf("]");
                             if (end > 0) {
                                 fgHex = cleanContent.substring(3, end);
                                 cleanContent = cleanContent.substring(end + 1);
                             } else break;
                         }
                         else if (cleanContent.startsWith("[b=#")) {
                             int end = cleanContent.indexOf("]");
                             if (end > 0) {
                                 bgHex = cleanContent.substring(3, end);
                                 cleanContent = cleanContent.substring(end + 1);
                             } else break;
                         }
                     }

                     String divStyle = "class='msg-block' style='color:" + fgHex + ";";
                     if (bgHex != null) {
                         divStyle += " background-color:" + bgHex + ";";
                     }
                     divStyle += "'";

                     html.append("<div ").append(divStyle).append(">");
                     html.append("<div class='header' style='color:#999;'>").append(timestamp).append(" - ").append(user).append(":</div>");
                     html.append("<div class='content'>").append(cleanContent).append("</div>");
                     html.append("</div>");
                     
                     kit.insertHTML(doc, doc.getLength(), html.toString(), 0, 0, null);
                     scrollToBottom();
                     return;
                 }
                 
                 // Regular message format: [user]: message
                 int split = msg.indexOf("]:");
                 if (split > 0) {
                     String user = msg.substring(1, split); 
                     if (user.startsWith("[")) user = user.substring(1);
                     String content = msg.substring(split + 3); 
                     
                     // Generate current timestamp
                     DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss");
                     String timestamp = LocalDateTime.now().format(formatter);
                     
                     // Parser
                     String fgHex = "#000000";
                     String bgHex = null;
                     String cleanContent = content;
                     
                     while(cleanContent.startsWith("[c=#") || cleanContent.startsWith("[b=#")) {
                         if (cleanContent.startsWith("[c=#")) {
                             int end = cleanContent.indexOf("]");
                             if (end > 0) {
                                 fgHex = cleanContent.substring(3, end);
                                 cleanContent = cleanContent.substring(end + 1);
                             } else break;
                         }
                         else if (cleanContent.startsWith("[b=#")) {
                             int end = cleanContent.indexOf("]");
                             if (end > 0) {
                                 bgHex = cleanContent.substring(3, end);
                                 cleanContent = cleanContent.substring(end + 1);
                             } else break;
                         }
                     }

                     String divStyle = "class='msg-block' style='color:" + fgHex + ";";
                     if (bgHex != null) {
                         divStyle += " background-color:" + bgHex + ";";
                     }
                     divStyle += "'";

                     html.append("<div ").append(divStyle).append(">");
                     html.append("<div class='header' style='color:#999;'>").append(timestamp).append(" - ").append(user).append(":</div>");
                     html.append("<div class='content'>").append(cleanContent).append("</div>");
                     html.append("</div>");
                     
                     kit.insertHTML(doc, doc.getLength(), html.toString(), 0, 0, null);
                     scrollToBottom();
                     return;
                 }
            }
            
            // Fallback for simple messages (System logs etc)
            String safeMsg = msg.replace("<", "&lt;").replace(">", "&gt;");
            kit.insertHTML(doc, doc.getLength(), "<div style='color:gray; font-style:italic;'>" + safeMsg + "</div>", 0, 0, null);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> chatArea.setCaretPosition(chatArea.getDocument().getLength()));
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
}
