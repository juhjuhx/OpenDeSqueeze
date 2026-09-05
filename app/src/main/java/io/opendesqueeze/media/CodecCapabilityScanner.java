package io.opendesqueeze.media;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.opendesqueeze.core.CodecPolicy;

public final class CodecCapabilityScanner {
    private CodecCapabilityScanner() {}

    public static List<CodecPolicy.Candidate> scanForSize(int width, int height) {
        ArrayList<CodecPolicy.Candidate> result = new ArrayList<>();
        MediaCodecInfo[] infos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
        for (MediaCodecInfo info : infos) {
            if (!info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                String mime = type.toLowerCase(Locale.ROOT);
                if (!CodecPolicy.AVC.equals(mime) && !CodecPolicy.HEVC.equals(mime) && !CodecPolicy.AV1.equals(mime)) continue;
                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
                    MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
                    if (video == null || !video.areSizeSupported(width, height)) continue;
                    int maxW = video.getSupportedWidths().getUpper();
                    int maxH = video.getSupportedHeights().getUpper();
                    result.add(new CodecPolicy.Candidate(info.getName(), mime, isHardware(info), maxW, maxH));
                } catch (IllegalArgumentException ignored) {
                    // Broken vendor codec capability records are common; skip the codec instead of crashing discovery.
                }
            }
        }
        return result;
    }

    private static boolean isHardware(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= 29) return info.isHardwareAccelerated();
        String name = info.getName().toLowerCase(Locale.ROOT);
        return !(name.startsWith("omx.google.") || name.startsWith("c2.android.")
                || name.startsWith("c2.google.") || name.contains("ffmpeg") || name.contains("sw."));
    }
}
