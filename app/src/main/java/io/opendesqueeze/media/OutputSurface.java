package io.opendesqueeze.media;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

public final class OutputSurface implements SurfaceTexture.OnFrameAvailableListener, AutoCloseable {
    private static final long FRAME_WAIT_MS = 2500L;
    private final Object frameSync = new Object();
    private final TextureRenderer renderer = new TextureRenderer();
    private final HandlerThread callbackThread = new HandlerThread("OpenDeSqueeze-frames");
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private boolean frameAvailable;

    public OutputSurface() {
        renderer.surfaceCreated();
        surfaceTexture = new SurfaceTexture(renderer.textureId());
        callbackThread.start();
        surfaceTexture.setOnFrameAvailableListener(this, new Handler(callbackThread.getLooper()));
        surface = new Surface(surfaceTexture);
    }

    public Surface surface() { return surface; }

    @Override public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (frameSync) {
            if (frameAvailable) return;
            frameAvailable = true;
            frameSync.notifyAll();
        }
    }

    public void awaitAndDraw(int width, int height) throws InterruptedException {
        synchronized (frameSync) {
            long deadline = System.currentTimeMillis() + FRAME_WAIT_MS;
            while (!frameAvailable) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) throw new RuntimeException("Timed out waiting for decoded video frame");
                frameSync.wait(remain);
            }
            frameAvailable = false;
        }
        surfaceTexture.updateTexImage();
        float[] matrix = new float[16];
        surfaceTexture.getTransformMatrix(matrix);
        renderer.draw(matrix, width, height);
    }

    @Override public void close() {
        if (surface != null) surface.release();
        if (surfaceTexture != null) surfaceTexture.release();
        renderer.release();
        callbackThread.quitSafely();
        surface = null;
        surfaceTexture = null;
    }
}
