package util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.File;
import java.net.URL;

public class SceneManager {

    private static Stage primaryStage;

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static void showScene(String fxmlFileName) {
        if (primaryStage == null) {
            System.err.println("SceneManager: primaryStage is null. Call setPrimaryStage(...) first.");
            return;
        }

        try {
            File f = new File("resources/fxml/" + fxmlFileName);
            if (!f.exists()) {
                System.err.println("FXML file not found: " + f.getAbsolutePath());
                return;
            }

            URL location = f.toURI().toURL();
            Parent root = FXMLLoader.load(location);
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);

        } catch (Exception e) {
            System.err.println("Failed to load scene: " + fxmlFileName);
            e.printStackTrace();
        }
    }

    /**
     * Load a ViewBill scene and pass the billId to its controller.
     */
    public static void showViewBill(int billId) {
        if (primaryStage == null) {
            System.err.println("SceneManager: primaryStage is null. Call setPrimaryStage(...) first.");
            return;
        }

        try {
            File f = new File("resources/fxml/ViewBill.fxml");
            if (!f.exists()) {
                System.err.println("FXML file not found: " + f.getAbsolutePath());
                return;
            }
            URL location = f.toURI().toURL();
            FXMLLoader loader = new FXMLLoader(location);
            Parent root = loader.load();

            // retrieve controller and pass billId
            Object ctrl = loader.getController();
            if (ctrl instanceof controller.ViewBillController) {
                ((controller.ViewBillController) ctrl).loadBill(billId);
            }

            Scene scene = new Scene(root);
            primaryStage.setScene(scene);

        } catch (Exception e) {
            System.err.println("Failed to load ViewBill.fxml");
            e.printStackTrace();
        }
    }
}
