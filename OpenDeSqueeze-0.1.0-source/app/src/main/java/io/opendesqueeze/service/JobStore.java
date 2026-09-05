package io.opendesqueeze.service;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.opendesqueeze.core.DesqueezeMath;
import io.opendesqueeze.model.ExportSettings;
import io.opendesqueeze.model.SelectedMedia;

public final class JobStore {
    public static final class Job {
        public final String id;
        public final List<SelectedMedia> media;
        public final ExportSettings settings;
        Job(String id, List<SelectedMedia> media, ExportSettings settings) {
            this.id = id;
            this.media = media;
            this.settings = settings;
        }
    }

    private JobStore() {}

    public static String write(Context context, List<SelectedMedia> media, ExportSettings settings) throws IOException {
        String id = UUID.randomUUID().toString();
        JSONObject root = new JSONObject();
        try {
            root.put("id", id);
            root.put("factor", settings.factor);
            root.put("mode", settings.mode.name());
            root.put("photoFormat", settings.photoFormat.name());
            root.put("photoQuality", settings.photoQuality);
            root.put("videoMime", settings.videoMime);
            root.put("videoBitrateMbps", settings.videoBitrateMbps);
            root.put("copyAudio", settings.copyAudio);
            root.put("experimentalHdr", settings.experimentalHdr);
            JSONArray items = new JSONArray();
            for (SelectedMedia m : media) {
                JSONObject item = new JSONObject();
                item.put("uri", m.uri.toString());
                item.put("name", m.displayName);
                item.put("mime", m.mimeType);
                item.put("width", m.width);
                item.put("height", m.height);
                item.put("durationMs", m.durationMs);
                item.put("rotation", m.rotationDegrees);
                items.put(item);
            }
            root.put("media", items);
        } catch (JSONException e) {
            throw new IOException("Unable to serialize job", e);
        }

        File file = jobFile(context, id);
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IOException("Unable to create job directory");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        return id;
    }

    public static Job read(Context context, String id) throws IOException {
        File file = jobFile(context, id);
        byte[] bytes;
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) >= 0) buffer.write(chunk, 0, read);
            bytes = buffer.toByteArray();
        }
        try {
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            ExportSettings settings = new ExportSettings(
                    root.getDouble("factor"),
                    DesqueezeMath.Mode.valueOf(root.getString("mode")),
                    ExportSettings.PhotoFormat.valueOf(root.getString("photoFormat")),
                    root.getInt("photoQuality"),
                    root.getString("videoMime"),
                    root.getInt("videoBitrateMbps"),
                    root.getBoolean("copyAudio"),
                    root.getBoolean("experimentalHdr"));
            JSONArray items = root.getJSONArray("media");
            ArrayList<SelectedMedia> media = new ArrayList<>(items.length());
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                media.add(new SelectedMedia(
                        Uri.parse(item.getString("uri")),
                        item.getString("name"),
                        item.getString("mime"),
                        item.getInt("width"),
                        item.getInt("height"),
                        item.getLong("durationMs"),
                        item.getInt("rotation")));
            }
            return new Job(id, media, settings);
        } catch (JSONException | IllegalArgumentException e) {
            throw new IOException("Invalid job file", e);
        }
    }

    public static void delete(Context context, String id) {
        File file = jobFile(context, id);
        if (file.exists()) file.delete();
    }

    private static File jobFile(Context context, String id) {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid job id");
        return new File(new File(context.getFilesDir(), "jobs"), id + ".json");
    }
}
