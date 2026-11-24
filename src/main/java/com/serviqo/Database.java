package com.serviqo;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteOpenMode;

final class Database {
    private static final String FILE_NAME = "serviqo.db";
    private static final int LOCK_WAIT_MS = 5_000;
    static final int PAGE_SIZE = 50;
    static final int NAME = 120;
    static final int PHONE = 40;
    static final int EMAIL = 254;
    static final int ADDRESS = 300;
    static final int REGISTRATION = 32;
    static final int VEHICLE_TYPE = 40;
    static final List<String> VEHICLE_TYPES = List.of("Car", "Motorcycle", "Van", "Three-wheeler", "Truck", "Other");
    static final int VEHICLE_DETAIL = 80;
    static final int NOTES = 1_000;
    static final int PROBLEM = 500;
    static final int DESCRIPTION = 2_000;
    static final int PART_NAME = 120;
    static final int PARTS_PER_JOB = 500;
    private static volatile Path databaseFile;

    private Database() {
    }

    static void requireLength(String value, int limit, String label) {
        if (value != null && (value.indexOf('\0') >= 0 || value.codePointCount(0, value.length()) > limit)) {
            throw new IllegalArgumentException(label + " must contain at most " + limit + " characters and no null characters.");
        }
    }

    static void bindPage(PreparedStatement statement, int start, long offset) throws SQLException {
        statement.setInt(start, PAGE_SIZE + 1);
        statement.setLong(start + 1, offset);
    }

    static Connection connect() throws SQLException {
        Path path = databaseFile;
        if (path == null) {
            throw new SQLException("Serviqo has not initialized its database.");
        }
        return connect(path);
    }

    private static Connection connect(Path path) throws SQLException {
        SQLiteConfig config = connectionConfig();
        return DriverManager.getConnection(url(path), config.toProperties());
    }

    private static SQLiteConfig connectionConfig() {
        SQLiteConfig config = new SQLiteConfig();
        config.setOpenMode(SQLiteOpenMode.OPEN_URI);
        config.resetOpenMode(SQLiteOpenMode.CREATE);
        config.enforceForeignKeys(true);
        config.setBusyTimeout(LOCK_WAIT_MS);
        return config;
    }

    static String userError(SQLException exception, String fallback) {
        int code = exception.getErrorCode() & 0xff;
        if (code == SQLiteErrorCode.SQLITE_BUSY.code || code == SQLiteErrorCode.SQLITE_LOCKED.code) {
            return "The database is busy. Wait a moment and try again.";
        }
        return fallback;
    }

    private static String url(Path path) {
        return "jdbc:sqlite:" + path.toUri().toASCIIString();
    }

    static void initialize(boolean demo, Path legacy) throws SQLException, IOException {
        databaseFile = null;
        if (legacy != null && (!legacy.isAbsolute() || legacy.toUri().getAuthority() != null
                || !Files.isRegularFile(legacy, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Choose an existing local SQLite database with an absolute path.");
        }
        Path directory = storageDirectory();
        makePrivateDirectory(directory);
        if (demo) {
            directory = directory.resolve("demo");
            makePrivateDirectory(directory);
        }

        Path destination = directory.resolve(FILE_NAME);
        Path lockPath = directory.resolve(".startup.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock lock = waitForStartupLock(channel)) {
            if (legacy != null) {
                importLegacy(legacy, destination);
            }
            if (Files.notExists(destination, LinkOption.NOFOLLOW_LINKS)) {
                Files.createFile(destination);
            }
            checkPrivateFile(destination, directory);
            for (String suffix : List.of("-journal", "-wal", "-shm")) {
                Path sidecar = directory.resolve(FILE_NAME + suffix);
                if (!Files.notExists(sidecar, LinkOption.NOFOLLOW_LINKS)) {
                    checkPrivateFile(sidecar, directory);
                }
            }
            initializeSchema(destination);
            databaseFile = destination;
        }
    }

    private static FileLock waitForStartupLock(FileChannel channel) throws IOException {
        long deadline = System.nanoTime() + Duration.ofMillis(LOCK_WAIT_MS).toNanos();
        do {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    return lock;
                }
            } catch (OverlappingFileLockException exception) {
                // A different thread in this process is still setting up the database.
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Waiting for Serviqo database setup was interrupted.", exception);
            }
        } while (System.nanoTime() - deadline < 0);
        throw new IOException("Another Serviqo instance is setting up this database. Try again shortly.");
    }

    private static Path storageDirectory() throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        String base = windows ? System.getenv("LOCALAPPDATA") : System.getProperty("user.home");
        if (base == null || base.isBlank() || !Path.of(base).isAbsolute()) {
            throw new IOException("Serviqo could not locate the user application data directory.");
        }
        Path directory = Path.of(base).resolve(windows ? "Serviqo" : ".serviqo").normalize();
        if (directory.toUri().getAuthority() != null) {
            throw new IOException("Serviqo requires a local application data directory.");
        }
        return directory;
    }

    private static void makePrivateDirectory(Path directory) throws IOException {
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) {
            try {
                if (Files.getFileStore(directory.getParent()).supportsFileAttributeView(PosixFileAttributeView.class)) {
                    Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
                } else {
                    Files.createDirectory(directory);
                }
            } catch (java.nio.file.FileAlreadyExistsException exception) {
                // Another instance created the directory. Verify it below.
            }
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !directory.toRealPath().equals(directory.getParent().toRealPath().resolve(directory.getFileName()))) {
            throw new IOException("The Serviqo storage directory must be a local, regular directory.");
        }
        restrictAccess(directory, true);
    }

    private static void checkPrivateFile(Path file, Path directory) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !Files.getOwner(file, LinkOption.NOFOLLOW_LINKS).equals(Files.getOwner(directory))) {
            throw new IOException("The Serviqo database or a sidecar is not a regular file owned by the storage directory owner.");
        }
        restrictAccess(file, false);
    }

    private static void restrictAccess(Path path, boolean directory) throws IOException {
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            var owner = acl.getOwner();
            var entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (directory) {
                entry.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
            }
            List<AclEntry> expected = List.of(entry.build());
            if (!hasPrivateAcl(acl, directory)) {
                acl.setAcl(expected);
            }
            if (!hasPrivateAcl(acl, directory)) {
                throw new IOException("Could not verify private access for Serviqo storage.");
            }
        } else if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        } else {
            throw new IOException("The filesystem cannot restrict access to Serviqo storage.");
        }
    }

    private static boolean hasPrivateAcl(AclFileAttributeView acl, boolean directory) throws IOException {
        var owner = acl.getOwner();
        boolean ownerHasAccess = false;
        for (AclEntry entry : acl.getAcl()) {
            if (entry.type() != AclEntryType.ALLOW || entry.permissions().isEmpty()) {
                continue;
            }
            if (entry.principal().equals(owner)) {
                boolean applies = !entry.flags().contains(AclEntryFlag.INHERIT_ONLY);
                boolean inherits = !directory || (entry.flags().contains(AclEntryFlag.FILE_INHERIT)
                        && entry.flags().contains(AclEntryFlag.DIRECTORY_INHERIT));
                if (applies && inherits && entry.permissions().contains(AclEntryPermission.READ_DATA)
                        && entry.permissions().contains(AclEntryPermission.WRITE_DATA)) {
                    ownerHasAccess = true;
                }
            } else {
                String name = entry.principal().getName().toUpperCase(Locale.ROOT);
                if (!List.of("NT AUTHORITY\\SYSTEM", "BUILTIN\\ADMINISTRATORS", "S-1-5-18", "S-1-5-32-544").contains(name)) {
                    return false;
                }
            }
        }
        return ownerHasAccess;
    }

    private static void importLegacy(Path source, Path destination) throws SQLException, IOException {
        if (!Files.notExists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A private Serviqo database already exists. Import cannot replace it.");
        }
        Path snapshot = Files.createTempFile(destination.getParent(), "legacy-", ".db");
        try {
            restrictAccess(snapshot, false);
            prepareLegacySnapshot(source, snapshot);
            Files.move(snapshot, destination);
        } finally {
            Files.deleteIfExists(snapshot);
        }
    }

    private static void prepareLegacySnapshot(Path source, Path snapshot) throws SQLException {
        SQLiteConfig config = connectionConfig();
        config.setReadOnly(true);
        try (Connection connection = DriverManager.getConnection(url(source), config.toProperties());
             PreparedStatement copy = connection.prepareStatement("VACUUM INTO ?")) {
            copy.setString(1, snapshot.toString());
            copy.execute();
        }
        try (Connection connection = connect(snapshot)) {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("PRAGMA quick_check")) {
                if (!rows.next() || !"ok".equals(rows.getString(1))) {
                    throw new SQLException("The imported database failed its integrity check.");
                }
            }
            checkLegacySchema(connection);
        }
        initializeSchema(snapshot);
    }

    private static void checkLegacySchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : List.of("customers", "vehicles", "service_jobs", "service_parts")) {
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT 1 FROM sqlite_schema WHERE type = 'table' AND name = ?")) {
                    query.setString(1, table);
                    try (ResultSet rows = query.executeQuery()) {
                        if (!rows.next()) {
                            throw new SQLException("The selected database is missing a Serviqo table: " + table);
                        }
                    }
                }
            }
            statement.executeQuery("SELECT id, name, phone, email, address, created_at FROM customers LIMIT 0").close();
            statement.executeQuery("""
                    SELECT id, customer_id, registration_number, vehicle_type, brand, model, manufacturing_year, notes
                    FROM vehicles LIMIT 0
                    """).close();
            statement.executeQuery("""
                    SELECT id, vehicle_id, reported_problem, service_description, service_date, status,
                           labor_charge_cents, created_at, completed_at FROM service_jobs LIMIT 0
                    """).close();
            statement.executeQuery("""
                    SELECT id, service_job_id, part_name, quantity, unit_price_cents FROM service_parts LIMIT 0
                    """).close();
            try (ResultSet rows = statement.executeQuery("PRAGMA foreign_key_check")) {
                if (rows.next()) {
                    throw new SQLException("The selected database has broken record links.");
                }
            }
        }
    }

    private static void initializeSchema(Path path) throws SQLException {
        try (Connection connection = connect(path); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS customers (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL CHECK (length(trim(name)) > 0 AND length(name) <= %d),
                        phone TEXT NOT NULL CHECK (length(trim(phone)) > 0 AND length(phone) <= %d),
                        email TEXT CHECK (length(email) <= %d),
                        address TEXT CHECK (length(address) <= %d),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """.formatted(NAME, PHONE, EMAIL, ADDRESS));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vehicles (
                        id INTEGER PRIMARY KEY,
                        customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
                        registration_number TEXT NOT NULL COLLATE NOCASE UNIQUE
                            CHECK (length(trim(registration_number)) > 0 AND length(registration_number) <= %d),
                        vehicle_type TEXT NOT NULL CHECK (%s),
                        brand TEXT NOT NULL CHECK (length(brand) <= %d AND %s),
                        model TEXT NOT NULL CHECK (length(model) <= %d AND %s),
                        manufacturing_year INTEGER,
                        notes TEXT CHECK (length(notes) <= %d)
                    )
                    """.formatted(REGISTRATION, allowedVehicleType("vehicle_type"), VEHICLE_DETAIL, nonBlank("brand"),
                            VEHICLE_DETAIL, nonBlank("model"), NOTES));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS service_jobs (
                        id INTEGER PRIMARY KEY,
                        vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
                        reported_problem TEXT NOT NULL CHECK (length(trim(reported_problem)) > 0 AND length(reported_problem) <= %d),
                        service_description TEXT CHECK (length(service_description) <= %d),
                        service_date TEXT NOT NULL CHECK (%s),
                        status TEXT NOT NULL DEFAULT 'Pending'
                            CHECK (status IN ('Pending', 'In Progress', 'Completed', 'Cancelled')),
                        labor_charge_cents INTEGER NOT NULL DEFAULT 0
                            CHECK (typeof(labor_charge_cents) = 'integer' AND labor_charge_cents >= 0),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        completed_at TEXT
                    )
                    """.formatted(PROBLEM, DESCRIPTION, calendarDate("service_date")));

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS service_parts (
                        id INTEGER PRIMARY KEY,
                        service_job_id INTEGER NOT NULL REFERENCES service_jobs(id) ON DELETE RESTRICT,
                        part_name TEXT NOT NULL CHECK (length(trim(part_name)) > 0 AND length(part_name) <= %d),
                        quantity INTEGER NOT NULL CHECK (typeof(quantity) = 'integer' AND quantity > 0),
                        unit_price_cents INTEGER NOT NULL
                            CHECK (typeof(unit_price_cents) = 'integer' AND unit_price_cents >= 0)
                    )
                    """.formatted(PART_NAME));
            StaffAccounts.initialize(statement);
            installTextLimits(statement);
            installRecordRules(statement);
            connection.commit();
        }
    }

    private static void installTextLimits(Statement statement) throws SQLException {
        textLimit(statement, "customers", "name", NAME);
        textLimit(statement, "customers", "phone", PHONE);
        textLimit(statement, "customers", "email", EMAIL);
        textLimit(statement, "customers", "address", ADDRESS);
        textLimit(statement, "vehicles", "registration_number", REGISTRATION);
        textLimit(statement, "vehicles", "vehicle_type", VEHICLE_TYPE);
        textLimit(statement, "vehicles", "brand", VEHICLE_DETAIL);
        textLimit(statement, "vehicles", "model", VEHICLE_DETAIL);
        textLimit(statement, "vehicles", "notes", NOTES);
        textLimit(statement, "service_jobs", "reported_problem", PROBLEM);
        textLimit(statement, "service_jobs", "service_description", DESCRIPTION);
        textLimit(statement, "service_jobs", "service_date", 10);
        textLimit(statement, "service_parts", "part_name", PART_NAME);
        textLimit(statement, "staff_accounts", "username", 32);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS customers_name ON customers(name COLLATE NOCASE, id)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS service_jobs_recent ON service_jobs(service_date DESC, id DESC)
                """);
        statement.executeUpdate("""
                CREATE INDEX IF NOT EXISTS service_parts_job ON service_parts(service_job_id)
                """);
        for (String operation : List.of("INSERT", "UPDATE OF service_job_id")) {
            boolean update = operation.startsWith("UPDATE");
            String condition = update ? "NEW.service_job_id IS NOT OLD.service_job_id AND " : "";
            statement.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS service_parts_count_%s
                    BEFORE %s ON service_parts
                    WHEN %s (SELECT 1 FROM service_parts WHERE service_job_id = NEW.service_job_id
                             LIMIT 1 OFFSET %d) IS NOT NULL
                    BEGIN SELECT RAISE(ABORT, 'This service job has reached its part limit'); END
                    """.formatted(update ? "update" : "insert", operation, condition, PARTS_PER_JOB - 1));
        }
    }

    private static void textLimit(Statement statement, String table, String column, int max) throws SQLException {
        String value = "NEW." + column;
        String invalid = "(length(" + value + ") > " + max + " OR instr(" + value + ", char(0)) > 0)";
        guardColumn(statement, table, column, "length", invalid, "Text exceeds the allowed limit");
    }

    private static String allowedVehicleType(String field) {
        String options = "'" + String.join("', '", VEHICLE_TYPES) + "'";
        return field + " IS NOT NULL AND " + field + " IN (" + options + ")";
    }

    private static String nonBlank(String field) {
        return "typeof(" + field + ") = 'text' AND length(trim(" + field
                + ", char(9,10,11,12,13,28,29,30,31,32,5760,8192,8193,8194,8195,8196,8197,8198,8200,8201,8202,8232,8233,8287,12288))) > 0";
    }

    private static String calendarDate(String field) {
        return """
                typeof(%1$s) = 'text'
                AND %1$s GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
                AND substr(%1$s, 1, 4) <> '0000'
                AND date(%1$s, '+0 days') IS %1$s
                """.formatted(field).strip();
    }

    private static void installRecordRules(Statement statement) throws SQLException {
        guardColumn(statement, "vehicles", "brand", "required", "NOT (" + nonBlank("NEW.brand") + ")",
                "Vehicle brand is required");
        guardColumn(statement, "vehicles", "model", "required", "NOT (" + nonBlank("NEW.model") + ")",
                "Vehicle model is required");
        guardColumn(statement, "vehicles", "vehicle_type", "allowed", "NOT (" + allowedVehicleType("NEW.vehicle_type") + ")",
                "Vehicle type is not supported");
        guardColumn(statement, "service_jobs", "service_date", "calendar", "NOT (" + calendarDate("NEW.service_date") + ")",
                "Service date must be a valid date in YYYY-MM-DD format");
    }

    private static void guardColumn(Statement statement, String table, String column, String rule, String invalid,
                                    String error) throws SQLException {
        for (boolean update : new boolean[]{false, true}) {
            String operation = update ? "UPDATE OF " + column : "INSERT";
            String changed = update ? "NEW." + column + " IS NOT OLD." + column + " AND " : "";
            statement.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS %s_%s_%s_%s
                    BEFORE %s ON %s WHEN %s%s
                    BEGIN SELECT RAISE(ABORT, '%s'); END
                    """.formatted(table, column, rule, update ? "update" : "insert", operation, table, changed, invalid, error));
        }
    }
}
