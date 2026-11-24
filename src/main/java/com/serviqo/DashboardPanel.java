package com.serviqo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

final class DashboardPanel extends JPanel {
    private final ServiceRepository repository = new ServiceRepository();
    private final JLabel customers = new JLabel();
    private final JLabel vehicles = new JLabel();
    private final JLabel pending = new JLabel();
    private final JLabel inProgress = new JLabel();
    private final JLabel completed = new JLabel();
    private final JLabel completedValue = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel recentJobs = new DefaultTableModel(
            new String[]{"Service date", "Registration", "Customer", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private SwingWorker<ServiceRepository.Dashboard, Void> loading;

    DashboardPanel() {
        super(new BorderLayout(0, 14));
        setOpaque(false);

        JPanel statistics = new JPanel(new GridLayout(2, 3, 10, 10));
        statistics.setOpaque(false);
        statistics.add(statistic("Customers", customers));
        statistics.add(statistic("Vehicles", vehicles));
        statistics.add(statistic("Pending jobs", pending));
        statistics.add(statistic("In-progress jobs", inProgress));
        statistics.add(statistic("Completed jobs", completed));
        if (StaffAccounts.isManager()) {
            statistics.add(statistic("Completed service value", completedValue));
        }
        add(statistics, BorderLayout.NORTH);

        JPanel activity = new JPanel(new BorderLayout(0, 8));
        activity.setOpaque(false);
        JLabel title = new JLabel("Recent service jobs");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        activity.add(title, BorderLayout.NORTH);
        JTable table = new JTable(recentJobs);
        Main.styleTable(table);
        table.getColumnModel().getColumn(2).setPreferredWidth(125);
        activity.add(new JScrollPane(table), BorderLayout.CENTER);
        activity.add(status, BorderLayout.SOUTH);
        add(activity, BorderLayout.CENTER);
    }

    void refresh() {
        if (loading != null) {
            loading.cancel(true);
        }
        status.setText("Loading workshop activity…");
        loading = new SwingWorker<>() {
            @Override
            protected ServiceRepository.Dashboard doInBackground() throws Exception {
                return repository.loadDashboard();
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    display(get());
                } catch (ExecutionException exception) {
                    System.err.println("Could not load the dashboard. " + exception.getCause());
                    clear();
                    String fallback = "Could not load the dashboard. Select Dashboard to retry.";
                    status.setText(exception.getCause() instanceof java.sql.SQLException databaseError
                            ? Database.userError(databaseError, fallback) : fallback);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    clear();
                    status.setText("Dashboard loading was interrupted. Select Dashboard to retry.");
                }
            }
        };
        loading.execute();
    }

    @Override
    public void removeNotify() {
        if (loading != null) {
            loading.cancel(true);
        }
        super.removeNotify();
    }

    private JPanel statistic(String title, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(12f));
        label.setForeground(Main.TEXT);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 18f));
        value.setForeground(Main.NAVY);
        value.setText("—");
        card.add(label, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private void display(ServiceRepository.Dashboard dashboard) {
        customers.setText(Long.toString(dashboard.customers()));
        vehicles.setText(Long.toString(dashboard.vehicles()));
        pending.setText(Long.toString(dashboard.pending()));
        inProgress.setText(Long.toString(dashboard.inProgress()));
        completed.setText(Long.toString(dashboard.completed()));
        String value = ServicePanel.formatMoney(dashboard.completedValue());
        completedValue.setText(value);
        completedValue.setToolTipText(value);

        recentJobs.setRowCount(0);
        for (ServiceRepository.Job job : dashboard.recentJobs()) {
            recentJobs.addRow(new Object[]{job.serviceDate(), job.registration(), job.owner(), job.status()});
        }
        status.setText(recentJobs.getRowCount() == 0 ? "No service jobs yet." : " ");
    }

    private void clear() {
        for (JLabel value : new JLabel[]{customers, vehicles, pending, inProgress, completed, completedValue}) {
            value.setText("—");
        }
        completedValue.setToolTipText(null);
        recentJobs.setRowCount(0);
    }
}
