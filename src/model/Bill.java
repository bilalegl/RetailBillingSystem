package model;

import java.util.ArrayList;
import java.util.List;

public class Bill {
    private int id;
    private String billDate; // ISO string or your chosen format
    private double subtotal;
    private double discountPercent;
    private double discountAmount;
    private double grandTotal;
    private Buyer buyer;
    private List<model.BillItem> items = new ArrayList<>();

    public Bill() {}

    // getters & setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBillDate() { return billDate; }
    public void setBillDate(String billDate) { this.billDate = billDate; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(double discountPercent) { this.discountPercent = discountPercent; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }

    public double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(double grandTotal) { this.grandTotal = grandTotal; }

    public Buyer getBuyer() { return buyer; }
    public void setBuyer(Buyer buyer) { this.buyer = buyer; }

    public List<model.BillItem> getItems() { return items; }
    public void setItems(List<model.BillItem> items) { this.items = items; }

    public void addItem(model.BillItem item) { this.items.add(item); }
}
