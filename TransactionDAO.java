import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    private static final int LOAN_PERIOD_DAYS = 14;   // books are due 14 days after issue
    private static final double FINE_PER_DAY = 5.0;   // Rs. 5 per day overdue

    private final BookDAO bookDAO = new BookDAO();

    /**
     * Issues a book to a member: creates a transaction row and decrements
     * the book's available copies. Runs as a single DB transaction so the
     * two updates never go out of sync.
     */
    public String issueBook(int bookId, int memberId) {
        String checkAvailSql = "SELECT available_copies FROM books WHERE book_id=?";
        String insertTxnSql = "INSERT INTO transactions (book_id, member_id, issue_date, due_date, status) " +
                               "VALUES (?, ?, ?, ?, 'ISSUED')";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            // Check availability first
            try (PreparedStatement ps = con.prepareStatement(checkAvailSql)) {
                ps.setInt(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        return "ERROR: Book not found.";
                    }
                    if (rs.getInt("available_copies") <= 0) {
                        con.rollback();
                        return "ERROR: No copies available for this book right now.";
                    }
                }
            }

            LocalDate today = LocalDate.now();
            LocalDate due = today.plusDays(LOAN_PERIOD_DAYS);

            try (PreparedStatement ps = con.prepareStatement(insertTxnSql)) {
                ps.setInt(1, bookId);
                ps.setInt(2, memberId);
                ps.setString(3, today.toString());
                ps.setString(4, due.toString());
                ps.executeUpdate();
            }

            boolean updated = bookDAO.decrementAvailableCopies(bookId, con);
            if (!updated) {
                con.rollback();
                return "ERROR: Could not update book copies (none available).";
            }

            con.commit();
            return "SUCCESS: Book issued. Due date: " + due;

        } catch (SQLException e) {
            rollbackQuietly(con);
            return "ERROR: " + e.getMessage();
        } finally {
            resetAutoCommit(con);
        }
    }

    /**
     * Returns a book: marks the transaction RETURNED, calculates any fine
     * for late return, and increments the book's available copies.
     */
    public String returnBook(int transactionId) {
        String getTxnSql = "SELECT * FROM transactions WHERE transaction_id=? AND status='ISSUED'";
        String updateTxnSql = "UPDATE transactions SET return_date=?, fine_amount=?, status='RETURNED' " +
                               "WHERE transaction_id=?";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            int bookId;
            LocalDate dueDate;

            try (PreparedStatement ps = con.prepareStatement(getTxnSql)) {
                ps.setInt(1, transactionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        return "ERROR: No active (issued) transaction found with that ID.";
                    }
                    bookId = rs.getInt("book_id");
                    dueDate = SqlDates.getDate(rs, "due_date").toLocalDate();
                }
            }

            LocalDate today = LocalDate.now();
            double fine = calculateFine(dueDate, today);

            try (PreparedStatement ps = con.prepareStatement(updateTxnSql)) {
                ps.setString(1, today.toString());
                ps.setDouble(2, fine);
                ps.setInt(3, transactionId);
                ps.executeUpdate();
            }

            bookDAO.incrementAvailableCopies(bookId, con);

            con.commit();

            if (fine > 0) {
                return String.format("SUCCESS: Book returned. Overdue - Fine charged: Rs.%.2f", fine);
            } else {
                return "SUCCESS: Book returned on time. No fine.";
            }

        } catch (SQLException e) {
            rollbackQuietly(con);
            return "ERROR: " + e.getMessage();
        } finally {
            resetAutoCommit(con);
        }
    }

    /** Core fine calculation: Rs.5/day for every day past the due date. */
    public double calculateFine(LocalDate dueDate, LocalDate returnDate) {
        long daysLate = ChronoUnit.DAYS.between(dueDate, returnDate);
        if (daysLate <= 0) {
            return 0.0;
        }
        return daysLate * FINE_PER_DAY;
    }

    /** Preview of the fine a currently-issued book would incur if returned today. */
    public double getProjectedFine(int transactionId) {
        String sql = "SELECT due_date FROM transactions WHERE transaction_id=? AND status='ISSUED'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LocalDate due = SqlDates.getDate(rs, "due_date").toLocalDate();
                    return calculateFine(due, LocalDate.now());
                }
            }
        } catch (SQLException e) {
            System.err.println("Error calculating projected fine: " + e.getMessage());
        }
        return 0.0;
    }

    public List<Transaction> getAllTransactions() {
        return runListQuery("SELECT t.*, b.title AS book_title, m.name AS member_name " +
                "FROM transactions t " +
                "JOIN books b ON t.book_id = b.book_id " +
                "JOIN members m ON t.member_id = m.member_id " +
                "ORDER BY t.transaction_id DESC");
    }

    public List<Transaction> getIssuedTransactions() {
        return runListQuery("SELECT t.*, b.title AS book_title, m.name AS member_name " +
                "FROM transactions t " +
                "JOIN books b ON t.book_id = b.book_id " +
                "JOIN members m ON t.member_id = m.member_id " +
                "WHERE t.status='ISSUED' " +
                "ORDER BY t.due_date");
    }

    public List<Transaction> getOverdueTransactions() {
        return runListQuery("SELECT t.*, b.title AS book_title, m.name AS member_name " +
                "FROM transactions t " +
                "JOIN books b ON t.book_id = b.book_id " +
                "JOIN members m ON t.member_id = m.member_id " +
                "WHERE t.status='ISSUED' AND t.due_date < DATE('now') " +
                "ORDER BY t.due_date");
    }

    public List<Transaction> getTransactionsByMember(int memberId) {
        String sql = "SELECT t.*, b.title AS book_title, m.name AS member_name " +
                "FROM transactions t " +
                "JOIN books b ON t.book_id = b.book_id " +
                "JOIN members m ON t.member_id = m.member_id " +
                "WHERE t.member_id=? ORDER BY t.transaction_id DESC";
        List<Transaction> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching member transactions: " + e.getMessage());
        }
        return list;
    }

    /** Top N most-borrowed titles by number of issues, for the reports page. */
    public List<java.util.Map.Entry<String, Integer>> getTopBorrowedBooks(int limit) {
        List<java.util.Map.Entry<String, Integer>> result = new ArrayList<>();
        String sql = "SELECT b.title AS title, COUNT(*) AS c FROM transactions t " +
                     "JOIN books b ON t.book_id = b.book_id " +
                     "GROUP BY b.title ORDER BY c DESC LIMIT ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(java.util.Map.entry(rs.getString("title"), rs.getInt("c")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching top borrowed books: " + e.getMessage());
        }
        return result;
    }

    /** Total fines actually collected (from returned books). */
    public double getTotalFinesCollected() {
        String sql = "SELECT COALESCE(SUM(fine_amount),0) AS total FROM transactions WHERE status='RETURNED'";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getDouble("total");
        } catch (SQLException e) {
            System.err.println("Error summing collected fines: " + e.getMessage());
        }
        return 0.0;
    }

    /** Total fines currently accruing on overdue, still-issued books (projected, not yet charged). */
    public double getTotalOutstandingFines() {
        double total = 0.0;
        for (Transaction t : getOverdueTransactions()) {
            total += getProjectedFine(t.getTransactionId());
        }
        return total;
    }

    private List<Transaction> runListQuery(String sql) {
        List<Transaction> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching transactions: " + e.getMessage());
        }
        return list;
    }

    private Transaction mapRow(ResultSet rs) throws SQLException {
        Transaction t = new Transaction(
                rs.getInt("transaction_id"),
                rs.getInt("book_id"),
                rs.getInt("member_id"),
                SqlDates.getDate(rs, "issue_date"),
                SqlDates.getDate(rs, "due_date"),
                SqlDates.getDate(rs, "return_date"),
                rs.getDouble("fine_amount"),
                rs.getString("status")
        );
        t.setBookTitle(rs.getString("book_title"));
        t.setMemberName(rs.getString("member_name"));
        return t;
    }

    private void rollbackQuietly(Connection con) {
        if (con != null) {
            try {
                con.rollback();
            } catch (SQLException ex) {
                System.err.println("Rollback failed: " + ex.getMessage());
            }
        }
    }

    private void resetAutoCommit(Connection con) {
        if (con != null) {
            try {
                con.setAutoCommit(true);
            } catch (SQLException ex) {
                System.err.println("Could not reset auto-commit: " + ex.getMessage());
            }
        }
    }
}
