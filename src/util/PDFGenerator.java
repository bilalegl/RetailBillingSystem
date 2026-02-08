package util;

import model.Bill;
import model.BillItem;
import model.Buyer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Simple PDF generator using PDFBox.
 * - Produces a multi-page A4 PDF if needed
 * - Uses snapshot data from Bill and BillItem (no recalculation)
 */
public class PDFGenerator {

    private static final float MARGIN = 40f;
    private static final float Y_START = PDRectangle.A4.getHeight() - MARGIN;
    private static final float TABLE_WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
    private static final float ROW_HEIGHT = 20f;
    private static final float HEADER_HEIGHT = 60f;
    private static final DecimalFormat moneyFmt = new DecimalFormat("#0.00");

    // Cached fonts (PDFBox 3: create PDType1Font with Standard14Fonts)
    private static final PDType1Font HELVETICA =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font HELVETICA_BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /**
     * Generate a PDF for the provided bill snapshot.
     *
     * @param bill Bill snapshot (must contain items and totals)
     * @param outputFile destination file (will be overwritten)
     * @param logoFile optional logo image file (png/jpg) - pass null if none
     * @param shopName Shop name string to render in header
     * @throws IOException on IO/PDF errors
     */
    public static void generateBillPDF(Bill bill, File outputFile, File logoFile, String shopName) throws IOException {
        if (bill == null) throw new IllegalArgumentException("bill is null");
        try (PDDocument doc = new PDDocument()) {

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = Y_START;

            // Header: optional logo and shop name + bill meta
            if (logoFile != null && logoFile.exists()) {
                try {
                    PDImageXObject pdImage = PDImageXObject.createFromFileByContent(logoFile, doc);
                    float logoHeight = 48f;
                    float logoWidth = pdImage.getWidth() * (logoHeight / pdImage.getHeight());
                    cs.drawImage(pdImage, MARGIN, y - logoHeight, logoWidth, logoHeight);
                } catch (Exception e) {
                    // ignore logo failures but continue
                    e.printStackTrace();
                }
            }

            // Shop name (right of logo)
            cs.beginText();
            cs.setFont(HELVETICA_BOLD, 16);
            cs.newLineAtOffset(MARGIN + 220, y - 18); // place near top-right area
            cs.showText(shopName == null ? "" : shopName);
            cs.endText();

            // Bill meta: ID and Date (right side)
            cs.beginText();
            cs.setFont(HELVETICA, 10);
            cs.newLineAtOffset(MARGIN + 220, y - 36);
            cs.showText("Bill ID: " + bill.getId());
            cs.endText();

            cs.beginText();
            cs.setFont(HELVETICA, 10);
            cs.newLineAtOffset(MARGIN + 220, y - 52);
            cs.showText("Date: " + (bill.getBillDate() == null ? "" : bill.getBillDate()));
            cs.endText();

            y -= HEADER_HEIGHT;

            // Buyer block
            Buyer buyer = bill.getBuyer();
            cs.beginText();
            cs.setFont(HELVETICA_BOLD, 11);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("Buyer:");
            cs.endText();

            cs.beginText();
            cs.setFont(HELVETICA, 10);
            cs.newLineAtOffset(MARGIN + 50, y);
            String buyerName = buyer == null ? "" : (buyer.getName() == null ? "" : buyer.getName());
            cs.showText(buyerName);
            cs.endText();

            cs.beginText();
            cs.setFont(HELVETICA, 10);
            cs.newLineAtOffset(MARGIN + 220, y);
            String buyerPhone = buyer == null ? "" : (buyer.getPhone() == null ? "" : buyer.getPhone());
            cs.showText("Phone: " + buyerPhone);
            cs.endText();

            y -= 25;

            // Table header
            float tableTopY = y;
            float colProductWidth = TABLE_WIDTH * 0.50f;
            float colQtyWidth = TABLE_WIDTH * 0.12f;
            float colUnitWidth = TABLE_WIDTH * 0.19f;
            float colTotalWidth = TABLE_WIDTH * 0.19f;

            // Header background (light) - use Color object
            cs.setNonStrokingColor(new Color(230, 230, 230));
            cs.addRect(MARGIN, y - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
            cs.fill();
            cs.setNonStrokingColor(Color.BLACK);

            // Header text
            cs.beginText();
            cs.setFont(HELVETICA_BOLD, 10);
            cs.newLineAtOffset(MARGIN + 4, y - 15);
            cs.showText("Product");
            // move cursor to Qty column (relative)
            cs.newLineAtOffset(colProductWidth, 0);
            cs.showText("Qty");
            cs.newLineAtOffset(colQtyWidth, 0);
            cs.showText("Unit Price");
            cs.newLineAtOffset(colUnitWidth, 0);
            cs.showText("Total");
            cs.endText();

            y -= ROW_HEIGHT;

            // Items rows
            List<BillItem> items = bill.getItems();
            cs.setFont(HELVETICA, 10);

            for (BillItem item : items) {
                // paginate if needed
                if (y < MARGIN + 100) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = Y_START - 20;
                }

                // draw row text
                cs.beginText();
                cs.newLineAtOffset(MARGIN + 4, y - 15);
                cs.showText(truncate(item.getProductName(), 40));
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(MARGIN + 4 + colProductWidth, y - 15);
                cs.showText(formatNumber(item.getQuantity()));
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(MARGIN + 4 + colProductWidth + colQtyWidth, y - 15);
                cs.showText(formatCurrency(item.getUnitPrice()));
                cs.endText();

                cs.beginText();
                cs.newLineAtOffset(MARGIN + 4 + colProductWidth + colQtyWidth + colUnitWidth, y - 15);
                cs.showText(formatCurrency(item.getItemTotal()));
                cs.endText();

                // draw horizontal line
                cs.moveTo(MARGIN, y - ROW_HEIGHT);
                cs.lineTo(MARGIN + TABLE_WIDTH, y - ROW_HEIGHT);
                cs.stroke();

                y -= ROW_HEIGHT;
            }

            // Totals block (bottom-right)
            if (y < MARGIN + 80) {
                cs.close();
                page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                cs = new PDPageContentStream(doc, page);
                y = Y_START - 20;
            }

            y -= 10;
            float totalsX = MARGIN + TABLE_WIDTH * 0.5f;

            cs.beginText();
            cs.setFont(HELVETICA, 10);
            cs.newLineAtOffset(totalsX, y - 0);
            cs.showText("Subtotal: " + formatCurrency(bill.getSubtotal()));
            cs.endText();

            cs.beginText();
            cs.setFont(HELVETICA, 10);
            cs.newLineAtOffset(totalsX, y - 18);
            cs.showText("Discount: " + formatCurrency(bill.getDiscountAmount()));
            cs.endText();

            cs.beginText();
            cs.setFont(HELVETICA_BOLD, 12);
            cs.newLineAtOffset(totalsX, y - 36);
            cs.showText("Grand Total: " + formatCurrency(bill.getGrandTotal()));
            cs.endText();

            cs.close();

            // Save
            doc.save(outputFile);
        }
    }

    private static String formatNumber(double d) {
        return moneyFmt.format(d);
    }

    private static String formatCurrency(double d) {
        return moneyFmt.format(d);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }
}
