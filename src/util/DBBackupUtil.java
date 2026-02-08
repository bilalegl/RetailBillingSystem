package util;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DBBackupUtil
 * - Copies the application's SQLite database file to a user-provided destination.
 * - Produces a timestamped filename like retailshop-backup-20260208-153012.db
 * - Uses atomic move/copy semantics where possible.
 */
public final class DBBackupUtil {

    private static final String DB_RELATIVE_PATH = "resources/database/retailshop.db";
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DBBackupUtil() {}

    /**
     * Copy the database file into the given target path (file or directory).
     *
     * @param targetPath If targetPath is a directory it will write a timestamped file inside it.
     *                   If targetPath is a file, the DB will be copied to that exact file.
     * @return Path of the written backup file.
     */
    public static Path backupDatabase(Path targetPath) throws IOException {
        Path source = Paths.get(DB_RELATIVE_PATH);
        if (!Files.exists(source)) {
            throw new IOException("Database file not found: " + source.toAbsolutePath());
        }

        Path destination;
        if (Files.isDirectory(targetPath)) {
            String base = "retailshop-backup-" + LocalDateTime.now().format(TF) + ".db";
            destination = targetPath.resolve(base);
        } else {
            destination = targetPath;
        }

        // Ensure parent exists
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);

        // Copy atomically where possible (REPLACE existing)
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);

        return destination;
    }
}
