package util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Objects;

public class SceneManager {

    private static Stage primaryStage;
    private static final String STYLESHEET = "/styles/styles.css"; // classpath path

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    /**
     * Show a scene by FXML filename. Works in IDE and when resources are packaged inside the JAR.
     * Tries multiple classpath locations so packaging layout differences won't break it.
     *
     * Example calls:
     *   SceneManager.showScene("MainMenu.fxml");
     *   SceneManager.showScene("fxml/MainMenu.fxml"); // also supported
     */
    public static void showScene(String fxmlFileName) {
        if (primaryStage == null) {
            System.err.println("SceneManager: primaryStage is null.");
            return;
        }

        try {
            URL location = resolveFxmlResource(fxmlFileName);
            if (location == null) {
                System.err.println("FXML file not found in classpath: " + fxmlFileName);
                return;
            }

            Parent root = FXMLLoader.load(location);
            Scene scene = new Scene(root);

            // Load CSS from classpath (if present)
            URL cssLocation = SceneManager.class.getResource(STYLESHEET);
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
            }

            primaryStage.setScene(scene);

        } catch (Exception e) {
            System.err.println("Failed to load scene: " + fxmlFileName);
            e.printStackTrace();
        }
    }

    /**
     * Show the view-bill scene and provide the bill id to the controller (if controller supports it).
     */
    public static void showViewBill(int billId) {
        if (primaryStage == null) return;

        try {
            URL location = resolveFxmlResource("ViewBill.fxml");
            if (location == null) {
                System.err.println("FXML file not found in classpath: ViewBill.fxml");
                return;
            }

            FXMLLoader loader = new FXMLLoader(location);
            Parent root = loader.load();

            Object ctrl = loader.getController();
            if (ctrl instanceof controller.ViewBillController) {
                ((controller.ViewBillController) ctrl).loadBill(billId);
            }

            Scene scene = new Scene(root);
            URL cssLocation = SceneManager.class.getResource(STYLESHEET);
            if (cssLocation != null) {
                scene.getStylesheets().add(cssLocation.toExternalForm());
            }
            primaryStage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Resolve an FXML resource from the classpath by trying a few common locations.
     * Returns the URL to pass to FXMLLoader, or null if not found.
     */
    private static URL resolveFxmlResource(String name) {
        // Normalize name if caller passed "fxml/MainMenu.fxml" or "MainMenu.fxml"
        String base = name.startsWith("/") ? name.substring(1) : name;

        // Try common candidate locations (ordered)
        String[] candidates = new String[] {
                "/fxml/" + base,
                "/resources/fxml/" + base,
                "/" + base,           // direct root (if jar packaged without fxml directory)
                "/views/" + base
        };

        for (String cand : candidates) {
            URL u = SceneManager.class.getResource(cand);
            if (u != null) return u;
        }
        // Last attempt: try ClassLoader root without leading slash
        ClassLoader cl = SceneManager.class.getClassLoader();
        if (cl != null) {
            URL u = cl.getResource("fxml/" + base);
            if (u != null) return u;
            u = cl.getResource("resources/fxml/" + base);
            if (u != null) return u;
            u = cl.getResource(base);
            if (u != null) return u;
        }
        return null;
    }
}
