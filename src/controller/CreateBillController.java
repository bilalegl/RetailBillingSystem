package controller;

import dao.BillDAO;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DoubleStringConverter;
import model.Bill;
import model.BillItem;
import model.Buyer;
import util.SceneManager;

import java.sql.SQLException;
import java.util.Locale;

public class CreateBillController {

    @FXML private TextField txtBuyerName;
    @FXML private TextField txtBuyerPhone;
    @FXML private Button btnAddRow;
    @FXML private Button btnRemoveRow;
    @FXML private Button btnBack;
    @FXML private Button btnSave;

    @FXML private TableView<BillItem> tableItems;
    @FXML private TableColumn<BillItem, String> colProduct;
    @FXML private TableColumn<BillItem, Double> colQuantity;
    @FXML private TableColumn<BillItem, Double> colUnitPrice;
    @FXML private TableColumn<BillItem, Double> colItemTotal;

    @FXML private Label lblSubtotal;
    @FXML private TextField txtDiscountPercent;
    @FXML private Label lblDiscountAmount;
    @FXML private Label lblGrandTotal;

    private final ObservableList<BillItem> items = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // Table setup
        tableItems.setItems(items);
        tableItems.setEditable(true);

        // Product column - editable text
        colProduct.setCellValueFactory(cell -> cell.getValue().productNameProperty());
        colProduct.setCellFactory(TextFieldTableCell.forTableColumn());
        colProduct.setOnEditCommit(ev -> {
            BillItem item = ev.getRowValue();
            item.setProductName(ev.getNewValue() == null ? "" : ev.getNewValue());
        });

        // Quantity column - editable double
        colQuantity.setCellValueFactory(cell -> cell.getValue().quantityProperty().asObject());
        colQuantity.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        colQuantity.setOnEditCommit(ev -> {
            BillItem item = ev.getRowValue();
            Double newValue = parseDoubleSafe(ev.getNewValue(), 1.0);
            if (newValue < 0) newValue = 0.0;
            item.setQuantity(newValue);
            tableItems.refresh();
            recalcTotals();
        });

        // Unit price column - editable double
        colUnitPrice.setCellValueFactory(cell -> cell.getValue().unitPriceProperty().asObject());
        colUnitPrice.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        colUnitPrice.setOnEditCommit(ev -> {
            BillItem item = ev.getRowValue();
            Double newValue = parseDoubleSafe(ev.getNewValue(), 0.0);
            if (newValue < 0) newValue = 0.0;
            item.setUnitPrice(newValue);
            tableItems.refresh();
            recalcTotals();
        });

        // Item total column - read-only
        colItemTotal.setCellValueFactory(cell -> cell.getValue().itemTotalProperty().asObject());
        colItemTotal.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(formatNumber(value));
                }
            }
        });

        items.addListener((ListChangeListener<BillItem>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (BillItem added : change.getAddedSubList()) {
                        attachItemListeners(added);
                    }
                }
            }
            recalcTotals();
        });

        txtDiscountPercent.textProperty().addListener((obs, oldV, newV) -> recalcTotals());

        // Add initial row
        items.add(new BillItem("", 1.0, 0.0));
    }

    private void attachItemListeners(BillItem item) {
        ChangeListener<Number> anyChange = (obs, oldVal, newVal) -> recalcTotals();
        item.quantityProperty().addListener(anyChange);
        item.unitPriceProperty().addListener(anyChange);
        item.itemTotalProperty().addListener((obs, oldVal, newVal) -> recalcTotals());
    }

    private void recalcTotals() {
        double subtotal = items.stream().mapToDouble(BillItem::getItemTotal).sum();
        double discountPercent = parseDoubleSafe(txtDiscountPercent.getText(), 0.0);
        if (Double.isNaN(discountPercent) || discountPercent < 0) discountPercent = 0.0;
        if (discountPercent > 100.0) discountPercent = 100.0;

        double discountAmount = subtotal * (discountPercent / 100.0);
        double grandTotal = subtotal - discountAmount;

        lblSubtotal.setText(formatNumber(subtotal));
        lblDiscountAmount.setText(formatNumber(discountAmount));
        lblGrandTotal.setText(formatNumber(grandTotal));
    }

    private String formatNumber(double value) {
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private double parseDoubleSafe(Object possibleNumber, double fallback) {
        if (possibleNumber == null) return fallback;
        try {
            if (possibleNumber instanceof Number) {
                return ((Number) possibleNumber).doubleValue();
            }
            String s = possibleNumber.toString().trim();
            if (s.isEmpty()) return fallback;
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    // --- Handlers ---

    @FXML
    private void handleAddRow(ActionEvent event) {
        items.add(new BillItem("", 1.0, 0.0));
        int lastIndex = items.size() - 1;
        tableItems.getSelectionModel().select(lastIndex);
        tableItems.scrollTo(lastIndex);
    }

    @FXML
    private void handleRemoveSelected(ActionEvent event) {
        BillItem selected = tableItems.getSelectionModel().getSelectedItem();
        if (selected != null) {
            items.remove(selected);
        } else {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "No row selected to remove.", ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        SceneManager.showScene("MainMenu.fxml");
    }

    /**
     * SAVE -> create Bill model, call DAO to persist within a transaction,
     * then lock UI on success.
     */
    @FXML
    private void handleSave(ActionEvent event) {
        // Basic validation: must have at least one item and non-empty totals
        if (items.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Add at least one item before saving.", ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }

        Bill bill = new Bill();
        bill.setSubtotal(parseDoubleSafe(lblSubtotal.getText(), 0.0));
        double discountPercent = parseDoubleSafe(txtDiscountPercent.getText(), 0.0);
        bill.setDiscountPercent(discountPercent);
        bill.setDiscountAmount(parseDoubleSafe(lblDiscountAmount.getText(), 0.0));
        bill.setGrandTotal(parseDoubleSafe(lblGrandTotal.getText(), 0.0));

        String buyerName = txtBuyerName.getText();
        String buyerPhone = txtBuyerPhone.getText();
        if ((buyerName != null && !buyerName.isBlank()) || (buyerPhone != null && !buyerPhone.isBlank())) {
            Buyer buyer = new Buyer(buyerName, buyerPhone);
            bill.setBuyer(buyer);
        }

        // copy items into bill model
        for (BillItem item : items) {
            bill.addItem(item);
        }

        // Call DAO
        BillDAO billDAO = new BillDAO();
        try {
            int generatedBillId = billDAO.saveBill(bill);
            // success
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Bill saved successfully. Bill ID: " + generatedBillId, ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
            lockUIAfterSave();
        } catch (SQLException ex) {
            ex.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR, "Failed to save bill: " + ex.getMessage(), ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
        }
    }

    /**
     * Lock UI after successful save: no editing allowed (immutable record).
     */
    private void lockUIAfterSave() {
        // disable editing
        tableItems.setEditable(false);
        colProduct.setEditable(false);
        colQuantity.setEditable(false);
        colUnitPrice.setEditable(false);

        // disable buttons & inputs
        btnAddRow.setDisable(true);
        btnRemoveRow.setDisable(true);
        btnSave.setDisable(true);
        txtBuyerName.setEditable(false);
        txtBuyerPhone.setEditable(false);
        txtDiscountPercent.setEditable(false);
    }
}
