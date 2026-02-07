package controller;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DoubleStringConverter;
import model.BillItem;
import util.SceneManager;

import java.text.DecimalFormat;
import java.util.Locale;

public class CreateBillController {

    @FXML private TextField txtBuyerName;
    @FXML private TextField txtBuyerPhone;
    @FXML private Button btnAddRow;
    @FXML private Button btnRemoveRow;
    @FXML private Button btnBack;

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

    // Decimal formatting (UI only)
    private final DecimalFormat moneyFormat = (DecimalFormat) DecimalFormat.getCurrencyInstance(Locale.getDefault());

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

        // Keep totals updated when list changes or item properties change
        items.addListener((ListChangeListener<BillItem>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (BillItem added : change.getAddedSubList()) {
                        attachItemListeners(added);
                    }
                }
                // no special detach needed (garbage collection)
            }
            recalcTotals();
        });

        // Discount percent change listener
        txtDiscountPercent.textProperty().addListener((obs, oldV, newV) -> {
            recalcTotals();
        });

        // Add an initial empty row so the user sees something
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
        // If user gives >100, clamp
        if (discountPercent > 100.0) discountPercent = 100.0;

        double discountAmount = subtotal * (discountPercent / 100.0);
        double grandTotal = subtotal - discountAmount;

        // Update UI (format numbers)
        lblSubtotal.setText(formatNumber(subtotal));
        lblDiscountAmount.setText(formatNumber(discountAmount));
        lblGrandTotal.setText(formatNumber(grandTotal));
    }

    private String formatNumber(double value) {
        // Use simple 2-decimal formatting without currency symbol for clarity
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
        // select the newly added row and scroll to it for UX
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
            // optional: show a small alert
            Alert a = new Alert(Alert.AlertType.INFORMATION, "No row selected to remove.", ButtonType.OK);
            a.setHeaderText(null);
            a.showAndWait();
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        // Strict rule: no persistence here
        SceneManager.showScene("MainMenu.fxml");
    }
}
