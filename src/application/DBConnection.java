package application;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DBConnection (EXE/fat-JAR ready):
 * - Copies template DB from resources to user folder if missing
 * - Forces SQLite driver registration
 * - Provides getConnection() for new connections
 */
public class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());
    private static DBConnection instance;

    // Folder under user home where DB will be copied
    private static final String USER_DB_FOLDER = System.getProperty("user.home") + File.separator + "RetailBillingSystem";
    private static final String USER_DB_FILE = USER_DB_FOLDER + File.separator + "retailshop.db";

    // Path inside the JAR where template DB resides
    private static final String TEMPLATE_DB_RESOURCE = "/database/retailshop.db";

        public static Path getRuntimeDbPath() {
        // USER_DB_FILE in your class is a String like "<user.home>/RetailBillingSystem/retailshop.db"
        return Paths.get(USER_DB_FILE);
    }
    static {
        try {
            // Force load SQLite driver for fat JAR
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found!", e);
        }
    }

    private DBConnection() {
        try {
            ensureDbFileAndBootstrap();
        } catch (SQLException | IOException e) {
            LOGGER.log(Level.SEVERE, "Database initialization failed", e);
        }
    }

    public static synchronized DBConnection getInstance() {
        if (instance == null) instance = new DBConnection();
        return instance;
    }

    /**
     * Return a NEW connection pointing to the writable DB in user folder
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + USER_DB_FILE);
    }

    /**
     * Ensure DB exists in writable location; copy template if missing, then bootstrap tables
     */
    private void ensureDbFileAndBootstrap() throws SQLException, IOException {
        File dbFolder = new File(USER_DB_FOLDER);
        if (!dbFolder.exists()) dbFolder.mkdirs();

        File dbFile = new File(USER_DB_FILE);
        if (!dbFile.exists()) {
            LOGGER.info("Copying template DB to user folder...");
            try (InputStream is = getClass().getResourceAsStream(TEMPLATE_DB_RESOURCE);
                 FileOutputStream fos = new FileOutputStream(dbFile)) {

                if (is == null) throw new IOException("Template DB not found in JAR: " + TEMPLATE_DB_RESOURCE);

                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
            LOGGER.info("Template DB copied to: " + USER_DB_FILE);
        }

        // Bootstrap tables in case template is empty
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");
            createTables(conn);
            LOGGER.info("SQLite database initialized at " + USER_DB_FILE);
        }
    }

    /**
     * Create tables if missing (same as before)
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

        // Optional: add buyer_id column if older DB (ignore errors)
        try (Statement alter = connection.createStatement()) {
            alter.execute("ALTER TABLE Bills ADD COLUMN buyer_id INTEGER;");
        } catch (SQLException ignored) {
        }
    }
}
