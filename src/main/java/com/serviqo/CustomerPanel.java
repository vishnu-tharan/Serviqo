package com.serviqo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

final class CustomerPanel extends JPanel {
    private final CustomerRepository repository = new CustomerRepository();
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Full name", "Phone", "Email", "Address", "Registered (UTC)"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Long.class : String.class;
        }
    };
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = new JTextField();
    private final JLabel status = new JLabel(" ");
    private final JButton edit = new JButton("Edit");
    private final JButton delete = new JButton("Delete");
    private List<CustomerRepository.Customer> customers = List.of();

    CustomerPanel() {
        super(new BorderLayout(0, 12));
        setOpaque(false);

        JPanel searchBar = new JPanel(new BorderLayout(10, 0));
        searchBar.setOpaque(false);
        JLabel searchLabel = new JLabel("Search by name or phone");
        searchLabel.setLabelFor(search);
        searchBar.add(searchLabel, BorderLayout.WEST);
        searchBar.add(search, BorderLayout.CENTER);
        add(searchBar, BorderLayout.NORTH);

        table.setRowSorter(sorter);
        Main.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(4).setPreferredWidth(150);
        table.getColumnModel().getColumn(5).setPreferredWidth(130);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton add = new JButton("Add customer");
        actions.add(add);
        actions.add(edit);
        actions.add(delete);
        footer.add(actions, BorderLayout.NORTH);
        status.setForeground(Color.decode("#344054"));
        footer.add(status, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        add.addActionListener(event -> showForm(null));
        edit.addActionListener(event -> showForm(selectedCustomer()));
        delete.addActionListener(event -> deleteSelectedCustomer());
        table.getSelectionModel().addListSelectionListener(event -> updateSelectionButtons());
        updateSelectionButtons();

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filterCustomers();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                filterCustomers();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                filterCustomers();
            }
        });
    }

    void refresh() {
        try {
            customers = repository.findAll();
        } catch (SQLException exception) {
            customers = List.of();
            model.setRowCount(0);
            status.setText("Customer records could not be loaded.");
            showDatabaseError("Could not load customers.", exception);
            return;
        }

        model.setRowCount(0);
        for (CustomerRepository.Customer customer : customers) {
            model.addRow(new Object[]{customer.id(), customer.name(), customer.phone(),
                    customer.email(), customer.address(), customer.createdAt()});
        }
        filterCustomers();
    }

    private void filterCustomers() {
        String query = search.getText().strip();
        sorter.setRowFilter(query.isEmpty() ? null : RowFilter.regexFilter("(?iu)" + Pattern.quote(query), 1, 2));
        int count = table.getRowCount();
        if (count == 0) {
            status.setText(query.isEmpty() ? "No customers yet." : "No matching customers.");
        } else {
            status.setText(count == 1 ? "1 customer" : count + " customers");
        }
    }

    private CustomerRepository.Customer selectedCustomer() {
        int row = table.getSelectedRow();
        return row < 0 ? null : customers.get(table.convertRowIndexToModel(row));
    }

    private void updateSelectionButtons() {
        boolean selected = table.getSelectedRow() >= 0;
        edit.setEnabled(selected);
        delete.setEnabled(selected);
    }

    private void showForm(CustomerRepository.Customer customer) {
        JTextField name = new JTextField(customer == null ? "" : customer.name(), 24);
        JTextField phone = new JTextField(customer == null ? "" : customer.phone(), 24);
        JTextField email = new JTextField(customer == null ? "" : customer.email(), 24);
        JTextField address = new JTextField(customer == null ? "" : customer.address(), 24);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        Main.addField(form, 0, "Full name", name);
        Main.addField(form, 1, "Phone number", phone);
        Main.addField(form, 2, "Email (optional)", email);
        Main.addField(form, 3, "Address (optional)", address);

        String title = customer == null ? "Add customer" : "Edit customer";
        while (JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            CustomerRepository.Details details = new CustomerRepository.Details(
                    name.getText().strip(), phone.getText().strip(), email.getText().strip(), address.getText().strip());
            String validationError = validate(details);
            if (validationError != null) {
                showError(validationError);
                continue;
            }

            try {
                if (customer == null) {
                    repository.add(details);
                } else if (!repository.update(customer.id(), details)) {
                    showError("This customer was already removed.");
                }
            } catch (SQLException exception) {
                showDatabaseError("Could not save the customer.", exception);
                continue;
            }
            refresh();
            return;
        }
    }

    static String validate(CustomerRepository.Details details) {
        if (details.name().isBlank()) {
            return "Enter the customer's full name.";
        }
        if (details.phone().isBlank()) {
            return "Enter the customer's phone number.";
        }
        String digits = details.phone().replaceAll("[^0-9]", "");
        if (!details.phone().matches("\\+?[0-9() -]+") || digits.length() < 7 || digits.length() > 15) {
            return "Enter a phone number with 7 to 15 digits. Spaces, hyphens, parentheses and a leading + are allowed.";
        }
        if (!details.email().isEmpty() && !details.email().matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")) {
            return "Enter a valid email address or leave it blank.";
        }
        return null;
    }

    private void deleteSelectedCustomer() {
        CustomerRepository.Customer customer = selectedCustomer();
        if (customer == null || JOptionPane.showConfirmDialog(this,
                "Delete " + customer.name() + "?", "Delete customer",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            switch (repository.delete(customer.id())) {
                case DELETED -> refresh();
                case HAS_VEHICLES -> showError("This customer has a registered vehicle and cannot be deleted.");
                case MISSING -> {
                    showError("This customer was already removed.");
                    refresh();
                }
            }
        } catch (SQLException exception) {
            showDatabaseError("Could not delete the customer.", exception);
        }
    }

    private void showDatabaseError(String message, SQLException exception) {
        System.err.println(message + " " + exception.getMessage());
        showError(message + " Check that the database is accessible and try again.");
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Customers", JOptionPane.ERROR_MESSAGE);
    }
}
