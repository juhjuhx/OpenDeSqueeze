package io.opendesqueeze.core;

public final class OutputNamingTest {
    public static void main(String[] args) {
        assertEquals("clip_desqueezed.mp4", OutputNaming.withSuffix("clip.mov", "mp4"));
        assertEquals("photo_desqueezed.jpg", OutputNaming.withSuffix("photo", "jpg"));
        assertEquals("a.b.c_desqueezed.png", OutputNaming.withSuffix("a.b.c.jpeg", "png"));
        assertEquals("media_desqueezed.webp", OutputNaming.withSuffix("", "webp"));
        System.out.println("OutputNamingTest: PASS");
    }
    private static void assertEquals(String e, String a) { if (!e.equals(a)) throw new AssertionError(e + " != " + a); }
}
