package io.opendesqueeze.media;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Pass-through external OES renderer. Output surface dimensions perform the non-uniform stretch. */
public final class TextureRenderer {
    private static final float[] QUAD = {
            -1f, -1f, 0f,   0f, 0f,
             1f, -1f, 0f,   1f, 0f,
            -1f,  1f, 0f,   0f, 1f,
             1f,  1f, 0f,   1f, 1f
    };

    private static final String VERTEX =
            "uniform mat4 uTexMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main(){ gl_Position=aPosition; vTexCoord=(uTexMatrix*vec4(aTexCoord,0.0,1.0)).xy; }\n";

    private static final String FRAGMENT =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main(){ gl_FragColor=texture2D(sTexture,vTexCoord); }\n";

    private final FloatBuffer vertexBuffer;
    private final float[] texMatrix = new float[16];
    private int program;
    private int textureId;
    private int aPosition;
    private int aTexCoord;
    private int uTexMatrix;

    public TextureRenderer() {
        vertexBuffer = ByteBuffer.allocateDirect(QUAD.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(QUAD).position(0);
        Matrix.setIdentityM(texMatrix, 0);
    }

    public void surfaceCreated() {
        program = createProgram(VERTEX, FRAGMENT);
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord");
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix");

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    public int textureId() { return textureId; }

    public void draw(float[] matrix, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(program);

        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 20, vertexBuffer);
        GLES20.glEnableVertexAttribArray(aPosition);
        vertexBuffer.position(3);
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 20, vertexBuffer);
        GLES20.glEnableVertexAttribArray(aTexCoord);
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, matrix, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glFinish();
    }

    public void release() {
        if (program != 0) GLES20.glDeleteProgram(program);
        if (textureId != 0) GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
        program = 0;
        textureId = 0;
    }

    private static int createProgram(String vertex, String fragment) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vertex);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("GL link failed: " + log);
        }
        return program;
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("GL compile failed: " + log);
        }
        return shader;
    }
}
