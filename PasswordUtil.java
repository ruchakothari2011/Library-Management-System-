import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Minimal dependency-free password hashing (salted SHA-256). Not as strong
 * as bcrypt/argon2, but a genuine improvement over storing plaintext, and
 * needs no third-party library. Stored format: "<saltHex>:<hashHex>".
 */
public class PasswordUtil {

    private static final SecureRandom RNG = new SecureRandom();

    public static String hash(String plainPassword) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt);
        String hashHex = sha256Hex(saltHex + plainPassword);
        return saltHex + ":" + hashHex;
    }

    public static boolean verify(String plainPassword, String stored) {
        if (stored == null || !stored.contains(":")) return false;
        String[] parts = stored.split(":", 2);
        String saltHex = parts[0];
        String expectedHash = parts[1];
        String actualHash = sha256Hex(saltHex + plainPassword);
        return constantTimeEquals(actualHash, expectedHash);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
