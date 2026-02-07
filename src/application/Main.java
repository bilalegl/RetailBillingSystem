package application;

import javafx.application.Application;
import javafx.stage.Stage;
import util.SceneManager;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Initialize DB (Phase 1) — tables will be verified/created there
        DBConnection.getInstance();

        // Register primary stage for scene switching
        SceneManager.setPrimaryStage(primaryStage);

        // Set window title
        primaryStage.setTitle("Retail Billing System");

        // Show Main Menu on startup
        SceneManager.showScene("MainMenu.fxml");

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
