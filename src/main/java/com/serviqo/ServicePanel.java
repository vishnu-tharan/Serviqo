package com.serviqo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

final class ServicePanel extends JPanel {
    private static final int STATUS_COLUMN = 4;
    private static final int REGISTRATION_COLUMN = 2;
    private static final int CUSTOMER_COLUMN = 3;

    private record VehicleOption(VehicleRepository.Vehicle vehicle) {
        @Override
        public String toString() {
            return vehicle.details().registration();
        }
    }

    private final ServiceRepository repository = new ServiceRepository();
    private final VehicleRepository vehicles = new VehicleRepository();
    private final boolean readOnly;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Service date", "Registration", "Customer", "Status", "Reported problem"}, 0) {
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
    private final JComboBox<String> filter = new JComboBox<>();
    private final JTextField search = new JTextField();
    private final JLabel count = new JLabel(" ");
    private final JButton detailsButton = new JButton("Open job");
    private final JButton chargesButton = new JButton("Parts and labor");
    private final JButton invoiceButton = new JButton("Preview invoice");
    private List<ServiceRepository.Job> jobs = List.of();

    ServicePanel(boolean readOnly) {
        super(new BorderLayout(0, 12));
        this.readOnly = readOnly;
        setOpaque(false);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterBar.setOpaque(false);
        JLabel filterLabel = new JLabel("Status");
        filterLabel.setLabelFor(filter);
        filterBar.add(filterLabel);
        filter.addItem("All statuses");
        for (ServiceRepository.Status status : ServiceRepository.Status.values()) {
            filter.addItem(status.toString());
        }
        filterBar.add(filter);
        if (readOnly) {
            JPanel searchBar = new JPanel(new BorderLayout(8, 0));
            searchBar.setOpaque(false);
            JLabel searchLabel = new JLabel("Search by registration or customer");
            searchLabel.setLabelFor(search);
            searchBar.add(searchLabel, BorderLayout.WEST);
            searchBar.add(search, BorderLayout.CENTER);

            JPanel filters = new JPanel(new BorderLayout(0, 8));
            filters.setOpaque(false);
            filters.add(searchBar, BorderLayout.NORTH);
            filters.add(filterBar, BorderLayout.SOUTH);
            add(filters, BorderLayout.NORTH);

            search.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    applyFilter();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    applyFilter();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    applyFilter();
                }
            });
        } else {
            add(filterBar, BorderLayout.NORTH);
        }

        table.setRowSorter(sorter);
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setMaxWidth(48);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(155);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        if (!readOnly) {
            JButton add = new JButton("New job");
            add.addActionListener(event -> showNewJob());
            actions.add(add);
        }
        actions.add(detailsButton);
        actions.add(chargesButton);
        actions.add(invoiceButton);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(count, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        detailsButton.addActionListener(event -> {
            ServiceRepository.Job job = selectedJob();
            if (job != null) {
                showJob(job);
            }
        });
        chargesButton.addActionListener(event -> {
            ServiceRepository.Job job = selectedJob();
            if (job != null) {
                showCharges(job);
            }
        });
        invoiceButton.setToolTipText("Invoices are available for completed jobs.");
        invoiceButton.addActionListener(event -> {
            ServiceRepository.Job job = selectedJob();
            if (job != null) {
                showInvoice(job.id());
            }
        });
        detailsButton.setEnabled(false);
        chargesButton.setEnabled(false);
        invoiceButton.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(event -> {
            ServiceRepository.Job job = selectedJob();
            detailsButton.setEnabled(job != null);
            chargesButton.setEnabled(job != null);
            invoiceButton.setEnabled(job != null && job.status() == ServiceRepository.Status.COMPLETED);
        });
        filter.addActionListener(event -> applyFilter());
    }

    private ServiceRepository.Job selectedJob() {
        int row = table.getSelectedRow();
        return row < 0 ? null : jobs.get(table.convertRowIndexToModel(row));
    }

    void refresh() {
        try {
            jobs = repository.findAll();
        } catch (SQLException exception) {
            jobs = List.of();
            model.setRowCount(0);
            count.setText("Service jobs could not be loaded.");
            showDatabaseError("Could not load service jobs.", exception);
            return;
        }
        model.setRowCount(0);
        for (ServiceRepository.Job job : jobs) {
            model.addRow(new Object[]{job.id(), job.serviceDate(), job.registration(), job.owner(),
                    job.status().toString(), job.problem()});
        }
        applyFilter();
    }

    private void applyFilter() {
        String status = (String) filter.getSelectedItem();
        String query = search.getText().strip();
        RowFilter<DefaultTableModel, Integer> statusFilter = filter.getSelectedIndex() == 0 ? null
                : RowFilter.regexFilter("^" + Pattern.quote(status) + "$", STATUS_COLUMN);
        RowFilter<DefaultTableModel, Integer> searchFilter = query.isEmpty() ? null
                : RowFilter.regexFilter("(?iu)" + Pattern.quote(query), REGISTRATION_COLUMN, CUSTOMER_COLUMN);
        if (statusFilter != null && searchFilter != null) {
            sorter.setRowFilter(RowFilter.andFilter(List.of(statusFilter, searchFilter)));
        } else {
            sorter.setRowFilter(statusFilter == null ? searchFilter : statusFilter);
        }
        int visible = table.getRowCount();
        if (visible == 0) {
            count.setText(query.isEmpty() && statusFilter == null ? "No service jobs yet." : "No matching service jobs.");
        } else {
            count.setText(visible == 1 ? "1 service job" : visible + " service jobs");
        }
    }

    private void showNewJob() {
        List<VehicleRepository.Vehicle> registeredVehicles;
        try {
            registeredVehicles = vehicles.findAll();
        } catch (SQLException exception) {
            showDatabaseError("Could not load registered vehicles.", exception);
            return;
        }
        if (registeredVehicles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Register a vehicle in Vehicles before creating a service job.",
                    "Service Jobs", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JComboBox<VehicleOption> vehicleBox = new JComboBox<>();
        for (VehicleRepository.Vehicle vehicle : registeredVehicles) {
            vehicleBox.addItem(new VehicleOption(vehicle));
        }
        vehicleBox.setSelectedIndex(-1);
        JTextField owner = readOnlyField("");
        JTextField vehicleDetails = readOnlyField("");
        vehicleBox.addActionListener(event -> {
            VehicleOption option = (VehicleOption) vehicleBox.getSelectedItem();
            if (option == null) {
                owner.setText("");
                vehicleDetails.setText("");
                return;
            }
            VehicleRepository.Details details = option.vehicle().details();
            owner.setText(option.vehicle().owner());
            vehicleDetails.setText(details.brand() + " " + details.model() + " (" + details.type() + ")");
            owner.setCaretPosition(0);
            vehicleDetails.setCaretPosition(0);
        });

        JTextField date = new JTextField(LocalDate.now().toString(), 26);
        JTextArea problem = textArea("");
        JTextArea description = textArea("");
        JPanel form = new JPanel(new GridBagLayout());
        addField(form, 0, "Registration", vehicleBox);
        addField(form, 1, "Customer", owner);
        addField(form, 2, "Vehicle", vehicleDetails);
        addField(form, 3, "Service date (YYYY-MM-DD)", date);
        addField(form, 4, "Reported problem", new JScrollPane(problem));
        addField(form, 5, "Repair description (optional)", new JScrollPane(description));

        while (confirmForm(form, "New service job")) {
            VehicleOption option = (VehicleOption) vehicleBox.getSelectedItem();
            try {
                long vehicleId = option == null ? 0 : option.vehicle().id();
                repository.add(readNewJob(vehicleId, problem.getText(), description.getText(), date.getText()));
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
                continue;
            } catch (SQLiteException exception) {
                if (exception.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY) {
                    showError("The selected vehicle no longer exists. Open the form again to select a vehicle.");
                    return;
                }
                showDatabaseError("Could not create the service job.", exception);
                continue;
            } catch (SQLException exception) {
                showDatabaseError("Could not create the service job.", exception);
                continue;
            }
            if (filter.getSelectedIndex() != 0) {
                filter.setSelectedItem(ServiceRepository.Status.PENDING.toString());
            }
            refresh();
            return;
        }
    }

    static ServiceRepository.NewJob readNewJob(long vehicleId, String problem, String description, String date) {
        if (vehicleId <= 0) {
            throw new IllegalArgumentException("Select a registered vehicle.");
        }
        if (problem.isBlank()) {
            throw new IllegalArgumentException("Enter the reported problem.");
        }
        String serviceDate = date.strip();
        String invalidDate = "Enter a valid service date in YYYY-MM-DD format.";
        if (!serviceDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || serviceDate.startsWith("0000")) {
            throw new IllegalArgumentException(invalidDate);
        }
        try {
            LocalDate.parse(serviceDate);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(invalidDate);
        }
        return new ServiceRepository.NewJob(vehicleId, problem.strip(), description.strip(), serviceDate);
    }

    private void showJob(ServiceRepository.Job job) {
        boolean active = !readOnly && job.status().isActive();
        JTextArea problem = textArea(job.problem());
        problem.setEditable(false);
        JTextArea description = textArea(job.description());
        description.setEditable(active);
        JComboBox<ServiceRepository.Status> statuses = new JComboBox<>();
        statuses.addItem(job.status());
        for (ServiceRepository.Status nextStatus : job.status().nextStatuses()) {
            statuses.addItem(nextStatus);
        }
        statuses.setEnabled(active);

        JPanel form = new JPanel(new GridBagLayout());
        addField(form, 0, "Registration", readOnlyField(job.registration()));
        addField(form, 1, "Customer", readOnlyField(job.owner()));
        addField(form, 2, "Vehicle", readOnlyField(job.vehicle()));
        addField(form, 3, "Service date", readOnlyField(job.serviceDate()));
        addField(form, 4, "Reported problem", new JScrollPane(problem));
        addField(form, 5, "Repair description", new JScrollPane(description));
        addField(form, 6, "Status", statuses);
        if (job.completedAt() != null) {
            addField(form, 7, "Completed (UTC)", readOnlyField(job.completedAt()));
        }

        String title = "Service job #" + job.id();
        if (!active) {
            JOptionPane.showMessageDialog(this, form, title, JOptionPane.PLAIN_MESSAGE);
            return;
        }
        while (confirmForm(form, title)) {
            ServiceRepository.Status newStatus = (ServiceRepository.Status) statuses.getSelectedItem();
            if (!newStatus.isActive() && JOptionPane.showConfirmDialog(this,
                    "Mark this job as " + newStatus + "? This closes the job.",
                    "Close service job", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                continue;
            }
            try {
                if (!repository.update(job, description.getText().strip(), newStatus)) {
                    showError("This job changed since it was opened. Open it again to see the latest details.");
                }
            } catch (SQLException exception) {
                showDatabaseError("Could not update the service job.", exception);
                continue;
            }
            refresh();
            return;
        }
    }

    private void showCharges(ServiceRepository.Job job) {
        ChargesPanel panel = new ChargesPanel(job.id());
        if (panel.reload()) {
            JOptionPane.showOptionDialog(this, panel, "Parts and labor · Job #" + job.id() + " · " + job.registration(),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new String[]{"Close"}, "Close");
        }
        if (!readOnly) {
            refresh();
        }
    }

    private void showInvoice(long jobId) {
        try {
            repository.findInvoice(jobId).ifPresentOrElse(invoice -> InvoicePrinter.showPreview(this, invoice), () -> {
                showError("Invoices are available for completed jobs.");
                refresh();
            });
        } catch (SQLException exception) {
            showDatabaseError("Could not load the invoice.", exception);
        }
    }

    private final class ChargesPanel extends JPanel {
        private final long jobId;
        private final DefaultTableModel partModel = new DefaultTableModel(
                new String[]{"Part name", "Quantity", "Unit price", "Line total"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        private final JTable partTable = new JTable(partModel);
        private final JLabel jobStatus = new JLabel();
        private final JLabel subtotal = new JLabel();
        private final JLabel laborTotal = new JLabel();
        private final JLabel grandTotal = new JLabel();
        private final JButton addPart = new JButton("Add part");
        private final JButton editPart = new JButton("Edit part");
        private final JButton removePart = new JButton("Remove part");
        private final JButton editLabor = new JButton("Set labor charge");
        private ServiceRepository.Costs costs;

        ChargesPanel(long jobId) {
            super(new BorderLayout(0, 10));
            this.jobId = jobId;
            add(jobStatus, BorderLayout.NORTH);

            partTable.setRowHeight(24);
            partTable.setFillsViewportHeight(true);
            partTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            partTable.getColumnModel().getColumn(0).setPreferredWidth(180);
            JScrollPane scroll = new JScrollPane(partTable);
            scroll.setPreferredSize(new Dimension(520, 175));
            add(scroll, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            actions.add(addPart);
            actions.add(editPart);
            actions.add(removePart);
            actions.add(editLabor);
            JPanel totals = new JPanel(new GridBagLayout());
            addField(totals, 0, "Parts subtotal", subtotal);
            addField(totals, 1, "Labor charge", laborTotal);
            addField(totals, 2, "Grand total", grandTotal);
            JPanel footer = new JPanel(new BorderLayout(0, 8));
            if (!readOnly) {
                footer.add(actions, BorderLayout.NORTH);
            }
            footer.add(totals, BorderLayout.CENTER);
            add(footer, BorderLayout.SOUTH);

            addPart.addActionListener(event -> showPartForm(null));
            editPart.addActionListener(event -> showPartForm(selectedPart()));
            removePart.addActionListener(event -> removeSelectedPart());
            editLabor.addActionListener(event -> showLaborForm());
            partTable.getSelectionModel().addListSelectionListener(event -> updateControls());
            updateControls();
        }

        private boolean reload() {
            try {
                costs = repository.loadCosts(jobId);
            } catch (SQLException exception) {
                costs = null;
                partModel.setRowCount(0);
                jobStatus.setText("Charges could not be loaded. Reopen this window to try again.");
                subtotal.setText("Unavailable");
                laborTotal.setText("Unavailable");
                grandTotal.setText("Unavailable");
                updateControls();
                databaseError("Could not load charges.", exception);
                return false;
            }
            partModel.setRowCount(0);
            for (ServiceRepository.Part part : costs.parts()) {
                ServiceRepository.PartDetails details = part.details();
                partModel.addRow(new Object[]{details.name(), details.quantity(),
                        formatMoney(BigDecimal.valueOf(details.unitPriceCents(), 2)), formatMoney(details.total())});
            }
            String suffix = !readOnly && costs.status().isActive() ? "" : " · Charges are read-only";
            jobStatus.setText("Status: " + costs.status() + suffix);
            subtotal.setText(formatMoney(costs.partsTotal()));
            laborTotal.setText(formatMoney(BigDecimal.valueOf(costs.laborCents(), 2)));
            grandTotal.setText(formatMoney(costs.grandTotal()));
            updateControls();
            return true;
        }

        private void updateControls() {
            boolean active = !readOnly && costs != null && costs.status().isActive();
            boolean partSelected = active && partTable.getSelectedRow() >= 0;
            addPart.setEnabled(active);
            editPart.setEnabled(partSelected);
            removePart.setEnabled(partSelected);
            editLabor.setEnabled(active);
        }

        private ServiceRepository.Part selectedPart() {
            return costs.parts().get(partTable.getSelectedRow());
        }

        private void showPartForm(ServiceRepository.Part part) {
            ServiceRepository.PartDetails details = part == null ? null : part.details();
            JTextField name = new JTextField(details == null ? "" : details.name(), 20);
            JTextField quantity = new JTextField(details == null ? "1" : Long.toString(details.quantity()), 12);
            JTextField price = new JTextField(details == null ? "" :
                    BigDecimal.valueOf(details.unitPriceCents(), 2).toPlainString(), 12);
            JPanel form = new JPanel(new GridBagLayout());
            addField(form, 0, "Part name", name);
            addField(form, 1, "Quantity", quantity);
            addField(form, 2, "Unit price (LKR)", price);

            String title = part == null ? "Add part" : "Edit part";
            while (confirmChargeForm(form, title)) {
                try {
                    ServiceRepository.PartDetails updated = readPart(name.getText(), quantity.getText(), price.getText());
                    boolean saved = part == null ? repository.addPart(jobId, updated)
                            : repository.updatePart(jobId, part, updated);
                    finishChange(saved);
                    return;
                } catch (IllegalArgumentException exception) {
                    chargeError(exception.getMessage());
                } catch (SQLException exception) {
                    databaseError("Could not save the part.", exception);
                }
            }
        }

        private void removeSelectedPart() {
            ServiceRepository.Part part = selectedPart();
            if (JOptionPane.showConfirmDialog(this, "Remove " + part.details().name() + " from this job?",
                    "Remove part", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                finishChange(repository.removePart(jobId, part));
            } catch (SQLException exception) {
                databaseError("Could not remove the part.", exception);
            }
        }

        private void showLaborForm() {
            long previousCents = costs.laborCents();
            JTextField amount = new JTextField(BigDecimal.valueOf(previousCents, 2).toPlainString(), 12);
            JPanel form = new JPanel(new GridBagLayout());
            addField(form, 0, "Labor charge (LKR)", amount);
            while (confirmChargeForm(form, "Set labor charge")) {
                try {
                    finishChange(repository.updateLabor(jobId, previousCents, readMoney(amount.getText(), "labor charge")));
                    return;
                } catch (IllegalArgumentException exception) {
                    chargeError(exception.getMessage());
                } catch (SQLException exception) {
                    databaseError("Could not save the labor charge.", exception);
                }
            }
        }

        private void finishChange(boolean saved) {
            if (!saved) {
                chargeError("The job closed or its charges changed. Review the latest details before trying again.");
            }
            reload();
        }

        private boolean confirmChargeForm(JPanel form, String title) {
            return JOptionPane.showConfirmDialog(this, form, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
        }

        private void databaseError(String message, SQLException exception) {
            System.err.println(message + " " + exception.getMessage());
            chargeError(message + " Check that the database is accessible and try again.");
        }

        private void chargeError(String message) {
            JOptionPane.showMessageDialog(this, message, "Parts and labor", JOptionPane.ERROR_MESSAGE);
        }
    }

    static ServiceRepository.PartDetails readPart(String name, String quantity, String price) {
        long units;
        try {
            units = Long.parseLong(quantity.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole-number quantity greater than zero.");
        }
        return new ServiceRepository.PartDetails(name.strip(), units, readMoney(price, "unit price"));
    }

    static long readMoney(String input, String label) {
        String amount = input.strip();
        if (!amount.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?|\\.[0-9]{1,2}")) {
            throw new IllegalArgumentException("Enter a non-negative " + label + " with up to two decimal places.");
        }
        try {
            return new BigDecimal(amount.replace(",", "")).movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("The " + label + " is too large.");
        }
    }

    static String formatMoney(BigDecimal amount) {
        return String.format(Locale.ROOT, "Rs. %,.2f", amount);
    }

    private static JTextField readOnlyField(String value) {
        JTextField field = new JTextField(value, 26);
        field.setEditable(false);
        return field;
    }

    private static JTextArea textArea(String value) {
        JTextArea area = new JTextArea(value, 3, 26);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static void addField(JPanel form, int row, String text, JComponent field) {
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

    private boolean confirmForm(JPanel form, String title) {
        return JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private void showDatabaseError(String message, SQLException exception) {
        System.err.println(message + " " + exception.getMessage());
        showError(message + " Check that the database is accessible and try again.");
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Service Jobs", JOptionPane.ERROR_MESSAGE);
    }
}
