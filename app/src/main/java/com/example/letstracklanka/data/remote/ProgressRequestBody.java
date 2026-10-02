package com.example.letstracklanka.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

/**
 * Wraps a RequestBody and reports how much of it has been written, so an upload can show a real
 * percentage. The listener is called on the OkHttp worker thread and only when the whole-number
 * percentage changes; post to the main thread before touching views.
 */
public class ProgressRequestBody extends RequestBody {

    public interface Listener {
        void onProgress(int percent);
    }

    private final RequestBody delegate;
    private final Listener listener;

    public ProgressRequestBody(RequestBody delegate, Listener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return delegate.contentType();
    }

    @Override
    public long contentLength() throws IOException {
        return delegate.contentLength();
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        final long total = contentLength();
        Sink counting = new ForwardingSink(sink) {
            long written = 0;
            int lastPercent = -1;

            @Override
            public void write(@NonNull Buffer source, long byteCount) throws IOException {
                super.write(source, byteCount);
                written += byteCount;
                if (total > 0) {
                    int percent = (int) Math.min(100, (written * 100) / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        listener.onProgress(percent);
                    }
                }
            }
        };
        BufferedSink buffered = Okio.buffer(counting);
        delegate.writeTo(buffered);
        buffered.flush();
    }
}