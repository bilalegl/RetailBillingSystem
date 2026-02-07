package dao;

import application.DBConnection;
import model.Buyer;

import java.sql.*;

public class BuyerDAO {

    /**
     * Inserts buyer using the provided connection (so caller controls transaction).
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
}
