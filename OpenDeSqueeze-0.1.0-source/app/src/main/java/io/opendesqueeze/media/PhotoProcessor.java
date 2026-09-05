package io.opendesqueeze.media;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.opendesqueeze.core.DesqueezeMath;
import io.opendesqueeze.core.OutputNaming;
import io.opendesqueeze.model.ExportSettings;
import io.opendesqueeze.model.SelectedMedia;

public final class PhotoProcessor {
    private static final long MAX_DECODE_BYTES = 320L * 1024L * 1024L;

    private PhotoProcessor() {}

    public static Uri process(Context context, SelectedMedia media, ExportSettings settings) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Bitmap source = null;
        Bitmap scaled = null;
        try {
            source = decode(resolver, media.uri, media.width, media.height);
            DesqueezeMath.Size target = DesqueezeMath.calculate(source.getWidth(), source.getHeight(), settings.factor, settings.mode);
            scaled = Bitmap.createScaledBitmap(source, target.width(), target.height(), true);

            FormatInfo format = formatInfo(settings.photoFormat);
            String name = OutputNaming.withSuffix(media.displayName, format.extension);
            try (MediaStoreWriter.PendingItem pending = MediaStoreWriter.createImage(context, name, format.mime)) {
                try (OutputStream out = resolver.openOutputStream(pending.uri, "w")) {
                    if (out == null) throw new IOException("Unable to open output stream");
                    int quality = settings.photoFormat == ExportSettings.PhotoFormat.JPEG ? settings.photoQuality : 100;
                    if (!scaled.compress(format.compressFormat, quality, out)) {
                        throw new IOException("Bitmap encoder rejected output format");
                    }
                }
                copyExifBestEffort(resolver, media.uri, pending.uri);
                pending.finish();
                return pending.uri;
            }
        } catch (OutOfMemoryError oom) {
            throw new IOException("Image is too large for available memory. Try Preserve Width or a smaller source file.", oom);
        } finally {
            if (scaled != null && scaled != source && !scaled.isRecycled()) scaled.recycle();
            if (source != null && !source.isRecycled()) source.recycle();
        }
    }

    private static Bitmap decode(ContentResolver resolver, Uri uri, int width, int height) throws IOException {
        long estimated = (long) Math.max(1, width) * Math.max(1, height) * 4L;
        if (estimated > MAX_DECODE_BYTES) {
            throw new IOException("Image decode would exceed 320 MiB; v1 refuses this file to avoid killing the app");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Unable to open image");
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            if (bitmap == null) throw new IOException("Unsupported image format");
            return bitmap;
        }
    }

    private static FormatInfo formatInfo(ExportSettings.PhotoFormat format) throws IOException {
        return switch (format) {
            case PNG -> new FormatInfo("png", "image/png", Bitmap.CompressFormat.PNG);
            case WEBP_LOSSLESS -> {
                if (Build.VERSION.SDK_INT < 30) {
                    throw new IOException("Lossless WebP export requires Android 11 or newer");
                }
                yield new FormatInfo("webp", "image/webp", Bitmap.CompressFormat.WEBP_LOSSLESS);
            }
            case JPEG -> new FormatInfo("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG);
        };
    }

    private static void copyExifBestEffort(ContentResolver resolver, Uri input, Uri output) {
        String[] tags = {
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_ISO_SPEED_RATINGS
        };
        try (ParcelFileDescriptor inFd = resolver.openFileDescriptor(input, "r");
             ParcelFileDescriptor outFd = resolver.openFileDescriptor(output, "rw")) {
            if (inFd == null || outFd == null) return;
            ExifInterface src = new ExifInterface(inFd.getFileDescriptor());
            ExifInterface dst = new ExifInterface(outFd.getFileDescriptor());
            for (String tag : tags) {
                String value = src.getAttribute(tag);
                if (value != null) dst.setAttribute(tag, value);
            }
            dst.saveAttributes();
        } catch (Exception ignored) {
            // Metadata is optional; media output remains valid if a provider or format rejects EXIF updates.
        }
    }

    private static final class FormatInfo {
        final String extension;
        final String mime;
        final Bitmap.CompressFormat compressFormat;
        FormatInfo(String extension, String mime, Bitmap.CompressFormat compressFormat) {
            this.extension = extension;
            this.mime = mime;
            this.compressFormat = compressFormat;
        }
    }
}
