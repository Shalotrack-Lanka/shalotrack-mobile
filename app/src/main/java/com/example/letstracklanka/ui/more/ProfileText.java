package com.example.letstracklanka.ui.more;

import androidx.annotation.Nullable;

/** Display helpers for the account card. */
public final class ProfileText {

    private ProfileText() {}

    /** "NP" from "Nuwan Perera", "N" from "Nuwan", "?" when there is no name. */
    public static String initials(@Nullable String name) {
        if (name == null) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            int cp = p.codePointAt(0);
            if (Character.isLetterOrDigit(cp)) sb.appendCodePoint(Character.toUpperCase(cp));
            if (sb.length() >= 2) break;
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    /** "+94 77 123 4567" for a Sri Lankan mobile in E.164; anything else unchanged. */
    public static String phone(@Nullable String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("[\\s-]", "");
        if (s.matches("\\+94\\d{9}")) {
            return s.substring(0, 3) + " " + s.substring(3, 5) + " " + s.substring(5, 8) + " " + s.substring(8);
        }
        if (s.matches("0\\d{9}")) {
            return s.substring(0, 3) + " " + s.substring(3, 6) + " " + s.substring(6);
        }
        return raw.trim();
    }
}
