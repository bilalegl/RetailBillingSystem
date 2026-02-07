package application;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class DBConnection {

    private static DBConnection instance;
    private Connection connection;

    private static final String DB_PATH = "jdbc:sqlite:resources/database/retailshop.db";

    private DBConnection() {
        try {
            connection = DriverManager.getConnection(DB_PATH);
            // ensure foreign keys are enforced
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            System.out.println("✅ SQLite database connected successfully.");

            createTables();

        } catch (SQLException e) {
            System.out.println("❌ Database connection failed.");
            e.printStackTrace();
        }
    }

    public static DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    private void createTables() {

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
                "quantity REAL NOT NULL," +    // changed to REAL to accept double quantities
                "price REAL NOT NULL," +
                "FOREIGN KEY (bill_id) REFERENCES Bills(bill_id)" +
                ");";

        String createBuyersTable =
                "CREATE TABLE IF NOT EXISTS Buyers (" +
                "buyer_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "phone TEXT" +
                ");";

        try (
            PreparedStatement ps1 = connection.prepareStatement(createBuyersTable);
            PreparedStatement ps2 = connection.prepareStatement(createBillsTable);
            PreparedStatement ps3 = connection.prepareStatement(createBillItemsTable)
        ) {
            // Create tables (order matters because of FK)
            ps1.execute();
            ps2.execute();
            ps3.execute();

            System.out.println("✅ Database tables verified/created.");

            // If the DB existed from Phase1 without buyer_id in Bills, attempt to add column.
            // If it already exists, ALTER will throw — catch and ignore.
            try (Statement alter = connection.createStatement()) {
                alter.execute("ALTER TABLE Bills ADD COLUMN buyer_id INTEGER;");
            } catch (SQLException ignored) {
                // column probably already exists — ignore
            }

        } catch (SQLException e) {
            System.out.println("❌ Error creating tables.");
            e.printStackTrace();
        }
    }
}
