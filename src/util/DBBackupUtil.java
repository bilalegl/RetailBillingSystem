package util;

import application.DBConnection;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * DBBackupUtil - robust runtime backup helper.
 *
 * Finds the runtime DB file (using DBConnection.getRuntimeDbPath()) and copies it to the
 * user-provided destination (file or directory). If a directory is provided a timestamped
 * filename is created.
 */
public final class DBBackupUtil {

    private static final Logger LOGGER = Logger.getLogger(DBBackupUtil.class.getName());
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DBBackupUtil() {}

    /**
     * Backup the currently-used DB to the given target (file or directory).
     *
     * @param targetPath target file or directory (if directory, a timestamped filename will be used)
     * @return Path of the created backup file
     * @throws IOException on failure
     */
    public static Path backupDatabase(Path targetPath) throws IOException {
        // 1) Try the canonical runtime DB path from DBConnection (preferred)
        Path runtimeDb = DBConnection.getRuntimeDbPath();

        // 2) Also build a few sensible fallbacks for dev/test environments
        Path[] candidates = new Path[] {
                runtimeDb,
                // working dir /database/retailshop.db (useful for dev or if you previously used that layout)
                Paths.get(System.getProperty("user.dir")).resolve("database").resolve("retailshop.db"),
                // working dir /retailshop.db
                Paths.get(System.getProperty("user.dir")).resolve("retailshop.db"),
                // user home fallback (same as runtime usually)
                Paths.get(System.getProperty("user.home")).resolve("RetailBillingSystem").resolve("retailshop.db")
        };

        Path source = null;
        for (Path p : candidates) {
            if (p != null && Files.exists(p) && Files.isRegularFile(p)) {
                source = p;
                break;
            }
        }

        if (source == null) {
            throw new IOException("Database file not found at runtime. Checked: "
                    + String.join(", ",
                        java.util.Arrays.stream(candidates).map(Path::toString).toArray(String[]::new)
                    ));
        }

        // determine destination file
        Path destination;
        if (Files.isDirectory(targetPath)) {
            String name = "retailshop-backup-" + LocalDateTime.now().format(TF) + ".db";
            destination = targetPath.resolve(name);
        } else {
            destination = targetPath;
        }

        // ensure parent exists
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);

        // perform copy (replace if exists)
        // Note: copying a live SQLite DB file is usually fine but can capture in-flight changes.
        // For a fully consistent backup while DB is in use, consider using SQLite online backup API.
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

        LOGGER.info("Database backup completed. Source: " + source.toAbsolutePath() + " -> " + destination.toAbsolutePath());
        return destination;
    }
}
