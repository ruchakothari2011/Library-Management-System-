import java.sql.Date;

public class Transaction {
    private int transactionId;
    private int bookId;
    private int memberId;
    private Date issueDate;
    private Date dueDate;
    private Date returnDate;
    private double fineAmount;
    private String status;

    // Extra display-only fields (populated via JOINs)
    private String bookTitle;
    private String memberName;

    public Transaction() {
    }

    public Transaction(int transactionId, int bookId, int memberId, Date issueDate,
                        Date dueDate, Date returnDate, double fineAmount, String status) {
        this.transactionId = transactionId;
        this.bookId = bookId;
        this.memberId = memberId;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.fineAmount = fineAmount;
        this.status = status;
    }

    // Getters and setters
    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) { this.transactionId = transactionId; }

    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public Date getIssueDate() { return issueDate; }
    public void setIssueDate(Date issueDate) { this.issueDate = issueDate; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public Date getReturnDate() { return returnDate; }
    public void setReturnDate(Date returnDate) { this.returnDate = returnDate; }

    public double getFineAmount() { return fineAmount; }
    public void setFineAmount(double fineAmount) { this.fineAmount = fineAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }

    @Override
    public String toString() {
        return String.format(
                "TxnID: %-4d | Book: %-25s | Member: %-15s | Issued: %s | Due: %s | Returned: %-10s | Fine: Rs.%.2f | %s",
                transactionId,
                bookTitle != null ? bookTitle : String.valueOf(bookId),
                memberName != null ? memberName : String.valueOf(memberId),
                issueDate, dueDate,
                returnDate != null ? returnDate.toString() : "-",
                fineAmount, status);
    }
}
