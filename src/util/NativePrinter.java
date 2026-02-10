package util;

import model.Bill;
import model.BillItem;
import model.Buyer;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.*;
import java.awt.print.*;
import java.text.DecimalFormat;
import java.util.List;

public class NativePrinter {

    private static final DecimalFormat CURRENCY = new DecimalFormat("#,##0.00");

    /* ================= PUBLIC API ================= */

    public static PrintService[] listPrintServices() {
        return PrintServiceLookup.lookupPrintServices(null, null);
    }

    public static void printBillToService(
            Bill bill,
            PrintService service,
            String shopName,
            boolean showDialog,
            Image logo
    ) throws PrinterException {

        if (bill == null || bill.getItems() == null) {
            throw new IllegalArgumentException("Bill or items cannot be null");
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        if (service != null) job.setPrintService(service);

        job.setJobName("Bill #" + bill.getId());

        /* ===== FORCE A4 ===== */
        PageFormat pf = job.defaultPage();
        Paper paper = new Paper();
        // A4 size (72 DPI) -> 595 x 842 points
        paper.setSize(595, 842);
        // 15mm margins
        paper.setImageableArea(42, 42, 595 - 84, 842 - 84);

        pf.setPaper(paper);
        pf.setOrientation(PageFormat.PORTRAIT);

        job.setPrintable(new BillPrintable(bill, shopName, logo), pf);

        if (!showDialog || job.printDialog()) {
            job.print();
        }
    }

    /* === BACKWARD COMPATIBILITY === */
    public static void printBillToService(Bill bill, PrintService service)
            throws PrinterException {
        Image logo = null;
        try {
            logo = Toolkit.getDefaultToolkit().createImage(
                NativePrinter.class.getResource("/images/logo.png").openStream().readAllBytes()
            );
        } catch (Exception e) {
            System.err.println("Logo not loaded: " + e.getMessage());
        }
        printBillToService(bill, service, "Light World", true, logo);
    }

    /* ================= PRINTABLE CLASS ================= */

    private static class BillPrintable implements Printable {

        private final Bill bill;
        private final List<BillItem> items;
        private final String shopName;
        private final Image logo;

        // Layout Constants
        private static final int MARGIN = 36;
        private static final int FOOTER_H = 40;
        private static final int ROW_H = 22; // Slightly taller rows for better read formatting

        // Column percentages
        private static final float P_SNO  = 0.08f;
        private static final float P_NAME = 0.45f;
        private static final float P_QTY  = 0.10f;
        private static final float P_UNIT = 0.18f;
        // Total takes the rest

        // Fonts
        private final Font headerFont = new Font("Dialog", Font.BOLD, 22);
        private final Font descFont   = new Font("Dialog", Font.PLAIN, 10);
        private final Font tableHeaderFont = new Font("Dialog", Font.BOLD, 10);
        private final Font boldFont   = new Font("Dialog", Font.BOLD, 10);
        private final Font font       = new Font("Dialog", Font.PLAIN, 10);
        private final Font smallFont  = new Font("Dialog", Font.PLAIN, 9);

        // Description Text
        private static final String SHOP_DESC = "Deal in All Kind of Electronic\nParts Extension Boards Importer & Stockist";

        BillPrintable(Bill bill, String shopName, Image logo) {
            this.bill = bill;
            this.items = bill.getItems();
            this.shopName = shopName != null ? shopName : "Light World";
            this.logo = logo;
        }

        @Override
        public int print(Graphics g0, PageFormat pf, int pageIndex) {

            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width  = (int) pf.getImageableWidth();
            int height = (int) pf.getImageableHeight();
            int startX = (int) pf.getImageableX();
            int startY = (int) pf.getImageableY();

            int y = startY;

            /* ===== HEADER SECTION ===== */
            // 1. Logo (Top Right)
            if (logo != null) {
                int logoW = 80;
                int logoH = 50;
                int logoX = startX + width - MARGIN - logoW;
                g.drawImage(logo, logoX, y, logoW, logoH, null);
            }

            // 2. Shop Name (Top Left)
            g.setColor(Color.BLACK);
            g.setFont(headerFont);
            g.drawString(shopName, startX + MARGIN, y + 20);
            y += 35;

            // 3. Description (Multi-line)
            g.setFont(descFont);
            for (String line : SHOP_DESC.split("\n")) {
                g.drawString(line, startX + MARGIN, y);
                y += 12;
            }
            y += 10; // Extra spacing after header

            // 4. Divider Line
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(startX + MARGIN, y, startX + width - MARGIN, y);
            g.setStroke(new BasicStroke(1.0f));
            y += 20;

            /* ===== BILL DETAILS (Right Aligned under header) ===== */
            g.setFont(font);
            int detailsX = startX + width - MARGIN - 160;
            int detailsY = startY + 35; // Position below logo
            
            // If description pushes down too far, adjust logic, but this layout is usually safe
            g.drawString("Bill #: " + bill.getId(), detailsX, detailsY);
            g.drawString("Date: " + bill.getBillDate(), detailsX, detailsY + 14);


            /* ===== BUYER INFO ===== */
            if (pageIndex == 0) {
                Buyer b = bill.getBuyer();
                if (b != null) {
                    g.setFont(boldFont);
                    g.drawString("Buyer:", startX + MARGIN, y);
                    
                    g.setFont(font);
                    String buyerName = b.getName() != null ? b.getName() : "Walk-in Customer";
                    g.drawString(buyerName, startX + MARGIN + 50, y);

                    // Phone
                    String phone = b.getPhone() != null ? b.getPhone() : "";
                    if (!phone.isEmpty()) {
                        g.setFont(boldFont);
                        g.drawString("Phone:", startX + MARGIN + 280, y);
                        g.setFont(font);
                        g.drawString(phone, startX + MARGIN + 325, y);
                    }
                    y += 25;
                } else {
                    y += 10; // Small gap if no buyer
                }
            }

            /* ===== CALCULATE TABLE COLUMNS ===== */
            int tableW = width - (MARGIN * 2);
            int wSno   = (int) (tableW * P_SNO);
            int wName  = (int) (tableW * P_NAME);
            int wQty   = (int) (tableW * P_QTY);
            int wUnit  = (int) (tableW * P_UNIT);
            int wTotal = tableW - (wSno + wName + wQty + wUnit);

            // X Coordinates for vertical lines
            int xSno   = startX + MARGIN;
            int xName  = xSno + wSno;
            int xQty   = xName + wName;
            int xUnit  = xQty + wQty;
            int xTotal = xUnit + wUnit;
            int xEnd   = xTotal + wTotal;

            /* ===== PAGINATION CALC ===== */
            int availableH = height - (y - startY) - FOOTER_H;
            int rowsPerPage = availableH / ROW_H;

            int startRow = pageIndex * rowsPerPage;
            if (startRow >= items.size()) return NO_SUCH_PAGE;
            int endRow = Math.min(startRow + rowsPerPage, items.size());

            /* ===== DRAW TABLE HEADER ===== */
            int tableTopY = y; // Remember top Y for vertical lines
            
            g.setColor(new Color(230, 230, 230));
            g.fillRect(xSno, y, tableW, ROW_H);
            g.setColor(Color.BLACK);
            g.drawRect(xSno, y, tableW, ROW_H); // Border around header

            g.setFont(tableHeaderFont);
            // Center S.No
            center(g, "S.No", xSno, wSno, y + 15);
            // Left Product
            g.drawString("Product Description", xName + 5, y + 15);
            // Center Qty
            center(g, "Qty", xQty, wQty, y + 15);
            // Right Unit Price
            right(g, "Unit Price", xTotal - 5, y + 15); // xTotal is start of Total column, so -5 is end of Unit
            // Right Total
            right(g, "Total", xEnd - 5, y + 15);

            y += ROW_H;

            /* ===== DRAW TABLE ROWS ===== */
            g.setFont(font);
            
            for (int i = startRow; i < endRow; i++) {
                BillItem it = items.get(i);

                // 1. S.No (Center)
                center(g, String.valueOf(i + 1), xSno, wSno, y + 15);

                // 2. Name (Left)
                g.drawString(trunc(it.getProductName(), 35), xName + 5, y + 15);

                // 3. Qty (Center)
                center(g, fmt(it.getQuantity()), xQty, wQty, y + 15);

                // 4. Unit (Right)
                right(g, CURRENCY.format(it.getUnitPrice()), xTotal - 5, y + 15);

                // 5. Total (Right)
                right(g, CURRENCY.format(it.getItemTotal()), xEnd - 5, y + 15);

                // Horizontal Line (Light)
                g.setColor(new Color(220, 220, 220));
                g.drawLine(xSno, y + ROW_H, xEnd, y + ROW_H);
                g.setColor(Color.BLACK);

                y += ROW_H;
            }

            int tableBottomY = y;

            /* ===== DRAW VERTICAL LINES ===== */
            // We draw lines from tableTopY to tableBottomY
            g.setColor(Color.GRAY);
            g.drawLine(xSno, tableTopY, xSno, tableBottomY);   // Left Border
            g.drawLine(xName, tableTopY, xName, tableBottomY); // After S.No
            g.drawLine(xQty, tableTopY, xQty, tableBottomY);   // After Name
            g.drawLine(xUnit, tableTopY, xUnit, tableBottomY); // After Qty
            g.drawLine(xTotal, tableTopY, xTotal, tableBottomY); // After Unit
            g.drawLine(xEnd, tableTopY, xEnd, tableBottomY);   // Right Border
            
            // Bottom Border of table
            g.drawLine(xSno, tableBottomY, xEnd, tableBottomY);
            
            g.setColor(Color.BLACK);

            /* ===== GRAND TOTAL (Last Page Only) ===== */
            if (endRow == items.size()) {
                y += 20;
                int totalBoxW = 200;
                int totalBoxX = xEnd - totalBoxW;
                
                g.setFont(boldFont);
                g.drawString("Grand Total:", totalBoxX + 10, y);
                
                g.setFont(new Font("Dialog", Font.BOLD, 12));
                right(g, "PKR " + CURRENCY.format(bill.getGrandTotal()), xEnd, y);
                
                // Double line below total
                y += 5;
                g.drawLine(totalBoxX, y, xEnd, y);
                g.drawLine(totalBoxX, y+2, xEnd, y+2);
            }

            /* ===== FOOTER ===== */
            g.setFont(smallFont);
            g.setColor(Color.GRAY);
            String footerText = "Thank you for shopping at " + shopName + "!";
            center(g, footerText, startX, width, startY + height - 15);

            return PAGE_EXISTS;
        }

        /* ===== HELPERS ===== */
        
        // Right align text at x
        private void right(Graphics2D g, String s, int x, int y) {
            if (s == null) return;
            g.drawString(s, x - g.getFontMetrics().stringWidth(s), y);
        }

        // Center align text within a column starting at x with width w
        private void center(Graphics2D g, String s, int x, int w, int y) {
            if (s == null) return;
            int strW = g.getFontMetrics().stringWidth(s);
            g.drawString(s, x + (w - strW) / 2, y);
        }

        private String fmt(double d) {
            return d == (int) d ? String.valueOf((int) d) : String.valueOf(d);
        }

        private String trunc(String s, int n) {
            if (s == null) return "";
            return s.length() <= n ? s : s.substring(0, n - 3) + "...";
        }
    }
}