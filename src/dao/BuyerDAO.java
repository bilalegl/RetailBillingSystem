package dao;

import application.DBConnection;
import model.Buyer;

import java.sql.*;

/**
 * BuyerDAO: read + insert helpers
 */
public class BuyerDAO {

    /**
     * Insert buyer using provided connection (participates in caller transaction).
     * Returns generated buyer_id.
     */
    public int insertBuyer(Connection conn, Buyer buyer) throws SQLException {
        String sql = "INSERT INTO Buyers(name, phone) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, buyer.getName());
            ps.setString(2, buyer.getPhone());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Inserting buyer failed, no rows affected.");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                } else {
                    throw new SQLException("Inserting buyer failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Convenience: insert buyer using its own connection (not transactional with caller).
     */
    public int insertBuyer(Buyer buyer) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return insertBuyer(conn, buyer);
        }
    }

    /**
     * Read-only: get buyer by id (uses its own connection).
     */
    public Buyer getBuyerById(int buyerId) throws SQLException {
        String sql = "SELECT buyer_id, name, phone FROM Buyers WHERE buyer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Buyer b = new Buyer();
                    b.setId(rs.getInt("buyer_id"));
                    b.setName(rs.getString("name"));
                    b.setPhone(rs.getString("phone"));
                    return b;
                } else {
                    return null;
                }
            }
        }
    }
}
