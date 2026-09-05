package io.opendesqueeze.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.opendesqueeze.MainActivity;
import io.opendesqueeze.R;
import io.opendesqueeze.media.PhotoProcessor;
import io.opendesqueeze.media.VideoTranscoder;
import io.opendesqueeze.model.ExportSettings;
import io.opendesqueeze.model.SelectedMedia;

public final class ProcessingService extends Service {
    public static final String ACTION_PROGRESS = "io.opendesqueeze.PROGRESS";
    public static final String EXTRA_DONE = "done";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_FINISHED = "finished";

    private static final String ACTION_RUN = "io.opendesqueeze.RUN";
    private static final String ACTION_CANCEL = "io.opendesqueeze.CANCEL";
    private static final String EXTRA_JOB_ID = "jobId";
    private static final String CHANNEL = "media_processing";
    private static final int NOTIFICATION_ID = 3107;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile String activeJobId;
    private PowerManager.WakeLock wakeLock;

    public static void enqueue(Context context, List<SelectedMedia> media, ExportSettings settings) throws Exception {
        String jobId = JobStore.write(context, media, settings);
        Intent intent = new Intent(context, ProcessingService.class).setAction(ACTION_RUN).putExtra(EXTRA_JOB_ID, jobId);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelled.set(true);
            return START_NOT_STICKY;
        }
        if (!ACTION_RUN.equals(intent.getAction())) return START_NOT_STICKY;
        String jobId = intent.getStringExtra(EXTRA_JOB_ID);
        if (jobId == null || activeJobId != null) return START_NOT_STICKY;
        activeJobId = jobId;
        cancelled.set(false);
        startForegroundCompat(notification("准备媒体处理", 0, 0));
        acquireWakeLock();
        worker.execute(() -> runJob(jobId, startId));
        return START_NOT_STICKY;
    }

    private void runJob(String jobId, int startId) {
        int done = 0;
        int errors = 0;
        int total = 0;
        try {
            JobStore.Job job = JobStore.read(this, jobId);
            total = job.media.size();
            sendProgress(0, total, "开始处理 " + total + " 个文件", false);
            for (int i = 0; i < job.media.size(); i++) {
                if (cancelled.get()) break;
                SelectedMedia media = job.media.get(i);
                final int itemIndex = i;
                String label = (i + 1) + "/" + total + " · " + media.displayName;
                updateNotification(label, i, total);
                sendProgress(i, total, label, false);
                try {
                    if (media.isImage()) {
                        PhotoProcessor.process(this, media, job.settings);
                    } else if (media.isVideo()) {
                        VideoTranscoder.transcode(this, media, job.settings, new VideoTranscoder.Callback() {
                            @Override public boolean isCancelled() { return cancelled.get(); }
                            @Override public void onProgress(float progress01) {
                                int percent = Math.max(0, Math.min(100, Math.round(progress01 * 100f)));
                                updateNotification((itemIndex + 1) + "/" + job.media.size() + " · " + percent + "% · " + media.displayName,
                                        itemIndex, job.media.size());
                            }
                        });
                    } else {
                        throw new IllegalArgumentException("Unsupported MIME: " + media.mimeType);
                    }
                } catch (InterruptedException e) {
                    cancelled.set(true);
                    break;
                } catch (Exception e) {
                    errors++;
                    sendProgress(done, total, "跳过 " + media.displayName + "：" + safeMessage(e), false);
                }
                done++;
            }
            String summary;
            if (cancelled.get()) summary = "已取消 · 完成 " + done + "/" + total + " · 错误 " + errors;
            else summary = "完成 " + done + "/" + total + (errors == 0 ? "" : " · 错误 " + errors);
            sendProgress(done, total, summary, true);
            activeJobId = null;
            updateNotification(summary, done, total);
        } catch (Exception e) {
            sendProgress(done, total, "任务失败：" + safeMessage(e), true);
            activeJobId = null;
            updateNotification("任务失败", done, Math.max(1, total));
        } finally {
            JobStore.delete(this, jobId);
            releaseWakeLock();
            activeJobId = null;
            stopForeground(false);
            stopSelfResult(startId);
        }
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String text, int done, int total) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, notification(text, done, total));
    }

    private Notification notification(String text, int done, int total) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent cancel = new Intent(this, ProcessingService.class).setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(this, 2, cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("OpenDeSqueeze")
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(activeJobId != null && !cancelled.get())
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPi).build());
        if (total > 0) b.setProgress(total, Math.min(done, total), false);
        else b.setProgress(0, 0, true);
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("OpenDeSqueeze photo and video export progress");
        channel.enableLights(false);
        channel.setLightColor(Color.GRAY);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void sendProgress(int done, int total, String message, boolean finished) {
        Intent intent = new Intent(ACTION_PROGRESS)
                .setPackage(getPackageName())
                .putExtra(EXTRA_DONE, done)
                .putExtra(EXTRA_TOTAL, total)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_FINISHED, finished);
        sendBroadcast(intent);
    }

    private void acquireWakeLock() {
        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenDeSqueeze:export");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(6 * 60 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        return (message == null || message.trim().isEmpty()) ? t.getClass().getSimpleName() : message;
    }

    @Override public void onTimeout(int startId, int fgsType) {
        cancelled.set(true);
        sendProgress(0, 0, "系统媒体处理时限已到，任务已停止", true);
        activeJobId = null;
        releaseWakeLock();
        stopForeground(true);
        stopSelf(startId);
    }

    @Override public void onDestroy() {
        cancelled.set(true);
        worker.shutdownNow();
        releaseWakeLock();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
