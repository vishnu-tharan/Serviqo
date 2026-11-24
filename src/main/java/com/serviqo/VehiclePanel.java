package com.serviqo;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.sqlite.SQLiteException;

final class VehiclePanel extends JPanel {
    private record Owner(long id, String name, String phone) {
        @Override
        public String toString() {
            return name + " (" + phone + ") · #" + id;
        }
    }

    private final VehicleRepository repository = new VehicleRepository();
    private final CustomerRepository customers = new CustomerRepository();
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Registration", "Owner", "Type", "Brand", "Model", "Year"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0 -> Long.class;
                case 6 -> Integer.class;
                default -> String.class;
            };
        }
    };
    private final JTable table = new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = new JTextField();
    private final Main.PageControls pages = new Main.PageControls((controls, offset) -> loadPage(offset));
    private final JLabel status = new JLabel(" ");
    private final JButton edit = new JButton("Edit");
    private List<VehicleRepository.Vehicle> vehicles = List.of();

    VehiclePanel() {
        super(new BorderLayout(0, 12));
        setOpaque(false);

        JPanel searchBar = new JPanel(new BorderLayout(10, 0));
        searchBar.setOpaque(false);
        JLabel searchLabel = new JLabel("Search by registration or owner");
        searchLabel.setLabelFor(search);
        searchBar.add(searchLabel, BorderLayout.WEST);
        searchBar.add(search, BorderLayout.CENTER);
        add(searchBar, BorderLayout.NORTH);

        table.setRowSorter(sorter);
        Main.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(48);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(145);
        table.getColumnModel().getColumn(6).setMaxWidth(60);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton add = new JButton("Register vehicle");
        actions.add(add);
        actions.add(edit);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(pages, BorderLayout.EAST);
        footer.add(status, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        add.addActionListener(event -> showForm(null));
        edit.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                showForm(vehicles.get(table.convertRowIndexToModel(row)));
            }
        });
        edit.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(event -> edit.setEnabled(table.getSelectedRow() >= 0));
        Main.watchSearch(search, pages::reset);
    }

    void refresh() {
        pages.reset();
    }

    private void loadPage(long offset) {
        try {
            vehicles = repository.search(search.getText().strip(), offset);
        } catch (SQLException exception) {
            vehicles = List.of();
            model.setRowCount(0);
            pages.update(offset, 0);
            status.setText("Vehicle records could not be loaded.");
            showDatabaseError("Could not load vehicles.", exception);
            return;
        }
        model.setRowCount(0);
        for (VehicleRepository.Vehicle vehicle : vehicles.subList(0, Math.min(vehicles.size(), Database.PAGE_SIZE))) {
            VehicleRepository.Details details = vehicle.details();
            model.addRow(new Object[]{vehicle.id(), details.registration(), vehicle.owner(), details.type(),
                    details.brand(), details.model(), details.year()});
        }
        pages.update(offset, vehicles.size());
        status.setText(table.getRowCount() == 0 ? "No matching vehicles." : table.getRowCount() + " vehicles on this page");
    }

    private void showForm(VehicleRepository.Vehicle vehicle) {
        var currentOwner = new java.util.concurrent.atomic.AtomicLong(vehicle == null ? 0 : vehicle.details().customerId());
        JTextField owner = new JTextField(vehicle == null ? "" : vehicle.owner(), 20);
        owner.setEditable(false);
        JButton chooseOwner = new JButton("Choose owner");
        chooseOwner.addActionListener(event -> {
            CustomerRepository.Customer selected = Main.pickRecord(owner, "Choose vehicle owner", "Name, phone or ID",
                    customers::search, customer -> new Owner(customer.id(), customer.name(), customer.phone()).toString());
            if (selected != null) {
                currentOwner.set(selected.id());
                owner.setText(selected.name());
                owner.setCaretPosition(0);
            }
        });
        JPanel ownerRow = new JPanel(new BorderLayout(8, 0));
        ownerRow.add(owner, BorderLayout.CENTER);
        ownerRow.add(chooseOwner, BorderLayout.EAST);
        JTextField registration = new JTextField(20);
        JComboBox<String> type = new JComboBox<>(Database.VEHICLE_TYPES.toArray(String[]::new));
        Main.usePlainText(type);
        JTextField brand = new JTextField(20);
        JTextField modelField = new JTextField(20);
        JTextField year = new JTextField(20);
        JTextArea notes = new JTextArea(3, 20);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);

        if (vehicle != null) {
            VehicleRepository.Details details = vehicle.details();
            registration.setText(details.registration());
            type.setSelectedItem(details.type());
            if (!details.type().equals(type.getSelectedItem())) {
                type.addItem(details.type());
                type.setSelectedItem(details.type());
            }
            brand.setText(details.brand());
            modelField.setText(details.model());
            year.setText(details.year() == null ? "" : details.year().toString());
            notes.setText(details.notes());
        }

        Main.limitText(registration, Database.REGISTRATION);
        Main.limitText(brand, Database.VEHICLE_DETAIL);
        Main.limitText(modelField, Database.VEHICLE_DETAIL);
        Main.limitText(year, 4);
        Main.limitText(notes, Database.NOTES);

        JPanel form = new JPanel(new GridBagLayout());
        Main.addField(form, 0, "Owner", ownerRow);
        Main.addField(form, 1, "Registration number", registration);
        Main.addField(form, 2, "Vehicle type", type);
        Main.addField(form, 3, "Brand", brand);
        Main.addField(form, 4, "Model", modelField);
        Main.addField(form, 5, "Manufacturing year (optional)", year);
        Main.addField(form, 6, "Notes (optional)", new JScrollPane(notes));

        String title = vehicle == null ? "Register vehicle" : "Edit vehicle";
        while (JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            VehicleRepository.Details details;
            try {
                details = readDetails(currentOwner.get(), registration.getText(),
                        (String) type.getSelectedItem(), brand.getText(), modelField.getText(), year.getText(), notes.getText());
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
                continue;
            }

            try {
                if (vehicle == null) {
                    repository.add(details);
                } else if (!repository.update(vehicle.id(), details)) {
                    showError("This vehicle was already removed.");
                }
            } catch (SQLiteException exception) {
                switch (exception.getResultCode()) {
                    case SQLITE_CONSTRAINT_UNIQUE -> showError("This registration number is already in use.");
                    case SQLITE_CONSTRAINT_FOREIGNKEY -> {
                        showError("The selected customer no longer exists. Open the form again to choose another owner.");
                        return;
                    }
                    default -> showDatabaseError("Could not save the vehicle.", exception);
                }
                continue;
            } catch (SQLException exception) {
                showDatabaseError("Could not save the vehicle.", exception);
                continue;
            }
            refresh();
            return;
        }
    }

    static VehicleRepository.Details readDetails(long ownerId, String registration, String type,
                                                 String brand, String model, String yearText, String notes) {
        if (ownerId <= 0) {
            throw new IllegalArgumentException("Select an existing customer as the owner.");
        }
        String plate = registration.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ")
                .replaceAll(" *- *", "-");
        Database.requireLength(plate, Database.REGISTRATION, "Registration number");
        Database.requireLength(type, Database.VEHICLE_TYPE, "Vehicle type");
        Database.requireLength(brand, Database.VEHICLE_DETAIL, "Brand");
        Database.requireLength(model, Database.VEHICLE_DETAIL, "Model");
        Database.requireLength(notes, Database.NOTES, "Notes");
        if (plate.isEmpty()) {
            throw new IllegalArgumentException("Enter the registration number.");
        }
        if (brand.isBlank() || model.isBlank()) {
            throw new IllegalArgumentException("Enter the vehicle brand and model.");
        }
        String year = yearText.strip();
        if (!year.isEmpty() && !year.matches("[1-9][0-9]{3}")) {
            throw new IllegalArgumentException("Enter a four-digit manufacturing year or leave it blank.");
        }
        return new VehicleRepository.Details(ownerId, plate, type, brand.strip(), model.strip(),
                year.isEmpty() ? null : Integer.valueOf(year), notes.strip());
    }

    private void showDatabaseError(String message, SQLException exception) {
        System.err.println(message + " " + exception.getMessage());
        showError(Database.userError(exception, message + " Check that the database is accessible and try again."));
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Vehicles", JOptionPane.ERROR_MESSAGE);
    }
}
