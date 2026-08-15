import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Handles the `users` table: real account creation and lookup for the
 * signup / login flow (replaces the old hardcoded demo credentials).
 */
public class UserDAO {

    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking username: " + e.getMessage());
            return false;
        }
    }

    public boolean emailExists(String email) {
        String sql = "SELECT 1 FROM users WHERE email=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking email: " + e.getMessage());
            return false;
        }
    }

    public User createUser(String fullName, String username, String email, String passwordHash) {
        String sql = "INSERT INTO users (full_name, username, email, password_hash) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, passwordHash);
            int rows = ps.executeUpdate();
            if (rows == 0) return null;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return getUserById(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error creating user: " + e.getMessage());
        }
        return null;
    }

    public User getUserByUsernameOrEmail(String usernameOrEmail) {
        String sql = "SELECT * FROM users WHERE username=? OR email=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usernameOrEmail);
            ps.setString(2, usernameOrEmail);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user: " + e.getMessage());
        }
        return null;
    }

    public User getUserById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user: " + e.getMessage());
        }
        return null;
    }

    public boolean updateProfile(int userId, String fullName, String email) {
        String sql = "UPDATE users SET full_name=?, email=? WHERE user_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating profile: " + e.getMessage());
            return false;
        }
    }

    public boolean updatePassword(int userId, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash=? WHERE user_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating password: " + e.getMessage());
            return false;
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("user_id"),
                rs.getString("full_name"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getTimestamp("created_at")
        );
    }
}
