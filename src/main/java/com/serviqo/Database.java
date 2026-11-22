package com.serviqo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

final class Database {
    private static final String URL = "jdbc:sqlite:serviqo.db";

    private Database() {
    }

    static Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
        return connection;
    }

    static void initialize() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS customers (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL CHECK (length(trim(name)) > 0),
                        phone TEXT NOT NULL CHECK (length(trim(phone)) > 0),
                        email TEXT,
                        address TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vehicles (
                        id INTEGER PRIMARY KEY,
                        customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
                        registration_number TEXT NOT NULL COLLATE NOCASE UNIQUE
                            CHECK (length(trim(registration_number)) > 0),
                        vehicle_type TEXT NOT NULL,
                        brand TEXT NOT NULL,
                        model TEXT NOT NULL,
                        manufacturing_year INTEGER,
                        notes TEXT
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS service_jobs (
                        id INTEGER PRIMARY KEY,
                        vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
                        reported_problem TEXT NOT NULL CHECK (length(trim(reported_problem)) > 0),
                        service_description TEXT,
                        service_date TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'Pending'
                            CHECK (status IN ('Pending', 'In Progress', 'Completed', 'Cancelled')),
                        labor_charge_cents INTEGER NOT NULL DEFAULT 0
                            CHECK (typeof(labor_charge_cents) = 'integer' AND labor_charge_cents >= 0),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        completed_at TEXT
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS service_parts (
                        id INTEGER PRIMARY KEY,
                        service_job_id INTEGER NOT NULL REFERENCES service_jobs(id) ON DELETE RESTRICT,
                        part_name TEXT NOT NULL CHECK (length(trim(part_name)) > 0),
                        quantity INTEGER NOT NULL CHECK (typeof(quantity) = 'integer' AND quantity > 0),
                        unit_price_cents INTEGER NOT NULL
                            CHECK (typeof(unit_price_cents) = 'integer' AND unit_price_cents >= 0)
                    )
                    """);
            connection.commit();
        }
    }
}
