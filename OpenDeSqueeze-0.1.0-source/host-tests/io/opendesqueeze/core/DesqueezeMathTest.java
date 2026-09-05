package io.opendesqueeze.core;

public final class DesqueezeMathTest {
    public static void main(String[] args) {
        testPreserveWidth133();
        testPreserveHeight133();
        testCustom155();
        testEvenRounding();
        testInvalidFactor();
        System.out.println("DesqueezeMathTest: PASS");
    }

    private static void testPreserveWidth133() {
        DesqueezeMath.Size s = DesqueezeMath.calculate(3840, 2160, 1.33, DesqueezeMath.Mode.PRESERVE_WIDTH);
        assertEquals(3840, s.width(), "preserve-width width");
        assertEquals(1624, s.height(), "preserve-width height");
    }

    private static void testPreserveHeight133() {
        DesqueezeMath.Size s = DesqueezeMath.calculate(3840, 2160, 1.33, DesqueezeMath.Mode.PRESERVE_HEIGHT);
        assertEquals(5108, s.width(), "preserve-height width");
        assertEquals(2160, s.height(), "preserve-height height");
    }

    private static void testCustom155() {
        DesqueezeMath.Size s = DesqueezeMath.calculate(4000, 3000, 1.55, DesqueezeMath.Mode.PRESERVE_WIDTH);
        assertEquals(4000, s.width(), "1.55 width");
        assertEquals(1936, s.height(), "1.55 height");
    }

    private static void testEvenRounding() {
        DesqueezeMath.Size s = DesqueezeMath.calculate(1921, 1081, 1.33, DesqueezeMath.Mode.PRESERVE_WIDTH);
        assertTrue((s.width() & 1) == 0, "width must be even");
        assertTrue((s.height() & 1) == 0, "height must be even");
    }

    private static void testInvalidFactor() {
        boolean thrown = false;
        try {
            DesqueezeMath.calculate(1920, 1080, 1.0, DesqueezeMath.Mode.PRESERVE_WIDTH);
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        assertTrue(thrown, "factor 1.0 must be rejected");
    }

    private static void assertEquals(int expected, int actual, String name) {
        if (expected != actual) throw new AssertionError(name + ": expected " + expected + " got " + actual);
    }

    private static void assertTrue(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
}
