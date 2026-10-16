package dev.spacerift;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
@Mod("spacerift")
public final class SpaceRift {public SpaceRift(){DistExecutor.safeRunWhenOn(Dist.CLIENT,()->RiftRenderer::initialize);}}
