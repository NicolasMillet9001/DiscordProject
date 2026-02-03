package fr.unilasalle.chat.server;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Vector;

public class AdminGUI extends JFrame {

    private static final String DB_URL = "jdbc:sqlite:users.db";
    private JTabbedPane tabbedPane;

    public AdminGUI() {
        setTitle("DiscordJava - Database Admin");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main Layout
        setLayout(new BorderLayout());

        // Tabs
        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        // Add tabs for each table
        addTableTab("Users", "users");
        addTableTab("Messages", "messages");
        addTableTab("Friends", "friends");
        addTableTab("Private Messages", "private_messages");

        // Toolbar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        
        JButton refreshBtn = new JButton("Rafraîchir");
        refreshBtn.addActionListener(e -> refreshAllTabs());
        toolBar.add(refreshBtn);

        add(toolBar, BorderLayout.NORTH);

        setVisible(true);
    }

    private void addTableTab(String title, String tableName) {
        JPanel panel = new JPanel(new BorderLayout());
        
        DefaultTableModel model = new DefaultTableModel();
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Load initial data
        loadData(tableName, model);
        
        // Store table name in client property for refresh
        panel.putClientProperty("table_name", tableName);
        panel.putClientProperty("table_model", model);
        
        tabbedPane.addTab(title, panel);
    }

    private void refreshAllTabs() {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            JPanel panel = (JPanel) tabbedPane.getComponentAt(i);
            String tableName = (String) panel.getClientProperty("table_name");
            DefaultTableModel model = (DefaultTableModel) panel.getClientProperty("table_model");
            if (tableName != null && model != null) {
                loadData(tableName, model);
            }
        }
    }

    private void loadData(String tableName, DefaultTableModel model) {
        model.setRowCount(0); // Clear existing data
        model.setColumnCount(0);

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Add columns
            for (int i = 1; i <= columnCount; i++) {
                model.addColumn(metaData.getColumnName(i));
            }

            // Add rows
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                model.addRow(row);
            }

        } catch (SQLException e) {
            // Table might not exist yet or error
            System.err.println("Error loading table " + tableName + ": " + e.getMessage());
            model.setColumnCount(1);
            model.addColumn("Error / Empty");
            model.addRow(new Object[]{"Could not load data (Table may not exist yet)"});
        }
    }

    public static void main(String[] args) {
        // Load SQLite driver
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(null, "SQLite JDBC Driver not found!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            // Apply a nice Look and Feel if possible
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            
            new AdminGUI();
        });
    }
}
