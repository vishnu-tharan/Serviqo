package com.serviqo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class CustomerRepository {
    record Customer(long id, String name, String phone, String email, String address, String createdAt) {
    }

    record Details(String name, String phone, String email, String address) {
    }

    enum DeleteResult {
        DELETED, HAS_VEHICLES, MISSING
    }

    List<Customer> findAll() throws SQLException {
        String sql = """
                SELECT id, name, phone, email, address, created_at
                FROM customers
                ORDER BY name COLLATE NOCASE, id
                """;
        List<Customer> customers = new ArrayList<>();
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                customers.add(new Customer(rows.getLong("id"), rows.getString("name"),
                        rows.getString("phone"), rows.getString("email"), rows.getString("address"),
                        rows.getString("created_at")));
            }
        }
        return customers;
    }

    void add(Details details) throws SQLException {
        String sql = "INSERT INTO customers (name, phone, email, address) VALUES (?, ?, ?, ?)";
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setDetails(statement, details);
            statement.executeUpdate();
        }
    }

    boolean update(long id, Details details) throws SQLException {
        String sql = "UPDATE customers SET name = ?, phone = ?, email = ?, address = ? WHERE id = ?";
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setDetails(statement, details);
            statement.setLong(5, id);
            return statement.executeUpdate() == 1;
        }
    }

    DeleteResult delete(long id) throws SQLException {
        String deleteSql = """
                DELETE FROM customers
                WHERE id = ? AND NOT EXISTS (
                    SELECT 1 FROM vehicles WHERE customer_id = customers.id
                )
                """;
        try (Connection connection = Database.connect();
             PreparedStatement delete = connection.prepareStatement(deleteSql)) {
            connection.setAutoCommit(false);
            delete.setLong(1, id);
            DeleteResult result;
            if (delete.executeUpdate() == 1) {
                result = DeleteResult.DELETED;
            } else {
                try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM customers WHERE id = ?")) {
                    check.setLong(1, id);
                    try (ResultSet rows = check.executeQuery()) {
                        result = rows.next() ? DeleteResult.HAS_VEHICLES : DeleteResult.MISSING;
                    }
                }
            }
            connection.commit();
            return result;
        }
    }

    private void setDetails(PreparedStatement statement, Details details) throws SQLException {
        statement.setString(1, details.name());
        statement.setString(2, details.phone());
        statement.setString(3, details.email());
        statement.setString(4, details.address());
    }
}
