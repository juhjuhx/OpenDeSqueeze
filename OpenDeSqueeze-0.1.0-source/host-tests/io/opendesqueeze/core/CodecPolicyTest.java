package io.opendesqueeze.core;

import java.util.List;

public final class CodecPolicyTest {
    public static void main(String[] args) {
        testAutoPrefersHardwareHevc();
        testAutoFallsBackToHardwareAvc();
        testExplicitAv1();
        testUnsupportedDimensions();
        testSoftwareFallback();
        testAutoDoesNotSelectAv1();
        testOrderedFallbacks();
        testExplicitCodecOrdersHardwareBeforeSoftware();
        System.out.println("CodecPolicyTest: PASS");
    }

    private static CodecPolicy.Candidate c(String name, String mime, boolean hardware, int maxW, int maxH) {
        return new CodecPolicy.Candidate(name, mime, hardware, maxW, maxH);
    }

    private static void testAutoPrefersHardwareHevc() {
        var chosen = CodecPolicy.choose(CodecPolicy.AUTO, List.of(
                c("sw-avc", "video/avc", false, 4096, 4096),
                c("hw-avc", "video/avc", true, 4096, 4096),
                c("hw-hevc", "video/hevc", true, 8192, 8192)), 3840, 1624);
        assertEquals("hw-hevc", chosen.name(), "auto HEVC preference");
    }

    private static void testAutoFallsBackToHardwareAvc() {
        var chosen = CodecPolicy.choose(CodecPolicy.AUTO, List.of(
                c("hw-hevc-small", "video/hevc", true, 1920, 1080),
                c("hw-avc", "video/avc", true, 4096, 4096)), 3840, 1624);
        assertEquals("hw-avc", chosen.name(), "auto AVC fallback");
    }

    private static void testExplicitAv1() {
        var chosen = CodecPolicy.choose("video/av01", List.of(
                c("hw-av1", "video/av01", true, 4096, 4096),
                c("hw-hevc", "video/hevc", true, 4096, 4096)), 1920, 812);
        assertEquals("hw-av1", chosen.name(), "explicit AV1");
    }

    private static void testUnsupportedDimensions() {
        boolean thrown = false;
        try {
            CodecPolicy.choose("video/hevc", List.of(c("hevc", "video/hevc", true, 4096, 2160)), 5108, 2160);
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        assertTrue(thrown, "unsupported dimensions must fail");
    }

    private static void testSoftwareFallback() {
        var chosen = CodecPolicy.choose(CodecPolicy.AUTO, List.of(
                c("sw-hevc", "video/hevc", false, 4096, 4096),
                c("sw-avc", "video/avc", false, 4096, 4096)), 1920, 812);
        assertEquals("sw-hevc", chosen.name(), "software HEVC before software AVC");
    }

    private static void testAutoDoesNotSelectAv1() {
        boolean thrown = false;
        try {
            CodecPolicy.choose(CodecPolicy.AUTO, List.of(c("hw-av1", "video/av01", true, 4096, 4096)), 1920, 812);
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        assertTrue(thrown, "auto must not silently select AV1");
    }

    private static void testOrderedFallbacks() {
        var ordered = CodecPolicy.orderedCandidates(CodecPolicy.AUTO, List.of(
                c("sw-avc", "video/avc", false, 8192, 8192),
                c("hw-avc", "video/avc", true, 8192, 8192),
                c("sw-hevc", "video/hevc", false, 8192, 8192),
                c("hw-hevc", "video/hevc", true, 8192, 8192)), 3840, 1624);
        assertEquals("hw-hevc", ordered.get(0).name(), "fallback order 0");
        assertEquals("hw-avc", ordered.get(1).name(), "fallback order 1");
        assertEquals("sw-hevc", ordered.get(2).name(), "fallback order 2");
        assertEquals("sw-avc", ordered.get(3).name(), "fallback order 3");
    }

    private static void testExplicitCodecOrdersHardwareBeforeSoftware() {
        var ordered = CodecPolicy.orderedCandidates(CodecPolicy.HEVC, List.of(
                c("sw-hevc", "video/hevc", false, 4096, 4096),
                c("hw-hevc", "video/hevc", true, 4096, 4096),
                c("hw-avc", "video/avc", true, 4096, 4096)), 1920, 812);
        assertEquals("hw-hevc", ordered.get(0).name(), "explicit hardware first");
        assertEquals("sw-hevc", ordered.get(1).name(), "explicit software second");
        assertTrue(ordered.size() == 2, "explicit codec filters other MIME types");
    }

    private static void assertEquals(String expected, String actual, String name) {
        if (!expected.equals(actual)) throw new AssertionError(name + ": expected " + expected + " got " + actual);
    }

    private static void assertTrue(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
}
