package application;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DBConnection {

    private static DBConnection instance;
    private Connection connection;

    private static final String DB_PATH = "jdbc:sqlite:resources/database/retailshop.db";

    private DBConnection() {
        try {
            connection = DriverManager.getConnection(DB_PATH);
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
                "total_amount REAL NOT NULL" +
                ");";

        String createBillItemsTable =
                "CREATE TABLE IF NOT EXISTS BillItems (" +
                "item_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "bill_id INTEGER NOT NULL," +
                "item_name TEXT NOT NULL," +
                "quantity INTEGER NOT NULL," +
                "price REAL NOT NULL," +
                "FOREIGN KEY (bill_id) REFERENCES Bills(bill_id)" +
                ");";

        String createBuyersTable =
                "CREATE TABLE IF NOT EXISTS Buyers (" +
                "buyer_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "phone TEXT" +
                ");";

        try (
            PreparedStatement ps1 = connection.prepareStatement(createBillsTable);
            PreparedStatement ps2 = connection.prepareStatement(createBillItemsTable);
            PreparedStatement ps3 = connection.prepareStatement(createBuyersTable)
        ) {
            ps1.execute();
            ps2.execute();
            ps3.execute();

            System.out.println("✅ Database tables verified/created.");

        } catch (SQLException e) {
            System.out.println("❌ Error creating tables.");
            e.printStackTrace();
        }
    }
}
