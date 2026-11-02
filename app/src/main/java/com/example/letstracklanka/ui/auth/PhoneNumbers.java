package com.example.letstracklanka.ui.auth;

import androidx.annotation.Nullable;

/**
 * Sri Lankan mobile numbers only, the same rule as the API: 9 digits after the country code, starting
 * with 7. Accepts "0771234567", "771234567", "94771234567" and "+94771234567" (spaces and dashes ignored).
 * The server normalises again; this only gives the customer an instant, friendly answer.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    /** "+94771234567", or null when the input is not a valid Sri Lankan mobile number. */
    @Nullable
    public static String toE164(@Nullable String raw) {
        if (raw == null) return null;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
        }
        String d = digits.toString();

        String national;   // 9 digits, no leading 0 / 94
        if (d.length() == 10 && d.startsWith("0")) {
            national = d.substring(1);
        } else if (d.length() == 11 && d.startsWith("94")) {
            national = d.substring(2);
        } else if (d.length() == 9) {
            national = d;
        } else {
            return null;
        }
        return national.charAt(0) == '7' ? "+94" + national : null;
    }
}