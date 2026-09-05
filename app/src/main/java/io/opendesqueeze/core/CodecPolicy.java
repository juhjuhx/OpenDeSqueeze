package io.opendesqueeze.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure codec selection policy independent of Android framework classes. */
public final class CodecPolicy {
    public static final String AUTO = "auto";
    public static final String AVC = "video/avc";
    public static final String HEVC = "video/hevc";
    public static final String AV1 = "video/av01";

    public static final class Candidate {
        private final String name;
        private final String mime;
        private final boolean hardware;
        private final int maxWidth;
        private final int maxHeight;

        public Candidate(String name, String mime, boolean hardware, int maxWidth, int maxHeight) {
            this.name = name;
            this.mime = mime;
            this.hardware = hardware;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
        }

        public String name() { return name; }
        public String mime() { return mime; }
        public boolean hardware() { return hardware; }
        public int maxWidth() { return maxWidth; }
        public int maxHeight() { return maxHeight; }

        public boolean supports(int width, int height) {
            return width > 0 && height > 0 && width <= maxWidth && height <= maxHeight;
        }
    }

    private CodecPolicy() {}

    public static Candidate choose(String requestedMime, List<Candidate> candidates, int width, int height) {
        return orderedCandidates(requestedMime, candidates, width, height).get(0);
    }

    public static List<Candidate> orderedCandidates(String requestedMime, List<Candidate> candidates, int width, int height) {
        final String originalRequest = (requestedMime == null || requestedMime.trim().isEmpty()) ? AUTO : requestedMime;
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No video encoders are available");
        }
        final String requested = originalRequest.toLowerCase(Locale.ROOT);
        ArrayList<Candidate> ordered = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate == null || !candidate.supports(width, height)) continue;
            boolean mimeMatches = AUTO.equals(requested)
                    ? HEVC.equalsIgnoreCase(candidate.mime()) || AVC.equalsIgnoreCase(candidate.mime())
                    : candidate.mime().equalsIgnoreCase(requested);
            if (mimeMatches) ordered.add(candidate);
        }
        ordered.sort(Comparator.comparingInt(c -> rank(requested, c)));
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("No encoder supports " + originalRequest + " at " + width + "x" + height);
        }
        return Collections.unmodifiableList(new ArrayList<>(ordered));
    }

    private static int rank(String requested, Candidate c) {
        if (!AUTO.equals(requested)) {
            return c.hardware() ? 0 : 1;
        }
        if (c.hardware() && HEVC.equalsIgnoreCase(c.mime())) return 0;
        if (c.hardware() && AVC.equalsIgnoreCase(c.mime())) return 1;
        if (!c.hardware() && HEVC.equalsIgnoreCase(c.mime())) return 2;
        if (!c.hardware() && AVC.equalsIgnoreCase(c.mime())) return 3;
        return 100;
    }
}
