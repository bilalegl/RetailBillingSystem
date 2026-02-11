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

import java.io.*;
import java.text.DecimalFormat;
import java.util.List;

/**
 * PDFGenerator aligned to NativePrinter layout:
 * - same column percentages
 * - same ROW_HEIGHT and baseline offsets (header/rows at top + 15 in Java2D -> top - 15 in PDF)
 * - same vertical/horizontal lines
 */
public class PDFGenerator {

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 36f;
    private static final float FOOTER_HEIGHT = 40f;
    private static final float ROW_HEIGHT = 22f;
    private static final float DESC_LINE_H = 12f;

    // Column percentages (copy from NativePrinter)
    private static final float P_SNO = 0.08f;
    private static final float P_DESC = 0.45f;
    private static final float P_QTY = 0.10f;
    private static final float P_UNIT = 0.18f;
    // total uses remaining width

    // Fonts (Standard 14)
    private static final PDType1Font FONT_HEADER = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font FONT_REG = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final float HEADER_FONT_SIZE = 18f;
    private static final float TABLE_HEADER_FONT_SIZE = 10f;
    private static final float TABLE_FONT_SIZE = 10f;
    private static final float SMALL_FONT_SIZE = 9f;

    private static final DecimalFormat CURRENCY = new DecimalFormat("#,##0.00");

    private static final String SHOP_DESC = "Deal in All Kind of Electronic\nParts Extension Boards Importer & Stockist";

    /* ================= PUBLIC API ================= */

    public static void generateBillPDF(Bill bill, File outputFile, String shopName) throws IOException {
        byte[] logoBytes = null;
        InputStream is = PDFGenerator.class.getResourceAsStream("/images/logo.png");
        if (is != null) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                is.transferTo(baos);
                logoBytes = baos.toByteArray();
            } catch (Exception e) {
                System.err.println("Logo not loaded: " + e.getMessage());
            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
            }
        }
        generateBillPDF(bill, outputFile, shopName, logoBytes);
    }

    public static void generateBillPDF(Bill bill, File outputFile, String shopName, byte[] logoBytes) throws IOException {
        validateInput(bill, outputFile);
        List<BillItem> items = bill.getItems();

        try (PDDocument document = new PDDocument()) {

            float pageWidth = PAGE_SIZE.getWidth();
            float tableWidth = pageWidth - 2 * MARGIN;

            // Column widths (same math as NativePrinter)
            float wSno = tableWidth * P_SNO;
            float wDesc = tableWidth * P_DESC;
            float wQty = tableWidth * P_QTY;
            float wUnit = tableWidth * P_UNIT;
            float wTotal = tableWidth - (wSno + wDesc + wQty + wUnit);

            // Column X coordinates
            float xSno = MARGIN;
            float xDesc = xSno + wSno;
            float xQty = xDesc + wDesc;
            float xUnit = xQty + wQty;
            float xTotal = xUnit + wUnit;
            float xEnd = MARGIN + tableWidth;

            PDImageXObject logoImg = null;
            if (logoBytes != null) {
                logoImg = PDImageXObject.createFromByteArray(document, logoBytes, "logo");
            }

            int rowIndex = 0;
            int pageIndex = 0;

            while (rowIndex < items.size()) {
                PDPage page = new PDPage(PAGE_SIZE);
                document.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    float yStart = PAGE_SIZE.getHeight() - MARGIN;
                    float y = yStart;

                    // ===== HEADER (first page) =====
                    if (pageIndex == 0) {
                        if (logoImg != null) {
                            cs.drawImage(logoImg, pageWidth - MARGIN - 80f, y - 50f, 80f, 50f);
                        }
                        drawText(cs, shopName != null ? shopName : "Fakhar Enterprises", MARGIN, y - 20f, FONT_HEADER, HEADER_FONT_SIZE);

                        float descY = y - 38f;
                        for (String line : SHOP_DESC.split("\n")) {
                            drawText(cs, line, MARGIN, descY, FONT_REG, SMALL_FONT_SIZE);
                            descY -= DESC_LINE_H;
                        }

                        // divider
                        cs.setStrokingColor(0f, 0f, 0f);
                        cs.setLineWidth(1.2f);
                        cs.moveTo(MARGIN, descY - 15f);
                        cs.lineTo(pageWidth - MARGIN, descY - 15f);
                        cs.stroke();
                        cs.setLineWidth(1f);

                        y = descY - 25f;

                        // right-aligned bill details (match NativePrinter placement)
                        drawTextRight(cs, "Bill #: " + bill.getId(), pageWidth - MARGIN, y + 30f, FONT_REG, TABLE_FONT_SIZE);
                        drawTextRight(cs, "Date: " + (bill.getBillDate() != null ? bill.getBillDate() : "N/A"), pageWidth - MARGIN, y + 16f, FONT_REG, TABLE_FONT_SIZE);

                        // buyer info (match NativePrinter)
                        Buyer b = bill.getBuyer();
                        if (b != null) {
                            drawText(cs, "Buyer:", MARGIN, y, FONT_BOLD, TABLE_FONT_SIZE);
                            drawText(cs, b.getName() != null ? b.getName() : "Walk-in Customer", MARGIN + 50f, y, FONT_REG, TABLE_FONT_SIZE);
                            if (b.getPhone() != null && !b.getPhone().isEmpty()) {
                                drawText(cs, "Phone:", MARGIN + 360f, y, FONT_BOLD, TABLE_FONT_SIZE);
                                drawText(cs, b.getPhone(), MARGIN + 405f, y, FONT_REG, TABLE_FONT_SIZE);
                            }
                            y -= 25f;
                        } else {
                            y -= 10f;
                        }
                    } else {
                        // small gap for subsequent pages
                        y -= 20f;
                    }

                    // ===== TABLE HEADER & PAGINATION =====
                    float availableHeight = y - MARGIN - FOOTER_HEIGHT;
                    int rowsPerPage = Math.max(1, (int) (availableHeight / ROW_HEIGHT));
                    int endRow = Math.min(rowIndex + rowsPerPage, items.size());

                    float tableTopY = y;

                    // header background rectangle (same as fillRect in Java2D)
                    cs.setNonStrokingColor(0.9f, 0.9f, 0.9f);
                    cs.addRect(MARGIN, tableTopY - ROW_HEIGHT, tableWidth, ROW_HEIGHT);
                    cs.fill();
                    cs.setNonStrokingColor(0f, 0f, 0f);

                    // ===== IMPORTANT: use the same baseline offset as NativePrinter =====
                    // NativePrinter used `y + 15` in Java2D for header/rows; in PDF coordinates that's `topY - 15`.
                    float headerBaseline = tableTopY - 15f;
                    drawTextCentered(cs, "S.No", xSno, wSno, headerBaseline, FONT_BOLD, TABLE_HEADER_FONT_SIZE);
                    drawText(cs, "Product Description", xDesc + 5f, headerBaseline, FONT_BOLD, TABLE_HEADER_FONT_SIZE);
                    drawTextCentered(cs, "Qty", xQty, wQty, headerBaseline, FONT_BOLD, TABLE_HEADER_FONT_SIZE);
                    drawTextRight(cs, "Unit Price", xUnit + wUnit - 5f, headerBaseline, FONT_BOLD, TABLE_HEADER_FONT_SIZE);
                    drawTextRight(cs, "Total", xEnd - 5f, headerBaseline, FONT_BOLD, TABLE_HEADER_FONT_SIZE);

                    // ===== ROWS using same top+15 baseline approach =====
                    float currentRowTop = tableTopY - ROW_HEIGHT; // top Y of first data row
                    int drawnRows = 0;

                    cs.setFont(FONT_REG, TABLE_FONT_SIZE);

                    for (int i = rowIndex; i < endRow; i++) {
                        BillItem it = items.get(i);

                        // baseline matches Java2D's `rowTop + 15` -> PDF: rowTop - 15
                        float baseline = currentRowTop - 15f;

                        // S.No (center)
                        drawTextCentered(cs, String.valueOf(i + 1), xSno, wSno, baseline, FONT_REG, TABLE_FONT_SIZE);
                        // Product (left)
                        drawText(cs, trunc(it.getProductName(), 35), xDesc + 5f, baseline, FONT_REG, TABLE_FONT_SIZE);
                        // Qty (center)
                        drawTextCentered(cs, fmt(it.getQuantity()), xQty, wQty, baseline, FONT_REG, TABLE_FONT_SIZE);
                        // Unit price (right)
                        drawTextRight(cs, CURRENCY.format(it.getUnitPrice()), xUnit + wUnit - 5f, baseline, FONT_REG, TABLE_FONT_SIZE);
                        // Item total (right)
                        drawTextRight(cs, CURRENCY.format(it.getItemTotal()), xEnd - 5f, baseline, FONT_REG, TABLE_FONT_SIZE);

                        // light horizontal separator line at bottom of this row (mimics Java2D drawLine at y+ROW_H)
                        cs.setStrokingColor(0.85f, 0.85f, 0.85f);
                        cs.moveTo(MARGIN, currentRowTop - ROW_HEIGHT);
                        cs.lineTo(MARGIN + tableWidth, currentRowTop - ROW_HEIGHT);
                        cs.stroke();
                        cs.setStrokingColor(0f, 0f, 0f);

                        // advance to next row top (downwards)
                        currentRowTop -= ROW_HEIGHT;
                        drawnRows++;
                    }

                    // bottom Y of table (below last drawn row) — matches Java2D y after rows
                    float tableBottomY = tableTopY - ROW_HEIGHT - (drawnRows * ROW_HEIGHT);

                    // ===== VERTICAL LINES from header top to bottom of last drawn row =====
                    float[] xCols = {xSno, xDesc, xQty, xUnit, xTotal, xEnd};
                    cs.setStrokingColor(0.7f, 0.7f, 0.7f);
                    for (float x : xCols) {
                        cs.moveTo(x, tableTopY);
                        cs.lineTo(x, tableBottomY);
                    }
                    cs.stroke();
                    cs.setStrokingColor(0f, 0f, 0f);

                    // ===== GRAND TOTAL on last page (same placement logic as NativePrinter) =====
                    if (endRow == items.size()) {
                        float yAfter = tableBottomY - 30f;
                        drawText(cs, "Grand Total:", xUnit + 10f, yAfter + 12f, FONT_BOLD, 10f);
                        drawTextRight(cs, "PKR " + CURRENCY.format(bill.getGrandTotal()), xEnd - 5f, yAfter + 12f, FONT_BOLD, 12f);

                        // optional double-line under total (visual parity)
                        cs.moveTo(xUnit, yAfter + 8f);
                        cs.lineTo(xEnd, yAfter + 8f);
                        cs.moveTo(xUnit, yAfter + 6f);
                        cs.lineTo(xEnd, yAfter + 6f);
                        cs.stroke();
                    }

                    // Footer (centered)
                    drawTextCentered(cs,
                            "Thank you for shopping at " + (shopName != null ? shopName : "Fakhar Enterprises") + "!",
                            MARGIN, tableWidth, MARGIN + FOOTER_HEIGHT / 4f, FONT_REG, SMALL_FONT_SIZE);

                    // advance
                    rowIndex = endRow;
                    pageIndex++;
                }
            }

            document.save(outputFile);
        }
    }

    /* ================= Helpers ================= */

    private static void validateInput(Bill bill, File outputFile) {
        if (bill == null) throw new IllegalArgumentException("Bill cannot be null");
        if (outputFile == null) throw new IllegalArgumentException("Output file cannot be null");
        if (bill.getItems() == null) throw new IllegalArgumentException("Bill items cannot be null");
    }

    private static void drawText(PDPageContentStream cs, String text, float x, float y, PDType1Font font, float fontSize) throws IOException {
        if (text == null || text.isEmpty()) return;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private static void drawTextRight(PDPageContentStream cs, String text, float rightX, float y, PDType1Font font, float fontSize) throws IOException {
        if (text == null) return;
        float textWidth = (font.getStringWidth(text) / 1000f) * fontSize;
        drawText(cs, text, rightX - textWidth, y, font, fontSize);
    }

    private static void drawTextCentered(PDPageContentStream cs, String text, float x, float width, float y, PDType1Font font, float fontSize) throws IOException {
        if (text == null) return;
        float textWidth = (font.getStringWidth(text) / 1000f) * fontSize;
        float cx = x + (width - textWidth) / 2f;
        drawText(cs, text, cx, y, font, fontSize);
    }

    private static String fmt(double d) {
        return (d == (int) d) ? String.valueOf((int) d) : String.valueOf(d);
    }

    private static String trunc(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 3) + "...";
    }
}
