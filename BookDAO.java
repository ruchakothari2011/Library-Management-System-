import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BookDAO {

    public boolean addBook(Book book) {
        String sql = "INSERT INTO books (title, author, isbn, category, total_copies, available_copies) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, book.getTitle());
            ps.setString(2, book.getAuthor());
            ps.setString(3, book.getIsbn());
            ps.setString(4, book.getCategory());
            ps.setInt(5, book.getTotalCopies());
            ps.setInt(6, book.getTotalCopies()); // available = total on creation

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error adding book: " + e.getMessage());
            return false;
        }
    }

    public boolean updateBook(Book book) {
        String sql = "UPDATE books SET title=?, author=?, isbn=?, category=?, total_copies=? " +
                     "WHERE book_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, book.getTitle());
            ps.setString(2, book.getAuthor());
            ps.setString(3, book.getIsbn());
            ps.setString(4, book.getCategory());
            ps.setInt(5, book.getTotalCopies());
            ps.setInt(6, book.getBookId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating book: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteBook(int bookId) {
        String sql = "DELETE FROM books WHERE book_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, bookId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting book (it may have active transactions): " + e.getMessage());
            return false;
        }
    }

    public Book getBookById(int bookId) {
        String sql = "SELECT * FROM books WHERE book_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching book: " + e.getMessage());
        }
        return null;
    }

    public List<Book> getAllBooks() {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books ORDER BY book_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                books.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching books: " + e.getMessage());
        }
        return books;
    }

    public List<Book> searchBooks(String keyword) {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books WHERE title LIKE ? OR author LIKE ? OR isbn LIKE ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            String pattern = "%" + keyword + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error searching books: " + e.getMessage());
        }
        return books;
    }

    /** Decrements available_copies by 1. Returns false if no copies are available. */
    public boolean decrementAvailableCopies(int bookId, Connection con) throws SQLException {
        String sql = "UPDATE books SET available_copies = available_copies - 1 " +
                     "WHERE book_id=? AND available_copies > 0";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Increments available_copies by 1 (used on return). */
    public boolean incrementAvailableCopies(int bookId, Connection con) throws SQLException {
        String sql = "UPDATE books SET available_copies = available_copies + 1 " +
                     "WHERE book_id=? AND available_copies < total_copies";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Returns category -> count of distinct titles, for the reports page. */
    public java.util.List<java.util.Map.Entry<String, Integer>> getCategoryBreakdown() {
        java.util.List<java.util.Map.Entry<String, Integer>> result = new ArrayList<>();
        String sql = "SELECT COALESCE(category,'Uncategorized') AS category, COUNT(*) AS c " +
                     "FROM books GROUP BY category ORDER BY c DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(java.util.Map.entry(rs.getString("category"), rs.getInt("c")));
            }
        } catch (SQLException e) {
            System.err.println("Error building category breakdown: " + e.getMessage());
        }
        return result;
    }

    private Book mapRow(ResultSet rs) throws SQLException {
        return new Book(
                rs.getInt("book_id"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getString("isbn"),
                rs.getString("category"),
                rs.getInt("total_copies"),
                rs.getInt("available_copies")
        );
    }
}
