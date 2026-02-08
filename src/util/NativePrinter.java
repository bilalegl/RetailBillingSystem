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
            boolean showDialog
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

        // A4 size (72 DPI)
        paper.setSize(595, 842);

        // 15mm margins
        paper.setImageableArea(42, 42, 595 - 84, 842 - 84);

        pf.setPaper(paper);
        pf.setOrientation(PageFormat.PORTRAIT);

        job.setPrintable(new BillPrintable(bill, shopName), pf);

        if (!showDialog || job.printDialog()) {
            job.print();
        }
    }

    /* === BACKWARD COMPATIBILITY (FIXES YOUR ERROR) === */
    public static void printBillToService(Bill bill, PrintService service)
            throws PrinterException {
        printBillToService(bill, service, "Light World", true);
    }

    /* ================= PRINTABLE ================= */

    private static class BillPrintable implements Printable {

        private final Bill bill;
        private final List<BillItem> items;
        private final String shopName;

        private static final int MARGIN = 36;
        private static final int HEADER_H = 110;
        private static final int FOOTER_H = 40;
        private static final int ROW_H = 20;

        // Column percentages
        private static final float P_SNO  = 0.06f;
        private static final float P_NAME = 0.44f;
        private static final float P_QTY  = 0.12f;
        private static final float P_UNIT = 0.19f;

        private final Font headerFont = new Font("Arial", Font.BOLD, 18);
        private final Font boldFont   = new Font("Arial", Font.BOLD, 10);
        private final Font font       = new Font("Arial", Font.PLAIN, 10);
        private final Font smallFont  = new Font("Arial", Font.PLAIN, 9);

        BillPrintable(Bill bill, String shopName) {
            this.bill = bill;
            this.items = bill.getItems();
            this.shopName = shopName != null ? shopName : "Light House";
        }

        
        @Override
public int print(Graphics g0, PageFormat pf, int pageIndex) {

    Graphics2D g = (Graphics2D) g0;
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    int width  = (int) pf.getImageableWidth();
    int height = (int) pf.getImageableHeight();
    int startX = (int) pf.getImageableX();
    int startY = (int) pf.getImageableY();

    int y = startY;

    /* ===== HEADER ===== */
    g.setFont(headerFont);
    g.drawString(shopName, startX + MARGIN, y + 30);

    g.setFont(font);
    int rightX = startX + width - MARGIN - 160;
    g.drawString("Bill #: " + bill.getId(), rightX, y + 20);
    g.drawString("Date: " + bill.getBillDate(), rightX, y + 36);

    y += HEADER_H;
    g.drawLine(startX + MARGIN, y, startX + width - MARGIN, y);
    y += 15;

    /* ===== BUYER ===== */
    if (pageIndex == 0) {
        Buyer b = bill.getBuyer();
        if (b != null && b.getName() != null && !b.getName().isBlank()) {
            g.setFont(boldFont);
            g.drawString("Buyer:", startX + MARGIN, y);
            g.setFont(font);
            g.drawString(b.getName(), startX + MARGIN + 50, y);
            y += 20;
        }
    }

    /* ===== COLUMN CALC ===== */
    int tableW = width - (MARGIN * 2);

    int wSno   = (int) (tableW * P_SNO);
    int wName  = (int) (tableW * P_NAME);
    int wQty   = (int) (tableW * P_QTY);
    int wUnit  = (int) (tableW * P_UNIT);
    int wTotal = tableW - (wSno + wName + wQty + wUnit);

    int xSno   = startX + MARGIN;
    int xName  = xSno + wSno;
    int xQty   = xName + wName;
    int xUnit  = xQty + wQty;
    int xTotal = xUnit + wUnit;
    int xEnd   = xTotal + wTotal;

    /* ===== ROW PAGINATION ===== */
    int availableH = height - (y - startY) - FOOTER_H;
    int rowsPerPage = availableH / ROW_H;

    int startRow = pageIndex * rowsPerPage;
    if (startRow >= items.size()) return NO_SUCH_PAGE;

    int endRow = Math.min(startRow + rowsPerPage, items.size());

    /* ===== TABLE HEADER ===== */
    g.setColor(new Color(240, 240, 240));
    g.fillRect(xSno, y, tableW, ROW_H);
    g.setColor(Color.BLACK);
    g.setFont(boldFont);

    g.drawString("S.No", xSno + 4, y + 14);
    g.drawString("Product", xName + 4, y + 14);
    g.drawString("Qty", xQty + 4, y + 14);
    g.drawString("Unit Price", xUnit + 4, y + 14);
    g.drawString("Total", xTotal + 4, y + 14);

    g.drawLine(xSno, y, xEnd, y);
    y += ROW_H;

    /* ===== TABLE ROWS ===== */
    g.setFont(font);

    for (int i = startRow; i < endRow; i++) {
        BillItem it = items.get(i);

        g.drawString(String.valueOf(i + 1), xSno + 4, y + 14);
        g.drawString(trunc(it.getProductName(), 32), xName + 4, y + 14);
        g.drawString(fmt(it.getQuantity()), xQty + 4, y + 14);

        right(g, "PKR " + CURRENCY.format(it.getUnitPrice()),
                xUnit + wUnit - 6, y + 14);

        right(g, "PKR " + CURRENCY.format(it.getItemTotal()),
                xTotal + wTotal - 6, y + 14);

        g.setColor(new Color(200, 200, 200));
        g.drawLine(xSno, y + ROW_H, xEnd, y + ROW_H);
        g.setColor(Color.BLACK);

        y += ROW_H;
    }

    /* ===== TOTAL (LAST PAGE ONLY) ===== */
    if (endRow == items.size()) {
        y += 10;
        g.drawLine(xTotal - 10, y, xEnd, y);
        y += 18;

        g.setFont(boldFont);
        g.drawString("Grand Total:", xTotal - 80, y);
        right(g, "PKR " + CURRENCY.format(bill.getGrandTotal()), xEnd, y);
    }

    /* ===== FOOTER ===== */
    g.setFont(smallFont);
    g.setColor(Color.GRAY);
    g.drawString("Thank you for your business!",
            startX + MARGIN, startY + height - 10);

    return PAGE_EXISTS;
}

        /* ===== HELPERS ===== */

        private void right(Graphics2D g, String s, int x, int y) {
            g.drawString(s, x - g.getFontMetrics().stringWidth(s), y);
        }

        private String fmt(double d) {
            return d == (int) d ? String.valueOf((int) d) : CURRENCY.format(d);
        }

        private String trunc(String s, int n) {
            if (s == null) return "";
            return s.length() <= n ? s : s.substring(0, n - 3) + "...";
        }
    }
}
