package dev.vfxworkbench.effects.lightning;

import net.minecraft.world.phys.Vec3;

/** Host preview placement; independent of the portable 63-field visual recipe. */
final class LightningPlacement {
    static void validateDistance(double distance) {
        if(!Double.isFinite(distance)||distance<1||distance>128)
            throw new IllegalArgumentException("触发距离必须在 1–128 格之间");
    }
    static Vec3 forward(Vec3 feet, double yawDegrees, double distance) {
        validateDistance(distance);
        double yaw=Math.toRadians(yawDegrees);
        return feet.add(-Math.sin(yaw)*distance,0,Math.cos(yaw)*distance);
    }
}
