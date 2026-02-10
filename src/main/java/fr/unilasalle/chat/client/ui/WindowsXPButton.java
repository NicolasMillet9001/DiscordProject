package fr.unilasalle.chat.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

public class WindowsXPButton extends JButton {

    private boolean isHovered = false;
    private boolean isPressed = false;

    public WindowsXPButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setFocusPainted(true); // We will paint focus ourselves or let super handle it if we want dotted line
        setBorderPainted(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(4, 14, 4, 14)); // XP buttons have some padding

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                isPressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // Colors based on generic XP theme analysis
        Color borderColor = new Color(0, 60, 116); // Dark blue outer border
        Color gradientTop = new Color(255, 255, 255);
        Color gradientBottom = new Color(212, 212, 212); // Standard gray
        
        if (isPressed) {
            gradientTop = new Color(200, 200, 200);
            gradientBottom = new Color(230, 230, 230);
        } else if (isHovered) {
             // Warmer hover effect
             gradientTop = new Color(255, 255, 255);
             gradientBottom = new Color(235, 235, 235); // lighter
             borderColor = new Color(60, 127, 177); // Lighter blue on hover
        }

        // Draw outer shape (Rounded Rectangle)
        // Use 0.5f offset to align stroke centers to pixel grid and avoid clipping
        // Stroke width 1.0 covers 0.0-1.0 (pixel 0) when centered at 0.5
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, 6, 6);

        // Fill Background
        GradientPaint gp = new GradientPaint(0, 0, gradientTop, 0, height, gradientBottom);
        g2.setPaint(gp);
        g2.fill(shape);

        // Highlight/Inner Shadow (simulated 3D look)
        // Top inner white line
        // Offset by 1px from the outer border (which is at 0.5), so start at 1.5
        // Width is width - 1 (outer) - 2 (left/right inset) = width - 3
        g2.setColor(new Color(255, 255, 255, 100));
        g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, width - 3f, height - 3f, 6, 6));

        // Draw Border
        g2.setColor(borderColor);
        g2.draw(shape);
        
        // Focus ring (Dotted rectangle)
        if (hasFocus() && !isPressed) {
            Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{1, 1}, 0);
            g2.setStroke(dashed);
            g2.setColor(Color.BLACK);
            g2.drawRect(3, 3, width - 7, height - 7);
        }

        g2.dispose();
        
        // Delegate text and icon painting to standard LookAndFeel (handles HTML)
        super.paintComponent(g);
    }
}
