package dao;

import application.DBConnection;
import model.Bill;
import model.BillItem;
import model.Buyer;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BillDAO {

    /**
     * Save the bill (with items and optional buyer) in a single transaction.
     * Returns generated bill_id.
     */
    public int saveBill(Bill bill) throws SQLException {
        String insertBillSql = "INSERT INTO Bills (bill_date, total_amount, buyer_id) VALUES (?, ?, ?)";
        String insertItemSql = "INSERT INTO BillItems (bill_id, item_name, quantity, price) VALUES (?, ?, ?, ?)";

        // Use a single new connection for the whole transaction
        try (Connection conn = DBConnection.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                Integer buyerId = null;
                Buyer buyer = bill.getBuyer();
                if (buyer != null && ((buyer.getName() != null && !buyer.getName().isBlank()) ||
                        (buyer.getPhone() != null && !buyer.getPhone().isBlank()))) {
                    // Use BuyerDAO insert that accepts a Connection
                    BuyerDAO buyerDAO = new BuyerDAO();
                    buyerId = buyerDAO.insertBuyer(conn, buyer);
                }

                // bill_date use ISO_LOCAL_DATE_TIME
                String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                bill.setBillDate(now);

                int billId;
                try (PreparedStatement psBill = conn.prepareStatement(insertBillSql, Statement.RETURN_GENERATED_KEYS)) {
                    psBill.setString(1, bill.getBillDate());
                    psBill.setDouble(2, bill.getGrandTotal());
                    if (buyerId != null) {
                        psBill.setInt(3, buyerId);
                    } else {
                        psBill.setNull(3, Types.INTEGER);
                    }
                    int affected = psBill.executeUpdate();
                    if (affected == 0) {
                        throw new SQLException("Creating bill failed, no rows affected.");
                    }
                    try (ResultSet keys = psBill.getGeneratedKeys()) {
                        if (keys.next()) {
                            billId = keys.getInt(1);
                        } else {
                            throw new SQLException("Creating bill failed, no ID obtained.");
                        }
                    }
                }

                try (PreparedStatement psItem = conn.prepareStatement(insertItemSql)) {
                    for (BillItem item : bill.getItems()) {
                        psItem.setInt(1, billId);
                        psItem.setString(2, item.getProductName());
                        psItem.setDouble(3, item.getQuantity());
                        psItem.setDouble(4, item.getUnitPrice());
                        psItem.addBatch();
                    }
                    psItem.executeBatch();
                }

                conn.commit();
                return billId;
            } catch (SQLException ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollEx) {
                    rollEx.printStackTrace();
                }
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Get a list of bills matching optional filters.
     */
    public List<Bill> getBills(Integer billId, String buyerName, LocalDate dateFrom, LocalDate dateTo) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT b.bill_id, b.bill_date, b.total_amount, b.buyer_id, br.name AS buyer_name ")
           .append("FROM Bills b LEFT JOIN Buyers br ON b.buyer_id = br.buyer_id WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (billId != null) {
            sql.append("AND b.bill_id = ? ");
            params.add(billId);
        }

        if (buyerName != null && !buyerName.isBlank()) {
            sql.append("AND LOWER(br.name) LIKE ? ");
            params.add("%" + buyerName.toLowerCase() + "%");
        }

        DateTimeFormatter dtf = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        if (dateFrom != null) {
            LocalDateTime start = LocalDateTime.of(dateFrom, LocalTime.MIN);
            sql.append("AND b.bill_date >= ? ");
            params.add(start.format(dtf));
        }

        if (dateTo != null) {
            LocalDateTime end = LocalDateTime.of(dateTo, LocalTime.MAX);
            sql.append("AND b.bill_date <= ? ");
            params.add(end.format(dtf));
        }

        sql.append("ORDER BY b.bill_date DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) {
                    ps.setInt(i + 1, (Integer) p);
                } else {
                    ps.setString(i + 1, p.toString());
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<Bill> out = new ArrayList<>();
                while (rs.next()) {
                    Bill b = new Bill();
                    b.setId(rs.getInt("bill_id"));
                    b.setBillDate(rs.getString("bill_date"));
                    b.setGrandTotal(rs.getDouble("total_amount"));

                    int buyerId = rs.getInt("buyer_id");
                    if (!rs.wasNull()) {
                        Buyer buyer = new Buyer();
                        buyer.setId(buyerId);
                        buyer.setName(rs.getString("buyer_name"));
                        b.setBuyer(buyer);
                    }
                    out.add(b);
                }
                return out;
            }
        }
    }

    /**
     * Get bill by id plus its items (items are loaded and attached).
     */
    public Bill getBillById(int billId) throws SQLException {
        String sqlBill = "SELECT bill_id, bill_date, total_amount, buyer_id FROM Bills WHERE bill_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlBill)) {

            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Bill b = new Bill();
                b.setId(rs.getInt("bill_id"));
                b.setBillDate(rs.getString("bill_date"));
                b.setGrandTotal(rs.getDouble("total_amount"));
                int buyerId = rs.getInt("buyer_id");
                if (!rs.wasNull()) {
                    BuyerDAO buyerDAO = new BuyerDAO();
                    Buyer buyer = buyerDAO.getBuyerById(buyerId);
                    b.setBuyer(buyer);
                }
                // Load items
                List<BillItem> items = getBillItems(b.getId());
                b.setItems(items);

                // compute subtotal and discount
                double subtotal = items.stream().mapToDouble(i -> i.getItemTotal()).sum();
                b.setSubtotal(subtotal);
                double discountAmount = subtotal - b.getGrandTotal();
                if (discountAmount < 0) discountAmount = 0.0;
                b.setDiscountAmount(discountAmount);
                double discountPercent = subtotal > 0 ? (discountAmount / subtotal) * 100.0 : 0.0;
                b.setDiscountPercent(discountPercent);

                return b;
            }
        }
    }

    /**
     * Return list of BillItem for a given bill_id
     */
    public List<BillItem> getBillItems(int billId) throws SQLException {
        String sql = "SELECT item_name, quantity, price FROM BillItems WHERE bill_id = ? ORDER BY item_id ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                List<BillItem> items = new ArrayList<>();
                while (rs.next()) {
                    String name = rs.getString("item_name");
                    double qty = rs.getDouble("quantity");
                    double price = rs.getDouble("price");
                    BillItem bi = new BillItem(name, qty, price);
                    items.add(bi);
                }
                return items;
            }
        }
    }
}
