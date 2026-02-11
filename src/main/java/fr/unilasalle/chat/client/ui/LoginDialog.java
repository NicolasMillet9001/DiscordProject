package fr.unilasalle.chat.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LoginDialog extends JDialog {
    private JTextField usernameField;
    private JTextField ipField;
    private JTextField portField;
    private JPasswordField passwordField;
    private boolean succeeded = false;
    private boolean registerMode = false;

    public LoginDialog(Frame parent) {
        super(parent, "MSN Messenger - Connexion", true);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(MsnTheme.BACKGROUND);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20)); // Margin
        GridBagConstraints cs = new GridBagConstraints();
        cs.fill = GridBagConstraints.HORIZONTAL;
        
        // Header Graphic (Simulated with Label)
        JLabel logo = new JLabel("Service MSN");
        logo.setFont(new Font("Trebuchet MS", Font.BOLD, 16));
        logo.setForeground(MsnTheme.HEADER_TOP);
        cs.gridx = 0;
        cs.gridy = 0;
        cs.gridwidth = 3;
        cs.insets = new Insets(0, 0, 15, 0);
        panel.add(logo, cs);

        // --- Username ---
        JLabel userLabel = new JLabel("Adresse de messagerie :");
        userLabel.setFont(MsnTheme.FONT_MAIN);
        cs.gridx = 0;
        cs.gridy = 1;
        cs.gridwidth = 1;
        cs.insets = new Insets(5, 5, 5, 5);
        panel.add(userLabel, cs);

        usernameField = new JTextField(20);
        styleTextField(usernameField);
        cs.gridx = 1;
        cs.gridy = 1;
        cs.gridwidth = 2;
        panel.add(usernameField, cs);

        // --- Password ---
        JLabel passLabel = new JLabel("Mot de passe :");
        passLabel.setFont(MsnTheme.FONT_MAIN);
        cs.gridx = 0;
        cs.gridy = 2;
        cs.gridwidth = 1;
        panel.add(passLabel, cs);

        passwordField = new JPasswordField(20);
        styleTextField(passwordField);
        cs.gridx = 1;
        cs.gridy = 2;
        cs.gridwidth = 2;
        panel.add(passwordField, cs);

        // --- Server IP ---
        JLabel ipLabel = new JLabel("IP du serveur :");
        ipLabel.setFont(MsnTheme.FONT_MAIN);
        cs.gridx = 0;
        cs.gridy = 3;
        cs.gridwidth = 1;
        panel.add(ipLabel, cs);

        ipField = new JTextField("172.21.200.235", 20);
        styleTextField(ipField);
        cs.gridx = 1;
        cs.gridy = 3;
        cs.gridwidth = 2;
        panel.add(ipField, cs);

        // --- Port ---
        JLabel portLabel = new JLabel("Port :");
        portLabel.setFont(MsnTheme.FONT_MAIN);
        cs.gridx = 0;
        cs.gridy = 4;
        cs.gridwidth = 1;
        panel.add(portLabel, cs);

        portField = new JTextField("5001", 10);
        styleTextField(portField);
        cs.gridx = 1;
        cs.gridy = 4;
        cs.gridwidth = 2;
        panel.add(portField, cs);

        // --- Buttons ---
        JButton btnLogin = new WindowsXPButton("Se connecter");

        JButton btnRegister = new WindowsXPButton("S'inscrire");
        // Make register look like a link or secondary button? 
        // For now, standard button but maybe different text color?
        
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

        JButton btnCancel = new WindowsXPButton("Annuler");
        btnCancel.addActionListener(e -> dispose());
        
        JPanel bp = new JPanel();
        bp.setBackground(MsnTheme.BACKGROUND);
        bp.add(btnLogin);
        bp.add(btnCancel);
        
        // Register button on separate row?
        JPanel registerPanel = new JPanel();
        registerPanel.setBackground(MsnTheme.BACKGROUND);
        registerPanel.add(btnRegister);

        getContentPane().add(panel, BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(bp, BorderLayout.CENTER);
        bottomPanel.add(registerPanel, BorderLayout.SOUTH);
        
        getContentPane().add(bottomPanel, BorderLayout.PAGE_END);

        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private boolean validateInput() {
        if (getUsername().isEmpty() || getPassword().isEmpty()) {
            JOptionPane.showMessageDialog(LoginDialog.this,
                    "Le nom d'utilisateur et le mot de passe sont requis.",
                    "Erreur",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private void styleTextField(JTextField tf) {
        tf.setBackground(Color.WHITE);
        tf.setForeground(MsnTheme.TEXT_NORMAL);
        tf.setCaretColor(Color.BLACK);
        tf.setFont(MsnTheme.FONT_MAIN);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(MsnTheme.BORDER_COLOR),
                BorderFactory.createEmptyBorder(3, 3, 3, 3)));
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
