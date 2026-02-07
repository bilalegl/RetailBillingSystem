package controller;

import dao.BillDAO;
import model.Bill;
import model.BillItem;
import model.Buyer;
import util.SceneManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for CheckRecords screen (read-only).
 */
public class CheckRecordsController {

    @FXML private TextField txtBillNumber;
    @FXML private TextField txtBuyerName;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Button btnSearch;
    @FXML private Button btnReset;
    @FXML private Button btnBack;

    @FXML private TableView<Bill> tblBills;
    @FXML private TableColumn<Bill, String> colBillId;
    @FXML private TableColumn<Bill, String> colBillDate;
    @FXML private TableColumn<Bill, String> colBuyer;
    @FXML private TableColumn<Bill, String> colGrandTotal;

    @FXML private Label lblBuyerName;
    @FXML private Label lblBuyerPhone;
    @FXML private Label lblSubtotal;
    @FXML private Label lblDiscountAmount;
    @FXML private Label lblGrandTotal;

    @FXML private TableView<BillItem> tblItems;
    @FXML private TableColumn<BillItem, String> colItemProduct;
    @FXML private TableColumn<BillItem, String> colItemQty;
    @FXML private TableColumn<BillItem, String> colItemPrice;
    @FXML private TableColumn<BillItem, String> colItemTotal;

    private final ObservableList<Bill> bills = FXCollections.observableArrayList();
    private final ObservableList<BillItem> items = FXCollections.observableArrayList();

    private final BillDAO billDAO = new BillDAO();

    private final DateTimeFormatter displayDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    private void initialize() {
        // Table columns (Bill)
colBillId.setCellValueFactory(c ->
    new SimpleStringProperty(String.valueOf(c.getValue().getId()))
);

colBillDate.setCellValueFactory(c ->
    new SimpleStringProperty(
        c.getValue().getBillDate() == null ? "" : c.getValue().getBillDate()
    )
);

colBuyer.setCellValueFactory(c -> {
    String name = (c.getValue().getBuyer() == null)
            ? ""
            : c.getValue().getBuyer().getName();
    return new SimpleStringProperty(name == null ? "" : name);
});

colGrandTotal.setCellValueFactory(c ->
    new SimpleStringProperty(String.format("%.2f", c.getValue().getGrandTotal()))
);


        tblBills.setItems(bills);

       colItemProduct.setCellValueFactory(c ->
    new SimpleStringProperty(c.getValue().getProductName())
);

colItemQty.setCellValueFactory(c ->
    new SimpleStringProperty(String.format("%.2f", c.getValue().getQuantity()))
);

colItemPrice.setCellValueFactory(c ->
    new SimpleStringProperty(String.format("%.2f", c.getValue().getUnitPrice()))
);

colItemTotal.setCellValueFactory(c ->
    new SimpleStringProperty(String.format("%.2f", c.getValue().getItemTotal()))
);


        tblItems.setItems(items);

        // Load all bills initially
        loadBills(null, null, null, null);

        // Selection listener
        tblBills.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                showBillDetails(sel);
            } else {
                clearDetails();
            }
        });
    }

    private void loadBills(Integer billId, String buyerName, LocalDate dateFrom, LocalDate dateTo) {
        bills.clear();
        try {
            List<Bill> result = billDAO.getBills(billId, buyerName, dateFrom, dateTo);
            bills.addAll(result);
            if (!bills.isEmpty()) {
                tblBills.getSelectionModel().selectFirst();
            } else {
                clearDetails();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load bills: " + ex.getMessage(), ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
        }
    }

    private void showBillDetails(Bill billSummary) {
        try {
            Bill full = billDAO.getBillById(billSummary.getId());
            if (full == null) {
                clearDetails();
                return;
            }

            Buyer buyer = full.getBuyer();
            lblBuyerName.setText(buyer == null ? "" : (buyer.getName() == null ? "" : buyer.getName()));
            lblBuyerPhone.setText(buyer == null ? "" : (buyer.getPhone() == null ? "" : buyer.getPhone()));
            lblSubtotal.setText(String.format("%.2f", full.getSubtotal()));
            lblDiscountAmount.setText(String.format("%.2f", full.getDiscountAmount()));
            lblGrandTotal.setText(String.format("%.2f", full.getGrandTotal()));

            items.clear();
            items.addAll(full.getItems());

        } catch (Exception ex) {
            ex.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load bill details: " + ex.getMessage(), ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
        }
    }

    private void clearDetails() {
        lblBuyerName.setText("");
        lblBuyerPhone.setText("");
        lblSubtotal.setText("");
        lblDiscountAmount.setText("");
        lblGrandTotal.setText("");
        items.clear();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        Integer billId = null;
        String billText = txtBillNumber.getText();
        if (billText != null && !billText.isBlank()) {
            try {
                billId = Integer.parseInt(billText.trim());
            } catch (NumberFormatException ex) {
                Alert a = new Alert(Alert.AlertType.WARNING, "Bill number must be an integer.", ButtonType.OK);
                a.setHeaderText(null);
                a.showAndWait();
                return;
            }
        }

        String buyerName = txtBuyerName.getText();
        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();

        loadBills(billId, buyerName, from, to);
    }

    @FXML
    private void handleReset(ActionEvent event) {
        txtBillNumber.clear();
        txtBuyerName.clear();
        dpFrom.setValue(null);
        dpTo.setValue(null);
        loadBills(null, null, null, null);
    }

    @FXML
    private void handleBack(ActionEvent event) {
        SceneManager.showScene("MainMenu.fxml");
    }
}
