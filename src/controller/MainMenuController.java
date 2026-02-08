package controller;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import util.DBBackupUtil;
import util.SceneManager;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main menu controller.
 * Added: Backup DB handler (Phase 10).
 */
public class MainMenuController {

    private static final Logger LOGGER = Logger.getLogger(MainMenuController.class.getName());

    @FXML private Button btnCreateBill;
    @FXML private Button btnCheckRecords;
    @FXML private Button btnBackupDB;
    @FXML private Button btnExit;

    @FXML
    private void handleCreateBill(ActionEvent event) {
        SceneManager.showScene("CreateBill.fxml");
    }

    @FXML
    private void handleCheckRecords(ActionEvent event) {
        SceneManager.showScene("CheckRecords.fxml");
    }

    @FXML
    private void handleExit(ActionEvent event) {
        // safe exit
        System.exit(0);
    }

    /**
     * Backup DB: prompts user for destination and copies DB in background.
     * Uses FileChooser; allows user to pick a folder or file. Operates on background thread.
     */
    @FXML
    private void handleBackupDB(ActionEvent event) {
        Window w = btnBackupDB.getScene().getWindow();

        // Let user choose file (recommended) or directory (platform dependent)
        FileChooser fc = new FileChooser();
        fc.setTitle("Save database backup");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite DB Files", "*.db"));
        // provide a smart default filename
        String suggested = "retailshop-backup.db";
        fc.setInitialFileName(suggested);

        java.io.File chosen = fc.showSaveDialog(w);
        if (chosen == null) return;

        // Run backup in background
        btnBackupDB.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Path dest = DBBackupUtil.backupDatabase(chosen.toPath());
                LOGGER.info("Database backed up to: " + dest.toAbsolutePath());
                return null;
            }
        };

        task.setOnSucceeded(ts -> {
            btnBackupDB.setDisable(false);
            showInfo("Backup complete", "Database backup saved successfully.");
        });
        task.setOnFailed(ts -> {
            btnBackupDB.setDisable(false);
            Throwable ex = task.getException();
            LOGGER.log(Level.SEVERE, "Backup failed", ex);
            showError("Backup failed", ex == null ? "Unknown error" : ex.getMessage());
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
