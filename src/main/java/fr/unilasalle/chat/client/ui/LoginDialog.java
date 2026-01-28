package fr.unilasalle.chat.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginDialog extends JDialog {
    private JTextField usernameField;
    private JTextField ipField;
    private JTextField portField;
    private JPasswordField passwordField;
    private boolean succeeded = false;
    private boolean registerMode = false;

    public LoginDialog(Frame parent) {
        super(parent, "Connect to Server", true);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(DiscordTheme.BACKGROUND);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20)); // Margin
        GridBagConstraints cs = new GridBagConstraints();
        cs.fill = GridBagConstraints.HORIZONTAL;

        // --- Username ---
        JLabel userLabel = new JLabel("Username:");
        userLabel.setForeground(DiscordTheme.TEXT_NORMAL);
        cs.gridx = 0;
        cs.gridy = 0;
        cs.gridwidth = 1;
        panel.add(userLabel, cs);

        usernameField = new JTextField(20);
        styleTextField(usernameField);
        cs.gridx = 1;
        cs.gridy = 0;
        cs.gridwidth = 2;
        panel.add(usernameField, cs);

        // --- Password ---
        JLabel passLabel = new JLabel("Password:");
        passLabel.setForeground(DiscordTheme.TEXT_NORMAL);
        cs.gridx = 0;
        cs.gridy = 1;
        cs.gridwidth = 1;
        panel.add(passLabel, cs);

        passwordField = new JPasswordField(20);
        styleTextField(passwordField);
        cs.gridx = 1;
        cs.gridy = 1;
        cs.gridwidth = 2;
        panel.add(passwordField, cs);

        // --- Server IP ---
        JLabel ipLabel = new JLabel("Server IP:");
        ipLabel.setForeground(DiscordTheme.TEXT_NORMAL);
        cs.gridx = 0;
        cs.gridy = 2;
        cs.gridwidth = 1;
        panel.add(ipLabel, cs);

        ipField = new JTextField("localhost", 20);
        styleTextField(ipField);
        cs.gridx = 1;
        cs.gridy = 2;
        cs.gridwidth = 2;
        panel.add(ipField, cs);

        // --- Port ---
        JLabel portLabel = new JLabel("Port:");
        portLabel.setForeground(DiscordTheme.TEXT_NORMAL);
        cs.gridx = 0;
        cs.gridy = 3;
        cs.gridwidth = 1;
        panel.add(portLabel, cs);

        portField = new JTextField("5001", 10);
        styleTextField(portField);
        cs.gridx = 1;
        cs.gridy = 3;
        cs.gridwidth = 2;
        panel.add(portField, cs);

        // --- Buttons ---
        JButton btnLogin = new JButton("Login");
        styleButton(btnLogin, DiscordTheme.ACTION_BG);

        JButton btnRegister = new JButton("Register");
        styleButton(btnRegister, DiscordTheme.HIGHLIGHT);

        btnLogin.addActionListener(e -> {
            if (validateInput()) {
                registerMode = false;
                succeeded = true;
                dispose();
            }
        });

        btnRegister.addActionListener(e -> {
            if (validateInput()) {
                registerMode = true;
                succeeded = true;
                dispose();
            }
        });

        JButton btnCancel = new JButton("Cancel");
        styleButton(btnCancel, Color.GRAY);
        btnCancel.addActionListener(e -> dispose());

        JPanel bp = new JPanel();
        bp.setBackground(DiscordTheme.BACKGROUND);
        bp.add(btnLogin);
        bp.add(btnRegister);
        bp.add(btnCancel);

        getContentPane().add(panel, BorderLayout.CENTER);
        getContentPane().add(bp, BorderLayout.PAGE_END);

        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private boolean validateInput() {
        if (getUsername().isEmpty() || getPassword().isEmpty()) {
            JOptionPane.showMessageDialog(LoginDialog.this,
                    "Username and Password are required.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private void styleTextField(JTextField tf) {
        tf.setBackground(DiscordTheme.INPUT_BG);
        tf.setForeground(DiscordTheme.TEXT_NORMAL);
        tf.setCaretColor(Color.WHITE);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DiscordTheme.SIDEBAR),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setOpaque(true);
        btn.setBorderPainted(false);
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    public String getIP() {
        return ipField.getText().trim();
    }

    public int getPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return 5001;
        }
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public boolean isRegisterMode() {
        return registerMode;
    }
}
