package com.serviqo;

import java.math.BigDecimal;
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

    record PartDetails(String name, long quantity, long unitPriceCents) {
        PartDetails {
            if (name.isBlank()) {
                throw new IllegalArgumentException("Enter the part name.");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Enter a whole-number quantity greater than zero.");
            }
            if (unitPriceCents < 0) {
                throw new IllegalArgumentException("Enter a non-negative unit price.");
            }
        }

        BigDecimal total() {
            return BigDecimal.valueOf(unitPriceCents, 2).multiply(BigDecimal.valueOf(quantity));
        }
    }

    record Part(long id, PartDetails details) {
    }

    record Costs(Status status, long laborCents, List<Part> parts) {
        Costs {
            parts = List.copyOf(parts);
        }

        BigDecimal partsTotal() {
            BigDecimal total = BigDecimal.ZERO;
            for (Part part : parts) {
                total = total.add(part.details().total());
            }
            return total;
        }

        BigDecimal grandTotal() {
            return partsTotal().add(BigDecimal.valueOf(laborCents, 2));
        }
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

    Costs loadCosts(long jobId) throws SQLException {
        String sql = """
                SELECT j.status, j.labor_charge_cents, p.id AS part_id, p.part_name, p.quantity, p.unit_price_cents
                FROM service_jobs j
                LEFT JOIN service_parts p ON p.service_job_id = j.id
                WHERE j.id = ?
                ORDER BY p.id
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, jobId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Service job #" + jobId + " could not be found.");
                }
                Status status = Status.fromLabel(rows.getString("status"));
                long laborCents = rows.getLong("labor_charge_cents");
                List<Part> parts = new ArrayList<>();
                do {
                    long partId = rows.getLong("part_id");
                    if (!rows.wasNull()) {
                        PartDetails details = new PartDetails(rows.getString("part_name"), rows.getLong("quantity"),
                                rows.getLong("unit_price_cents"));
                        parts.add(new Part(partId, details));
                    }
                } while (rows.next());
                return new Costs(status, laborCents, parts);
            }
        }
    }

    boolean addPart(long jobId, PartDetails details) throws SQLException {
        String sql = """
                INSERT INTO service_parts (part_name, quantity, unit_price_cents, service_job_id)
                SELECT ?, ?, ?, id FROM service_jobs WHERE id = ? AND status IN ('Pending', 'In Progress')
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPartDetails(statement, 1, details);
            statement.setLong(4, jobId);
            return statement.executeUpdate() == 1;
        }
    }

    boolean updatePart(long jobId, Part original, PartDetails details) throws SQLException {
        String sql = """
                UPDATE service_parts
                SET part_name = ?, quantity = ?, unit_price_cents = ?
                WHERE service_job_id = ? AND id = ? AND part_name = ? AND quantity = ? AND unit_price_cents = ?
                  AND EXISTS (
                      SELECT 1 FROM service_jobs
                      WHERE id = service_parts.service_job_id AND status IN ('Pending', 'In Progress')
                  )
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPartDetails(statement, 1, details);
            statement.setLong(4, jobId);
            statement.setLong(5, original.id());
            bindPartDetails(statement, 6, original.details());
            return statement.executeUpdate() == 1;
        }
    }

    boolean removePart(long jobId, Part original) throws SQLException {
        String sql = """
                DELETE FROM service_parts
                WHERE service_job_id = ? AND id = ? AND part_name = ? AND quantity = ? AND unit_price_cents = ?
                  AND EXISTS (
                      SELECT 1 FROM service_jobs
                      WHERE id = service_parts.service_job_id AND status IN ('Pending', 'In Progress')
                  )
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, jobId);
            statement.setLong(2, original.id());
            bindPartDetails(statement, 3, original.details());
            return statement.executeUpdate() == 1;
        }
    }

    boolean updateLabor(long jobId, long previousCents, long laborCents) throws SQLException {
        if (laborCents < 0) {
            throw new IllegalArgumentException("Enter a non-negative labor charge.");
        }
        String sql = """
                UPDATE service_jobs SET labor_charge_cents = ?
                WHERE id = ? AND labor_charge_cents = ? AND status IN ('Pending', 'In Progress')
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, laborCents);
            statement.setLong(2, jobId);
            statement.setLong(3, previousCents);
            return statement.executeUpdate() == 1;
        }
    }

    private static void bindPartDetails(PreparedStatement statement, int firstParameter, PartDetails details) throws SQLException {
        statement.setString(firstParameter, details.name());
        statement.setLong(firstParameter + 1, details.quantity());
        statement.setLong(firstParameter + 2, details.unitPriceCents());
    }
}
