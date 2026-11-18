package com.example.letstracklanka.ui.common;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import androidx.core.content.ContextCompat;

import com.example.letstracklanka.R;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats a Sri Lankan number plate for the plate chip (Widget.ST.Plate): the province code small and
 * grey, the number bold. "wp cab-1234", "WP-CAB-1234" and "WP CAB 1234" all become "WP CAB-1234".
 * Anything that does not start with a province code is shown upper-cased as typed.
 */
public final class Plates {

    private static final Pattern WITH_PROVINCE = Pattern.compile(
            "^\\s*(WP|CP|SP|NP|EP|NW|NC|UP|SG)[\\s\\-]+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LETTERS_DIGITS = Pattern.compile("^([A-Z]{1,3})[\\s\\-]?(\\d{1,4})$");

    private Plates() {}

    public static CharSequence format(Context context, String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String upper = raw.trim().toUpperCase(Locale.US);
        Matcher m = WITH_PROVINCE.matcher(upper);
        if (!m.matches()) return upper;

        String province = m.group(1);
        String number = tidyNumber(m.group(2));
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(province);
        int end = sb.length();
        sb.setSpan(new RelativeSizeSpan(0.62f), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.st_text_2)), 0, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(' ').append(number);
        return sb;
    }

    /** Plain text for accessibility and sharing: "WP CAB-1234". */
    public static String plain(String raw) {
        if (raw == null) return "";
        String upper = raw.trim().toUpperCase(Locale.US);
        Matcher m = WITH_PROVINCE.matcher(upper);
        return m.matches() ? m.group(1) + " " + tidyNumber(m.group(2)) : upper;
    }

    /** "CAB 1234" -> "CAB-1234"; "KX0091" -> "KX-0091". Leaves anything unusual as it is. */
    private static String tidyNumber(String n) {
        String s = n.trim().replaceAll("\\s+", " ");
        Matcher letterDigit = LETTERS_DIGITS.matcher(s);
        if (letterDigit.matches()) return letterDigit.group(1) + "-" + letterDigit.group(2);
        return s;
    }
}
