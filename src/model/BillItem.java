package model;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class BillItem {

    private final StringProperty productName = new SimpleStringProperty("");
    private final DoubleProperty quantity = new SimpleDoubleProperty(1.0);
    private final DoubleProperty unitPrice = new SimpleDoubleProperty(0.0);
    private final ReadOnlyDoubleWrapper itemTotal = new ReadOnlyDoubleWrapper();

    // ✅ REQUIRED no-arg constructor
    public BillItem() {
        DoubleBinding totalBinding = quantity.multiply(unitPrice);
        itemTotal.bind(totalBinding);
    }

    // ✅ REQUIRED constructor used by controller
    public BillItem(String productName, double quantity, double unitPrice) {
        this();
        setProductName(productName);
        setQuantity(quantity);
        setUnitPrice(unitPrice);
    }

    // ---- getters / setters ----

    public String getProductName() {
        return productName.get();
    }

    public void setProductName(String value) {
        productName.set(value);
    }

    public StringProperty productNameProperty() {
        return productName;
    }

    public double getQuantity() {
        return quantity.get();
    }

    public void setQuantity(double value) {
        quantity.set(value);
    }

    public DoubleProperty quantityProperty() {
        return quantity;
    }

    public double getUnitPrice() {
        return unitPrice.get();
    }

    public void setUnitPrice(double value) {
        unitPrice.set(value);
    }

    public DoubleProperty unitPriceProperty() {
        return unitPrice;
    }

    public double getItemTotal() {
        return itemTotal.get();
    }

    public ReadOnlyDoubleProperty itemTotalProperty() {
        return itemTotal.getReadOnlyProperty();
    }
}
