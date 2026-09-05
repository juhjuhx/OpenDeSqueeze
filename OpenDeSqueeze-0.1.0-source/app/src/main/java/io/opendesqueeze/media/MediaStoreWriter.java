package io.opendesqueeze.media;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.IOException;

/** Creates pending MediaStore rows and guarantees cleanup/commit semantics. */
public final class MediaStoreWriter {
    public static final class PendingItem implements AutoCloseable {
        private final ContentResolver resolver;
        public final Uri uri;
        private final boolean pendingSupported;
        private boolean finished;

        private PendingItem(ContentResolver resolver, Uri uri, boolean pendingSupported) {
            this.resolver = resolver;
            this.uri = uri;
            this.pendingSupported = pendingSupported;
        }

        public void finish() {
            if (finished) return;
            if (pendingSupported) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
            finished = true;
        }

        public void abort() {
            if (finished) return;
            resolver.delete(uri, null, null);
            finished = true;
        }

        @Override public void close() {
            if (!finished) abort();
        }
    }

    private MediaStoreWriter() {}

    public static PendingItem createImage(Context context, String displayName, String mimeType) throws IOException {
        return create(context, false, displayName, mimeType);
    }

    public static PendingItem createVideo(Context context, String displayName, String mimeType) throws IOException {
        return create(context, true, displayName, mimeType);
    }

    private static PendingItem create(Context context, boolean video, String displayName, String mimeType) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        boolean pending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        if (pending) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES) + "/OpenDeSqueeze");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        } else {
            File publicRoot = Environment.getExternalStoragePublicDirectory(
                    video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES);
            File outputDir = new File(publicRoot, "OpenDeSqueeze");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("Unable to create legacy output directory: " + outputDir);
            }
            File outputFile = uniqueFile(outputDir, displayName);
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, outputFile.getName());
            values.put(MediaStore.MediaColumns.DATA, outputFile.getAbsolutePath());
        }
        Uri collection = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri uri = resolver.insert(collection, values);
        if (uri == null) throw new IOException("MediaStore rejected output row");
        return new PendingItem(resolver, uri, pending);
    }
    private static File uniqueFile(File directory, String displayName) {
        File candidate = new File(directory, displayName);
        if (!candidate.exists()) return candidate;
        int dot = displayName.lastIndexOf('.');
        String base = dot > 0 ? displayName.substring(0, dot) : displayName;
        String ext = dot > 0 ? displayName.substring(dot) : "";
        for (int i = 2; i < 10_000; i++) {
            candidate = new File(directory, base + "_" + i + ext);
            if (!candidate.exists()) return candidate;
        }
        return new File(directory, base + "_" + System.currentTimeMillis() + ext);
    }

}
