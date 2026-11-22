package com.serviqo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class ServiceRepository {
    enum Status {
        PENDING("Pending"), IN_PROGRESS("In Progress"), COMPLETED("Completed"), CANCELLED("Cancelled");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        boolean isActive() {
            return this == PENDING || this == IN_PROGRESS;
        }

        List<Status> nextStatuses() {
            return switch (this) {
                case PENDING -> List.of(IN_PROGRESS, CANCELLED);
                case IN_PROGRESS -> List.of(COMPLETED, CANCELLED);
                case COMPLETED, CANCELLED -> List.of();
            };
        }

        static Status fromLabel(String label) throws SQLException {
            for (Status status : values()) {
                if (status.label.equals(label)) {
                    return status;
                }
            }
            throw new SQLException("Unknown service status: " + label);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    record NewJob(long vehicleId, String problem, String description, String serviceDate) {
    }

    record Job(long id, String registration, String owner, String vehicle, String problem,
               String description, String serviceDate, Status status, String completedAt) {
    }

    List<Job> findAll() throws SQLException {
        String sql = """
                SELECT j.id, j.reported_problem, j.service_description, j.service_date,
                       j.status, j.completed_at, v.registration_number, v.vehicle_type,
                       v.brand, v.model, c.name AS owner
                FROM service_jobs j
                JOIN vehicles v ON v.id = j.vehicle_id
                JOIN customers c ON c.id = v.customer_id
                ORDER BY j.service_date DESC, j.id DESC
                """;
        List<Job> jobs = new ArrayList<>();
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String vehicle = rows.getString("brand") + " " + rows.getString("model")
                        + " (" + rows.getString("vehicle_type") + ")";
                jobs.add(new Job(rows.getLong("id"), rows.getString("registration_number"),
                        rows.getString("owner"), vehicle, rows.getString("reported_problem"),
                        rows.getString("service_description"), rows.getString("service_date"),
                        Status.fromLabel(rows.getString("status")), rows.getString("completed_at")));
            }
        }
        return jobs;
    }

    void add(NewJob job) throws SQLException {
        String sql = """
                INSERT INTO service_jobs (vehicle_id, reported_problem, service_description, service_date)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, job.vehicleId());
            statement.setString(2, job.problem());
            statement.setString(3, job.description());
            statement.setString(4, job.serviceDate());
            statement.executeUpdate();
        }
    }

    boolean update(Job job, String description, Status newStatus) throws SQLException {
        Status currentStatus = job.status();
        if (!currentStatus.isActive() || (newStatus != currentStatus && !currentStatus.nextStatuses().contains(newStatus))) {
            throw new IllegalArgumentException("This service status change is not allowed.");
        }

        String sql = """
                UPDATE service_jobs
                SET service_description = ?, status = ?,
                    completed_at = CASE WHEN ? = 'Completed' THEN CURRENT_TIMESTAMP ELSE NULL END
                WHERE id = ? AND status = ? AND service_description IS ?
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, description);
            statement.setString(2, newStatus.toString());
            statement.setString(3, newStatus.toString());
            statement.setLong(4, job.id());
            statement.setString(5, currentStatus.toString());
            statement.setString(6, job.description());
            return statement.executeUpdate() == 1;
        }
    }
}
