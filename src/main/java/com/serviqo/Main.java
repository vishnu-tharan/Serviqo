package com.serviqo;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

public final class Main {
    static final Color NAVY = Color.decode("#172B4D");
    private static final Color BLUE = Color.decode("#2563EB");
    private static final Color ORANGE = Color.decode("#F59E0B");
    private static final Color BACKGROUND = Color.decode("#F4F6F9");
    static final Color TEXT = Color.decode("#252525");

    private Main() {
    }

    public static void main(String[] args) {
        boolean demo = args.length == 1 && "--demo".equals(args[0]);
        Path legacy = null;
        try {
            if (args.length == 2 && "--import-legacy".equals(args[0])) {
                legacy = Path.of(args[1]);
            } else if (args.length != 0 && !demo) {
                throw new IllegalArgumentException("Usage: Serviqo [--demo | --import-legacy <absolute path>]");
            }
            Database.initialize(demo, legacy);
        } catch (SQLException | IOException | IllegalArgumentException exception) {
            System.err.println("Serviqo database startup failed. " + exception.getMessage());
            String fallback = "Serviqo could not open its database. Check the startup instructions and terminal output.";
            String message = exception instanceof SQLException databaseError
                    ? Database.userError(databaseError, fallback) : fallback;
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null, message, "Serviqo startup error", JOptionPane.ERROR_MESSAGE));
            return;
        }
        SwingUtilities.invokeLater(Main::signInAndOpenWindow);
    }

    private static void signInAndOpenWindow() {
        if (!StaffLogin.open()) {
            return;
        }
        JFrame window = new JFrame("Serviqo - Vehicle Service & Repair Management System");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setMinimumSize(new Dimension(820, 500));
        window.setSize(1020, 660);
        window.setLocationRelativeTo(null);
        window.setContentPane(createContent());
        JMenuBar menuBar = new JMenuBar();
        JMenu staffMenu = new JMenu("Staff");
        if (StaffAccounts.isManager()) {
            JMenuItem addStaff = new JMenuItem("Add staff account");
            addStaff.addActionListener(event -> StaffLogin.createAccount(window, false));
            staffMenu.add(addStaff);
            JMenuItem audit = new JMenuItem("View audit log");
            audit.addActionListener(event -> StaffLogin.showAudit(window));
            staffMenu.add(audit);
        }
        JMenuItem signOut = new JMenuItem("Sign out");
        signOut.addActionListener(event -> {
            StaffAccounts.signOut();
            window.dispose();
            signInAndOpenWindow();
        });
        staffMenu.add(signOut);
        menuBar.add(staffMenu);
        window.setJMenuBar(menuBar);
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
        if (StaffAccounts.isManager()) {
            CustomerPanel customers = new CustomerPanel();
            addScreen(sidebar, navigation, screens, layout, "Customers", customers, customers::refresh);
            VehiclePanel vehicles = new VehiclePanel();
            addScreen(sidebar, navigation, screens, layout, "Vehicles", vehicles, vehicles::refresh);
        }
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
        DefaultTableCellRenderer text = new DefaultTableCellRenderer();
        text.putClientProperty("html.disable", Boolean.TRUE);
        DefaultTableCellRenderer number = new DefaultTableCellRenderer();
        number.putClientProperty("html.disable", Boolean.TRUE);
        number.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int column = 0; column < table.getColumnCount(); column++) {
            boolean numeric = Number.class.isAssignableFrom(table.getModel().getColumnClass(column));
            table.getColumnModel().getColumn(column).setCellRenderer(numeric ? number : text);
        }
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.setGridColor(Color.decode("#E1E6EF"));
        table.setSelectionBackground(Color.decode("#DCE9FF"));
        table.setSelectionForeground(NAVY);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
    }

    static void usePlainText(JComboBox<?> comboBox) {
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        comboBox.setRenderer(renderer);
    }

    static void limitText(JTextComponent field, int max) {
        field.setToolTipText("Maximum " + max + " characters");
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
                    throws BadLocationException {
                replace(bypass, offset, 0, text, attributes);
            }

            @Override
            public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
                    throws BadLocationException {
                var document = bypass.getDocument();
                String incoming = text == null ? "" : text;
                if (incoming.length() > max * 2 || incoming.indexOf('\0') >= 0) {
                    field.getToolkit().beep();
                    return;
                }
                int current = document.getLength();
                int updated = current - length + incoming.length();
                if (!(field instanceof JPasswordField) && updated > max) {
                    String previous = document.getText(0, current);
                    String next = previous.substring(0, offset) + incoming + previous.substring(offset + length);
                    current = previous.codePointCount(0, previous.length());
                    updated = next.codePointCount(0, next.length());
                }
                if (updated <= max || updated < current) {
                    bypass.replace(offset, length, incoming, attributes);
                } else {
                    field.getToolkit().beep();
                }
            }
        });
    }

    static Timer watchSearch(JTextField search, Runnable refresh) {
        limitText(search, Database.NAME);
        Timer debounce = new Timer(250, event -> refresh.run());
        debounce.setRepeats(false);
        search.addActionListener(event -> {
            debounce.stop();
            refresh.run();
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { debounce.restart(); }
            @Override public void removeUpdate(DocumentEvent event) { debounce.restart(); }
            @Override public void changedUpdate(DocumentEvent event) { debounce.restart(); }
        });
        return debounce;
    }

    static final class PageControls extends JPanel {
        private final JButton previous = new JButton("Previous");
        private final JButton next = new JButton("Next");
        private final JLabel page = new JLabel("Page 1");
        private final LongConsumer load;
        private long offset;

        PageControls(BiConsumer<PageControls, Long> load) {
            super(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            this.load = offset -> load.accept(this, offset);
            setOpaque(false);
            add(previous);
            add(page);
            add(next);
            previous.addActionListener(event -> this.load.accept(offset - Database.PAGE_SIZE));
            next.addActionListener(event -> this.load.accept(offset + Database.PAGE_SIZE));
            update(0, 0);
        }

        void reset() {
            load.accept(0);
        }

        void update(long loadedOffset, int rows) {
            offset = loadedOffset;
            page.setText("Page " + (offset / Database.PAGE_SIZE + 1));
            previous.setEnabled(offset > 0);
            next.setEnabled(rows > Database.PAGE_SIZE);
        }
    }

    @FunctionalInterface
    interface RecordSearch<T> {
        List<T> find(String text, long offset) throws SQLException;
    }

    static <T> T pickRecord(JComponent parent, String title, String prompt, RecordSearch<T> query,
                            Function<T, String> display) {
        JTextField search = new JTextField(24);
        JList<T> options = new JList<>();
        options.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        options.setCellRenderer(new DefaultListCellRenderer() {
            {
                putClientProperty("html.disable", Boolean.TRUE);
            }

            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focused) {
                @SuppressWarnings("unchecked")
                T option = (T) value;
                return super.getListCellRendererComponent(list, option == null ? "" : display.apply(option),
                        index, selected, focused);
            }
        });
        JLabel status = new JLabel(" ");
        PageControls pages = new PageControls((controls, offset) -> {
            List<T> results;
            String text = search.getText().strip();
            try {
                results = query.find(text, offset);
                status.setText(results.isEmpty() ? "No matching records." : " ");
            } catch (SQLException exception) {
                System.err.println("Could not search for a record. " + exception.getMessage());
                results = List.of();
                status.setText(Database.userError(exception, "Records could not be loaded."));
            }
            controls.update(offset, results.size());
            var model = new javax.swing.DefaultListModel<T>();
            model.addAll(results.subList(0, Math.min(results.size(), Database.PAGE_SIZE)));
            options.setModel(model);
            options.putClientProperty("loadedSearchText", text);
        });
        JPanel form = new JPanel(new BorderLayout(0, 8));
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        JLabel label = new JLabel(prompt);
        label.setLabelFor(search);
        searchRow.add(label, BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        form.add(searchRow, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(options);
        scroll.setPreferredSize(new Dimension(475, 220));
        form.add(scroll, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(status, BorderLayout.CENTER);
        footer.add(pages, BorderLayout.EAST);
        form.add(footer, BorderLayout.SOUTH);
        Timer debounce = watchSearch(search, pages::reset);
        pages.reset();
        try {
            while (JOptionPane.showConfirmDialog(parent, form, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
                if (!search.getText().strip().equals(options.getClientProperty("loadedSearchText"))) {
                    pages.reset();
                }
                if (options.getSelectedValue() != null) {
                    return options.getSelectedValue();
                }
                JOptionPane.showMessageDialog(parent, "Select a record from the results.");
            }
            return null;
        } finally {
            debounce.stop();
        }
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
