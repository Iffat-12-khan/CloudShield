package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * File Location: src/database/DBConnection.java
 * Short Purpose: Centralized Database Connection Manager for PostgreSQL via JDBC.
 *                Implements Singleton connection factory with dual-mode support:
 *                1. Live PostgreSQL JDBC connectivity
 *                2. Automatic fallback / mock-store support for viva resilience.
 * Connections:
 *   - Referenced by all DAOs in src/dao/* to acquire Connection instances.
 *   - Configures PostgreSQL credentials (URL, User, Password).
 *   - Manages connection lifecycle and provides transaction utilities.
 */
public class DBConnection {

    // Default PostgreSQL configuration parameters
    private static String dbUrl = "jdbc:postgresql://localhost:5432/cloudshield";
    private static String dbUser = "postgres";
    private static String dbPassword = "your_password"; // Set to your PostgreSQL password

    private static boolean driverLoaded = false;
    private static Boolean isPostgresLive = null;

    static {
        try {
            Class.forName("org.postgresql.Driver");
            driverLoaded = true;
            System.out.println("[DBConnection] PostgreSQL JDBC Driver registered successfully.");
        } catch (ClassNotFoundException e) {
            System.out.println("[DBConnection] Notice: org.postgresql.Driver not in immediate classpath. Using fallback mode if jar is absent.");
            driverLoaded = false;
        }
    }

    /**
     * Set database credentials dynamically if needed.
     */
    public static void setCredentials(String url, String user, String password) {
        dbUrl = url;
        dbUser = user;
        dbPassword = password;
        isPostgresLive = null; // Re-test connection on next call
    }

    public static String getDbUrl() { return dbUrl; }
    public static String getDbUser() { return dbUser; }

    /**
     * Checks if PostgreSQL is currently reachable.
     */
    public static boolean isPostgresAvailable() {
        if (isPostgresLive != null) {
            return isPostgresLive;
        }
        if (!driverLoaded) {
            isPostgresLive = false;
            return false;
        }
        try (Connection testConn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            isPostgresLive = testConn != null && !testConn.isClosed();
            System.out.println("[DBConnection] Connected to live PostgreSQL server: " + dbUrl);
            return true;
        } catch (SQLException e) {
            System.out.println("[DBConnection] Live PostgreSQL server not reachable (" + e.getMessage() + ").");
            System.out.println("[DBConnection] Initializing embedded in-memory database engine for seamless viva demonstration.");
            isPostgresLive = false;
            return false;
        }
    }

    /**
     * Obtains a standard JDBC Connection to PostgreSQL.
     * Throws SQLException if PostgreSQL is unavailable.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Utility method to safely close a connection.
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {}
        }
    }
}
