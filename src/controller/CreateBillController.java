package controller;

import javafx.concurrent.Task;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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

/**
 * CreateBillController with Phase-9 validation & safety improvements:
 *  - prevents empty product names
 *  - prevents negative quantities/prices
 *  - disables Save if no items or invalid rows
 *  - friendly error dialogs
 */
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

    @FXML private Button btnNativePrint;

    private int currentSavedBillId = -1;

    private final ObservableList<BillItem> items = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // Table setup
        tableItems.setItems(items);
        tableItems.setEditable(true);

        // Product column - editable text with validation (no empty names)
        colProduct.setCellValueFactory(cell -> cell.getValue().productNameProperty());
        colProduct.setCellFactory(TextFieldTableCell.forTableColumn());
        colProduct.setOnEditCommit(ev -> {
            BillItem item = ev.getRowValue();
            String newVal = ev.getNewValue() == null ? "" : ev.getNewValue().trim();
            if (newVal.isEmpty()) {
                showAlert("Invalid product name", "Product name cannot be empty. Reverting to previous value.");
                // revert to old value
                item.setProductName(ev.getOldValue() == null ? "" : ev.getOldValue());
                tableItems.refresh();
            } else {
                item.setProductName(newVal);
            }
            updateSaveButtonState();
        });

        // Quantity column - editable double with validation (no negatives)
        colQuantity.setCellValueFactory(cell -> cell.getValue().quantityProperty().asObject());
        colQuantity.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        colQuantity.setOnEditCommit(ev -> {
            BillItem item = ev.getRowValue();
            Double newValue = parseDoubleSafe(ev.getNewValue(), null);
            if (newValue == null) {
                showAlert("Invalid quantity", "Quantity must be a numeric value. Reverting to previous value.");
                // revert
                item.setQuantity(ev.getOldValue() == null ? 1.0 : ev.getOldValue());
            } else if (newValue < 0) {
                showAlert("Invalid quantity", "Quantity cannot be negative. Reverting to previous value.");
                item.setQuantity(ev.getOldValue() == null ? 1.0 : ev.getOldValue());
            } else {
                item.setQuantity(newValue);
            }
            tableItems.refresh();
            recalcTotals();
            updateSaveButtonState();
        });

        // Unit price column - editable double with validation (no negatives)
        colUnitPrice.setCellValueFactory(cell -> cell.getValue().unitPriceProperty().asObject());
        colUnitPrice.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        colUnitPrice.setOnEditCommit(ev -> {
            BillItem item = ev.getRowValue();
            Double newValue = parseDoubleSafe(ev.getNewValue(), null);
            if (newValue == null) {
                showAlert("Invalid unit price", "Unit price must be a numeric value. Reverting to previous value.");
                item.setUnitPrice(ev.getOldValue() == null ? 0.0 : ev.getOldValue());
            } else if (newValue < 0) {
                showAlert("Invalid unit price", "Unit price cannot be negative. Reverting to previous value.");
                item.setUnitPrice(ev.getOldValue() == null ? 0.0 : ev.getOldValue());
            } else {
                item.setUnitPrice(newValue);
            }
            tableItems.refresh();
            recalcTotals();
            updateSaveButtonState();
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

        // Update totals and attach listeners when list changes
        items.addListener((ListChangeListener<BillItem>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (BillItem added : change.getAddedSubList()) {
                        attachItemListeners(added);
                    }
                }
            }
            recalcTotals();
            updateSaveButtonState();
        });

        // discount changes affect totals only
        txtDiscountPercent.textProperty().addListener((obs, oldV, newV) -> recalcTotals());

        // Start with one empty row but Save is disabled until valid
        items.add(new BillItem("", 1.0, 0.0));
        updateSaveButtonState();
    }

    private void attachItemListeners(BillItem item) {
        ChangeListener<Number> anyChange = (obs, oldVal, newVal) -> {
            recalcTotals();
            updateSaveButtonState();
        };
        item.quantityProperty().addListener(anyChange);
        item.unitPriceProperty().addListener(anyChange);
        item.itemTotalProperty().addListener((obs, oldVal, newVal) -> {
            recalcTotals();
            updateSaveButtonState();
        });
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

    private Double parseDoubleSafe(Object possibleNumber, Double fallback) {
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
        updateSaveButtonState();
    }

    @FXML
    private void handleRemoveSelected(ActionEvent event) {
        BillItem selected = tableItems.getSelectionModel().getSelectedItem();
        if (selected != null) {
            items.remove(selected);
            updateSaveButtonState();
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
        // Final validation before saving (should normally be prevented by disabled button)
        List<String> errors = getValidationErrors();
        if (!errors.isEmpty()) {
            String msg = "Please fix the following errors before saving:\n\n" + String.join("\n", errors);
            showAlert("Cannot save - validation failed", msg);
            return;
        }

        if (items.isEmpty()) {
            showAlert("No items", "Add at least one item before saving.");
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
            currentSavedBillId = billDAO.saveBill(bill); // ✅ update controller state
            // success
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Bill saved successfully. Bill ID: " + currentSavedBillId, ButtonType.OK);
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

    // Native print handler (keeps existing behavior but uses currentSavedBillId)
    @FXML
    private void handleNativePrint() {

        if (currentSavedBillId <= 0) {
            showAlert("Not saved", "Save the bill before printing.");
            return;
        }

        // 1. Get available printers
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services == null || services.length == 0) {
            showAlert("No printers", "No printers found on this system.");
            return;
        }

        List<String> printerNames = Arrays.stream(services)
                .map(PrintService::getName)
                .collect(Collectors.toList());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(printerNames.get(0), printerNames);
        dialog.setTitle("Select Printer");
        dialog.setHeaderText("Select printer for bill printing");
        dialog.setContentText("Printer:");

        dialog.showAndWait().ifPresent(selectedPrinter -> {

            PrintService chosenService = Arrays.stream(services)
                    .filter(p -> p.getName().equals(selectedPrinter))
                    .findFirst()
                    .orElse(null);

            if (chosenService == null) {
                showAlert("Printer error", "Selected printer not found.");
                return;
            }

            // 2. Background printing task
            Task<Void> printTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    dao.BillDAO billDAO = new dao.BillDAO();
                    model.Bill bill = billDAO.getBillById(currentSavedBillId);

                    // Actual native printing (BLOCKING but now on background thread)
                    util.NativePrinter.printBillToService(bill, chosenService);
                    return null;
                }
            };

            // 3. UI state handling
            printTask.setOnRunning(e -> {
                btnNativePrint.setDisable(true);
            });

            printTask.setOnSucceeded(e -> {
                btnNativePrint.setDisable(false);
                showAlert("Print", "Bill sent to printer successfully.");
            });

            printTask.setOnFailed(e -> {
                btnNativePrint.setDisable(false);
                Throwable ex = printTask.getException();
                ex.printStackTrace();
                showAlert("Print failed", ex.getMessage());
            });

            // 4. Start background thread
            Thread t = new Thread(printTask);
            t.setDaemon(true);
            t.start();
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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

    // ---------------- Validation helpers ----------------

    /**
     * Returns list of validation error messages (empty if valid).
     */
    private List<String> getValidationErrors() {
        List<String> errors = FXCollections.observableArrayList();

        if (items.isEmpty()) {
            errors.add("No items in the bill.");
            return errors;
        }

        int idx = 1;
        for (BillItem it : items) {
            String pname = it.getProductName() == null ? "" : it.getProductName().trim();
            if (pname.isEmpty()) {
                errors.add("Row " + idx + ": Product name cannot be empty.");
            }
            if (it.getQuantity() < 0) {
                errors.add("Row " + idx + ": Quantity cannot be negative.");
            }
            if (it.getUnitPrice() < 0) {
                errors.add("Row " + idx + ": Unit price cannot be negative.");
            }
            idx++;
        }
        return errors;
    }

    /**
     * Enable/disable Save button based on validation state.
     */
    private void updateSaveButtonState() {
        List<String> errors = getValidationErrors();
        boolean valid = errors.isEmpty();
        btnSave.setDisable(!valid);
    }
}
