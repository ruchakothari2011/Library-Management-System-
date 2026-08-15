import java.util.List;
import java.util.Scanner;

public class Main {

    private static final Scanner sc = new Scanner(System.in);
    private static final BookDAO bookDAO = new BookDAO();
    private static final MemberDAO memberDAO = new MemberDAO();
    private static final TransactionDAO transactionDAO = new TransactionDAO();

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   LIBRARY MANAGEMENT SYSTEM");
        System.out.println("=================================================");

        boolean running = true;
        while (running) {
            printMainMenu();
            int choice = readInt("Enter choice: ");

            switch (choice) {
                case 1 -> bookMenu();
                case 2 -> memberMenu();
                case 3 -> issueBookFlow();
                case 4 -> returnBookFlow();
                case 5 -> viewIssuedBooks();
                case 6 -> viewOverdueBooks();
                case 7 -> viewAllTransactions();
                case 0 -> {
                    running = false;
                    System.out.println("Goodbye!");
                }
                default -> System.out.println("Invalid choice. Try again.");
            }
        }
        DBConnection.closeConnection();
    }

    // ============================ MAIN MENU ============================

    private static void printMainMenu() {
        System.out.println("\n--------------- MAIN MENU ---------------");
        System.out.println("1. Manage Books");
        System.out.println("2. Manage Members");
        System.out.println("3. Issue a Book");
        System.out.println("4. Return a Book");
        System.out.println("5. View Currently Issued Books");
        System.out.println("6. View Overdue Books");
        System.out.println("7. View All Transactions");
        System.out.println("0. Exit");
        System.out.println("------------------------------------------");
    }

    // ============================ BOOK MENU ============================

    private static void bookMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Manage Books ---");
            System.out.println("1. Add Book");
            System.out.println("2. Update Book");
            System.out.println("3. Delete Book");
            System.out.println("4. View All Books");
            System.out.println("5. Search Books");
            System.out.println("0. Back to Main Menu");
            int choice = readInt("Enter choice: ");

            switch (choice) {
                case 1 -> addBook();
                case 2 -> updateBook();
                case 3 -> deleteBook();
                case 4 -> viewAllBooks();
                case 5 -> searchBooks();
                case 0 -> back = true;
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private static void addBook() {
        System.out.println("\n-- Add New Book --");
        String title = readString("Title: ");
        String author = readString("Author: ");
        String isbn = readString("ISBN: ");
        String category = readString("Category: ");
        int copies = readInt("Number of copies: ");

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setIsbn(isbn);
        book.setCategory(category);
        book.setTotalCopies(copies);

        boolean ok = bookDAO.addBook(book);
        System.out.println(ok ? "Book added successfully." : "Failed to add book.");
    }

    private static void updateBook() {
        int id = readInt("Enter Book ID to update: ");
        Book existing = bookDAO.getBookById(id);
        if (existing == null) {
            System.out.println("No book found with that ID.");
            return;
        }
        System.out.println("Current details: " + existing);
        String title = readString("New title (leave blank to keep '" + existing.getTitle() + "'): ");
        String author = readString("New author (leave blank to keep '" + existing.getAuthor() + "'): ");
        String isbn = readString("New ISBN (leave blank to keep '" + existing.getIsbn() + "'): ");
        String category = readString("New category (leave blank to keep '" + existing.getCategory() + "'): ");
        String copiesStr = readString("New total copies (leave blank to keep " + existing.getTotalCopies() + "): ");

        if (!title.isBlank()) existing.setTitle(title);
        if (!author.isBlank()) existing.setAuthor(author);
        if (!isbn.isBlank()) existing.setIsbn(isbn);
        if (!category.isBlank()) existing.setCategory(category);
        if (!copiesStr.isBlank()) existing.setTotalCopies(Integer.parseInt(copiesStr));

        boolean ok = bookDAO.updateBook(existing);
        System.out.println(ok ? "Book updated successfully." : "Failed to update book.");
    }

    private static void deleteBook() {
        int id = readInt("Enter Book ID to delete: ");
        boolean ok = bookDAO.deleteBook(id);
        System.out.println(ok ? "Book deleted successfully." : "Failed to delete book.");
    }

    private static void viewAllBooks() {
        List<Book> books = bookDAO.getAllBooks();
        System.out.println("\n-- All Books (" + books.size() + ") --");
        if (books.isEmpty()) {
            System.out.println("No books found.");
        }
        for (Book b : books) {
            System.out.println(b);
        }
    }

    private static void searchBooks() {
        String keyword = readString("Enter title/author/ISBN keyword: ");
        List<Book> results = bookDAO.searchBooks(keyword);
        System.out.println("\n-- Search Results (" + results.size() + ") --");
        if (results.isEmpty()) {
            System.out.println("No matching books found.");
        }
        for (Book b : results) {
            System.out.println(b);
        }
    }

    // ============================ MEMBER MENU ============================

    private static void memberMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Manage Members ---");
            System.out.println("1. Add Member");
            System.out.println("2. Update Member");
            System.out.println("3. Delete Member");
            System.out.println("4. View All Members");
            System.out.println("5. View a Member's Transaction History");
            System.out.println("0. Back to Main Menu");
            int choice = readInt("Enter choice: ");

            switch (choice) {
                case 1 -> addMember();
                case 2 -> updateMember();
                case 3 -> deleteMember();
                case 4 -> viewAllMembers();
                case 5 -> viewMemberHistory();
                case 0 -> back = true;
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private static void addMember() {
        System.out.println("\n-- Add New Member --");
        String name = readString("Name: ");
        String email = readString("Email: ");
        String phone = readString("Phone: ");
        String address = readString("Address: ");

        Member member = new Member();
        member.setName(name);
        member.setEmail(email);
        member.setPhone(phone);
        member.setAddress(address);

        boolean ok = memberDAO.addMember(member);
        System.out.println(ok ? "Member added successfully." : "Failed to add member (email may already exist).");
    }

    private static void updateMember() {
        int id = readInt("Enter Member ID to update: ");
        Member existing = memberDAO.getMemberById(id);
        if (existing == null) {
            System.out.println("No member found with that ID.");
            return;
        }
        System.out.println("Current details: " + existing);
        String name = readString("New name (leave blank to keep '" + existing.getName() + "'): ");
        String email = readString("New email (leave blank to keep '" + existing.getEmail() + "'): ");
        String phone = readString("New phone (leave blank to keep '" + existing.getPhone() + "'): ");
        String address = readString("New address (leave blank to keep '" + existing.getAddress() + "'): ");
        String status = readString("Status ACTIVE/INACTIVE (leave blank to keep '" + existing.getStatus() + "'): ");

        if (!name.isBlank()) existing.setName(name);
        if (!email.isBlank()) existing.setEmail(email);
        if (!phone.isBlank()) existing.setPhone(phone);
        if (!address.isBlank()) existing.setAddress(address);
        if (!status.isBlank()) existing.setStatus(status.toUpperCase());

        boolean ok = memberDAO.updateMember(existing);
        System.out.println(ok ? "Member updated successfully." : "Failed to update member.");
    }

    private static void deleteMember() {
        int id = readInt("Enter Member ID to delete: ");
        boolean ok = memberDAO.deleteMember(id);
        System.out.println(ok ? "Member deleted successfully." : "Failed to delete member.");
    }

    private static void viewAllMembers() {
        List<Member> members = memberDAO.getAllMembers();
        System.out.println("\n-- All Members (" + members.size() + ") --");
        if (members.isEmpty()) {
            System.out.println("No members found.");
        }
        for (Member m : members) {
            System.out.println(m);
        }
    }

    private static void viewMemberHistory() {
        int id = readInt("Enter Member ID: ");
        List<Transaction> history = transactionDAO.getTransactionsByMember(id);
        System.out.println("\n-- Transaction History (" + history.size() + ") --");
        if (history.isEmpty()) {
            System.out.println("No transactions found for this member.");
        }
        for (Transaction t : history) {
            System.out.println(t);
        }
    }

    // ============================ ISSUE / RETURN ============================

    private static void issueBookFlow() {
        System.out.println("\n-- Issue a Book --");
        int bookId = readInt("Enter Book ID: ");
        int memberId = readInt("Enter Member ID: ");

        Book book = bookDAO.getBookById(bookId);
        Member member = memberDAO.getMemberById(memberId);

        if (book == null) {
            System.out.println("No book found with that ID.");
            return;
        }
        if (member == null) {
            System.out.println("No member found with that ID.");
            return;
        }

        String result = transactionDAO.issueBook(bookId, memberId);
        System.out.println(result);
    }

    private static void returnBookFlow() {
        System.out.println("\n-- Return a Book --");
        viewIssuedBooks();
        int transactionId = readInt("Enter Transaction ID to return: ");

        double projected = transactionDAO.getProjectedFine(transactionId);
        if (projected > 0) {
            System.out.printf("Note: this book is overdue. Fine to be charged: Rs.%.2f%n", projected);
        }

        String result = transactionDAO.returnBook(transactionId);
        System.out.println(result);
    }

    // ============================ REPORTS ============================

    private static void viewIssuedBooks() {
        List<Transaction> issued = transactionDAO.getIssuedTransactions();
        System.out.println("\n-- Currently Issued Books (" + issued.size() + ") --");
        if (issued.isEmpty()) {
            System.out.println("No books are currently issued.");
        }
        for (Transaction t : issued) {
            System.out.println(t);
        }
    }

    private static void viewOverdueBooks() {
        List<Transaction> overdue = transactionDAO.getOverdueTransactions();
        System.out.println("\n-- Overdue Books (" + overdue.size() + ") --");
        if (overdue.isEmpty()) {
            System.out.println("No overdue books. Nice!");
        }
        for (Transaction t : overdue) {
            double projected = transactionDAO.getProjectedFine(t.getTransactionId());
            System.out.printf("%s | Projected fine if returned today: Rs.%.2f%n", t, projected);
        }
    }

    private static void viewAllTransactions() {
        List<Transaction> all = transactionDAO.getAllTransactions();
        System.out.println("\n-- All Transactions (" + all.size() + ") --");
        if (all.isEmpty()) {
            System.out.println("No transactions yet.");
        }
        for (Transaction t : all) {
            System.out.println(t);
        }
    }

    // ============================ INPUT HELPERS ============================

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static String readString(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }
}
