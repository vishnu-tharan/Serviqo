package com.serviqo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

final class VehicleRepository {
    record Details(long customerId, String registration, String type, String brand,
                   String model, Integer year, String notes) {
    }

    record Vehicle(long id, String owner, Details details) {
    }

    List<Vehicle> search(String query, long offset) throws SQLException {
        StaffAccounts.requireSignedIn();
        String sql = """
                SELECT v.id, v.customer_id, v.registration_number, v.vehicle_type,
                       v.brand, v.model, v.manufacturing_year, v.notes, c.name AS owner
                FROM vehicles v
                JOIN customers c ON c.id = v.customer_id
                WHERE instr(lower(v.registration_number), lower(?)) > 0 OR instr(lower(c.name), lower(?)) > 0
                ORDER BY v.registration_number COLLATE NOCASE, v.id
                LIMIT ? OFFSET ?
                """;
        List<Vehicle> vehicles = new ArrayList<>();
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, query);
            statement.setString(2, query);
            Database.bindPage(statement, 3, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int year = rows.getInt("manufacturing_year");
                    Integer optionalYear = rows.wasNull() ? null : year;
                    Details details = new Details(rows.getLong("customer_id"), rows.getString("registration_number"),
                            rows.getString("vehicle_type"), rows.getString("brand"), rows.getString("model"),
                            optionalYear, rows.getString("notes"));
                    vehicles.add(new Vehicle(rows.getLong("id"), rows.getString("owner"), details));
                }
            }
        }
        return vehicles;
    }

    void add(Details details) throws SQLException {
        StaffAccounts.requireManager();
        String sql = """
                INSERT INTO vehicles (customer_id, registration_number, vehicle_type, brand, model,
                                      manufacturing_year, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            setDetails(statement, details);
            StaffAccounts.commitChange(connection, statement.executeUpdate() == 1, "Registered vehicle", null);
        }
    }

    boolean update(long id, Details details) throws SQLException {
        StaffAccounts.requireManager();
        String sql = """
                UPDATE vehicles
                SET customer_id = ?, registration_number = ?, vehicle_type = ?, brand = ?, model = ?,
                    manufacturing_year = ?, notes = ?
                WHERE id = ?
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            setDetails(statement, details);
            statement.setLong(8, id);
            return StaffAccounts.commitChange(connection, statement.executeUpdate() == 1, "Updated vehicle details and owner", id);
        }
    }

    private void setDetails(PreparedStatement statement, Details details) throws SQLException {
        statement.setLong(1, details.customerId());
        statement.setString(2, details.registration());
        statement.setString(3, details.type());
        statement.setString(4, details.brand());
        statement.setString(5, details.model());
        if (details.year() == null) {
            statement.setNull(6, Types.INTEGER);
        } else {
            statement.setInt(6, details.year());
        }
        statement.setString(7, details.notes());
    }
}
