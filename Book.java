public class Book {
    private int bookId;
    private String title;
    private String author;
    private String isbn;
    private String category;
    private int totalCopies;
    private int availableCopies;

    public Book() {
    }

    public Book(int bookId, String title, String author, String isbn,
                String category, int totalCopies, int availableCopies) {
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.category = category;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    // Getters and setters
    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getTotalCopies() { return totalCopies; }
    public void setTotalCopies(int totalCopies) { this.totalCopies = totalCopies; }

    public int getAvailableCopies() { return availableCopies; }
    public void setAvailableCopies(int availableCopies) { this.availableCopies = availableCopies; }

    @Override
    public String toString() {
        return String.format(
                "ID: %-4d | %-30s | %-20s | ISBN: %-14s | %-15s | Available: %d/%d",
                bookId, title, author, isbn, category, availableCopies, totalCopies);
    }
}
