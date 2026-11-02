package com.example.letstracklanka.utils;

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ONE place for the rules every typed-in value must pass before it leaves the phone.
 *
 * Why this exists: the API validates again (it must, the phone is untrusted), but checking here gives the
 * customer an instant answer and keeps junk, oversized text and hidden control characters out of requests.
 * The same text is later shown in the Fleet portal and the Admin portal, so values that can carry markup
 * or invisible characters are rejected at the first door instead of being trusted downstream.
 *
 * Every check method returns null when the value is fine, or a short customer-facing message.
 * Always send {@link #clean(String)} of the value, never the raw text.
 */
public final class InputValidator {

    private InputValidator() {}

    /** Own pattern (not android.util.Patterns) so it behaves identically in unit tests. */
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,24}$");
    private static final Pattern NIC = Pattern.compile("^([0-9]{9}[VX]|[0-9]{12})$");
    /** \p{M} (combining marks) is required: Sinhala and Tamil vowel signs are marks, not letters. */
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M}\\p{N} .,'&()/\\-]{1,79}$");
    /** Sri Lankan plates: letters, digits, one dash or space groups, e.g. CAB-1234, 250-1234, WP CAB 1234. */
    private static final Pattern PLATE = Pattern.compile("^[A-Z0-9][A-Z0-9 \\-]{1,13}[A-Z0-9]$");
    private static final Pattern IMEI = Pattern.compile("^[0-9]{15}$");
    private static final Pattern SL_PHONE = Pattern.compile("^(\\+94|0)?7[0-9]{8}$");
    /** An HTML/script tag or comment. Plain "<" in "a < b" stays allowed in long free text. */
    private static final Pattern MARKUP = Pattern.compile("(?i)</?[a-z!][^>]*>|javascript:|data:text/html|on[a-z]{3,}\\s*=");
    /**
     * Control characters (except new line) and the invisible characters used to disguise text: zero-width
     * space, bidirectional overrides and isolates ("Trojan Source"), word joiner, BOM, line/paragraph
     * separators. U+200C and U+200D (joiners) are deliberately KEPT: Sinhala conjuncts (e.g. the "kra" in
     * Sinhala) are written with them, so removing them would corrupt customers' names.
     */
    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\p{Cc}&&[^\\n]]|[\\u200B\\u200E\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF\\u2028\\u2029]");

    public static final int MAX_NAME = 80;
    public static final int MAX_ADDRESS = 200;
    public static final int MAX_EMAIL = 150;
    public static final int MAX_SHORT = 100;
    public static final int MAX_MESSAGE = 1000;

    // ---- cleaning ---------------------------------------------------------------------------------

    /**
     * Normalised copy that is safe to send: trimmed, Unicode-normalised (NFC), invisible and control
     * characters removed (new lines and tabs kept), runs of spaces collapsed. Never null.
     */
    public static String clean(@Nullable String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC);
        s = s.replace('\t', ' ');
        s = INVISIBLE.matcher(s).replaceAll("");
        s = s.replaceAll("[ ]{2,}", " ");
        return s.trim();
    }

    /** Single-line variant: also turns new lines into spaces. */
    public static String cleanLine(@Nullable String raw) {
        return clean(raw).replaceAll("\\s+", " ");
    }

    // ---- checks (null = valid) --------------------------------------------------------------------

    @Nullable
    public static String name(@Nullable String raw) {
        String s = cleanLine(raw);
        if (s.length() < 2) return "Enter your full name";
        if (s.length() > MAX_NAME) return "Name is too long";
        if (!NAME.matcher(s).matches()) return "Use letters, numbers and . , ' & ( ) / - only";
        return null;
    }

    @Nullable
    public static String nic(@Nullable String raw) {
        return NIC.matcher(canonicalNic(raw)).matches()
                ? null : "Enter a valid NIC, for example 861234567V or 198612345678";
    }

    /** NIC with no spaces and upper-case V/X, so duplicates cannot be dodged by spacing or case. */
    public static String canonicalNic(@Nullable String raw) {
        return cleanLine(raw).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    @Nullable
    public static String email(@Nullable String raw) {
        String s = cleanLine(raw);
        if (s.isEmpty() || s.length() > MAX_EMAIL || s.contains(" ")
                || !EMAIL.matcher(s).matches()) {
            return "Enter a valid email address";
        }
        return null;
    }

    @Nullable
    public static String address(@Nullable String raw) {
        String s = cleanLine(raw);
        if (s.length() < 5) return "Enter your home address";
        if (s.length() > MAX_ADDRESS) return "Address is too long";
        if (hasMarkup(s)) return "Address contains characters that are not allowed";
        return null;
    }

    /** Short label such as a place name, circle name or relationship. */
    @Nullable
    public static String label(@Nullable String raw, int minLen, int maxLen, String fieldName) {
        String s = cleanLine(raw);
        if (s.length() < minLen) return "Enter " + fieldName;
        if (s.length() > maxLen) return capitalise(fieldName) + " is too long (max " + maxLen + ")";
        if (s.indexOf('<') >= 0 || s.indexOf('>') >= 0 || hasMarkup(s)) {
            return capitalise(fieldName) + " contains characters that are not allowed";
        }
        return null;
    }

    @Nullable
    public static String plate(@Nullable String raw) {
        String s = cleanLine(raw).toUpperCase(Locale.ROOT);
        return PLATE.matcher(s).matches() ? null : "Enter a valid vehicle number, for example CAB-1234";
    }

    @Nullable
    public static String imei(@Nullable String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        return IMEI.matcher(digits).matches() && luhn(digits) ? null : "Enter the 15-digit IMEI";
    }

    @Nullable
    public static String phone(@Nullable String raw) {
        String s = cleanLine(raw).replaceAll("[ \\-]", "");
        return SL_PHONE.matcher(s).matches() ? null : "Enter a valid mobile number, for example 0771234567";
    }

    /** Emergency contacts may be landlines too: 9 digits after the 0 / +94 prefix. */
    @Nullable
    public static String phoneAny(@Nullable String raw) {
        String s = cleanLine(raw).replaceAll("[ \\-]", "");
        return s.matches("^(\\+94|0)?[1-9][0-9]{8}$") ? null : "Enter a valid phone number, for example 0771234567";
    }

    /** +94 form of a number already accepted by {@link #phoneAny}. */
    public static String toE164Any(String raw) {
        String s = cleanLine(raw).replaceAll("[ \\-]", "");
        if (s.startsWith("+94")) return s;
        if (s.startsWith("0")) return "+94" + s.substring(1);
        return "+94" + s;
    }

    /** Long free text (complaint description, reply). Markup is rejected, size is capped. */
    @Nullable
    public static String message(@Nullable String raw, int minLen) {
        String s = clean(raw);
        if (s.length() < minLen) return "Write at least " + minLen + " characters";
        if (s.length() > MAX_MESSAGE) return "Too long (max " + MAX_MESSAGE + " characters)";
        if (hasMarkup(s)) return "Remove web addresses or code from your message (tags are not allowed)";
        return null;
    }

    @Nullable
    public static String inRange(double value, double min, double max, String fieldName) {
        if (Double.isNaN(value) || value < min || value > max) {
            return capitalise(fieldName) + " must be between " + trim(min) + " and " + trim(max);
        }
        return null;
    }

    @Nullable
    public static String otp(@Nullable String raw) {
        return raw != null && raw.matches("^[0-9]{6}$") ? null : "Enter all 6 digits.";
    }

    private static final String[] COMMON_PASSWORDS = {
            "password", "password1", "passw0rd", "12345678", "123456789", "1234567890", "qwerty123",
            "qwertyuiop", "abc12345", "iloveyou1", "welcome1", "admin123", "letmein1", "shalotrack1"};

    /**
     * Password for the optional email + password sign-in. Same base rule as the Fleet portal (8+ characters,
     * a letter and a number), plus: capped at 128 characters (an unbounded password is a cheap way to burn
     * the server's hashing time), not the email address, and not one of the passwords attackers try first.
     */
    @Nullable
    public static String password(@Nullable String pw, @Nullable String email) {
        if (pw == null || pw.length() < 8) return "Use at least 8 characters.";
        if (pw.length() > 128) return "Use 128 characters or fewer.";
        if (!pw.matches(".*\\p{L}.*") || !pw.matches(".*\\d.*")) return "Include at least one letter and one number.";
        String lower = pw.toLowerCase(Locale.ROOT);
        for (String bad : COMMON_PASSWORDS) {
            if (lower.equals(bad)) return "That password is too common. Choose something harder to guess.";
        }
        if (email != null && !email.isEmpty()) {
            String e = email.toLowerCase(Locale.ROOT);
            String local = e.contains("@") ? e.substring(0, e.indexOf('@')) : e;
            if (lower.equals(e) || (local.length() >= 4 && lower.equals(local))) {
                return "Your password can't be your email address.";
            }
        }
        return null;
    }

    // ---- helpers ----------------------------------------------------------------------------------

    public static boolean hasMarkup(String s) {
        return MARKUP.matcher(s).find();
    }

    /** Luhn checksum: every real IMEI passes, a mistyped one almost never does. */
    static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
