package fr.unilasalle.chat.client.ui;

import fr.unilasalle.chat.client.Client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

public class ProfileDialog extends JDialog {
    private String username;
    private Client client;
    private Image currentAvatar;
    private JLabel avatarPreview;
    private JTextField statusMessageField;

    public ProfileDialog(JFrame parent, String username, Client client, Image currentAvatar, String currentStatusMsg) {
        super(parent, "Mon Profil", true);
        this.username = username;
        this.client = client;
        this.currentAvatar = currentAvatar;

        setSize(350, 450);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBorder(new EmptyBorder(20, 20, 20, 20));
        main.setBackground(Color.WHITE);

        JLabel title = new JLabel("Paramètres du profil");
        title.setFont(new Font("Tahoma", Font.BOLD, 16));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        main.add(title);
        main.add(Box.createVerticalStrut(20));

        // Avatar Preview
        avatarPreview = new JLabel();
        avatarPreview.setPreferredSize(new Dimension(120, 120));
        avatarPreview.setMaximumSize(new Dimension(120, 120));
        avatarPreview.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        avatarPreview.setAlignmentX(Component.CENTER_ALIGNMENT);
        updateAvatarPreview();
        main.add(avatarPreview);
        main.add(Box.createVerticalStrut(10));

        JButton changeBtn = new WindowsXPButton("Changer la photo");
        changeBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        changeBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int r = chooser.showOpenDialog(this);
            if (r == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                try {
                    byte[] data = Files.readAllBytes(f.toPath());
                    String b64 = Base64.getEncoder().encodeToString(data);
                    client.sendMessage("/setavatar " + b64);
                    
                    // Optimistically update preview if it's small enough, 
                    // or wait for server AVATAR_DATA? 
                    // Better to rely on AVATAR_DATA broadcast/response if implement.
                    // For now, let's keep it simple.
                    ImageIcon icon = new ImageIcon(data);
                    this.currentAvatar = icon.getImage();
                    updateAvatarPreview();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Erreur lors du chargement de l'image.");
                }
            }
        });
        main.add(changeBtn);
        main.add(Box.createVerticalStrut(20));

        // Status Message
        JLabel statusLabel = new JLabel("Message personnel :");
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        main.add(statusLabel);
        
        statusMessageField = new JTextField(currentStatusMsg);
        statusMessageField.setMaximumSize(new Dimension(300, 30));
        main.add(statusMessageField);
        main.add(Box.createVerticalStrut(20));

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setOpaque(false);
        JButton closeBtn = new WindowsXPButton("Fermer");
        closeBtn.addActionListener(e -> {
            // Save status message if changed?
            String newMsg = statusMessageField.getText();
            if (!newMsg.equals(currentStatusMsg)) {
                client.sendMessage("/setmsg " + newMsg);
            }
            dispose();
        });
        buttons.add(closeBtn);

        add(main, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void updateAvatarPreview() {
        if (currentAvatar != null) {
            Image scaled = currentAvatar.getScaledInstance(120, 120, Image.SCALE_SMOOTH);
            avatarPreview.setIcon(new ImageIcon(scaled));
        } else {
            avatarPreview.setText("Pas d'avatar");
            avatarPreview.setHorizontalAlignment(SwingConstants.CENTER);
        }
    }
}
