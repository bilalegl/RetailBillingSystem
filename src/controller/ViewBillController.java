package controller;

import dao.BillDAO;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.transform.Scale;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.stage.Window;
import model.Bill;
import model.BillItem;
import model.Buyer;
import util.SceneManager;

import java.util.List;

public class ViewBillController {

    @FXML private Label lblBillId;
    @FXML private Label lblBillDate;
    @FXML private Label lblBuyerName;
    @FXML private Label lblBuyerPhone;

    @FXML private TableView<BillItem> tblItems;
    @FXML private TableColumn<BillItem, String> colItemProduct;
    @FXML private TableColumn<BillItem, Double> colItemQty;
    @FXML private TableColumn<BillItem, Double> colItemPrice;
    @FXML private TableColumn<BillItem, Double> colItemTotal;

    @FXML private Label lblSubtotal;
    @FXML private Label lblDiscountAmount;
    @FXML private Label lblGrandTotal;
    @FXML private Button btnPrint; 

    private final BillDAO billDAO = new BillDAO();

    private int currentBillId = -1;

    @FXML
    private void initialize() {
        // setup read-only table columns
        colItemProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colItemQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colItemPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colItemTotal.setCellValueFactory(cell -> cell.getValue().itemTotalProperty().asObject());

        tblItems.setEditable(false);
    }

    /**
     * Public loader called by SceneManager after FXML loads.
     */
    public void loadBill(int billId) {
        this.currentBillId = billId;
        try {
            Bill bill = billDAO.getBillById(billId);
            if (bill == null) {
                showAlert("Bill not found", "The requested bill was not found in the database.");
                return;
            }

            // populate header
            lblBillId.setText(String.valueOf(bill.getId()));
            lblBillDate.setText(bill.getBillDate() == null ? "" : bill.getBillDate());

            Buyer buyer = bill.getBuyer();
            lblBuyerName.setText(buyer == null ? "" : (buyer.getName() == null ? "" : buyer.getName()));
            lblBuyerPhone.setText(buyer == null ? "" : (buyer.getPhone() == null ? "" : buyer.getPhone()));

            // items
            List<BillItem> items = bill.getItems();
            tblItems.getItems().clear();
            tblItems.getItems().addAll(items);

            // totals (snapshot - do not recalc)
            lblSubtotal.setText(String.format("%.2f", bill.getSubtotal()));
            lblDiscountAmount.setText(String.format("%.2f", bill.getDiscountAmount()));
            lblGrandTotal.setText(String.format("%.2f", bill.getGrandTotal()));

            // ensure all controls disabled/readonly
            disableAllInputs();

        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Error", "Failed to load bill: " + ex.getMessage());
        }
    }

    private void disableAllInputs() {
        // Labels are not editable. Ensure table selection is disabled to prevent accidental change.
        tblItems.setMouseTransparent(false); // allow selection for copy if desired
        tblItems.setFocusTraversable(false);
    }

    @FXML
private void handlePrint() {

    Printer printer = Printer.getDefaultPrinter();
    if (printer == null) {
        System.out.println("No printer found");
        return;
    }

    // A4 landscape page
    PageLayout pageLayout = printer.createPageLayout(
            Paper.A4,
            PageOrientation.LANDSCAPE,
            Printer.MarginType.DEFAULT
    );

    PrinterJob job = PrinterJob.createPrinterJob(printer);
    if (job == null) {
        System.out.println("Could not create printer job");
        return;
    }

    job.getJobSettings().setPageLayout(pageLayout);

    // ROOT NODE of your scene
    Node root = btnPrint.getScene().getRoot();

    // Calculate scale
    double scaleX = pageLayout.getPrintableWidth() / root.getBoundsInParent().getWidth();
    double scaleY = pageLayout.getPrintableHeight() / root.getBoundsInParent().getHeight();
    double scale = Math.min(scaleX, scaleY);

    Scale scaling = new Scale(scale, scale);
    root.getTransforms().add(scaling);

    boolean success = job.printPage(root);

    if (success) {
        job.endJob();
    }

    // IMPORTANT: remove scale after printing
    root.getTransforms().remove(scaling);
}


    @FXML
    private void handleExportPdf() {
        // We use the system print dialog and instruct user to choose a PDF printer (e.g., "Microsoft Print to PDF").
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            showAlert("Export error", "No printers found on this system.");
            return;
        }
        Window w = lblBillId.getScene().getWindow();
        boolean proceed = job.showPrintDialog(w);
        if (!proceed) return;

        Node printable = lblBillId.getScene().getRoot();
        boolean printed = job.printPage(printable);
        if (printed) {
            job.endJob();
            showAlert("Export", "Sent to the selected printer. If you chose a PDF printer, a PDF file was generated.");
        } else {
            showAlert("Export failed", "Exporting to PDF failed.");
        }
    }

    @FXML
    private void handleClose() {
        SceneManager.showScene("CheckRecords.fxml");
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle(title);
        a.showAndWait();
    }
}
