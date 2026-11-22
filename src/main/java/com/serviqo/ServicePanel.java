package com.serviqo;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
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
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

final class ServicePanel extends JPanel {
    private static final int STATUS_COLUMN = 4;

    private record VehicleOption(VehicleRepository.Vehicle vehicle) {
        @Override
        public String toString() {
            return vehicle.details().registration();
        }
    }

    private final ServiceRepository repository = new ServiceRepository();
    private final VehicleRepository vehicles = new VehicleRepository();
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
    private final JLabel count = new JLabel(" ");
    private final JButton detailsButton = new JButton("Open job");
    private List<ServiceRepository.Job> jobs = List.of();

    ServicePanel() {
        super(new BorderLayout(0, 12));
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
        add(filterBar, BorderLayout.NORTH);

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
        JButton add = new JButton("New job");
        actions.add(add);
        actions.add(detailsButton);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(count, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        add.addActionListener(event -> showNewJob());
        detailsButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                showJob(jobs.get(table.convertRowIndexToModel(row)));
            }
        });
        detailsButton.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(event ->
                detailsButton.setEnabled(table.getSelectedRow() >= 0));
        filter.addActionListener(event -> applyFilter());
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
        sorter.setRowFilter(filter.getSelectedIndex() == 0 ? null :
                RowFilter.regexFilter("^" + Pattern.quote(status) + "$", STATUS_COLUMN));
        int visible = table.getRowCount();
        if (visible == 0) {
            count.setText(filter.getSelectedIndex() == 0 ? "No service jobs yet." : "No jobs with this status.");
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
        boolean active = job.status().isActive();
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
