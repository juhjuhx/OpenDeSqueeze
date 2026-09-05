package io.opendesqueeze.media;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.io.InputStream;

import io.opendesqueeze.model.SelectedMedia;

public final class MediaInspector {
    private MediaInspector() {}

    public static SelectedMedia inspect(ContentResolver resolver, Uri uri) throws IOException {
        String mime = resolver.getType(uri);
        String name = queryName(resolver, uri);
        if (mime != null && mime.startsWith("video/")) {
            return inspectVideo(resolver, uri, name, mime);
        }
        return inspectImage(resolver, uri, name, mime == null ? "image/*" : mime);
    }

    private static SelectedMedia inspectImage(ContentResolver resolver, Uri uri, String name, String mime) throws IOException {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Unable to open " + uri);
            BitmapFactory.decodeStream(in, null, opts);
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw new IOException("Unable to read image dimensions: " + name);
        }
        return new SelectedMedia(uri, name, mime, opts.outWidth, opts.outHeight, 0L, 0);
    }

    private static SelectedMedia inspectVideo(ContentResolver resolver, Uri uri, String name, String mime) throws IOException {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            if (pfd == null) throw new IOException("Unable to open " + uri);
            r.setDataSource(pfd.getFileDescriptor());
            int width = parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            long duration = parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            int rotation = parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            if (width <= 0 || height <= 0) throw new IOException("Unable to read video dimensions: " + name);
            return new SelectedMedia(uri, name, mime, width, height, duration, rotation);
        } finally {
            r.release();
        }
    }

    private static String queryName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (RuntimeException ignored) {}
        String fallback = uri.getLastPathSegment();
        return fallback == null ? "media" : fallback;
    }

    private static int parseInt(String value) {
        try { return value == null ? 0 : Integer.parseInt(value); }
        catch (NumberFormatException e) { return 0; }
    }

    private static long parseLong(String value) {
        try { return value == null ? 0L : Long.parseLong(value); }
        catch (NumberFormatException e) { return 0L; }
    }
}
