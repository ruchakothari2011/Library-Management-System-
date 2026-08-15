import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small dependency-free JSON helper. Handles the flat (non-nested)
 * request/response shapes this app needs, so we don't require Jackson/Gson.
 */
public class JsonUtil {

    // ---------- Writing ----------

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public static class ObjectBuilder {
        private final StringBuilder sb = new StringBuilder("{");
        private boolean first = true;

        private void sep() {
            if (!first) sb.append(",");
            first = false;
        }

        public ObjectBuilder add(String key, String value) {
            sep();
            sb.append("\"").append(escape(key)).append("\":");
            sb.append(value == null ? "null" : "\"" + escape(value) + "\"");
            return this;
        }

        public ObjectBuilder add(String key, int value) {
            sep();
            sb.append("\"").append(escape(key)).append("\":").append(value);
            return this;
        }

        public ObjectBuilder add(String key, double value) {
            sep();
            sb.append("\"").append(escape(key)).append("\":").append(value);
            return this;
        }

        public ObjectBuilder addRaw(String key, String rawJson) {
            sep();
            sb.append("\"").append(escape(key)).append("\":").append(rawJson);
            return this;
        }

        public String build() {
            return sb.append("}").toString();
        }
    }

    public static ObjectBuilder object() {
        return new ObjectBuilder();
    }

    // ---------- Parsing (flat objects only) ----------

    /** Parses a flat JSON object like {"title":"Foo","copies":3} into a String map. */
    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null) return map;
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && (s.charAt(i) == ' ' || s.charAt(i) == ',' || s.charAt(i) == '\n' || s.charAt(i) == '\r' || s.charAt(i) == '\t')) i++;
            if (i >= n) break;

            // key
            if (s.charAt(i) != '"') break;
            i++;
            StringBuilder key = new StringBuilder();
            while (i < n && s.charAt(i) != '"') {
                key.append(s.charAt(i));
                i++;
            }
            i++; // closing quote

            while (i < n && (s.charAt(i) == ' ' || s.charAt(i) == ':')) i++;

            // value
            StringBuilder val = new StringBuilder();
            if (i < n && s.charAt(i) == '"') {
                i++;
                while (i < n && s.charAt(i) != '"') {
                    char c = s.charAt(i);
                    if (c == '\\' && i + 1 < n) {
                        i++;
                        char esc = s.charAt(i);
                        val.append(switch (esc) {
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            default -> esc;
                        });
                    } else {
                        val.append(c);
                    }
                    i++;
                }
                i++; // closing quote
            } else {
                while (i < n && s.charAt(i) != ',' && s.charAt(i) != '}') {
                    val.append(s.charAt(i));
                    i++;
                }
            }
            map.put(key.toString(), val.toString().trim());
        }
        return map;
    }
}
