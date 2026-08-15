import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * rs.getDate() delegates to the JDBC driver's own date parsing, and
 * different drivers are stricter than others about the exact text format
 * stored in the column. SQLite in particular can end up with a DATE column
 * holding either ISO text ('yyyy-MM-dd', e.g. from CURRENT_DATE defaults or
 * plain SQL inserts) or an epoch-millis integer (some drivers store
 * PreparedStatement.setDate() values that way). Reading the raw Object and
 * handling both shapes ourselves avoids depending on driver-specific
 * parsing rules.
 */
public class SqlDates {

    private SqlDates() {
    }

    public static Date getDate(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);
        if (raw == null) return null;

        if (raw instanceof Date d) {
            return d;
        }
        if (raw instanceof java.util.Date d) {
            return new Date(d.getTime());
        }
        if (raw instanceof Number n) {
            return new Date(n.longValue());
        }

        String text = raw.toString().trim();
        if (text.isEmpty()) return null;
        // Some values may include a time portion; keep just the date part.
        String datePart = text.length() >= 10 ? text.substring(0, 10) : text;
        try {
            return Date.valueOf(datePart);
        } catch (IllegalArgumentException e) {
            // Last resort: maybe it actually was a numeric epoch stored as text.
            try {
                return new Date(Long.parseLong(text));
            } catch (NumberFormatException nfe) {
                throw new SQLException("Could not parse date value '" + text + "' from column " + column, e);
            }
        }
    }
}
