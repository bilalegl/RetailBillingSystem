package application;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DBConnection:
 * - Keeps a singleton instance for bootstrap (create tables)
 * - But provides a static getConnection() that returns a NEW Connection each time (Option A)
 */
public class DBConnection {

    private static DBConnection instance;

    // Keep the same path you used before
    private static final String DB_PATH = "jdbc:sqlite:resources/database/retailshop.db";

    private DBConnection() {
        // Bootstrap: create tables and set PRAGMAs using a fresh connection
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Enable foreign key support
            stmt.execute("PRAGMA foreign_keys = ON;");

            createTables(conn);

            System.out.println("✅ SQLite database connected and tables verified/created.");

        } catch (SQLException e) {
            System.out.println("❌ Database initialization failed.");
            e.printStackTrace();
        }
    }

    /**
     * Keep this method so your Main.start() call DBConnection.getInstance() still works.
     */
    public static synchronized DBConnection getInstance() {
        if (instance == null) instance = new DBConnection();
        return instance;
    }

    /**
     * Option A: return a NEW connection each time. Callers should use try-with-resources.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_PATH);
    }

    /**
     * Create tables (called during bootstrap). Uses the provided connection.
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

        // Use PreparedStatement in try-with-resources (connection remains open while method executes)
        try (PreparedStatement ps1 = connection.prepareStatement(createBuyersTable);
             PreparedStatement ps2 = connection.prepareStatement(createBillsTable);
             PreparedStatement ps3 = connection.prepareStatement(createBillItemsTable)) {

            ps1.execute();
            ps2.execute();
            ps3.execute();
        }

        // Try to add buyer_id column for backward compatibility; ignore errors if exists
        try (Statement alter = connection.createStatement()) {
            alter.execute("ALTER TABLE Bills ADD COLUMN buyer_id INTEGER;");
        } catch (SQLException ignored) {
            // column likely already exists; ignore
        }
    }
}
