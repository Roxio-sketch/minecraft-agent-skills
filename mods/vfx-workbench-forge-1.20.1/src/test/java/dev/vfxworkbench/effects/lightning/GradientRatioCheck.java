package dev.vfxworkbench.effects.lightning;

/** Pure color and proportion regressions; no renderer or game context is required. */
final class GradientRatioCheck {
    private static int assertions;

    public static void main(String[] args) { run(); }

    static void run() {
        assertions = 0;
        checkDefaultAppearance();
        checkExtremesAndTints();
        checkProportions();
        checkFiniteMonotonicAndSymmetric();
        checkDisabled();
        System.out.println("PASS: gradient start/end proportions, exact 50% legacy colors, extremes, base tints, midpoint, symmetry, monotonicity and disabled isolation (" + assertions + " assertions)");
    }

    private static void checkDefaultAppearance() {
        LightningRecipe r = new LightningRecipe();
        require(r.gradientRatio == 50, "gradient ratio default changed");
        r.gradientEnabled = true;
        String[][] gradients = {{"#ff0000", "#0000ff"}, {"#d46721", "#017bec"}, {"#000000", "#ffffff"}};
        for (String base : new String[]{"#ffffff", "#8040c0", "#87cfff", "#012345"}) {
            r.coreColor = base; r.glowColor = base;
            for (String[] gradient : gradients) {
                r.gradientStart = gradient[0]; r.gradientEnd = gradient[1];
                for (boolean halo : new boolean[]{false, true}) {
                    for (int i = 0; i <= 512; i++) {
                        double x = i / 512.0;
                        close(LightningAppearance.gradientMix(x, 50), x, 0, "default interpolation changed");
                        exact(LightningAppearance.colorAt(r, halo, x), legacyColor(r, halo, x), "default colors changed");
                    }
                    // Half-byte boundaries exercise the original channel rounding.
                    for (int i = 0; i < 255; i++)
                        exact(LightningAppearance.colorAt(r, halo, (i + .5) / 255), legacyColor(r, halo, (i + .5) / 255), "default half-byte rounding changed");
                    for (double x : new double[]{-1, 2})
                        exact(LightningAppearance.colorAt(r, halo, x), legacyColor(r, halo, x), "default clamp changed");
                }
            }
        }
    }

    private static void checkExtremesAndTints() {
        LightningRecipe r = new LightningRecipe();
        r.gradientEnabled = true; r.gradientStart = "#ff0000"; r.gradientEnd = "#0000ff";
        r.coreColor = "#8040c0"; r.glowColor = "#206080";
        for (double percentage : new double[]{0, 100}) {
            r.gradientRatio = percentage;
            for (boolean halo : new boolean[]{false, true})
                for (double x : new double[]{-1, 0, .25, .5, .75, 1, 2})
                    exact(LightningAppearance.colorAt(r, halo, x), legacyColor(r, halo, percentage == 0 ? 1 : 0), "extreme ratio must select one color throughout");
        }
        r.gradientRatio = 75;
        exact(LightningAppearance.colorAt(r, false, .5), new float[]{95 / 255f, 0, 48 / 255f}, "core tint or start-color weighting changed");
        exact(LightningAppearance.colorAt(r, true, .5), new float[]{23 / 255f, 0, 32 / 255f}, "halo tint or start-color weighting changed");
        r.gradientRatio = 25;
        exact(LightningAppearance.colorAt(r, false, .5), new float[]{32 / 255f, 0, 143 / 255f}, "core end-color weighting changed");
        exact(LightningAppearance.colorAt(r, true, .5), new float[]{8 / 255f, 0, 95 / 255f}, "halo end-color weighting changed");
    }

    private static void checkProportions() {
        close(LightningAppearance.gradientMix(.5, 25), .75, 0, "25% does not favor the end color");
        close(LightningAppearance.gradientMix(.5, 75), .25, 0, "75% does not favor the start color");
        for (double percentage : new double[]{1, 5, 25, 50, 75, 95, 99}) {
            close(LightningAppearance.gradientMix(percentage / 100, percentage), .5, 1e-15, "equal-color point does not match the ratio");
            close(LightningAppearance.gradientMix(0, percentage), 0, 0, "start endpoint changed");
            close(LightningAppearance.gradientMix(1, percentage), 1, 0, "end endpoint changed");
            close(LightningAppearance.gradientMix(-1, percentage), 0, 0, "start clamp changed");
            close(LightningAppearance.gradientMix(2, percentage), 1, 0, "end clamp changed");
        }
    }

    private static void checkFiniteMonotonicAndSymmetric() {
        double[] ratios = {0, Double.MIN_VALUE, 1e-300, 1e-8, .1, 1, 25, 50, 75, 99, 99.9, Math.nextDown(100.0), 100};
        for (double percentage : ratios) {
            double previous = -1;
            for (int i = 0; i <= 1000; i++) {
                double t = LightningAppearance.gradientMix(i / 1000.0, percentage);
                require(Double.isFinite(t) && t >= 0 && t <= 1, "nonfinite or out-of-range gradient weight");
                require(t >= previous, "gradient reverses along the path");
                previous = t;
            }
        }
        for (int i = 0; i <= 200; i++) {
            double x = i / 200.0, previous = 1;
            for (double percentage : new double[]{0, .1, 1, 25, 50, 75, 99, 99.9, 100}) {
                double t = LightningAppearance.gradientMix(x, percentage);
                require(t <= previous + 1e-15, "increasing ratio gives less start color");
                close(t + LightningAppearance.gradientMix(1 - x, 100 - percentage), 1, 1e-12, "start/end exchange lost symmetry");
                previous = t;
            }
        }
    }

    private static void checkDisabled() {
        LightningRecipe r = new LightningRecipe();
        r.gradientEnabled = false; r.coreColor = "#8040c0"; r.glowColor = "#206080";
        r.gradientStart = null; r.gradientEnd = "unused";
        for (double percentage : new double[]{0, 25, 50, 75, 100, Double.NaN}) {
            r.gradientRatio = percentage;
            for (double x : new double[]{-1, 0, .5, 1, 2, Double.NaN}) {
                exact(LightningAppearance.colorAt(r, false, x), new float[]{128 / 255f, 64 / 255f, 192 / 255f}, "disabled gradient changed core tint");
                exact(LightningAppearance.colorAt(r, true, x), new float[]{32 / 255f, 96 / 255f, 128 / 255f}, "disabled gradient changed halo tint");
            }
        }
    }

    /** Frozen pre-ratio color contract, evaluated independently per channel. */
    private static float[] legacyColor(LightningRecipe r, boolean halo, double along) {
        int base = Integer.parseInt((halo ? r.glowColor : r.coreColor).substring(1), 16);
        int start = Integer.parseInt(r.gradientStart.substring(1), 16), end = Integer.parseInt(r.gradientEnd.substring(1), 16);
        double x = Math.max(0, Math.min(1, along));
        float[] color = new float[3];
        for (int i = 0; i < 3; i++) {
            int shift = 16 - i * 8;
            int a = (start >> shift) & 255, b = (end >> shift) & 255;
            int gradient = (int)Math.round(a + (b - a) * x);
            color[i] = (int)(((base >> shift) & 255) * gradient / 255.0) / 255f;
        }
        return color;
    }

    private static void exact(float[] actual, float[] expected, String message) {
        require(actual.length == expected.length, message);
        for (int i = 0; i < actual.length; i++)
            require(Float.floatToIntBits(actual[i]) == Float.floatToIntBits(expected[i]), message + " channel " + i);
    }
    private static void close(double actual, double expected, double tolerance, String message) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance, message);
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
