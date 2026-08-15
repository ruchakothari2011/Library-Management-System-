import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves the web front-end and a small JSON REST API on top of the
 * existing DAO layer.
 *
 * Run with:   java -cp .:../lib/sqlite-jdbc-*.jar ApiServer [port]
 * Default port is 8090. Pass a different port as the first argument, e.g:
 *   java -cp .:../lib/sqlite-jdbc-*.jar ApiServer 8095
 */
public class ApiServer {

    private static final BookDAO bookDAO = new BookDAO();
    private static final MemberDAO memberDAO = new MemberDAO();
    private static final TransactionDAO transactionDAO = new TransactionDAO();
    private static final UserDAO userDAO = new UserDAO();

    private static final int DEFAULT_PORT = 8090;
    private static final String WEB_ROOT = resolveWebRoot();

    // token -> userId. In-memory session store; resets when the server restarts.
    private static final Map<String, Integer> SESSIONS = new ConcurrentHashMap<>();
    private static final SecureRandom RNG = new SecureRandom();

    // Endpoints reachable without being logged in.
    private static final Set<String> PUBLIC_PATHS = Set.of("/api/login", "/api/signup");

    private static final Pattern ID_PATH = Pattern.compile("^/api/(\\w+)/(\\d+)$");
    private static final Pattern MEMBER_HISTORY_PATH = Pattern.compile("^/api/members/(\\d+)/history$");

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("Ignoring invalid port argument '" + args[0] + "', using " + DEFAULT_PORT);
            }
        }

        System.out.println("Serving static files from: " + new File(WEB_ROOT).getAbsolutePath());
        if (!new File(WEB_ROOT, "index.html").exists()) {
            System.err.println("WARNING: could not find index.html under " + WEB_ROOT +
                    " -- the web/ folder may be missing or in an unexpected location.");
        }

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (BindException e) {
            System.err.println("Port " + port + " is already in use.");
            System.err.println("Run again with a free port, e.g.: java -cp .:../lib/mysql-connector-j-*.jar ApiServer 8091");
            return;
        }

        server.createContext("/api/", ApiServer::handleApi);
        server.createContext("/", ApiServer::handleStatic);

        server.setExecutor(null);
        server.start();
        System.out.println("Library Management System running at http://localhost:" + port);
    }

    /**
     * Finds the web/ folder regardless of the working directory the server was
     * launched from. Looks first next to the running class files (the normal
     * "out/ + web/ are siblings" layout), then falls back to a couple of other
     * common locations so `java ApiServer` still works from the project root
     * or from inside src/.
     */
    private static String resolveWebRoot() {
        File classesDir = null;
        try {
            File codeSource = new File(ApiServer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            classesDir = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
        } catch (URISyntaxException | NullPointerException ignored) {
            // fall through to cwd-based guesses below
        }

        List<File> candidates = new java.util.ArrayList<>();
        if (classesDir != null) {
            candidates.add(new File(classesDir, "../web"));   // out/ + web/ siblings (recommended layout)
            candidates.add(new File(classesDir, "web"));      // web/ copied alongside classes
        }
        candidates.add(new File("web"));                      // launched from project root
        candidates.add(new File("../web"));                   // launched from src/
        candidates.add(new File("./LibraryManagementSystem/web"));

        for (File f : candidates) {
            if (new File(f, "index.html").exists()) {
                return f.getPath();
            }
        }
        // Nothing matched; default to the historical relative path so the
        // startup warning above at least points at the right place.
        return classesDir != null ? new File(classesDir, "../web").getPath() : "../web";
    }

    // ============================ STATIC FILES ============================

    private static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        File file = new File(WEB_ROOT, path).getCanonicalFile();
        File webRootDir = new File(WEB_ROOT).getCanonicalFile();

        if (!file.getPath().startsWith(webRootDir.getPath()) || !file.exists() || file.isDirectory()) {
            send(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }

        String contentType = guessContentType(file.getName());
        byte[] bytes = Files.readAllBytes(file.toPath());
        send(ex, 200, contentType, bytes);
    }

    private static String guessContentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    // ============================ API ROUTING ============================

    private static void handleApi(HttpExchange ex) {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getRawQuery();

            // Auth gate: everything under /api/ requires a valid session
            // except login/signup themselves.
            Integer currentUserId = getSessionUserId(ex);
            if (!PUBLIC_PATHS.contains(path) && currentUserId == null) {
                sendJson(ex, 401, JsonUtil.object().add("error", "Not authenticated").build());
                return;
            }

            switch (path) {
                case "/api/signup" -> {
                    if (method.equals("POST")) signup(ex);
                    else methodNotAllowed(ex);
                    return;
                }
                case "/api/login" -> {
                    if (method.equals("POST")) login(ex);
                    else methodNotAllowed(ex);
                    return;
                }
                case "/api/logout" -> {
                    if (method.equals("POST")) logout(ex);
                    else methodNotAllowed(ex);
                    return;
                }
                case "/api/me" -> {
                    if (method.equals("GET")) me(ex, currentUserId);
                    else methodNotAllowed(ex);
                    return;
                }
                case "/api/profile" -> {
                    if (method.equals("PUT")) updateProfile(ex, currentUserId);
                    else methodNotAllowed(ex);
                    return;
                }
                case "/api/change-password" -> {
                    if (method.equals("POST")) changePassword(ex, currentUserId);
                    else methodNotAllowed(ex);
                    return;
                }
                case "/api/books" -> {
                    if (method.equals("GET")) getBooks(ex, query);
                    else if (method.equals("POST")) addBook(ex);
                    else methodNotAllowed(ex);
                    return;
                }
                case "/api/members" -> {
                    if (method.equals("GET")) getMembers(ex);
                    else if (method.equals("POST")) addMember(ex);
                    else methodNotAllowed(ex);
                    return;
                }
                case "/api/issue" -> {
                    if (method.equals("POST")) issueBook(ex);
                    else methodNotAllowed(ex);
                    return;
                }
                case "/api/transactions" -> {
                    if (method.equals("GET")) getAllTransactions(ex);
                    else methodNotAllowed(ex);
                    return;
                }
                case "/api/transactions/issued" -> {
                    getIssuedTransactions(ex);
                    return;
                }
                case "/api/transactions/overdue" -> {
                    getOverdueTransactions(ex);
                    return;
                }
                case "/api/dashboard" -> {
                    getDashboardStats(ex);
                    return;
                }
                case "/api/reports" -> {
                    getReports(ex);
                    return;
                }
                default -> { /* fall through to pattern routes below */ }
            }

            Matcher mh = MEMBER_HISTORY_PATH.matcher(path);
            if (mh.matches()) {
                getMemberHistory(ex, Integer.parseInt(mh.group(1)));
                return;
            }

            Matcher m = ID_PATH.matcher(path);
            if (m.matches()) {
                String resource = m.group(1);
                int id = Integer.parseInt(m.group(2));

                switch (resource) {
                    case "books" -> {
                        if (method.equals("PUT")) updateBook(ex, id);
                        else if (method.equals("DELETE")) deleteBook(ex, id);
                        else methodNotAllowed(ex);
                        return;
                    }
                    case "members" -> {
                        if (method.equals("PUT")) updateMember(ex, id);
                        else if (method.equals("DELETE")) deleteMember(ex, id);
                        else methodNotAllowed(ex);
                        return;
                    }
                    case "return" -> {
                        if (method.equals("POST")) returnBook(ex, id);
                        else methodNotAllowed(ex);
                        return;
                    }
                }
            }

            send(ex, 404, "application/json", "{\"error\":\"Not found\"}".getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            try {
                String msg = JsonUtil.object().add("error", e.getMessage() == null ? "Unexpected server error" : e.getMessage()).build();
                send(ex, 500, "application/json", msg.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }
    }

    // ============================ AUTH ============================

    private static void signup(HttpExchange ex) throws IOException {
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        String fullName = body.getOrDefault("fullName", "").trim();
        String username = body.getOrDefault("username", "").trim();
        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "");

        if (fullName.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            sendJson(ex, 400, JsonUtil.object().add("error", "All fields are required.").build());
            return;
        }
        if (password.length() < 6) {
            sendJson(ex, 400, JsonUtil.object().add("error", "Password must be at least 6 characters.").build());
            return;
        }
        if (userDAO.usernameExists(username)) {
            sendJson(ex, 409, JsonUtil.object().add("error", "That username is already taken.").build());
            return;
        }
        if (userDAO.emailExists(email)) {
            sendJson(ex, 409, JsonUtil.object().add("error", "An account with that email already exists.").build());
            return;
        }

        String hash = PasswordUtil.hash(password);
        User user = userDAO.createUser(fullName, username, email, hash);
        if (user == null) {
            sendJson(ex, 500, JsonUtil.object().add("error", "Could not create the account. Please try again.").build());
            return;
        }

        String token = startSession(user.getUserId());
        setSessionCookie(ex, token);
        sendJson(ex, 201, JsonUtil.object().add("success", "true").addRaw("user", userJson(user)).build());
    }

    private static void login(HttpExchange ex) throws IOException {
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        String usernameOrEmail = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");

        User user = userDAO.getUserByUsernameOrEmail(usernameOrEmail);
        boolean ok = user != null && PasswordUtil.verify(password, user.getPasswordHash());

        if (!ok) {
            sendJson(ex, 401, JsonUtil.object().add("success", "false").add("error", "Incorrect username or password.").build());
            return;
        }

        String token = startSession(user.getUserId());
        setSessionCookie(ex, token);
        sendJson(ex, 200, JsonUtil.object().add("success", "true").addRaw("user", userJson(user)).build());
    }

    private static void logout(HttpExchange ex) throws IOException {
        String token = getCookieValue(ex, "SID");
        if (token != null) SESSIONS.remove(token);
        ex.getResponseHeaders().add("Set-Cookie", "SID=; Path=/; Max-Age=0; SameSite=Lax");
        sendJson(ex, 200, JsonUtil.object().add("success", "true").build());
    }

    private static void me(HttpExchange ex, int userId) throws IOException {
        User user = userDAO.getUserById(userId);
        if (user == null) {
            sendJson(ex, 404, JsonUtil.object().add("error", "User not found").build());
            return;
        }
        sendJson(ex, 200, userJson(user));
    }

    private static void updateProfile(HttpExchange ex, int userId) throws IOException {
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        User existing = userDAO.getUserById(userId);
        if (existing == null) {
            sendJson(ex, 404, JsonUtil.object().add("error", "User not found").build());
            return;
        }
        String fullName = body.getOrDefault("fullName", existing.getFullName());
        String email = body.getOrDefault("email", existing.getEmail());
        boolean ok = userDAO.updateProfile(userId, fullName, email);
        if (ok) {
            User updated = userDAO.getUserById(userId);
            sendJson(ex, 200, JsonUtil.object().add("success", "true").addRaw("user", userJson(updated)).build());
        } else {
            sendJson(ex, 400, JsonUtil.object().add("success", "false").add("error", "Could not update profile.").build());
        }
    }

    private static void changePassword(HttpExchange ex, int userId) throws IOException {
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        String currentPassword = body.getOrDefault("currentPassword", "");
        String newPassword = body.getOrDefault("newPassword", "");

        User user = userDAO.getUserById(userId);
        if (user == null || !PasswordUtil.verify(currentPassword, user.getPasswordHash())) {
            sendJson(ex, 401, JsonUtil.object().add("success", "false").add("error", "Current password is incorrect.").build());
            return;
        }
        if (newPassword.length() < 6) {
            sendJson(ex, 400, JsonUtil.object().add("success", "false").add("error", "New password must be at least 6 characters.").build());
            return;
        }
        boolean ok = userDAO.updatePassword(userId, PasswordUtil.hash(newPassword));
        sendJson(ex, ok ? 200 : 400, JsonUtil.object().add("success", ok ? "true" : "false").build());
    }

    private static String userJson(User u) {
        return JsonUtil.object()
                .add("userId", u.getUserId())
                .add("fullName", u.getFullName())
                .add("username", u.getUsername())
                .add("email", u.getEmail())
                .build();
    }

    private static String startSession(int userId) {
        byte[] raw = new byte[24];
        RNG.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        SESSIONS.put(token, userId);
        return token;
    }

    private static void setSessionCookie(HttpExchange ex, String token) {
        ex.getResponseHeaders().add("Set-Cookie", "SID=" + token + "; Path=/; Max-Age=1209600; SameSite=Lax");
    }

    private static Integer getSessionUserId(HttpExchange ex) {
        String token = getCookieValue(ex, "SID");
        if (token == null) return null;
        return SESSIONS.get(token);
    }

    private static String getCookieValue(HttpExchange ex, String name) {
        List<String> cookieHeaders = ex.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) return null;
        for (String header : cookieHeaders) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    // ============================ BOOKS ============================

    private static void getBooks(HttpExchange ex, String query) throws IOException {
        List<Book> books;
        String keyword = queryParam(query, "q");
        if (keyword != null && !keyword.isBlank()) {
            books = bookDAO.searchBooks(keyword);
        } else {
            books = bookDAO.getAllBooks();
        }
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < books.size(); i++) {
            if (i > 0) arr.append(",");
            arr.append(bookJson(books.get(i)));
        }
        arr.append("]");
        sendJson(ex, 200, arr.toString());
    }

    private static void addBook(HttpExchange ex) throws IOException {
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        Book b = new Book();
        b.setTitle(body.getOrDefault("title", ""));
        b.setAuthor(body.getOrDefault("author", ""));
        b.setIsbn(body.getOrDefault("isbn", ""));
        b.setCategory(body.getOrDefault("category", ""));
        b.setTotalCopies(parseIntSafe(body.get("totalCopies"), 1));

        boolean ok = bookDAO.addBook(b);
        sendJson(ex, ok ? 201 : 400, JsonUtil.object().add("success", ok ? "true" : "false").build());
    }

    private static void updateBook(HttpExchange ex, int id) throws IOException {
        Book existing = bookDAO.getBookById(id);
        if (existing == null) {
            sendJson(ex, 404, JsonUtil.object().add("error", "Book not found").build());
            return;
        }
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        if (body.containsKey("title")) existing.setTitle(body.get("title"));
        if (body.containsKey("author")) existing.setAuthor(body.get("author"));
        if (body.containsKey("isbn")) existing.setIsbn(body.get("isbn"));
        if (body.containsKey("category")) existing.setCategory(body.get("category"));
        if (body.containsKey("totalCopies")) existing.setTotalCopies(parseIntSafe(body.get("totalCopies"), existing.getTotalCopies()));

        boolean ok = bookDAO.updateBook(existing);
        sendJson(ex, 200, JsonUtil.object().add("success", ok ? "true" : "false").build());
    }

    private static void deleteBook(HttpExchange ex, int id) throws IOException {
        boolean ok = bookDAO.deleteBook(id);
        sendJson(ex, ok ? 200 : 400, JsonUtil.object().add("success", ok ? "true" : "false").build());
    }

    private static String bookJson(Book b) {
        return JsonUtil.object()
                .add("bookId", b.getBookId())
                .add("title", b.getTitle())
                .add("author", b.getAuthor())
                .add("isbn", b.getIsbn())
                .add("category", b.getCategory())
                .add("totalCopies", b.getTotalCopies())
                .add("availableCopies", b.getAvailableCopies())
                .build();
    }

    // ============================ MEMBERS ============================

    private static void getMembers(HttpExchange ex) throws IOException {
        List<Member> members = memberDAO.getAllMembers();
        Map<Integer, MemberStanding> standings = computeMemberStandings();

        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) arr.append(",");
            Member m = members.get(i);
            arr.append(memberJson(m, standings.getOrDefault(m.getMemberId(), MemberStanding.EMPTY)));
        }
        arr.append("]");
        sendJson(ex, 200, arr.toString());
    }

    /** issuedCount / overdueCount / duesOwed per member, derived from currently-issued loans. */
    private record MemberStanding(int issuedCount, int overdueCount, double duesOwed) {
        static final MemberStanding EMPTY = new MemberStanding(0, 0, 0);
    }

    private static Map<Integer, MemberStanding> computeMemberStandings() {
        Map<Integer, int[]> counts = new java.util.HashMap<>();      // memberId -> [issued, overdue]
        Map<Integer, Double> dues = new java.util.HashMap<>();       // memberId -> projected fine total
        java.time.LocalDate today = java.time.LocalDate.now();

        for (Transaction t : transactionDAO.getIssuedTransactions()) {
            int mid = t.getMemberId();
            int[] c = counts.computeIfAbsent(mid, k -> new int[2]);
            c[0]++; // issued
            java.time.LocalDate due = t.getDueDate().toLocalDate();
            if (due.isBefore(today)) {
                c[1]++; // overdue
                double fine = transactionDAO.calculateFine(due, today);
                dues.merge(mid, fine, Double::sum);
            }
        }

        Map<Integer, MemberStanding> result = new java.util.HashMap<>();
        for (Map.Entry<Integer, int[]> e : counts.entrySet()) {
            int mid = e.getKey();
            result.put(mid, new MemberStanding(e.getValue()[0], e.getValue()[1], dues.getOrDefault(mid, 0.0)));
        }
        return result;
    }

    private static void addMember(HttpExchange ex) throws IOException {
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        Member m = new Member();
        m.setName(body.getOrDefault("name", ""));
        m.setEmail(body.getOrDefault("email", ""));
        m.setPhone(body.getOrDefault("phone", ""));
        m.setAddress(body.getOrDefault("address", ""));

        boolean ok = memberDAO.addMember(m);
        sendJson(ex, ok ? 201 : 400, JsonUtil.object().add("success", ok ? "true" : "false").build());
    }

    private static void updateMember(HttpExchange ex, int id) throws IOException {
        Member existing = memberDAO.getMemberById(id);
        if (existing == null) {
            sendJson(ex, 404, JsonUtil.object().add("error", "Member not found").build());
            return;
        }
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        if (body.containsKey("name")) existing.setName(body.get("name"));
        if (body.containsKey("email")) existing.setEmail(body.get("email"));
        if (body.containsKey("phone")) existing.setPhone(body.get("phone"));
        if (body.containsKey("address")) existing.setAddress(body.get("address"));
        if (body.containsKey("status")) existing.setStatus(body.get("status"));

        boolean ok = memberDAO.updateMember(existing);
        sendJson(ex, 200, JsonUtil.object().add("success", ok ? "true" : "false").build());
    }

    private static void deleteMember(HttpExchange ex, int id) throws IOException {
        boolean ok = memberDAO.deleteMember(id);
        sendJson(ex, ok ? 200 : 400, JsonUtil.object().add("success", ok ? "true" : "false").build());
    }

    private static String memberJson(Member m, MemberStanding s) {
        return JsonUtil.object()
                .add("memberId", m.getMemberId())
                .add("name", m.getName())
                .add("email", m.getEmail())
                .add("phone", m.getPhone())
                .add("address", m.getAddress())
                .add("joinDate", m.getJoinDate() != null ? m.getJoinDate().toString() : "")
                .add("status", m.getStatus())
                .add("issuedCount", s.issuedCount())
                .add("overdueCount", s.overdueCount())
                .add("duesOwed", s.duesOwed())
                .build();
    }

    // ============================ ISSUE / RETURN ============================

    private static void issueBook(HttpExchange ex) throws IOException {
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        int bookId = parseIntSafe(body.get("bookId"), -1);
        int memberId = parseIntSafe(body.get("memberId"), -1);

        String result = transactionDAO.issueBook(bookId, memberId);
        boolean ok = result.startsWith("SUCCESS");
        sendJson(ex, ok ? 200 : 400, JsonUtil.object().add("success", ok ? "true" : "false").add("message", result).build());
    }

    private static void returnBook(HttpExchange ex, int transactionId) throws IOException {
        String result = transactionDAO.returnBook(transactionId);
        boolean ok = result.startsWith("SUCCESS");
        sendJson(ex, ok ? 200 : 400, JsonUtil.object().add("success", ok ? "true" : "false").add("message", result).build());
    }

    // ============================ TRANSACTIONS / REPORTS ============================

    private static void getAllTransactions(HttpExchange ex) throws IOException {
        sendJson(ex, 200, transactionsJson(transactionDAO.getAllTransactions()));
    }

    private static void getIssuedTransactions(HttpExchange ex) throws IOException {
        sendJson(ex, 200, transactionsJson(transactionDAO.getIssuedTransactions()));
    }

    private static void getOverdueTransactions(HttpExchange ex) throws IOException {
        List<Transaction> overdue = transactionDAO.getOverdueTransactions();
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < overdue.size(); i++) {
            if (i > 0) arr.append(",");
            Transaction t = overdue.get(i);
            double projected = transactionDAO.getProjectedFine(t.getTransactionId());
            arr.append(JsonUtil.object()
                    .add("transactionId", t.getTransactionId())
                    .add("bookTitle", t.getBookTitle())
                    .add("memberName", t.getMemberName())
                    .add("issueDate", t.getIssueDate().toString())
                    .add("dueDate", t.getDueDate().toString())
                    .add("status", t.getStatus())
                    .add("projectedFine", projected)
                    .build());
        }
        arr.append("]");
        sendJson(ex, 200, arr.toString());
    }

    private static void getDashboardStats(HttpExchange ex) throws IOException {
        int totalBooks = bookDAO.getAllBooks().size();
        int totalMembers = memberDAO.getAllMembers().size();
        int issuedCount = transactionDAO.getIssuedTransactions().size();
        int overdueCount = transactionDAO.getOverdueTransactions().size();

        String json = JsonUtil.object()
                .add("totalBooks", totalBooks)
                .add("totalMembers", totalMembers)
                .add("issuedCount", issuedCount)
                .add("overdueCount", overdueCount)
                .build();
        sendJson(ex, 200, json);
    }

    private static void getMemberHistory(HttpExchange ex, int memberId) throws IOException {
        List<Transaction> history = transactionDAO.getTransactionsByMember(memberId);
        sendJson(ex, 200, transactionsJson(history));
    }

    private static void getReports(HttpExchange ex) throws IOException {
        List<Map.Entry<String, Integer>> categories = bookDAO.getCategoryBreakdown();
        List<Map.Entry<String, Integer>> topBooks = transactionDAO.getTopBorrowedBooks(5);
        double collected = transactionDAO.getTotalFinesCollected();
        double outstanding = transactionDAO.getTotalOutstandingFines();

        StringBuilder catArr = new StringBuilder("[");
        for (int i = 0; i < categories.size(); i++) {
            if (i > 0) catArr.append(",");
            Map.Entry<String, Integer> e = categories.get(i);
            catArr.append(JsonUtil.object().add("category", e.getKey()).add("count", e.getValue()).build());
        }
        catArr.append("]");

        StringBuilder topArr = new StringBuilder("[");
        for (int i = 0; i < topBooks.size(); i++) {
            if (i > 0) topArr.append(",");
            Map.Entry<String, Integer> e = topBooks.get(i);
            topArr.append(JsonUtil.object().add("title", e.getKey()).add("count", e.getValue()).build());
        }
        topArr.append("]");

        String json = JsonUtil.object()
                .addRaw("categoryBreakdown", catArr.toString())
                .addRaw("topBooks", topArr.toString())
                .add("finesCollected", collected)
                .add("finesOutstanding", outstanding)
                .build();
        sendJson(ex, 200, json);
    }

    private static String transactionsJson(List<Transaction> list) {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) arr.append(",");
            Transaction t = list.get(i);
            arr.append(JsonUtil.object()
                    .add("transactionId", t.getTransactionId())
                    .add("bookTitle", t.getBookTitle())
                    .add("memberName", t.getMemberName())
                    .add("issueDate", t.getIssueDate().toString())
                    .add("dueDate", t.getDueDate().toString())
                    .add("returnDate", t.getReturnDate() != null ? t.getReturnDate().toString() : "")
                    .add("fineAmount", t.getFineAmount())
                    .add("status", t.getStatus())
                    .build());
        }
        arr.append("]");
        return arr.toString();
    }

    // ============================ HELPERS ============================

    private static String queryParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static int parseIntSafe(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void methodNotAllowed(HttpExchange ex) throws IOException {
        sendJson(ex, 405, JsonUtil.object().add("error", "Method not allowed").build());
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        send(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
