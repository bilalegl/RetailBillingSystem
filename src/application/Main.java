package application;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {

        // Initialize DB (tables will be created here)
        DBConnection.getInstance();

        Pane root = new Pane(); // Placeholder UI
        Scene scene = new Scene(root, 600, 400);

        primaryStage.setTitle("Retail Billing System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
