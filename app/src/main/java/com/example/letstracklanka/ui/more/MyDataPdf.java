package com.example.letstracklanka.ui.more;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;

import com.example.letstracklanka.R;
import com.example.letstracklanka.utils.ApiJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Download my data" as a readable PDF, built on the phone with the platform's PdfDocument (no library, no
 * server work). Same sections, columns and wording as Fleet's exports/my-data.blade.php, and the same date
 * rule as Fleet's AccountController::formatDate, so the two PDFs always agree.
 *
 * The PDF is written straight to the stream the customer chose; nothing is kept in the app's storage.
 * Call {@link #write} off the main thread (text layout for a few hundred rows is real work).
 */
public final class MyDataPdf {

    /** Alerts shown in the PDF; the JSON has them all (Fleet: PDF_ALERT_LIMIT). */
    static final int ALERT_LIMIT = 200;

    // A4 in PDF points (1/72 inch).
    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final float MARGIN = 36f;
    private static final float CONTENT_W = PAGE_W - 2 * MARGIN;
    private static final float FOOTER_H = 22f;
    private static final float BOTTOM = PAGE_H - MARGIN - FOOTER_H;
    private static final float CELL_PAD_X = 5f;
    private static final float CELL_PAD_Y = 4f;

    // Fleet's PDF palette.
    private static final int NAVY = Color.parseColor("#021F4A");
    private static final int ORANGE = Color.parseColor("#FA6908");
    private static final int TEXT = Color.parseColor("#1E293B");
    private static final int MUTED = Color.parseColor("#94A3B8");
    private static final int LABEL = Color.parseColor("#64748B");
    private static final int REPLY = Color.parseColor("#475569");
    private static final int RULE = Color.parseColor("#EEF2F7");
    private static final int BORDER = Color.parseColor("#E2E8F0");
    private static final int ALT_ROW = Color.parseColor("#F8FAFC");
    private static final int NOTE_BG = Color.parseColor("#FFFBEB");
    private static final int NOTE_BORDER = Color.parseColor("#FDE68A");
    private static final int NOTE_TEXT = Color.parseColor("#92400E");

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final Context c;
    private final JsonObject d;
    private final String generatedAt;
    private final TextPaint body, bodyBold, small, head, h2, brand, title;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private PdfDocument doc;
    private PdfDocument.Page page;
    private Canvas canvas;
    private float y;
    private int pageNo;

    private MyDataPdf(Context context, JsonObject data) {
        c = context.getApplicationContext();
        d = data;
        generatedAt = ZonedDateTime.now(COLOMBO).format(DATE_TIME);

        Typeface regular = font(R.font.inter_regular, Typeface.DEFAULT);
        Typeface semibold = font(R.font.inter_semibold, Typeface.DEFAULT_BOLD);
        Typeface brandFace = font(R.font.archivo_extrabold, Typeface.DEFAULT_BOLD);
        body = paint(regular, 8f, TEXT);
        bodyBold = paint(semibold, 8.5f, NAVY);
        small = paint(regular, 6.5f, MUTED);
        head = paint(semibold, 6f, Color.WHITE);
        head.setLetterSpacing(0.04f);
        h2 = paint(semibold, 7f, LABEL);
        h2.setLetterSpacing(0.08f);
        brand = paint(brandFace, 18f, NAVY);
        title = paint(semibold, 11f, NAVY);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(0.75f);
    }

    /** Builds the whole PDF and writes it to {@code out}. The caller owns (and closes) the stream. */
    public static void write(@NonNull Context context, @NonNull JsonObject data, @NonNull OutputStream out) throws IOException {
        new MyDataPdf(context, data).render(out);
    }

    // ---------------------------------------------------------------------------------------------
    // Document
    // ---------------------------------------------------------------------------------------------

    private void render(OutputStream out) throws IOException {
        doc = new PdfDocument();
        try {
            newPage();
            header();
            profile();
            vehicles();
            reminders();
            places();
            geofences();
            contacts();
            complaints();
            renewals();
            shares(R.string.st_pdf_shares_given, "vehicleSharesGiven", R.string.st_pdf_col_shared_with);
            shares(R.string.st_pdf_shares_received, "vehicleSharesReceived", R.string.st_pdf_col_owner);
            pushDevices();
            alerts();
            finishPage();
            doc.writeTo(out);
        } finally {
            doc.close();
        }
    }

    private void header() {
        // "ShaloTrack" with the orange "Track", like the portal's PDF.
        SpannableStringBuilder name = new SpannableStringBuilder("ShaloTrack");
        name.setSpan(new ForegroundColorSpan(ORANGE), 5, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        StaticLayout brandL = layout(name, brand, CONTENT_W * 0.45f, Layout.Alignment.ALIGN_NORMAL);
        StaticLayout subL = layout(s(R.string.st_pdf_brand_sub).toUpperCase(Locale.ENGLISH), small, CONTENT_W * 0.45f,
                Layout.Alignment.ALIGN_NORMAL);
        StaticLayout titleL = layout(s(R.string.st_pdf_title), title, CONTENT_W * 0.55f, Layout.Alignment.ALIGN_OPPOSITE);
        StaticLayout dateL = layout(c.getString(R.string.st_pdf_generated, generatedAt), small, CONTENT_W * 0.55f,
                Layout.Alignment.ALIGN_OPPOSITE);

        draw(brandL, MARGIN, y);
        draw(subL, MARGIN, y + brandL.getHeight());
        float right = MARGIN + CONTENT_W * 0.45f;
        draw(titleL, right, y + 3);
        draw(dateL, right, y + 3 + titleL.getHeight() + 3);
        y += Math.max(brandL.getHeight() + subL.getHeight(), titleL.getHeight() + dateL.getHeight() + 6) + 6;

        fill.setColor(NAVY);
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_W, y + 2f, fill);
        y += 12;

        StaticLayout note = layout(s(R.string.st_pdf_note), paint(body.getTypeface(), 7.5f, NOTE_TEXT), CONTENT_W - 18,
                Layout.Alignment.ALIGN_NORMAL);
        float h = note.getHeight() + 12;
        RectF box = new RectF(MARGIN, y, MARGIN + CONTENT_W, y + h);
        fill.setColor(NOTE_BG);
        canvas.drawRoundRect(box, 4, 4, fill);
        stroke.setColor(NOTE_BORDER);
        canvas.drawRoundRect(box, 4, 4, stroke);
        draw(note, MARGIN + 9, y + 6);
        y += h + 4;
    }

    private void profile() {
        JsonObject p = obj(d, "profile");
        sectionTitle(s(R.string.st_pdf_profile));
        String[][] rows = {
                {s(R.string.st_pdf_name), str(p, "fullName")},
                {s(R.string.st_pdf_email), str(p, "email")},
                {s(R.string.st_pdf_phone), str(p, "phoneNumber")},
                {s(R.string.st_pdf_nic), str(p, "nicNumber")},
                {s(R.string.st_pdf_address), str(p, "address")},
                {s(R.string.st_pdf_account_status), str(p, "accountStatus")},
                {s(R.string.st_pdf_weekly), yesNo(bool(p, "weeklySummaryEnabled"))},
                {s(R.string.st_pdf_member_since), date(p, "createdAt", false)},
        };
        for (String[] r : rows) {
            StaticLayout k = layout(r[0], paint(body.getTypeface(), 8f, LABEL), CONTENT_W * 0.28f - 2 * CELL_PAD_X,
                    Layout.Alignment.ALIGN_NORMAL);
            StaticLayout v = layout(r[1], body, CONTENT_W * 0.72f - 2 * CELL_PAD_X, Layout.Alignment.ALIGN_NORMAL);
            float h = Math.max(k.getHeight(), v.getHeight()) + 2 * CELL_PAD_Y;
            ensure(h);
            draw(k, MARGIN + CELL_PAD_X, y + CELL_PAD_Y);
            draw(v, MARGIN + CONTENT_W * 0.28f + CELL_PAD_X, y + CELL_PAD_Y);
            y += h;
            rule(y);
        }
    }

    private void vehicles() {
        JsonArray list = arr(d, "vehicles");
        sectionTitle(count(R.string.st_pdf_vehicles, list));
        Table t = new Table(new float[]{14, 20, 26, 12, 11, 17},
                R.string.st_pdf_col_plate, R.string.st_pdf_col_vehicle, R.string.st_pdf_col_details,
                R.string.st_pdf_col_speed_limit, R.string.st_pdf_col_idle_alert, R.string.st_pdf_col_tracker);
        for (JsonObject v : objects(list)) {
            String make = join(" ", raw(v, "make"), raw(v, "model"), yearOf(v));
            SpannableStringBuilder details = new SpannableStringBuilder(orDash(join(" · ", raw(v, "color"),
                    raw(v, "vehicleType"), raw(v, "fuelType"))));
            String chassis = raw(v, "chassisNumber");
            if (chassis != null) muted(details, "\n" + c.getString(R.string.st_pdf_chassis, chassis));
            String engine = raw(v, "engineNumber");
            if (engine != null) muted(details, "\n" + c.getString(R.string.st_pdf_engine, engine));
            Integer idle = integer(v, "idleAlertMinutes");
            String imei = raw(v, "deviceImei");
            SpannableStringBuilder tracker = new SpannableStringBuilder();
            if (imei != null) {
                tracker.append(imei);
                muted(tracker, "\n" + c.getString(R.string.st_pdf_since, date(v, "deviceBoundAt", false)));
            } else {
                muted(tracker, s(R.string.st_pdf_none_short));
            }
            Integer limit = integer(v, "speedLimitKmh");
            t.row(str(v, "vehicleNumber"), orDash(make), details,
                    limit != null ? c.getString(R.string.st_pdf_kmh, limit) : "-",
                    idle != null && idle > 0 ? c.getString(R.string.st_pdf_minutes, idle) : s(R.string.st_pdf_off),
                    tracker);
        }
        t.end();
    }

    private void reminders() {
        JsonArray list = arr(d, "reminders");
        sectionTitle(count(R.string.st_pdf_reminders, list));
        Table t = new Table(new float[]{20, 20, 20, 40},
                R.string.st_pdf_col_vehicle, R.string.st_pdf_col_type, R.string.st_pdf_col_due, R.string.st_pdf_col_notes);
        for (JsonObject r : objects(list)) {
            t.row(str(r, "vehicleNumber"), str(r, "type"), date(r, "dueDate", false), blank(r, "notes"));
        }
        t.end();
    }

    private void places() {
        JsonArray list = arr(d, "savedPlaces");
        sectionTitle(count(R.string.st_pdf_places, list));
        Table t = new Table(new float[]{24, 15, 15, 11, 10, 25},
                R.string.st_pdf_col_name, R.string.st_pdf_col_latitude, R.string.st_pdf_col_longitude,
                R.string.st_pdf_col_radius, R.string.st_pdf_col_visits, R.string.st_pdf_col_last_visit);
        for (JsonObject x : objects(list)) {
            Integer visits = integer(x, "visitCount");
            t.row(str(x, "name"), str(x, "latitude"), str(x, "longitude"), metres(x),
                    String.valueOf(visits != null ? visits : 0), date(x, "lastVisitedAt", true));
        }
        t.end();
    }

    private void geofences() {
        JsonArray list = arr(d, "geofences");
        sectionTitle(count(R.string.st_pdf_geofences, list));
        Table t = new Table(new float[]{20, 15, 14, 14, 10, 15, 12},
                R.string.st_pdf_col_name, R.string.st_pdf_col_vehicle, R.string.st_pdf_col_latitude,
                R.string.st_pdf_col_longitude, R.string.st_pdf_col_radius, R.string.st_pdf_col_alerts,
                R.string.st_pdf_col_active);
        for (JsonObject g : objects(list)) {
            String plate = raw(g, "vehicleNumber");
            String alerts = join(" + ", bool(g, "alertOnEnter") ? s(R.string.st_pdf_enter) : null,
                    bool(g, "alertOnExit") ? s(R.string.st_pdf_exit) : null);
            t.row(str(g, "name"), plate != null ? plate : s(R.string.st_pdf_all), str(g, "latitude"),
                    str(g, "longitude"), metres(g), alerts.isEmpty() ? s(R.string.st_pdf_none_short) : alerts,
                    yesNo(bool(g, "isActive")));
        }
        t.end();
    }

    private void contacts() {
        JsonArray list = arr(d, "emergencyContacts");
        sectionTitle(count(R.string.st_pdf_contacts, list));
        Table t = new Table(new float[]{35, 30, 35},
                R.string.st_pdf_col_name, R.string.st_pdf_col_phone, R.string.st_pdf_col_relationship);
        for (JsonObject x : objects(list)) {
            t.row(str(x, "name"), str(x, "phoneNumber"), blank(x, "relationship"));
        }
        t.end();
    }

    private void complaints() {
        JsonArray list = arr(d, "complaints");
        sectionTitle(count(R.string.st_pdf_complaints, list));
        List<JsonObject> items = objects(list);
        if (items.isEmpty()) {
            empty();
            return;
        }
        float inner = CONTENT_W - 18;
        for (JsonObject cmp : items) {
            List<StaticLayout> parts = new ArrayList<>();
            List<Float> indents = new ArrayList<>();
            parts.add(layout(str(cmp, "vehicleNumber") + " · " + str(cmp, "category") + " · " + str(cmp, "status"),
                    bodyBold, inner, Layout.Alignment.ALIGN_NORMAL));
            indents.add(0f);
            String opened = c.getString(R.string.st_pdf_opened, date(cmp, "createdAt", true));
            String resolved = raw(cmp, "resolvedAt");
            if (resolved != null) opened += " · " + c.getString(R.string.st_pdf_resolved, date(cmp, "resolvedAt", true));
            parts.add(layout(opened, small, inner, Layout.Alignment.ALIGN_NORMAL));
            indents.add(0f);
            parts.add(layout(blank(cmp, "description"), body, inner, Layout.Alignment.ALIGN_NORMAL));
            indents.add(0f);
            for (JsonObject r : objects(arr(cmp, "replies"))) {
                SpannableStringBuilder reply = new SpannableStringBuilder(blank(r, "from"));
                reply.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, reply.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                muted(reply, "  " + date(r, "createdAt", true));
                reply.append('\n').append(blank(r, "message"));
                parts.add(layout(reply, paint(body.getTypeface(), 8f, REPLY), inner - 17, Layout.Alignment.ALIGN_NORMAL));
                indents.add(17f);
            }

            float h = 14;
            for (StaticLayout l : parts) h += l.getHeight() + 3;
            if (h <= BOTTOM - MARGIN) {
                // Normal case: the whole complaint in one bordered box, never split across pages.
                ensure(h);
                RectF box = new RectF(MARGIN, y, MARGIN + CONTENT_W, y + h - 6);
                stroke.setColor(BORDER);
                canvas.drawRoundRect(box, 4, 4, stroke);
                float yy = y + 7;
                for (int i = 0; i < parts.size(); i++) {
                    float ind = indents.get(i);
                    if (ind > 0) {
                        fill.setColor(BORDER);
                        canvas.drawRect(MARGIN + 9 + 10, yy, MARGIN + 9 + 11.5f, yy + parts.get(i).getHeight(), fill);
                    }
                    draw(parts.get(i), MARGIN + 9 + ind, yy);
                    yy += parts.get(i).getHeight() + 3;
                }
                y += h;
            } else {
                // A complaint longer than a page: flow it without the box rather than cut text off.
                for (int i = 0; i < parts.size(); i++) {
                    ensure(parts.get(i).getHeight() + 3);
                    draw(parts.get(i), MARGIN + indents.get(i), y);
                    y += parts.get(i).getHeight() + 3;
                }
                y += 6;
            }
        }
    }

    private void renewals() {
        JsonArray list = arr(d, "renewals");
        sectionTitle(count(R.string.st_pdf_renewals, list));
        Table t = new Table(new float[]{13, 11, 13, 14, 19, 15, 15},
                R.string.st_pdf_col_vehicle, R.string.st_pdf_col_period, R.string.st_pdf_col_payment,
                R.string.st_pdf_col_amount, R.string.st_pdf_col_status, R.string.st_pdf_col_requested,
                R.string.st_pdf_col_decided);
        for (JsonObject r : objects(list)) {
            JsonElement amount = ApiJson.get(r, "amountLkr");
            String amountText = "-";
            if (amount != null && amount.isJsonPrimitive()) {
                try {
                    amountText = String.format(Locale.ENGLISH, "LKR %,.2f", amount.getAsDouble());
                } catch (RuntimeException ignored) {
                    amountText = "-";
                }
            }
            SpannableStringBuilder status = new SpannableStringBuilder(str(r, "status"));
            String reason = raw(r, "decisionReason");
            if (reason != null) muted(status, "\n" + reason);
            t.row(str(r, "vehicleNumber"), str(r, "duration"), str(r, "paymentMethod"), amountText, status,
                    date(r, "createdAt", false), date(r, "decidedAt", false));
        }
        t.end();
    }

    private void shares(@StringRes int titleRes, String key, @StringRes int otherCol) {
        JsonArray list = arr(d, key);
        sectionTitle(count(titleRes, list));
        Table t = new Table(new float[]{25, 30, 20, 25},
                R.string.st_pdf_col_vehicle, otherCol, R.string.st_pdf_col_status, R.string.st_pdf_col_invited);
        for (JsonObject x : objects(list)) {
            t.row(str(x, "vehicleNumber"), blank(x, "otherPartyPhoneMasked"), str(x, "status"),
                    date(x, "invitedAt", false));
        }
        t.end();
    }

    private void pushDevices() {
        JsonArray list = arr(obj(d, "notifications"), "pushDevices");
        sectionTitle(count(R.string.st_pdf_push_devices, list));
        Table t = new Table(new float[]{40, 60}, R.string.st_pdf_col_platform, R.string.st_pdf_col_last_seen);
        for (JsonObject x : objects(list)) {
            t.row(str(x, "platform"), date(x, "lastSeenAt", true));
        }
        t.end();
    }

    private void alerts() {
        JsonArray list = arr(d, "recentAlerts");
        sectionTitle(count(R.string.st_pdf_alerts, list));
        Table t = new Table(new float[]{20, 15, 17, 48},
                R.string.st_pdf_col_when, R.string.st_pdf_col_vehicle, R.string.st_pdf_col_type, R.string.st_pdf_col_message);
        List<JsonObject> items = objects(list);
        for (int i = 0; i < items.size() && i < ALERT_LIMIT; i++) {
            JsonObject a = items.get(i);
            t.row(date(a, "triggeredAt", true), str(a, "vehicleNumber"), str(a, "alertType"), blank(a, "message"));
        }
        t.end();
        if (items.size() > ALERT_LIMIT) {
            StaticLayout more = layout(c.getString(R.string.st_pdf_alerts_more, ALERT_LIMIT, items.size()), small,
                    CONTENT_W, Layout.Alignment.ALIGN_NORMAL);
            ensure(more.getHeight() + 4);
            draw(more, MARGIN, y + 2);
            y += more.getHeight() + 4;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Layout primitives
    // ---------------------------------------------------------------------------------------------

    /** A table that repeats its navy header on every page it spans and never splits a row. */
    private final class Table {
        private final float[] widths;
        private final String[] heads;
        private int rows;

        Table(float[] weights, @StringRes int... headRes) {
            float sum = 0;
            for (float w : weights) sum += w;
            widths = new float[weights.length];
            for (int i = 0; i < weights.length; i++) widths[i] = CONTENT_W * weights[i] / sum;
            heads = new String[headRes.length];
            for (int i = 0; i < headRes.length; i++) heads[i] = s(headRes[i]).toUpperCase(Locale.ENGLISH);
        }

        void row(CharSequence... cells) {
            StaticLayout[] ls = new StaticLayout[cells.length];
            float h = 0;
            for (int i = 0; i < cells.length; i++) {
                ls[i] = layout(cells[i], body, widths[i] - 2 * CELL_PAD_X, Layout.Alignment.ALIGN_NORMAL);
                h = Math.max(h, ls[i].getHeight());
            }
            h += 2 * CELL_PAD_Y;
            float headH = headerHeight();
            if (rows == 0) {
                ensure(headH + h);
                drawHeader();
            } else if (y + h > BOTTOM) {
                newPageAfterFinish();
                drawHeader();
            }
            if (rows % 2 == 1) {
                fill.setColor(ALT_ROW);
                canvas.drawRect(MARGIN, y, MARGIN + CONTENT_W, y + h, fill);
            }
            float x = MARGIN;
            for (int i = 0; i < ls.length; i++) {
                draw(ls[i], x + CELL_PAD_X, y + CELL_PAD_Y);
                x += widths[i];
            }
            y += h;
            rule(y);
            rows++;
        }

        void end() {
            if (rows == 0) empty();
        }

        private float headerHeight() {
            float h = 0;
            for (int i = 0; i < heads.length; i++) {
                h = Math.max(h, layout(heads[i], head, widths[i] - 2 * CELL_PAD_X, Layout.Alignment.ALIGN_NORMAL).getHeight());
            }
            return h + 2 * CELL_PAD_Y;
        }

        private void drawHeader() {
            float h = headerHeight();
            fill.setColor(NAVY);
            canvas.drawRect(MARGIN, y, MARGIN + CONTENT_W, y + h, fill);
            float x = MARGIN;
            for (int i = 0; i < heads.length; i++) {
                draw(layout(heads[i], head, widths[i] - 2 * CELL_PAD_X, Layout.Alignment.ALIGN_NORMAL), x + CELL_PAD_X,
                        y + CELL_PAD_Y);
                x += widths[i];
            }
            y += h;
        }
    }

    private void sectionTitle(String text) {
        StaticLayout l = layout(text.toUpperCase(Locale.ENGLISH), h2, CONTENT_W, Layout.Alignment.ALIGN_NORMAL);
        // Keep a heading with at least its first row (about 30 pt) instead of stranding it at a page foot.
        ensure(14 + l.getHeight() + 30);
        y += 14;
        draw(l, MARGIN, y);
        y += l.getHeight() + 5;
    }

    private void empty() {
        StaticLayout l = layout(s(R.string.st_pdf_none), paint(body.getTypeface(), 7.5f, MUTED), CONTENT_W,
                Layout.Alignment.ALIGN_NORMAL);
        ensure(l.getHeight() + 4);
        draw(l, MARGIN, y + 2);
        y += l.getHeight() + 4;
    }

    private void rule(float at) {
        fill.setColor(RULE);
        canvas.drawRect(MARGIN, at - 0.5f, MARGIN + CONTENT_W, at, fill);
    }

    private void ensure(float height) {
        if (y + height > BOTTOM) newPageAfterFinish();
    }

    private void newPageAfterFinish() {
        finishPage();
        newPage();
    }

    private void newPage() {
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create());
        canvas = page.getCanvas();
        y = MARGIN;
    }

    private void finishPage() {
        if (page == null) return;
        float fy = PAGE_H - MARGIN - FOOTER_H + 8;
        fill.setColor(BORDER);
        canvas.drawRect(MARGIN, fy, MARGIN + CONTENT_W, fy + 0.75f, fill);
        StaticLayout left = layout(c.getString(R.string.st_pdf_footer, generatedAt), small, CONTENT_W * 0.8f,
                Layout.Alignment.ALIGN_NORMAL);
        StaticLayout right = layout(c.getString(R.string.st_pdf_page, pageNo), small, CONTENT_W * 0.2f,
                Layout.Alignment.ALIGN_OPPOSITE);
        draw(left, MARGIN, fy + 5);
        draw(right, MARGIN + CONTENT_W * 0.8f, fy + 5);
        doc.finishPage(page);
        page = null;
    }

    private void draw(StaticLayout l, float x, float top) {
        canvas.save();
        canvas.translate(x, top);
        l.draw(canvas);
        canvas.restore();
    }

    private static StaticLayout layout(CharSequence text, TextPaint p, float width, Layout.Alignment align) {
        CharSequence t = text == null ? "" : text;
        return StaticLayout.Builder.obtain(t, 0, t.length(), p, Math.max(1, (int) width))
                .setAlignment(align)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.15f)
                .build();
    }

    private static TextPaint paint(Typeface face, float size, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(face);
        p.setTextSize(size);
        p.setColor(color);
        return p;
    }

    private Typeface font(int res, Typeface fallback) {
        try {
            Typeface t = ResourcesCompat.getFont(c, res);
            return t != null ? t : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Data helpers (same "-" for missing values as Fleet)
    // ---------------------------------------------------------------------------------------------

    private String s(@StringRes int res) {
        return c.getString(res);
    }

    private String count(@StringRes int res, JsonArray list) {
        return c.getString(res, list.size());
    }

    private String yesNo(boolean b) {
        return s(b ? R.string.st_pdf_yes : R.string.st_pdf_no);
    }

    private String metres(JsonObject o) {
        Integer m = integer(o, "radiusMeters");
        return m != null ? c.getString(R.string.st_pdf_metres, m) : "-";
    }

    private static void muted(SpannableStringBuilder sb, String text) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(MUTED), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Nullable
    private static String yearOf(JsonObject v) {
        Integer y = integer(v, "year");
        return y != null && y > 0 ? String.valueOf(y) : null;
    }

    private static String join(String sep, String... parts) {
        List<String> keep = new ArrayList<>();
        for (String p : parts) if (p != null && !p.trim().isEmpty()) keep.add(p.trim());
        return TextUtils.join(sep, keep);
    }

    private static String orDash(String s) {
        return s == null || s.isEmpty() ? "-" : s;
    }

    /** The value as text, or null when missing or blank. */
    @Nullable
    private static String raw(@Nullable JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = ApiJson.get(o, key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return null;
        String s = e.getAsString();
        return s.trim().isEmpty() ? null : s;
    }

    private static String str(@Nullable JsonObject o, String key) {
        return orDash(raw(o, key));
    }

    private static String blank(@Nullable JsonObject o, String key) {
        String s = raw(o, key);
        return s != null ? s : "";
    }

    private static boolean bool(@Nullable JsonObject o, String key) {
        if (o == null) return false;
        JsonElement e = ApiJson.get(o, key);
        try {
            return e != null && e.isJsonPrimitive() && e.getAsBoolean();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Nullable
    private static Integer integer(@Nullable JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = ApiJson.get(o, key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static JsonObject obj(@Nullable JsonObject o, String key) {
        JsonElement e = o != null ? ApiJson.get(o, key) : null;
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray arr(@Nullable JsonObject o, String key) {
        JsonElement e = o != null ? ApiJson.get(o, key) : null;
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static List<JsonObject> objects(JsonArray a) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : a) if (e != null && e.isJsonObject()) out.add(e.getAsJsonObject());
        return out;
    }

    /**
     * Fleet's rule (AccountController::formatDate): a value with a zone (Z or +hh:mm) is converted to Sri Lanka
     * time; a bare value is already Sri Lanka local time, as the database stores it, and is shown as is.
     */
    private static String date(@Nullable JsonObject o, String key, boolean withTime) {
        String v = raw(o, key);
        if (v == null) return "-";
        DateTimeFormatter f = withTime ? DATE_TIME : DATE_ONLY;
        try {
            return OffsetDateTime.parse(v).atZoneSameInstant(COLOMBO).format(f);
        } catch (RuntimeException ignored) {
            // no zone
        }
        try {
            return LocalDateTime.parse(v).format(f);
        } catch (RuntimeException ignored) {
            // date only, e.g. a reminder's "2027-03-12"
        }
        try {
            return java.time.LocalDate.parse(v).format(DATE_ONLY);
        } catch (RuntimeException e) {
            return "-";
        }
    }
}
