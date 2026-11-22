package com.serviqo;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public final class Main {
    static final Color NAVY = Color.decode("#172B4D");
    private static final Color BLUE = Color.decode("#2563EB");
    private static final Color ORANGE = Color.decode("#F59E0B");
    private static final Color BACKGROUND = Color.decode("#F4F6F9");
    static final Color TEXT = Color.decode("#252525");

    private Main() {
    }

    public static void main(String[] args) {
        try {
            Database.initialize();
        } catch (SQLException exception) {
            String message = "Serviqo could not initialize its database.\n" + exception.getMessage();
            System.err.println(message);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null, message, "Serviqo startup error", JOptionPane.ERROR_MESSAGE));
            return;
        }
        SwingUtilities.invokeLater(Main::showWindow);
    }

    private static void showWindow() {
        JFrame window = new JFrame("Serviqo - Vehicle Service & Repair Management System");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setMinimumSize(new Dimension(820, 500));
        window.setSize(1020, 660);
        window.setLocationRelativeTo(null);
        window.setContentPane(createContent());
        window.setVisible(true);
    }

    static JPanel createContent() {
        CardLayout layout = new CardLayout();
        JPanel screens = new JPanel(layout);
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(NAVY);
        sidebar.setPreferredSize(new Dimension(200, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(24, 14, 20, 14));

        JLabel brand = new JLabel("Serviqo");
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 27f));
        brand.setAlignmentX(0f);
        JLabel tagline = new JLabel("Every Service. Perfectly Tracked.");
        tagline.setForeground(ORANGE);
        tagline.setFont(tagline.getFont().deriveFont(11f));
        tagline.setAlignmentX(0f);
        sidebar.add(brand);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(36));

        ButtonGroup navigation = new ButtonGroup();
        DashboardPanel dashboard = new DashboardPanel();
        addScreen(sidebar, navigation, screens, layout, "Dashboard", dashboard, dashboard::refresh);
        CustomerPanel customers = new CustomerPanel();
        addScreen(sidebar, navigation, screens, layout, "Customers", customers, customers::refresh);
        VehiclePanel vehicles = new VehiclePanel();
        addScreen(sidebar, navigation, screens, layout, "Vehicles", vehicles, vehicles::refresh);
        ServicePanel services = new ServicePanel(false);
        addScreen(sidebar, navigation, screens, layout, "Service Jobs", services, services::refresh);
        ServicePanel history = new ServicePanel(true);
        addScreen(sidebar, navigation, screens, layout, "Service History", history, history::refresh);

        JPanel content = new JPanel(new BorderLayout());
        content.add(sidebar, BorderLayout.WEST);
        content.add(screens, BorderLayout.CENTER);
        dashboard.refresh();
        return content;
    }

    private static void addScreen(JPanel sidebar, ButtonGroup navigation, JPanel screens,
                                  CardLayout layout, String title, JPanel body, Runnable onOpen) {
        JToggleButton button = new JToggleButton(title);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.setForeground(Color.WHITE);
        button.setBackground(NAVY);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(0f);
        button.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        button.setPreferredSize(new Dimension(170, 42));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.addItemListener(event -> button.setBackground(button.isSelected() ? BLUE : NAVY));
        button.addActionListener(event -> {
            layout.show(screens, title);
            onOpen.run();
        });
        navigation.add(button);
        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(5));
        if (navigation.getSelection() == null) {
            button.setSelected(true);
        }

        JPanel screen = new JPanel(new BorderLayout(0, 14));
        screen.setBackground(BACKGROUND);
        screen.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JLabel heading = new JLabel(title);
        heading.setForeground(TEXT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 30f));
        screen.add(heading, BorderLayout.NORTH);

        screen.add(body, BorderLayout.CENTER);
        screens.add(screen, title);
    }

    static void styleTable(JTable table) {
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.setGridColor(Color.decode("#E1E6EF"));
        table.setSelectionBackground(Color.decode("#DCE9FF"));
        table.setSelectionForeground(NAVY);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
    }

    static void addField(JPanel form, int row, String text, JComponent field) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field instanceof JScrollPane scrollPane ? scrollPane.getViewport().getView() : field);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(4, 0, 4, 12);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 4, 0);
        form.add(field, constraints);
    }
}
