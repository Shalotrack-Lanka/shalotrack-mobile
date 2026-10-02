package com.example.letstracklanka.utils;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Turns whatever the customer picked into something the API will accept (JPEG, PNG or PDF, 2 MB max).
 *
 * Why this exists: a phone camera photo is routinely 3-10 MB, so without this most real slips would be
 * refused. Photos are decoded at reduced size, rotated upright using their EXIF tag, flattened on white
 * (PNG screenshots can be transparent) and re-saved as JPEG. Re-encoding also drops all EXIF data, so the
 * GPS location a photo may carry is never uploaded. PDFs cannot be shrunk and are only size-checked.
 *
 * The file type is decided from the file's own bytes, never from its name or the picker's claim. The
 * server checks again; this is for a good error message, not for trust.
 *
 * Nothing is written to disk and nothing from the file (not even its name) is logged. CALL OFF THE MAIN
 * THREAD.
 */
public final class SlipPreparer {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_EDGE_PX = 2000;
    private static final int MAX_PDF_READ = MAX_BYTES + 1;

    public static final class Result {
        public final byte[] bytes;
        public final String mimeType;
        /** A fixed name: the original file name is never sent. */
        public final String fileName;

        Result(byte[] bytes, String mimeType, String fileName) {
            this.bytes = bytes;
            this.mimeType = mimeType;
            this.fileName = fileName;
        }
    }

    /** Message is safe to show to the customer as is. */
    public static final class SlipException extends Exception {
        public SlipException(String message) {
            super(message);
        }
    }

    private SlipPreparer() {
    }

    public static Result prepare(ContentResolver resolver, Uri uri) throws SlipException {
        byte[] head = readHead(resolver, uri);
        if (head.length == 0) {
            throw new SlipException("That file looks empty. Please choose another.");
        }
        if (isPdf(head)) {
            return preparePdf(resolver, uri);
        }
        return prepareImage(resolver, uri);
    }

    // ------------------------------------------------------------------------------------ PDF

    private static Result preparePdf(ContentResolver resolver, Uri uri) throws SlipException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new SlipException("Couldn't open that file.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_PDF_READ) {
                    throw new SlipException("That PDF is larger than 2 MB. Please send a photo of the slip instead.");
                }
            }
            return new Result(out.toByteArray(), "application/pdf", "slip.pdf");
        } catch (IOException | SecurityException e) {
            throw new SlipException("Couldn't read that file. Please try again.");
        }
    }

    // ---------------------------------------------------------------------------------- image

    private static Result prepareImage(ContentResolver resolver, Uri uri) throws SlipException {
        int width;
        int height;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new SlipException("Couldn't open that file.");
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (IOException | SecurityException e) {
            throw new SlipException("Couldn't read that file. Please try again.");
        }
        width = bounds.outWidth;
        height = bounds.outHeight;
        if (width <= 0 || height <= 0) {
            throw new SlipException("That doesn't look like a photo or a PDF. Please choose a photo of the slip.");
        }

        // Power-of-two downsample while decoding, so a 12 MP photo never sits in memory at full size.
        int sample = 1;
        while ((width / (sample * 2)) >= MAX_EDGE_PX || (height / (sample * 2)) >= MAX_EDGE_PX) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new SlipException("Couldn't open that file.");
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        } catch (IOException | SecurityException | OutOfMemoryError e) {
            throw new SlipException("Couldn't read that photo. Please try a smaller one.");
        }
        if (bitmap == null) {
            throw new SlipException("That doesn't look like a photo or a PDF. Please choose a photo of the slip.");
        }

        bitmap = rotateUpright(resolver, uri, bitmap);
        bitmap = flattenOnWhite(bitmap);
        bitmap = limitEdge(bitmap, MAX_EDGE_PX);

        // Shrink quality, then size, until it fits. Slips are text on paper: this stays legible.
        int quality = 85;
        for (int attempt = 0; attempt < 8; attempt++) {
            byte[] jpeg = compress(bitmap, quality);
            if (jpeg.length <= MAX_BYTES) {
                bitmap.recycle();
                return new Result(jpeg, "image/jpeg", "slip.jpg");
            }
            if (quality > 55) {
                quality -= 10;
            } else {
                Bitmap smaller = Bitmap.createScaledBitmap(
                        bitmap, Math.max(1, (int) (bitmap.getWidth() * 0.75f)), Math.max(1, (int) (bitmap.getHeight() * 0.75f)), true);
                if (smaller != bitmap) bitmap.recycle();
                bitmap = smaller;
            }
        }
        bitmap.recycle();
        throw new SlipException("Couldn't make that photo small enough. Please take a clearer, closer photo of the slip.");
    }

    private static byte[] compress(Bitmap bitmap, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }

    private static Bitmap limitEdge(Bitmap bitmap, int maxEdge) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxEdge) return bitmap;
        float scale = (float) maxEdge / (float) longest;
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    private static Bitmap flattenOnWhite(Bitmap src) {
        if (!src.hasAlpha()) return src;
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(src, 0f, 0f, null);
        src.recycle();
        return out;
    }

    private static Bitmap rotateUpright(ContentResolver resolver, Uri uri, Bitmap src) {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in != null) {
                orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (IOException | RuntimeException ignored) {
            // No readable EXIF (a PNG, a screenshot): leave the picture as it is.
        }

        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                m.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                m.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                m.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                m.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                m.postScale(1f, -1f);
                break;
            default:
                return src;
        }
        try {
            Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
            if (rotated != src) src.recycle();
            return rotated;
        } catch (OutOfMemoryError e) {
            return src;
        }
    }

    // --------------------------------------------------------------------------------- sniffing

    private static byte[] readHead(ContentResolver resolver, Uri uri) throws SlipException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new SlipException("Couldn't open that file.");
            byte[] head = new byte[8];
            int total = 0;
            int n;
            while (total < head.length && (n = in.read(head, total, head.length - total)) > 0) {
                total += n;
            }
            byte[] out = new byte[total];
            System.arraycopy(head, 0, out, 0, total);
            return out;
        } catch (IOException | SecurityException e) {
            throw new SlipException("Couldn't read that file. Please try again.");
        }
    }

    private static boolean isPdf(byte[] head) {
        return head.length >= 5 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F' && head[4] == '-';
    }
}