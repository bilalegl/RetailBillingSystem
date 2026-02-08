package application;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DBConnection (Phase 10 - production-ready):
 * - Bootstraps DB on first use
 * - Provides getConnection() returning a NEW Connection (Option A)
 * - Uses java.util.logging instead of System.out prints
 *
 * Note: Keep DB file under resources/database/retailshop.db (project relative path).
 */
public class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());
    private static DBConnection instance;

    // Keep the same DB path as before
    private static final String DB_PATH = "jdbc:sqlite:resources/database/retailshop.db";

    private DBConnection() {
        // Bootstrap and ensure tables exist. Uses a fresh connection (auto-closed).
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON;");

            createTables(conn);
            LOGGER.info("SQLite database initialized.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database initialization failed", e);
        }
    }

    /**
     * Ensure singleton instance exists and DB is bootstrapped.
     */
    public static synchronized DBConnection getInstance() {
        if (instance == null) instance = new DBConnection();
        return instance;
    }

    /**
     * Return a NEW Connection for callers. Callers should use try-with-resources.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_PATH);
    }

    /**
     * Create required tables if missing. Uses the provided connection.
     */
    private void createTables(Connection connection) throws SQLException {

        String createBuyersTable =
                "CREATE TABLE IF NOT EXISTS Buyers (" +
                "buyer_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "phone TEXT" +
                ");";

        String createBillsTable =
                "CREATE TABLE IF NOT EXISTS Bills (" +
                "bill_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "bill_date TEXT NOT NULL," +
                "total_amount REAL NOT NULL," +
                "buyer_id INTEGER," +
                "FOREIGN KEY (buyer_id) REFERENCES Buyers(buyer_id)" +
                ");";

        String createBillItemsTable =
                "CREATE TABLE IF NOT EXISTS BillItems (" +
                "item_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "bill_id INTEGER NOT NULL," +
                "item_name TEXT NOT NULL," +
                "quantity REAL NOT NULL," +
                "price REAL NOT NULL," +
                "FOREIGN KEY (bill_id) REFERENCES Bills(bill_id)" +
                ");";

        try (PreparedStatement ps1 = connection.prepareStatement(createBuyersTable);
             PreparedStatement ps2 = connection.prepareStatement(createBillsTable);
             PreparedStatement ps3 = connection.prepareStatement(createBillItemsTable)) {
            ps1.execute();
            ps2.execute();
            ps3.execute();
        }

        // Best-effort: add buyer_id if it doesn't exist (older DBs). Ignore errors.
        try (Statement alter = connection.createStatement()) {
            alter.execute("ALTER TABLE Bills ADD COLUMN buyer_id INTEGER;");
        } catch (SQLException ignored) {
            // Ignore — column likely exists already.
        }
    }
}
