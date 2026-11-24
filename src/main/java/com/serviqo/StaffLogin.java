package com.serviqo;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.sql.SQLException;
import java.util.Arrays;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

final class StaffLogin {
    private StaffLogin() {
    }

    static boolean open() {
        try {
            while (StaffAccounts.needsSetup()) {
                if (!createAccount(null, true)) {
                    return false;
                }
            }
            JTextField username = new JTextField(24);
            JPasswordField password = new JPasswordField(24);
            Main.limitText(username, 64);
            Main.limitText(password, 128);
            JPanel form = new JPanel(new GridBagLayout());
            Main.addField(form, 0, "Username", username);
            Main.addField(form, 1, "Passphrase", password);
            while (confirm(null, form, "Serviqo staff sign-in")) {
                char[] secret = password.getPassword();
                password.setText("");
                try {
                    if (StaffAccounts.signIn(username.getText(), secret)) {
                        return true;
                    }
                    error(null, "The username or passphrase is incorrect.");
                } catch (SQLException exception) {
                    databaseError(null, exception);
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }
            password.setText("");
        } catch (SQLException exception) {
            databaseError(null, exception);
        }
        return false;
    }

    static boolean createAccount(Component parent, boolean first) {
        JTextField username = new JTextField(24);
        JPasswordField password = new JPasswordField(24);
        JPasswordField confirmation = new JPasswordField(24);
        Main.limitText(username, 32);
        Main.limitText(password, 128);
        Main.limitText(confirmation, 128);
        JComboBox<StaffAccounts.Role> role = new JComboBox<>(StaffAccounts.Role.values());
        Main.usePlainText(role);
        role.setSelectedItem(StaffAccounts.Role.MECHANIC);
        JPanel form = new JPanel(new GridBagLayout());
        Main.addField(form, 0, "Username", username);
        Main.addField(form, 1, "Passphrase (15–128 characters)", password);
        Main.addField(form, 2, "Confirm passphrase", confirmation);
        if (!first) {
            Main.addField(form, 3, "Role", role);
        }
        String title = first ? "Create the first Serviqo manager" : "Add staff account";
        while (confirm(parent, form, title)) {
            char[] secret = password.getPassword();
            char[] repeated = confirmation.getPassword();
            password.setText("");
            confirmation.setText("");
            try {
                if (!Arrays.equals(secret, repeated)) {
                    error(parent, "The passphrases do not match.");
                    continue;
                }
                boolean created = first ? StaffAccounts.createFirstManager(username.getText(), secret)
                        : StaffAccounts.addStaff(username.getText(), secret, (StaffAccounts.Role) role.getSelectedItem());
                if (created) {
                    if (!first) {
                        JOptionPane.showMessageDialog(parent, "Staff account created.");
                    }
                    return true;
                }
                if (first) {
                    return true;
                }
                error(parent, "That username is already in use.");
            } catch (IllegalArgumentException exception) {
                error(parent, exception.getMessage());
            } catch (SQLException exception) {
                databaseError(parent, exception);
                return false;
            } finally {
                Arrays.fill(secret, '\0');
                Arrays.fill(repeated, '\0');
            }
        }
        password.setText("");
        confirmation.setText("");
        return false;
    }

    static void showAudit(Component parent) {
        DefaultTableModel model = new DefaultTableModel(new String[]{"Time (UTC)", "Staff", "Action", "Record ID"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 3 ? Long.class : String.class;
            }
        };
        try {
            for (StaffAccounts.AuditEntry entry : StaffAccounts.recentAudit()) {
                model.addRow(new Object[]{entry.time(), entry.staff(), entry.action(), entry.recordId()});
            }
        } catch (SQLException exception) {
            databaseError(parent, exception);
            return;
        }
        JTable table = new JTable(model);
        Main.styleTable(table);
        table.getColumnModel().getColumn(2).setPreferredWidth(290);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(700, 385));
        JOptionPane.showMessageDialog(parent, scroll, "Recent staff activity", JOptionPane.PLAIN_MESSAGE);
    }

    private static boolean confirm(Component parent, JPanel form, String title) {
        return JOptionPane.showConfirmDialog(parent, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private static void databaseError(Component parent, SQLException exception) {
        System.err.println("Staff account operation failed. " + exception.getMessage());
        error(parent, Database.userError(exception,
                "Could not complete the staff account operation. Check the database and try again."));
    }

    private static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Serviqo staff accounts", JOptionPane.ERROR_MESSAGE);
    }
}
