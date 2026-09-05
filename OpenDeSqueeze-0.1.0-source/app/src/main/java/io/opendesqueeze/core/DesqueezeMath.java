package io.opendesqueeze.core;

/** Pure geometry helpers for anamorphic desqueeze. */
public final class DesqueezeMath {
    public enum Mode { PRESERVE_WIDTH, PRESERVE_HEIGHT }

    public static final class Size {
        private final int width;
        private final int height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int width() { return width; }
        public int height() { return height; }

        @Override public String toString() { return width + "x" + height; }
    }

    private DesqueezeMath() {}

    public static Size calculate(int width, int height, double factor, Mode mode) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Input dimensions must be positive");
        }
        if (!Double.isFinite(factor) || factor <= 1.0 || factor > 3.0) {
            throw new IllegalArgumentException("Squeeze factor must be > 1.0 and <= 3.0");
        }
        if (mode == null) throw new IllegalArgumentException("Mode is required");

        if (mode == Mode.PRESERVE_WIDTH) {
            return new Size(toEven(width), toEven(height / factor));
        }
        return new Size(toEven(width * factor), toEven(height));
    }

    private static int toEven(double value) {
        long rounded = Math.round(value);
        if ((rounded & 1L) != 0L) rounded += 1L;
        if (rounded < 2L || rounded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Calculated dimension is out of range");
        }
        return (int) rounded;
    }
}
