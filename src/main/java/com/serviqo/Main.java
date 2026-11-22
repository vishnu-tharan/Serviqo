package com.serviqo;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public final class Main {
    private static final Color NAVY = Color.decode("#172B4D");
    private static final Color BLUE = Color.decode("#2563EB");
    private static final Color ORANGE = Color.decode("#F59E0B");
    private static final Color BACKGROUND = Color.decode("#F4F6F9");
    private static final Color TEXT = Color.decode("#252525");

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
        window.setMinimumSize(new Dimension(700, 460));
        window.setSize(1020, 660);
        window.setLocationRelativeTo(null);

        CardLayout layout = new CardLayout();
        JPanel screens = new JPanel(layout);
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(NAVY);
        sidebar.setPreferredSize(new Dimension(215, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(28, 16, 20, 16));

        JLabel brand = new JLabel("Serviqo");
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 27f));
        JLabel tagline = new JLabel("Every Service. Perfectly Tracked.");
        tagline.setForeground(ORANGE);
        tagline.setFont(tagline.getFont().deriveFont(11f));
        sidebar.add(brand);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(36));

        ButtonGroup navigation = new ButtonGroup();
        addScreen(sidebar, navigation, screens, layout, "Dashboard", "Database ready. Choose a section from the sidebar.");
        CustomerPanel customers = new CustomerPanel();
        addScreen(sidebar, navigation, screens, layout, "Customers", customers, customers::refresh);
        VehiclePanel vehicles = new VehiclePanel();
        addScreen(sidebar, navigation, screens, layout, "Vehicles", vehicles, vehicles::refresh);
        ServicePanel services = new ServicePanel();
        addScreen(sidebar, navigation, screens, layout, "Service Jobs", services, services::refresh);
        addScreen(sidebar, navigation, screens, layout, "Service History", "Service history is planned for Phase 7.");

        window.add(sidebar, BorderLayout.WEST);
        window.add(screens, BorderLayout.CENTER);
        window.setVisible(true);
    }

    private static void addScreen(JPanel sidebar, ButtonGroup navigation, JPanel screens,
                                  CardLayout layout, String title, String message) {
        JLabel detail = new JLabel(message);
        detail.setForeground(TEXT);
        detail.setFont(detail.getFont().deriveFont(15f));
        detail.setOpaque(true);
        detail.setBackground(Color.WHITE);
        detail.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(detail, BorderLayout.NORTH);
        addScreen(sidebar, navigation, screens, layout, title, body, null);
    }

    private static void addScreen(JPanel sidebar, ButtonGroup navigation, JPanel screens,
                                  CardLayout layout, String title, JPanel body, Runnable onOpen) {
        JToggleButton button = new JToggleButton(title);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.setForeground(Color.WHITE);
        button.setBackground(NAVY);
        button.setOpaque(true);
        button.setContentAreaFilled(false);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.addItemListener(event -> button.setBackground(button.isSelected() ? BLUE : NAVY));
        button.addActionListener(event -> {
            layout.show(screens, title);
            if (onOpen != null) {
                onOpen.run();
            }
        });
        navigation.add(button);
        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(5));
        if (navigation.getSelection() == null) {
            button.setSelected(true);
        }

        JPanel screen = new JPanel(new BorderLayout(0, 20));
        screen.setBackground(BACKGROUND);
        screen.setBorder(BorderFactory.createEmptyBorder(36, 36, 36, 36));
        JLabel heading = new JLabel(title);
        heading.setForeground(TEXT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 30f));
        screen.add(heading, BorderLayout.NORTH);

        screen.add(body, BorderLayout.CENTER);
        screens.add(screen, title);
    }
}
