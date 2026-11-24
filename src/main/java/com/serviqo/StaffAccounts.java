package com.serviqo;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class StaffAccounts {
    enum Role { MANAGER, MECHANIC }

    record AuditEntry(String time, String staff, String action, Long recordId) {
    }

    private record Session(long id, Role role) {
    }

    private static final int HASH_ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] UNKNOWN_USER_SALT = new byte[16];
    private static volatile Session session;

    private StaffAccounts() {
    }

    static void initialize(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS staff_accounts (
                    id INTEGER PRIMARY KEY,
                    username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                    role TEXT NOT NULL CHECK (role IN ('MANAGER', 'MECHANIC')),
                    salt BLOB NOT NULL,
                    password_hash BLOB NOT NULL,
                    hash_iterations INTEGER NOT NULL CHECK (hash_iterations >= 600000)
                )
                """);
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS staff_audit (
                    id INTEGER PRIMARY KEY,
                    staff_id INTEGER NOT NULL REFERENCES staff_accounts(id) ON DELETE RESTRICT,
                    action TEXT NOT NULL,
                    record_id INTEGER,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    static boolean needsSetup() throws SQLException {
        try (Connection connection = Database.connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT 1 FROM staff_accounts LIMIT 1")) {
            return !rows.next();
        }
    }

    static boolean createFirstManager(String username, char[] password) throws SQLException {
        return create(username, password, Role.MANAGER, true);
    }

    static boolean addStaff(String username, char[] password, Role role) throws SQLException {
        requireManager();
        return create(username, password, role, false);
    }

    private static boolean create(String username, char[] password, Role role, boolean first) throws SQLException {
        String normalized = normalize(username);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,31}")) {
            throw new IllegalArgumentException("Use 3 to 32 letters, digits, dots, underscores or hyphens for the username. Start with a letter or digit.");
        }
        if (password.length < 15 || password.length > 128) {
            throw new IllegalArgumentException("Use a passphrase with 15 to 128 characters.");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = hash(password, salt, HASH_ITERATIONS);
        String sql = """
                INSERT INTO staff_accounts (username, role, salt, password_hash, hash_iterations)
                SELECT ?, ?, ?, ?, ?
                """;
        if (first) {
            sql += "WHERE NOT EXISTS (SELECT 1 FROM staff_accounts)";
        } else {
            sql += "WHERE NOT EXISTS (SELECT 1 FROM staff_accounts WHERE username = ?)";
        }
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            statement.setString(1, normalized);
            statement.setString(2, role.name());
            statement.setBytes(3, salt);
            statement.setBytes(4, hash);
            statement.setInt(5, HASH_ITERATIONS);
            if (!first) {
                statement.setString(6, normalized);
            }
            if (statement.executeUpdate() == 0) {
                return false;
            }
            long id = lastInsertedId(connection);
            if (first) {
                audit(connection, id, "Created first manager", id);
            } else {
                recordChange(connection, "Created staff account (" + role + ")", id);
            }
            connection.commit();
            return true;
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    static boolean signIn(String username, char[] password) throws SQLException {
        if (session != null) {
            throw new IllegalStateException("Sign out before switching accounts.");
        }
        if (username.length() > 64 || password.length > 128) {
            return false;
        }
        String sql = "SELECT id, role, salt, password_hash, hash_iterations FROM staff_accounts WHERE username = ?";
        Session candidate = null;
        byte[] salt = UNKNOWN_USER_SALT;
        byte[] expected = new byte[32];
        int iterations = HASH_ITERATIONS;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    candidate = new Session(rows.getLong("id"), Role.valueOf(rows.getString("role")));
                    salt = rows.getBytes("salt");
                    expected = rows.getBytes("password_hash");
                    iterations = rows.getInt("hash_iterations");
                }
            }
        }
        byte[] actual = hash(password, salt, iterations);
        boolean matches = MessageDigest.isEqual(expected, actual);
        Arrays.fill(actual, (byte) 0);
        Arrays.fill(expected, (byte) 0);
        if (candidate == null || !matches) {
            return false;
        }
        try (Connection connection = Database.connect()) {
            audit(connection, candidate.id(), "Signed in", null);
        }
        session = candidate;
        return true;
    }

    static void signOut() {
        session = null;
    }

    static boolean isManager() {
        Session current = session;
        return current != null && current.role() == Role.MANAGER;
    }

    static void requireSignedIn() throws SQLException {
        requireStaff();
    }

    static void requireManager() throws SQLException {
        if (requireStaff().role() != Role.MANAGER) {
            throw new SQLException("Manager access is required.");
        }
    }

    static void recordChange(Connection connection, String action, Long id) throws SQLException {
        audit(connection, requireStaff().id(), action, id);
    }

    static boolean commitChange(Connection connection, boolean changed, String action, Long id) throws SQLException {
        if (changed) {
            recordChange(connection, action, id == null ? lastInsertedId(connection) : id);
        }
        connection.commit();
        return changed;
    }

    static long lastInsertedId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT last_insert_rowid()")) {
            if (!rows.next()) {
                throw new SQLException("Could not obtain the new record ID.");
            }
            return rows.getLong(1);
        }
    }

    static List<AuditEntry> recentAudit() throws SQLException {
        requireManager();
        String sql = """
                SELECT a.created_at, s.username, a.action, a.record_id
                FROM staff_audit a JOIN staff_accounts s ON s.id = a.staff_id
                ORDER BY a.id DESC LIMIT 200
                """;
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection connection = Database.connect();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                long id = rows.getLong("record_id");
                entries.add(new AuditEntry(rows.getString("created_at"), rows.getString("username"),
                        rows.getString("action"), rows.getObject("record_id") == null ? null : id));
            }
        }
        return entries;
    }

    private static Session requireStaff() throws SQLException {
        Session current = session;
        if (current == null) {
            throw new SQLException("Staff sign-in is required.");
        }
        return current;
    }

    private static void audit(Connection connection, long staffId, String action, Long id) throws SQLException {
        String sql = "INSERT INTO staff_audit (staff_id, action, record_id) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, staffId);
            statement.setString(2, action);
            statement.setObject(3, id);
            statement.executeUpdate();
        }
    }

    private static String normalize(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    private static byte[] hash(char[] password, byte[] salt, int iterations) throws SQLException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new SQLException("Staff password verification is unavailable.", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
