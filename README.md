# Stacks — Library Management System

A full library management web app: **Java (JDBC) backend, embedded SQLite
database, plain HTML/CSS/JS frontend** — no framework, no build step, and
nothing external to install or configure. Real accounts (sign up / log in),
a dashboard, and dedicated pages for books, members, issuing/returning,
overdue fines, transactions, reports, and profile settings.

## Why this version is different

Earlier versions of this project needed a separately installed MySQL
server, a manually-run SQL script, and exact-matching credentials — any one
of those being off broke the whole app. This version removes all of that:

- **No database server to install or start.** The app uses an embedded
  SQLite database — just a single file, `database/library.db`.
- **No SQL script to run by hand.** The first time you start the app, it
  creates the schema and sample data itself, including a working demo
  login. You'll see `First run detected...` in the console the first time.
- **The JDBC driver is already in `lib/`.** Nothing to download.
- **The server finds its own files automatically**, no matter which folder
  you launch `java` from.
- **Port defaults to 8090** and is configurable; if it's already taken you
  get a clear message instead of a crash.

In short: `javac`, then `java`, then open the browser. That's it.

## Tech Stack

- Java 17+ (works on 21)
- Embedded SQLite (via the bundled `sqlite-jdbc` driver — zero setup)
- Backend web layer: the JDK's built-in `com.sun.net.httpserver.HttpServer`
  — no Spring/Tomcat needed, just the JDK
- Frontend: plain HTML/CSS/JS, hash-based client-side routing, no build step
- DAO pattern — the web UI and the console app share the same
  `BookDAO` / `MemberDAO` / `TransactionDAO` / `UserDAO` classes

## Project Structure

```
LibraryManagementSystem/
├── database/
│   ├── library_db.sql          # Reference schema (for docs/manual resets — not required to run)
│   └── library.db              # Created automatically on first run
├── src/
│   ├── Book.java, Member.java, Transaction.java, User.java   # Models
│   ├── BookDAO.java, MemberDAO.java, TransactionDAO.java     # Data access
│   ├── UserDAO.java             # Account creation / lookup for auth
│   ├── PasswordUtil.java        # Salted SHA-256 password hashing
│   ├── DBConnection.java        # Opens/creates the SQLite database file
│   ├── SchemaInitializer.java   # Creates tables + demo data on first run
│   ├── SqlDates.java            # Small helper for reading DATE columns safely
│   ├── JsonUtil.java            # Tiny dependency-free JSON reader/writer
│   ├── Main.java                # Console UI (menu-driven, optional)
│   └── ApiServer.java           # REST API + static file server + sessions
├── web/                         # Browser front end served by ApiServer
│   ├── index.html
│   ├── style.css
│   └── app.js
├── lib/
│   └── sqlite-jdbc-3.53.2.1.jar # Already included — nothing to download
└── README.md
```

## Setup & Run

From the project root:

```bash
cd src
javac -d ../out *.java
cd ../out

# macOS/Linux:
java -cp .:../lib/sqlite-jdbc-3.53.2.1.jar ApiServer

# Windows:
java -cp ".;../lib/sqlite-jdbc-3.53.2.1.jar" ApiServer
```

You should see:

```
Serving static files from: /path/to/LibraryManagementSystem/out/../web
Library Management System running at http://localhost:8090
First run detected — creating the database schema and demo data...
Database ready. Demo login -> username: admin, password: admin123
```

("First run detected..." only appears the very first time — after that the
database file already exists and it's skipped.)

Open **http://localhost:8090** in your browser. Log in with `admin` /
`admin123`, or sign up your own account.

### Using a different port

```bash
java -cp .:../lib/sqlite-jdbc-3.53.2.1.jar ApiServer 8095
```

If the port you pick is already in use, the app tells you clearly instead
of crashing — just try another one.

### Resetting your data

Delete `database/library.db` and restart the app — it will recreate a fresh
database with the demo account and sample books/members, exactly like a
first run.

## Running in VS Code

1. Install the **Extension Pack for Java** (Microsoft) from the Extensions
   panel, and make sure a JDK 17+ is installed.
2. File → Open Folder → select this project folder.
3. Open the built-in terminal (`` Ctrl+` ``) and run the compile/run commands
   from **Setup & Run** above. The Java extension's own "Run" button often
   doesn't pick up the `-cp` classpath entry for the driver jar correctly,
   so the terminal is the reliable way to launch it.

## Troubleshooting

- **"Port already in use"** — pick a different port (see above).
- **"SQLite JDBC driver not found"** — double-check the `-cp` path matches
  where `sqlite-jdbc-3.53.2.1.jar` actually is relative to where you're
  running the command from.
- **"Incorrect username or password" for admin/admin123`** — this means the
  request reached the server but no matching account was found. Delete
  `database/library.db` (if it exists) and restart the app so it can create
  a fresh one with the demo account.
- **Blank/broken page** — hard refresh (Ctrl+Shift+R) to clear any cached
  version of an older frontend.

## Features

- **Real accounts** — sign up, log in, log out, edit your profile, change
  your password. Sessions are cookie-based and stored server-side.
- **Books** — searchable catalog, add via a 3-step wizard, edit, delete,
  live available/total copy tracking with a progress bar.
- **Members** — list, add via a 2-step wizard, edit, delete, and view each
  member's full borrowing history on their own page.
- **Issue & Return** — issue any available book to any member (14-day due
  date), and return books from the same page.
- **Overdue & Fines** — every overdue loan with a live projected fine
  (₹5/day), one click to mark returned.
- **Transactions** — the full historical ledger of every loan.
- **Reports** — books-by-category bar chart, most-borrowed titles, and
  running fines collected vs. outstanding.
- **Dashboard** — the first thing you see after logging in: live stats plus
  a shortlist of overdue loans.
- All DB writes use **PreparedStatements** and commit/rollback transactions
  so copy counts and transaction records never fall out of sync.
