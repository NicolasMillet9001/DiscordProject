package fr.unilasalle.chat.client.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

public class CallWindow extends JFrame {
    private String partner;
    private ActionListener onHangup;
    private ActionListener onScreenShare;
    private ActionListener onCamera;
    
    private VideoPanel videoPanel;
    private JLabel statusLabel;
    private Image partnerAvatar;
    private Image defaultAvatar;
    
    public CallWindow(String partner, ActionListener onHangup, ActionListener onScreenShare, ActionListener onCamera) {
        this.partner = partner;
        this.onHangup = onHangup;
        this.onScreenShare = onScreenShare;
        this.onCamera = onCamera;
        
        // Prepare default avatar
        BufferedImage defImg = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = defImg.createGraphics();
        g2.setColor(new Color(100, 100, 100));
        g2.fillRect(0, 0, 200, 200);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 80));
        String initials = partner.length() > 0 ? partner.substring(0, 1).toUpperCase() : "?";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(initials, (200 - fm.stringWidth(initials)) / 2, (200 + fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
        this.defaultAvatar = defImg;
        this.partnerAvatar = defaultAvatar;

        setTitle("Appel avec " + partner);
        setSize(400, 350);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(new Color(40, 40, 40)); // Dark theme
        
        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER));
        header.setOpaque(false);
        JLabel title = new JLabel("En appel avec " + partner);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Arial", Font.BOLD, 16));
        header.add(title);
        
        statusLabel = new JLabel("Audio Connecté");
        statusLabel.setForeground(Color.GREEN);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        
        JPanel headerContainer = new JPanel(new BorderLayout());
        headerContainer.setOpaque(false);
        headerContainer.add(header, BorderLayout.CENTER);
        JPanel statusP = new JPanel(new FlowLayout(FlowLayout.CENTER));
        statusP.setOpaque(false);
        statusP.add(statusLabel);
        headerContainer.add(statusP, BorderLayout.SOUTH);
        
        main.add(headerContainer, BorderLayout.NORTH);
        
        // Video Area
        videoPanel = new VideoPanel();
        main.add(videoPanel, BorderLayout.CENTER);
        
        // Controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        controls.setOpaque(false);
        
        JButton camBtn = createCircleButton("Caméra", new Color(100, 100, 100));
        camBtn.addActionListener(e -> {
            onCamera.actionPerformed(e);
            toggleState(camBtn);
        });
        
        JButton screenBtn = createCircleButton("Ecran", new Color(100, 100, 100));
        screenBtn.addActionListener(e -> {
            onScreenShare.actionPerformed(e);
            toggleState(screenBtn);
        });
        
        JButton hangupBtn = createCircleButton("Fin", Color.RED);
        hangupBtn.addActionListener(onHangup);
        
        controls.add(camBtn);
        controls.add(screenBtn);
        controls.add(hangupBtn);
        
        main.add(controls, BorderLayout.SOUTH);
        
        setContentPane(main);
        setVisible(true);
    }
    
    private JButton createCircleButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension(80, 40));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        return btn;
    }
    
    private void toggleState(JButton btn) {
        if (btn.getBackground().equals(Color.GREEN)) {
             btn.setBackground(new Color(100, 100, 100));
        } else {
             btn.setBackground(Color.GREEN);
        }
    }
    
    public void setPartnerAvatar(Image img) {
        this.partnerAvatar = img != null ? img : defaultAvatar;
        videoPanel.repaint();
    }

    public void updateVideo(Image img) {
        videoPanel.setFrame(img);
    }

    public void clearVideo() {
        videoPanel.setFrame(null);
    }
    
    public void setStatus(String status) {
        statusLabel.setText(status);
    }
    
    public void close() {
        dispose();
    }

    private class VideoPanel extends JPanel {
        private Image currentFrame;

        public VideoPanel() {
            setBackground(Color.BLACK);
            setBorder(BorderFactory.createLineBorder(Color.GRAY));
        }

        public void setFrame(Image frame) {
            this.currentFrame = frame;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (currentFrame != null) {
                // Fill with black first
                g2.drawImage(currentFrame, 0, 0, getWidth(), getHeight(), null);
            } else if (partnerAvatar != null) {
                // Draw avatar centered
                int size = Math.min(getWidth(), getHeight()) / 2;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                // Draw circular mask? Maybe just draw centered for now
                g2.drawImage(partnerAvatar, x, y, size, size, null);
                
                g2.setColor(Color.GRAY);
                g2.setFont(new Font("Arial", Font.PLAIN, 12));
                String text = partner + " est en audio uniquement";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, y + size + 20);
            }
        }
    }
}
