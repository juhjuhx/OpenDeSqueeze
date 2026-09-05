package io.opendesqueeze.model;

import android.net.Uri;

public final class SelectedMedia {
    public final Uri uri;
    public final String displayName;
    public final String mimeType;
    public final int width;
    public final int height;
    public final long durationMs;
    public final int rotationDegrees;

    public SelectedMedia(Uri uri, String displayName, String mimeType, int width, int height, long durationMs, int rotationDegrees) {
        this.uri = uri;
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.durationMs = durationMs;
        this.rotationDegrees = rotationDegrees;
    }

    public boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video/");
    }

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
