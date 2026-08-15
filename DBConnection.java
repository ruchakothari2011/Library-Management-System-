import java.io.File;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Centralized JDBC connection handler.
 *
 * This app uses an embedded SQLite database — there is nothing to install,
 * start, or log into. The database lives in a single file,
 * database/library.db, next to this project. The very first time the app
 * runs (or any time that file is missing), the schema and sample data
 * (including the demo "admin" / "admin123" account) are created
 * automatically — no manual SQL script to run.
 *
 * To point at a different file, set DB_FILE before running the app, e.g:
 *   DB_FILE=/tmp/my-library.db
 */
public class DBConnection {

    private static final String DB_FILE_PATH = firstNonBlank(System.getenv("DB_FILE"), resolveDefaultDbFile());
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE_PATH;

    private static Connection connection = null;
    private static boolean initialized = false;

    private DBConnection() {
        // utility class; no instances
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not found. " +
                        "Make sure lib/sqlite-jdbc-*.jar is on the classpath " +
                        "(see the README run command).", e);
            }
            try {
                connection = DriverManager.getConnection(DB_URL);
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA foreign_keys = ON");
                }
            } catch (SQLException e) {
                throw new SQLException("Could not open the database file at " + DB_FILE_PATH + ". " +
                        "Make sure the process has permission to create/read files in that folder.", e);
            }
        }
        if (!initialized) {
            SchemaInitializer.ensureSchema(connection);
            initialized = true;
        }
        return connection;
    }

    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * Places the database file at <project-root>/database/library.db,
     * regardless of the directory `java` was launched from — same trick
     * ApiServer uses to find web/, so `library.db` doesn't end up scattered
     * across whatever folder you happened to run the app from.
     */
    private static String resolveDefaultDbFile() {
        File classesDir = null;
        try {
            File codeSource = new File(DBConnection.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            classesDir = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
        } catch (URISyntaxException | NullPointerException ignored) {
            // fall through
        }

        File dbDir;
        if (classesDir != null && new File(classesDir, "../database").isDirectory()) {
            dbDir = new File(classesDir, "../database");
        } else if (new File("database").isDirectory()) {
            dbDir = new File("database");
        } else if (new File("../database").isDirectory()) {
            dbDir = new File("../database");
        } else {
            // Nothing found yet (very first run before database/ exists relative
            // to cwd) -- default to a database/ folder next to the classes dir,
            // creating it if needed.
            dbDir = classesDir != null ? new File(classesDir, "../database") : new File("database");
            dbDir.mkdirs();
        }
        return new File(dbDir, "library.db").getPath();
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
