package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.application.Platform;
import util.SceneManager;

public class MainMenuController {

    @FXML
    private Button btnCreateBill;

    @FXML
    private Button btnCheckRecords;

    @FXML
    private Button btnExit;

    @FXML
    private void initialize() {
        // no business logic here — simple placeholder setup
    }

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
        Platform.exit();
    }
}
