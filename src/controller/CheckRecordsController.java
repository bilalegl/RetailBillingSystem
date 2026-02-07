package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import util.SceneManager;

public class CheckRecordsController {

    @FXML
    private Button btnBack;

    @FXML
    private void initialize() {
        // placeholder
    }

    @FXML
    private void handleBack(ActionEvent event) {
        SceneManager.showScene("MainMenu.fxml");
    }
}
