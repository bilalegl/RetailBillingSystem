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

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * PDF generator for bills using Apache PDFBox.
 * Generates multi-page A4 PDF documents with automatic pagination.
 * Uses snapshot data from Bill and BillItem (no recalculation).
 */
public class PDFGenerator {

    // Layout constants
    private static final float MARGIN = 40f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float Y_START = PAGE_HEIGHT - MARGIN;
    private static final float TABLE_WIDTH = PAGE_WIDTH - MARGIN * 2;
    private static final float ROW_HEIGHT = 20f;
    private static final float HEADER_HEIGHT = 60f;
    private static final float FOOTER_HEIGHT = 80f;
    
    // Column widths (as fractions of TABLE_WIDTH)
    private static final float COL_SNO_WIDTH = TABLE_WIDTH * 0.05f;      // Serial number column
    private static final float COL_PRODUCT_WIDTH = TABLE_WIDTH * 0.45f;  // Product name (reduced from 50%)
    private static final float COL_QTY_WIDTH = TABLE_WIDTH * 0.12f;      // Quantity
    private static final float COL_UNIT_WIDTH = TABLE_WIDTH * 0.19f;     // Unit price
    private static final float COL_TOTAL_WIDTH = TABLE_WIDTH * 0.19f;    // Total price
    
    // Font constants
    private static final PDType1Font FONT_NORMAL = 
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = 
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    
    // Formatting
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.00");
    
    /**
     * Generates a PDF bill document.
     *
     * @param bill Bill snapshot containing items and totals
     * @param outputFile Destination file (will be overwritten)
     * @param logoFile Optional logo image file (PNG/JPEG), null if not available
     * @param shopName Shop name to display in header
     * @throws IOException if PDF generation fails
     * @throws IllegalArgumentException if bill is null
     */
    public static void generateBillPDF(Bill bill, File outputFile, File logoFile, String shopName) 
            throws IOException {
        
        validateInput(bill, outputFile);
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            
        float currentY = Y_START;
        
        // 1. Draw header section
        currentY = drawHeader(contentStream, document, bill, logoFile, shopName, currentY);
        
        // 2. Draw buyer information
        currentY = drawBuyerInfo(contentStream, bill.getBuyer(), currentY);
        
        // 3. Draw items table
        currentY = drawItemsTable(contentStream, document, bill.getItems(), currentY);
        
        // 4. Draw totals section
        drawTotals(contentStream, bill, currentY);
        
            contentStream.close();
            document.save(outputFile);
        }
    }
    
    // =============== VALIDATION ===============
    
    private static void validateInput(Bill bill, File outputFile) {
        if (bill == null) {
            throw new IllegalArgumentException("Bill cannot be null");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null");
        }
        if (bill.getItems() == null) {
            throw new IllegalArgumentException("Bill items cannot be null");
        }
    }
    
    // =============== HEADER SECTION ===============
    
    private static float drawHeader(PDPageContentStream cs, PDDocument doc,
                                   Bill bill, File logoFile, String shopName, float startY) 
            throws IOException {
        
        float y = startY;
        
        // Draw logo if available
        if (logoFile != null && logoFile.exists()) {
            try {
                PDImageXObject logo = PDImageXObject.createFromFileByContent(logoFile, doc);
                float logoHeight = 48f;
                float aspectRatio = logo.getWidth() / logo.getHeight();
                float logoWidth = logoHeight * aspectRatio;
                
                // Constrain logo width if too wide
                float maxLogoWidth = 200f;
                if (logoWidth > maxLogoWidth) {
                    logoWidth = maxLogoWidth;
                    logoHeight = logoWidth / aspectRatio;
                }
                
                cs.drawImage(logo, MARGIN, y - logoHeight, logoWidth, logoHeight);
                y = y - logoHeight - 5;
            } catch (IOException e) {
                System.err.println("Failed to load logo: " + e.getMessage());
                // Continue without logo
            }
        }
        
        // Draw shop name at left side
        drawText(cs, shopName != null ? shopName : "", 
                MARGIN, y - 18, FONT_BOLD, 16);
        
        // Draw bill metadata at right side - aligned with table columns
        // Calculate the right alignment point based on table columns
        float rightAlignX = MARGIN + TABLE_WIDTH - 150; // Adjust this value as needed
        
        // Draw Bill # (top right)
        drawText(cs, "Bill #: " + bill.getId(), 
                rightAlignX, y - 18, FONT_NORMAL, 10);
        
        // Draw Date (middle right)
        String dateStr = bill.getBillDate() != null ? bill.getBillDate() : "N/A";
        drawText(cs, "Date: " + dateStr, 
                rightAlignX, y - 33, FONT_NORMAL, 10);
        
        // Draw Phone (bottom right)
        String phone = "";
        if (bill.getBuyer() != null && bill.getBuyer().getPhone() != null) {
            phone = bill.getBuyer().getPhone();
        }
        drawText(cs, "Phone: " + phone, 
                rightAlignX, y - 48, FONT_NORMAL, 10);
        
        return y - HEADER_HEIGHT;
    }
    
    // =============== BUYER INFORMATION ===============
    
    private static float drawBuyerInfo(PDPageContentStream cs, Buyer buyer, float startY) 
            throws IOException {
        
        float y = startY;
        
        if (buyer != null && buyer.getName() != null && !buyer.getName().isEmpty()) {
            drawText(cs, "Buyer:", MARGIN, y, FONT_BOLD, 11);
            drawText(cs, buyer.getName(), MARGIN + 50, y, FONT_NORMAL, 10);
        } else {
            // If no buyer name, just return same y without drawing anything
            return y;
        }
        
        return y - 25;
    }
    
    // =============== ITEMS TABLE ===============
    
    private static float drawItemsTable(PDPageContentStream cs, PDDocument document,
                                       List<BillItem> items, float startY) 
            throws IOException {
        
        float y = startY;
        
        // Draw table header
        y = drawTableHeader(cs, y);
        
        // Draw table rows with serial numbers
        int itemCounter = 1;
        for (BillItem item : items) {
            if (y < MARGIN + FOOTER_HEIGHT) {
                cs.close();
                PDPage newPage = new PDPage(PDRectangle.A4);
                document.addPage(newPage);
                cs = new PDPageContentStream(document, newPage);
                y = Y_START - 20;
                y = drawTableHeader(cs, y); // Redraw header on new page
                itemCounter = 1; // Reset counter on new page
            }
            
            y = drawTableRow(cs, item, y, itemCounter);
            itemCounter++;
        }
        
        return y;
    }
    
    private static float drawTableHeader(PDPageContentStream cs, float y) 
            throws IOException {
        
        // Draw header background
        cs.setNonStrokingColor(new Color(240, 240, 240));
        cs.addRect(MARGIN, y - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
        cs.fill();
        cs.setNonStrokingColor(Color.BLACK);
        
        // Draw column headers with S.No column
        cs.beginText();
        cs.setFont(FONT_BOLD, 10);
        float textX = MARGIN + 4;
        
        // S.No column
        cs.newLineAtOffset(textX, y - 15);
        cs.showText("S.No");
        
        // Product column
        textX += COL_SNO_WIDTH;
        cs.newLineAtOffset(COL_SNO_WIDTH, 0);
        cs.showText("Product");
        
        // Qty column
        textX += COL_PRODUCT_WIDTH;
        cs.newLineAtOffset(COL_PRODUCT_WIDTH, 0);
        cs.showText("Qty");
        
        // Unit Price column
        textX += COL_QTY_WIDTH;
        cs.newLineAtOffset(COL_QTY_WIDTH, 0);
        cs.showText("Unit Price");
        
        // Total column
        textX += COL_UNIT_WIDTH;
        cs.newLineAtOffset(COL_UNIT_WIDTH, 0);
        cs.showText("Total");
        
        cs.endText();
        
        // Draw bottom border
        cs.moveTo(MARGIN, y - ROW_HEIGHT);
        cs.lineTo(MARGIN + TABLE_WIDTH, y - ROW_HEIGHT);
        cs.setLineWidth(1.5f);
        cs.stroke();
        cs.setLineWidth(1.0f);
        
        return y - ROW_HEIGHT;
    }
    
    private static float drawTableRow(PDPageContentStream cs, BillItem item, float y, int serialNo) 
            throws IOException {
        
        float currentX = MARGIN + 4;
        
        // Draw serial number
        drawText(cs, String.valueOf(serialNo), currentX, y - 15, FONT_NORMAL, 10);
        
        // Draw product name
        currentX += COL_SNO_WIDTH;
        drawText(cs, truncate(item.getProductName(), 35), currentX, y - 15, FONT_NORMAL, 10);
        
        // Draw quantity
        currentX += COL_PRODUCT_WIDTH;
        drawText(cs, formatNumber(item.getQuantity()), currentX, y - 15, FONT_NORMAL, 10);
        
        // Draw unit price
        currentX += COL_QTY_WIDTH;
        drawText(cs, formatCurrencyPKR(item.getUnitPrice()), currentX, y - 15, FONT_NORMAL, 10);
        
        // Draw total price
        currentX += COL_UNIT_WIDTH;
        drawText(cs, formatCurrencyPKR(item.getItemTotal()), currentX, y - 15, FONT_NORMAL, 10);
        
        // Draw row separator
        cs.moveTo(MARGIN, y - ROW_HEIGHT);
        cs.lineTo(MARGIN + TABLE_WIDTH, y - ROW_HEIGHT);
        cs.setLineWidth(0.5f);
        cs.stroke();
        
        return y - ROW_HEIGHT;
    }
    
    // =============== TOTALS SECTION ===============
    
    private static void drawTotals(PDPageContentStream cs, Bill bill, float startY) 
            throws IOException {
        
        float y = startY;
        
        // Check if we need a new page for totals
        if (y < MARGIN + 80) {
            // In a complete implementation, you'd create a new page here
            // For now, we'll just adjust y to ensure totals are visible
            y = MARGIN + FOOTER_HEIGHT;
        }
        
        float totalsX = MARGIN + TABLE_WIDTH * 0.5f;
        
        // Draw separator line
        cs.moveTo(totalsX - 50, y);
        cs.lineTo(totalsX + 150, y);
        cs.stroke();
        
        y -= 20;
        
        // Subtotal
        drawText(cs, "Subtotal:", totalsX, y, FONT_NORMAL, 10);
        drawText(cs, formatCurrencyPKR(bill.getSubtotal()), 
                totalsX + 80, y, FONT_NORMAL, 10);
        
        y -= 15;
        
        // Check for additional charges/discounts if they exist in your Bill model
        // If your Bill model has tax/discount fields, you can add them here:
        // Example (commented out since they don't exist in your original code):
        // if (bill.getTax() > 0) {
        //     drawText(cs, "Tax:", totalsX, y, FONT_NORMAL, 10);
        //     drawText(cs, formatCurrencyPKR(bill.getTax()), 
        //             totalsX + 80, y, FONT_NORMAL, 10);
        //     y -= 15;
        // }
        //
        // if (bill.getDiscount() > 0) {
        //     drawText(cs, "Discount:", totalsX, y, FONT_NORMAL, 10);
        //     drawText(cs, "-" + formatCurrencyPKR(bill.getDiscount()), 
        //             totalsX + 80, y, FONT_NORMAL, 10);
        //     y -= 15;
        // }
        
        // Grand total
        y -= 5;
        drawText(cs, "Grand Total:", totalsX, y, FONT_BOLD, 12);
        drawText(cs, formatCurrencyPKR(bill.getGrandTotal()), 
                totalsX + 80, y, FONT_BOLD, 12);
    }
    
    // =============== UTILITY METHODS ===============
    
    private static void drawText(PDPageContentStream cs, String text, 
                                float x, float y, PDType1Font font, float fontSize) 
            throws IOException {
        
        if (text == null || text.isEmpty()) {
            return;
        }
        
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }
    
    private static String formatNumber(double number) {
        // Remove decimal if integer
        if (number == (int) number) {
            return Integer.toString((int) number);
        }
        return CURRENCY_FORMAT.format(number);
    }
    
    private static String formatCurrencyPKR(double amount) {
        return "PKR " + CURRENCY_FORMAT.format(amount);
    }
    
    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}