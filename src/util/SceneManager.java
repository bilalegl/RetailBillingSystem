package util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;

public class SceneManager {

    private static Stage primaryStage;

    /**
     * Set the primary stage (call once from main app)
     */
    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    /**
     * Show a scene by loading FXML from resources/fxml/<fxmlFileName>
     * (does NOT create a new Stage — it reuses the primaryStage)
     *
     * @param fxmlFileName file name, e.g. "MainMenu.fxml"
     */
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
}
