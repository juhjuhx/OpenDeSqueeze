package io.opendesqueeze;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.opendesqueeze.core.CodecPolicy;
import io.opendesqueeze.core.DesqueezeMath;
import io.opendesqueeze.media.MediaInspector;
import io.opendesqueeze.model.ExportSettings;
import io.opendesqueeze.model.SelectedMedia;
import io.opendesqueeze.service.ProcessingService;

public final class MainActivity extends Activity {
    private static final int REQ_PICK_MEDIA = 1001;
    private static final int REQ_NOTIFICATIONS = 1002;
    private static final int REQ_LEGACY_STORAGE = 1003;

    private final List<SelectedMedia> selected = new ArrayList<>();
    private final ExecutorService inspectorExecutor = Executors.newSingleThreadExecutor();

    private TextView selectedSummary;
    private TextView qualityLabel;
    private TextView statusText;
    private ProgressBar progressBar;
    private Spinner factorSpinner;
    private EditText customFactor;
    private Spinner geometrySpinner;
    private Spinner photoFormatSpinner;
    private SeekBar qualitySeek;
    private Spinner videoCodecSpinner;
    private Spinner bitrateSpinner;
    private CheckBox copyAudio;
    private CheckBox experimentalHdr;
    private Button exportButton;

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ProcessingService.ACTION_PROGRESS.equals(intent.getAction())) return;
            int done = intent.getIntExtra(ProcessingService.EXTRA_DONE, 0);
            int total = intent.getIntExtra(ProcessingService.EXTRA_TOTAL, 0);
            String message = intent.getStringExtra(ProcessingService.EXTRA_MESSAGE);
            progressBar.setMax(Math.max(1, total));
            progressBar.setProgress(done);
            statusText.setText(message == null ? (done + "/" + total) : message);
            if (intent.getBooleanExtra(ProcessingService.EXTRA_FINISHED, false)) {
                exportButton.setEnabled(true);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        registerProgressReceiver();
        maybeRequestNotifications();
        maybeRequestLegacyStorage();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(progressReceiver); } catch (IllegalArgumentException ignored) {}
        inspectorExecutor.shutdownNow();
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(28), pad, dp(32));
        root.setBackgroundColor(Color.rgb(16, 17, 20));

        TextView title = text("OpenDeSqueeze", 28, true);
        root.addView(title);
        TextView subtitle = text("本地批量 Anamorphic Desqueeze · 不联网", 14, false);
        subtitle.setTextColor(Color.rgb(183, 187, 196));
        root.addView(subtitle, marginTop(4));

        Button pick = button("选择照片 / 影片");
        pick.setOnClickListener(v -> pickMedia());
        root.addView(pick, marginTop(20));

        selectedSummary = text("尚未选择媒体", 14, false);
        selectedSummary.setTextColor(Color.rgb(183, 187, 196));
        selectedSummary.setTextIsSelectable(true);
        root.addView(selectedSummary, marginTop(12));

        root.addView(section("镜头倍率"), marginTop(22));
        factorSpinner = spinner(new String[]{"1.33×", "1.50×", "1.55×", "1.80×", "2.00×", "自定义"});
        root.addView(factorSpinner, marginTop(6));
        customFactor = new EditText(this);
        customFactor.setHint("例如 1.33");
        customFactor.setTextColor(Color.WHITE);
        customFactor.setHintTextColor(Color.GRAY);
        customFactor.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        customFactor.setVisibility(View.GONE);
        root.addView(customFactor, marginTop(6));

        root.addView(section("几何方式"), marginTop(18));
        geometrySpinner = spinner(new String[]{"保持宽度（推荐）", "保持高度（更宽输出）"});
        root.addView(geometrySpinner, marginTop(6));

        root.addView(section("照片输出"), marginTop(18));
        photoFormatSpinner = spinner(new String[]{"JPEG", "PNG", "WebP Lossless"});
        root.addView(photoFormatSpinner, marginTop(6));
        qualitySeek = new SeekBar(this);
        qualitySeek.setMax(20);
        qualitySeek.setProgress(15);
        qualitySeek.setProgressTintList(ColorStateList.valueOf(Color.LTGRAY));
        root.addView(qualitySeek, marginTop(6));
        qualityLabel = text("JPEG 品质：95", 13, false);
        qualityLabel.setTextColor(Color.rgb(183, 187, 196));
        root.addView(qualityLabel);

        root.addView(section("影片输出"), marginTop(18));
        videoCodecSpinner = spinner(new String[]{"Auto（HEVC → AVC）", "H.264 / AVC", "H.265 / HEVC", "AV1（设备支持时）"});
        root.addView(videoCodecSpinner, marginTop(6));
        bitrateSpinner = spinner(new String[]{"Auto", "10 Mbps", "20 Mbps", "40 Mbps", "60 Mbps", "100 Mbps"});
        root.addView(bitrateSpinner, marginTop(6));
        copyAudio = new CheckBox(this);
        copyAudio.setText("复制原始音频，不重新编码");
        copyAudio.setTextColor(Color.WHITE);
        copyAudio.setChecked(true);
        root.addView(copyAudio, marginTop(6));
        experimentalHdr = new CheckBox(this);
        experimentalHdr.setText("尝试保留 HDR metadata（实验性）");
        experimentalHdr.setTextColor(Color.WHITE);
        root.addView(experimentalHdr);

        TextView hint = text("输出目录：Pictures/OpenDeSqueeze 与 Movies/OpenDeSqueeze。原文件不会被修改。", 12, false);
        hint.setTextColor(Color.rgb(150, 154, 163));
        root.addView(hint, marginTop(18));

        exportButton = button("开始批量处理");
        exportButton.setEnabled(false);
        exportButton.setOnClickListener(v -> startExport());
        root.addView(exportButton, marginTop(18));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1);
        root.addView(progressBar, marginTop(12));
        statusText = text("等待任务", 13, false);
        statusText.setTextColor(Color.rgb(183, 187, 196));
        root.addView(statusText, marginTop(6));

        factorSpinner.setOnItemSelectedListener(new SimpleItemListener(this::onFactorChanged));
        geometrySpinner.setOnItemSelectedListener(new SimpleItemListener(this::refreshPreview));
        customFactor.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) refreshPreview(); });
        qualitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                qualityLabel.setText("JPEG 品质：" + (80 + progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        return scroll;
    }

    private void onFactorChanged() {
        boolean custom = factorSpinner.getSelectedItemPosition() == 5;
        customFactor.setVisibility(custom ? View.VISIBLE : View.GONE);
        refreshPreview();
    }

    private void pickMedia() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_MEDIA);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_MEDIA || resultCode != RESULT_OK || data == null) return;

        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getData() != null) uris.add(data.getData());
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        }
        if (uris.isEmpty()) return;

        for (Uri uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {}
        }

        statusText.setText("读取媒体信息…");
        exportButton.setEnabled(false);
        inspectorExecutor.execute(() -> {
            List<SelectedMedia> inspected = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (Uri uri : uris) {
                try { inspected.add(MediaInspector.inspect(getContentResolver(), uri)); }
                catch (Exception e) { errors.add(uri + ": " + e.getMessage()); }
            }
            runOnUiThread(() -> {
                selected.clear();
                selected.addAll(inspected);
                refreshPreview();
                exportButton.setEnabled(!selected.isEmpty());
                statusText.setText(errors.isEmpty() ? "已读取 " + selected.size() + " 个文件" : "有 " + errors.size() + " 个文件无法读取");
            });
        });
    }

    private void refreshPreview() {
        if (selectedSummary == null) return;
        if (selected.isEmpty()) {
            selectedSummary.setText("尚未选择媒体");
            return;
        }
        double factor;
        try { factor = selectedFactor(); }
        catch (IllegalArgumentException e) {
            selectedSummary.setText("倍率无效：" + e.getMessage());
            return;
        }
        DesqueezeMath.Mode mode = geometrySpinner.getSelectedItemPosition() == 0
                ? DesqueezeMath.Mode.PRESERVE_WIDTH : DesqueezeMath.Mode.PRESERVE_HEIGHT;
        StringBuilder sb = new StringBuilder();
        sb.append(selected.size()).append(" 个文件\n");
        int shown = 0;
        for (SelectedMedia media : selected) {
            if (shown++ >= 12) { sb.append("…其余 ").append(selected.size() - 12).append(" 个\n"); break; }
            try {
                DesqueezeMath.Size out = DesqueezeMath.calculate(media.width, media.height, factor, mode);
                sb.append(media.displayName).append("\n  ")
                        .append(media.width).append('×').append(media.height)
                        .append(" → ").append(out.width()).append('×').append(out.height());
                if (media.isVideo() && media.rotationDegrees != 0) sb.append(" · rotation ").append(media.rotationDegrees).append('°');
                sb.append('\n');
            } catch (IllegalArgumentException e) {
                sb.append(media.displayName).append(" · ").append(e.getMessage()).append('\n');
            }
        }
        selectedSummary.setText(sb.toString());
    }

    private void startExport() {
        if (selected.isEmpty()) return;
        ExportSettings settings;
        try { settings = readSettings(); }
        catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        exportButton.setEnabled(false);
        progressBar.setMax(selected.size());
        progressBar.setProgress(0);
        statusText.setText("建立任务…");
        try {
            ProcessingService.enqueue(this, Collections.unmodifiableList(new ArrayList<>(selected)), settings);
        } catch (Exception e) {
            exportButton.setEnabled(true);
            statusText.setText("无法启动：" + e.getMessage());
        }
    }

    private ExportSettings readSettings() {
        double factor = selectedFactor();
        DesqueezeMath.Mode mode = geometrySpinner.getSelectedItemPosition() == 0
                ? DesqueezeMath.Mode.PRESERVE_WIDTH : DesqueezeMath.Mode.PRESERVE_HEIGHT;
        ExportSettings.PhotoFormat photoFormat = switch (photoFormatSpinner.getSelectedItemPosition()) {
            case 1 -> ExportSettings.PhotoFormat.PNG;
            case 2 -> ExportSettings.PhotoFormat.WEBP_LOSSLESS;
            default -> ExportSettings.PhotoFormat.JPEG;
        };
        String videoMime = switch (videoCodecSpinner.getSelectedItemPosition()) {
            case 1 -> CodecPolicy.AVC;
            case 2 -> CodecPolicy.HEVC;
            case 3 -> CodecPolicy.AV1;
            default -> CodecPolicy.AUTO;
        };
        int bitrate = switch (bitrateSpinner.getSelectedItemPosition()) {
            case 1 -> 10;
            case 2 -> 20;
            case 3 -> 40;
            case 4 -> 60;
            case 5 -> 100;
            default -> 0;
        };
        return new ExportSettings(factor, mode, photoFormat, 80 + qualitySeek.getProgress(), videoMime,
                bitrate, copyAudio.isChecked(), experimentalHdr.isChecked());
    }

    private double selectedFactor() {
        if (factorSpinner.getSelectedItemPosition() == 5) {
            String raw = customFactor.getText().toString().trim();
            try {
                double value = Double.parseDouble(raw);
                if (value <= 1.0 || value > 3.0) throw new NumberFormatException();
                return value;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("自定义倍率必须介于 1.01 到 3.00");
            }
        }
        return switch (factorSpinner.getSelectedItemPosition()) {
            case 1 -> 1.50;
            case 2 -> 1.55;
            case 3 -> 1.80;
            case 4 -> 2.00;
            default -> 1.33;
        };
    }

    private void registerProgressReceiver() {
        IntentFilter filter = new IntentFilter(ProcessingService.ACTION_PROGRESS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(progressReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(progressReceiver, filter);
    }

    private void maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void maybeRequestLegacyStorage() {
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_LEGACY_STORAGE);
        }
    }

    private TextView section(String value) {
        TextView v = text(value, 14, true);
        v.setTextColor(Color.WHITE);
        return v;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(Color.WHITE);
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        return v;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        return b;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        return s;
    }

    private LinearLayout.LayoutParams marginTop(int dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(dp);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class SimpleItemListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable action;
        SimpleItemListener(Runnable action) { this.action = action; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { action.run(); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }
}
