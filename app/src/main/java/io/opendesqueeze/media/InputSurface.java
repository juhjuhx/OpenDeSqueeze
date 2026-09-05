package io.opendesqueeze.media;

import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.view.Surface;

/** EGL wrapper for a MediaCodec encoder input Surface. */
public final class InputSurface implements AutoCloseable {
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private Surface surface;

    public InputSurface(Surface surface) {
        if (surface == null) throw new IllegalArgumentException("surface == null");
        this.surface = surface;
        setup();
    }

    private void setup() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) throw new RuntimeException("Unable to get EGL display");
        int[] versions = new int[2];
        if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) throw new RuntimeException("Unable to initialize EGL");

        int[] configAttrs = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            throw new RuntimeException("Unable to find recordable EGL config");
        }
        int[] contextAttrs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0);
        check("eglCreateContext");
        int[] surfaceAttrs = {EGL14.EGL_NONE};
        eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], surface, surfaceAttrs, 0);
        check("eglCreateWindowSurface");
    }

    public void makeCurrent() {
        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) throw new RuntimeException("eglMakeCurrent failed");
    }

    public boolean swapBuffers() {
        return EGL14.eglSwapBuffers(display, eglSurface);
    }

    public void setPresentationTime(long nsecs) {
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, nsecs);
    }

    private void check(String op) {
        int error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) throw new RuntimeException(op + " failed: 0x" + Integer.toHexString(error));
    }

    @Override public void close() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display, eglSurface);
            EGL14.eglDestroyContext(display, context);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(display);
        }
        if (surface != null) surface.release();
        surface = null;
        display = EGL14.EGL_NO_DISPLAY;
        context = EGL14.EGL_NO_CONTEXT;
        eglSurface = EGL14.EGL_NO_SURFACE;
    }
}
