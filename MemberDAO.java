import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MemberDAO {

    public boolean addMember(Member member) {
        String sql = "INSERT INTO members (name, email, phone, address) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, member.getName());
            ps.setString(2, member.getEmail());
            ps.setString(3, member.getPhone());
            ps.setString(4, member.getAddress());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error adding member: " + e.getMessage());
            return false;
        }
    }

    public boolean updateMember(Member member) {
        String sql = "UPDATE members SET name=?, email=?, phone=?, address=?, status=? WHERE member_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, member.getName());
            ps.setString(2, member.getEmail());
            ps.setString(3, member.getPhone());
            ps.setString(4, member.getAddress());
            ps.setString(5, member.getStatus());
            ps.setInt(6, member.getMemberId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating member: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteMember(int memberId) {
        String sql = "DELETE FROM members WHERE member_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, memberId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting member (they may have active transactions): " + e.getMessage());
            return false;
        }
    }

    public Member getMemberById(int memberId) {
        String sql = "SELECT * FROM members WHERE member_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching member: " + e.getMessage());
        }
        return null;
    }

    public List<Member> getAllMembers() {
        List<Member> members = new ArrayList<>();
        String sql = "SELECT * FROM members ORDER BY member_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                members.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching members: " + e.getMessage());
        }
        return members;
    }

    private Member mapRow(ResultSet rs) throws SQLException {
        return new Member(
                rs.getInt("member_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"),
                SqlDates.getDate(rs, "join_date"),
                rs.getString("status")
        );
    }
}
