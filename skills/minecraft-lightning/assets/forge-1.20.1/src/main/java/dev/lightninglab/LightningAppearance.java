package dev.lightninglab;

/** Pure material contract shared by both ribbon passes and their regression checks. */
final class LightningAppearance {
    private LightningAppearance() {}

    static double ribbonScale(LightningRecipe r, boolean halo, double baseWidth) {
        return halo ? r.glowWidth / baseWidth : 2;
    }

    static float[] colorAt(LightningRecipe r, boolean halo, double along) {
        int base = parse(halo ? r.glowColor : r.coreColor), rgb = base;
        if (r.gradientEnabled) {
            int a = parse(r.gradientStart), b = parse(r.gradientEnd);
            double t = gradientMix(along, r.gradientRatio);
            int gr = (int)Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
            int gg = (int)Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
            int gb = (int)Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
            // The original skill multiplies the gradient by each pass's base tint.
            rgb = ((int)(((base >> 16) & 255) * gr / 255.0) << 16)
                | ((int)(((base >> 8) & 255) * gg / 255.0) << 8)
                | (int)((base & 255) * gb / 255.0);
        }
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
    }

    /** End-color weight; a larger ratio gives the start color more of the path. */
    static double gradientMix(double along, double percentage) {
        double x = LightningMath.clamp(along, 0, 1), ratio = percentage / 100;
        if (ratio <= 0) return 1;
        if (ratio >= 1) return 0;
        // Preserve the old interpolation and channel rounding exactly at the default.
        if (ratio == .5) return x;
        double endWeight = x * (1 - ratio), startWeight = (1 - x) * ratio;
        return endWeight / (endWeight + startWeight);
    }

    private static int parse(String value) { return Integer.parseInt(value.substring(1), 16); }
}
