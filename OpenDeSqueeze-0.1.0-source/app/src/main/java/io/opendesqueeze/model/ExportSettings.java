package io.opendesqueeze.model;

import io.opendesqueeze.core.DesqueezeMath;

public final class ExportSettings {
    public enum PhotoFormat { JPEG, PNG, WEBP_LOSSLESS }

    public final double factor;
    public final DesqueezeMath.Mode mode;
    public final PhotoFormat photoFormat;
    public final int photoQuality;
    public final String videoMime;
    public final int videoBitrateMbps;
    public final boolean copyAudio;
    public final boolean experimentalHdr;

    public ExportSettings(
            double factor,
            DesqueezeMath.Mode mode,
            PhotoFormat photoFormat,
            int photoQuality,
            String videoMime,
            int videoBitrateMbps,
            boolean copyAudio,
            boolean experimentalHdr) {
        this.factor = factor;
        this.mode = mode;
        this.photoFormat = photoFormat;
        this.photoQuality = photoQuality;
        this.videoMime = videoMime;
        this.videoBitrateMbps = videoBitrateMbps;
        this.copyAudio = copyAudio;
        this.experimentalHdr = experimentalHdr;
    }
}
