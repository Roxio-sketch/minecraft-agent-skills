package dev.cosmiclab;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;

/** Actual world data; 8 m biome voxels and 1 m precipitation ceilings. */
final class LabPrecipitation {
    static final int CELL = 8, SIDE = 16, SURFACE = 128;
    static int volumeTexture, surfaceTexture, updates, rainCells, snowCells, noneCells, unknownCells;
    static int originX, originY, originZ, surfaceX, surfaceZ;
    static float lastCpuMs;
    static boolean ready;
    private static ClientLevel cachedLevel;
    private static long lastTick = Long.MIN_VALUE;

    static void release() {
        if (volumeTexture != 0) GlStateManager._deleteTexture(volumeTexture);
        if (surfaceTexture != 0) GlStateManager._deleteTexture(surfaceTexture);
        volumeTexture = surfaceTexture = 0; ready = false; cachedLevel = null; lastTick = Long.MIN_VALUE;
    }

    static void prepare(ClientLevel level, double x, double y, double z) {
        int ox = Math.floorDiv((int)Math.floor(x), CELL) * CELL - SIDE * CELL / 2;
        int oy = Math.floorDiv((int)Math.floor(y), CELL) * CELL - SIDE * CELL / 2;
        int oz = Math.floorDiv((int)Math.floor(z), CELL) * CELL - SIDE * CELL / 2;
        int sx = (int)Math.floor(x) - SURFACE / 2, sz = (int)Math.floor(z) - SURFACE / 2;
        long tick = level.getGameTime();
        if (ready && cachedLevel == level && ox == originX && oy == originY && oz == originZ &&
                sx == surfaceX && sz == surfaceZ && tick - lastTick < 10) return;
        long start = System.nanoTime();
        FloatBuffer biome = BufferUtils.createFloatBuffer(64 * 64 * 4);
        FloatBuffer surface = BufferUtils.createFloatBuffer(SURFACE * SURFACE * 4);
        BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
        rainCells = snowCells = noneCells = unknownCells = 0;
        for (int iy = 0; iy < SIDE; iy++) for (int iz = 0; iz < SIDE; iz++) for (int ix = 0; ix < SIDE; ix++) {
            point.set(ox + ix * CELL + CELL / 2, oy + iy * CELL + CELL / 2, oz + iz * CELL + CELL / 2);
            boolean loaded = level.hasChunkAt(point);
            Biome.Precipitation kind = loaded ? level.getBiome(point).value().getPrecipitationAt(point) : Biome.Precipitation.NONE;
            int index = (ix + iy % 4 * SIDE + (iz + iy / 4 * SIDE) * 64) * 4;
            biome.put(index, kind == Biome.Precipitation.RAIN ? 1f : 0f);
            biome.put(index + 1, kind == Biome.Precipitation.SNOW ? 1f : 0f);
            biome.put(index + 3, loaded ? 1f : 0f);
            if (!loaded) unknownCells++; else if (kind == Biome.Precipitation.RAIN) rainCells++;
            else if (kind == Biome.Precipitation.SNOW) snowCells++; else noneCells++;
        }
        for (int iz = 0; iz < SURFACE; iz++) for (int ix = 0; ix < SURFACE; ix++) {
            point.set(sx + ix, (int)Math.floor(y), sz + iz);
            boolean loaded = level.hasChunkAt(point);
            int index = (ix + iz * SURFACE) * 4;
            surface.put(index, loaded ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, point.getX(), point.getZ()) : 0f);
            surface.put(index + 1, loaded ? 1f : 0f); surface.put(index + 3, 1f);
        }
        try (LabEnvironment.State state = new LabEnvironment.State()) {
            volumeTexture = upload(volumeTexture, 64, biome);
            surfaceTexture = upload(surfaceTexture, SURFACE, surface);
        }
        cachedLevel = level; originX = ox; originY = oy; originZ = oz; surfaceX = sx; surfaceZ = sz;
        lastTick = tick; ready = true; updates++; lastCpuMs = (System.nanoTime() - start) / 1_000_000f;
    }

    private static int upload(int texture, int side, FloatBuffer data) {
        boolean create = texture == 0;
        if (create) texture = GlStateManager._genTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(texture);
        if (create) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, side, side, 0, GL11.GL_RGBA, GL11.GL_FLOAT, data);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } else GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, side, side, GL11.GL_RGBA, GL11.GL_FLOAT, data);
        return texture;
    }

    static void bind(ShaderInstance shader) {
        shader.safeGetUniform("WorldPrecipOrigin").set((float)originX, (float)originY, (float)originZ);
        shader.safeGetUniform("WorldPrecipGeometry").set((float)CELL, (float)SIDE, 4f, ready ? 1f : 0f);
        shader.safeGetUniform("WorldSurfaceGrid").set((float)surfaceX, (float)surfaceZ, 1f, (float)SURFACE);
        shader.safeGetUniform("WorldPrecipPresence").set(ready && rainCells > 0 ? 1f : 0f, ready && snowCells > 0 ? 1f : 0f);
        shader.setSampler("WeatherVolumeSampler", volumeTexture);
        shader.setSampler("SurfaceSampler", surfaceTexture);
    }
}
