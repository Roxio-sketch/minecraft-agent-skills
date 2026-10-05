package dev.blackholelab;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Perspective eye in the same camera-relative world coordinates as depth reconstruction. */
final class BlackHoleCamera {
    private BlackHoleCamera() {}

    static Vec3 origin(Matrix4f inverseViewProjection) {
        // Minecraft puts walking translation in the projection, before camera
        // rotation. Inverse(view) alone therefore misses the displaced eye.
        // A perspective eye projects to (0,0,z,0), including off-axis zoom,
        // hurt roll and the affine portal/screen distortion before projection.
        Vector4f eye=new Vector4f(0,0,1,0).mul(inverseViewProjection);
        if(!Float.isFinite(eye.x)||!Float.isFinite(eye.y)||!Float.isFinite(eye.z)
            ||!Float.isFinite(eye.w)||Math.abs(eye.w)<1e-8f)
            throw new IllegalStateException("Invalid perspective eye in world view projection");
        return new Vec3(eye.x/(double)eye.w,eye.y/(double)eye.w,eye.z/(double)eye.w);
    }
}
