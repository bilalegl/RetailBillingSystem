package dao;

import application.DBConnection;
import model.Bill;
import model.Buyer;
import model.BillItem;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BillDAO {

    /**
     * Save the bill (with items and optional buyer) in a single transaction.
     * Returns generated bill_id.
     */
    public int saveBill(Bill bill) throws SQLException {
        Connection conn = DBConnection.getInstance().getConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            Integer buyerId = null;
            Buyer buyer = bill.getBuyer();
            if (buyer != null && ((buyer.getName() != null && !buyer.getName().isBlank()) ||
                    (buyer.getPhone() != null && !buyer.getPhone().isBlank()))) {
                BuyerDAO buyerDAO = new BuyerDAO();
                buyerId = buyerDAO.insertBuyer(conn, buyer);
            }

            // bill_date use ISO_LOCAL_DATE_TIME
            String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            bill.setBillDate(now);

            String insertBillSql = "INSERT INTO Bills (bill_date, total_amount, buyer_id) VALUES (?, ?, ?)";
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

            // Insert bill items
            String insertItemSql = "INSERT INTO BillItems (bill_id, item_name, quantity, price) VALUES (?, ?, ?, ?)";
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
                // log rollback failure
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
