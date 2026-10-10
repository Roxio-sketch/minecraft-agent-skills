package dev.vfxworkbench.effects.water;

import dev.vfxworkbench.core.ParamState;

/** Fixed-step height/velocity solver mirroring the water-ripples skill. HOT
 *  parameters are re-read from ParamState each tick / each draw. */
public final class WaveField {
    public final int size;

    public ParamState stateForRender() { return state; }
    private float[] height, velocity, nextHeight, nextVelocity;
    private final ParamState state;
    private final String solver;
    private final String boundary;

    public WaveField(ParamState state) {
        this.state = state;
        this.size = state.getInt("grid");
        this.solver = state.get("solver");
        this.boundary = state.get("boundary");
        if (size < 16 || size > 256) throw new IllegalArgumentException("grid 16..256");
        int n = size * size;
        height = new float[n]; velocity = new float[n]; nextHeight = new float[n]; nextVelocity = new float[n];
    }

    public void impulse(double x, double z, double radius, double strength) {
        if (!Double.isFinite(x + z + radius + strength) || radius <= 0 || Math.abs(strength) > 0.5)
            throw new IllegalArgumentException("invalid impulse");
        for (int j = 1; j < size - 1; j++) for (int i = 1; i < size - 1; i++) {
            double d = Math.max(0, 1 - Math.hypot(i - x, j - z) / radius);
            int k = j * size + i;
            float h = (float) ((0.5 - 0.5 * Math.cos(Math.PI * d)) * strength);
            height[k] = clampHeight(height[k] + h);
            if (solver.equals("history")) velocity[k] = clampHeight(velocity[k] + h);
        }
    }

    /** Positions and radius in blocks, measured from the patch corner. */
    public void impulseWorld(double x,double z,double radius,double strength) {
        double scale=(size-1)/state.getDouble("width");
        impulse(x*scale,z*scale,radius*scale,strength);
    }

    public void tick() {
        float spd = state.getFloat("speed");
        float dmp = state.getFloat("damping");
        float maxH = state.getFloat("maxHeight");
        for (int s = 0; s < 3; s++) step(spd, dmp, maxH);
    }

    private void step(float speed, float damping, float maxHeight) {
        boolean history = solver.equals("history");
        boolean absorb=boundary.equals("absorb");
        float accelerationScale=2.0f*speed*speed;
        for (int j = 1; j < size - 1; j++) for (int i = 1; i < size - 1; i++) {
            int k = j * size + i;
            float average = (height[k - 1] + height[k + 1] + height[k - size] + height[k + size]) * 0.25f;
            int edge = Math.min(Math.min(i, j), Math.min(size - 1 - i, size - 1 - j));
            float local = absorb && edge < 8 ? 0.90f + (damping - 0.90f) * edge / 8.0f : damping;
            float acceleration = (average - height[k]) * accelerationScale;
            if (history) {
                nextHeight[k] = clamp((2.0f * height[k] - velocity[k] + acceleration) * local, maxHeight);
                nextVelocity[k] = height[k];
            } else {
                float v = (velocity[k] + acceleration) * local;
                nextHeight[k] = clamp(height[k] + v, maxHeight);
                nextVelocity[k] = nextHeight[k] - height[k];
            }
        }
        if (boundary.equals("reflect")) {
            for (int i = 1; i < size - 1; i++) {
                nextHeight[i] = nextHeight[size + i];
                nextHeight[(size - 1) * size + i] = nextHeight[(size - 2) * size + i];
                nextHeight[i * size] = nextHeight[i * size + 1];
                nextHeight[i * size + size - 1] = nextHeight[i * size + size - 2];
            }
        }
        float[] tmp = height; height = nextHeight; nextHeight = tmp;
        tmp = velocity; velocity = nextVelocity; nextVelocity = tmp;
    }

    private static float clamp(float v, float max) {
        if (v > max) return max;
        if (v < -max) return -max;
        return v;
    }

    private float clampHeight(float v) {
        float maxH = state.getFloat("maxHeight");
        if (v > maxH) return maxH;
        if (v < -maxH) return -maxH;
        return v;
    }

    public float sample(int x, int z, float partial) {
        int x0 = clampCell(x), x1 = clampCell(x + 1);
        int z0 = clampCell(z), z1 = clampCell(z + 1);
        float h00 = height[z0 * size + x0], h10 = height[z0 * size + x1];
        float h01 = height[z1 * size + x0], h11 = height[z1 * size + x1];
        float fx = x % 1.0f, fz = z % 1.0f;
        float top = h00 + (h10 - h00) * fx;
        float bottom = h01 + (h11 - h01) * fx;
        return top + (bottom - top) * fz;
    }

    private int clampCell(int c) {
        if (c < 0) return 0;
        if (c > size - 1) return size - 1;
        return c;
    }
}
