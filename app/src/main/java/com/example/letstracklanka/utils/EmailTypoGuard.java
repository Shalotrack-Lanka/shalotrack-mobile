package com.example.letstracklanka.utils;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Catches typos in the DOMAIN of an email (gamil.com, gmial.com, hotmial.com, yaho.com). Same rules as the
 * Fleet portal. The email is not verified, so a typo would be stored as typed. This only SUGGESTS: the
 * customer can keep what they typed, because a real but unusual domain must never be blocked.
 */
public final class EmailTypoGuard {

    private static final List<String> COMMON = Arrays.asList(
            "gmail.com", "googlemail.com", "yahoo.com", "ymail.com", "hotmail.com", "outlook.com",
            "live.com", "msn.com", "icloud.com", "protonmail.com", "sltnet.lk");

    /** Real providers that sit close to a common one (mail.com is 1 edit from gmail.com): never "correct" these. */
    private static final List<String> KNOWN_GOOD = Arrays.asList(
            "mail.com", "aol.com", "gmx.com", "gmx.net", "zoho.com", "proton.me", "pm.me", "yandex.com",
            "fastmail.com", "hey.com", "me.com", "mac.com", "slt.lk", "dialog.lk", "mobitel.lk");

    private EmailTypoGuard() {}

    /** The corrected address ("a@gmail.com"), or null when the domain looks fine. */
    @Nullable
    public static String suggest(@Nullable String email) {
        if (email == null) return null;
        String trimmed = email.trim();
        int at = trimmed.lastIndexOf('@');
        if (at <= 0) return null;   // no '@', or nothing before it

        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
        if (domain.isEmpty() || COMMON.contains(domain) || KNOWN_GOOD.contains(domain)) return null;

        String best = null;
        int bestDistance = 3;
        for (String candidate : COMMON) {
            int d = levenshtein(domain, candidate);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }

        // 1 edit always counts; 2 edits only for longer domains (avoids flagging short unrelated names).
        if (best == null || (bestDistance == 2 && domain.length() < 9)) return null;
        return local + "@" + best;
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }
}