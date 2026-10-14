package dev.alchemyvfx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Read-only, version-specific host integration. No host classes/assets are bundled. */
public final class HostAdapter {
    static final String HOST = "cn.blockforge.generated.generatedmod.";
    private static boolean checked, available, hookInstalled;
    static String lastError = "";
    private static Class<?> furnaceClass;
    private static Method formed, inward, rotation, brewing, start, finish, result, age, fire, seed, tier, completed;

    public record Furnace(BlockEntity entity, BlockPos core, Vec3 anchor, double yaw, boolean brewing,
                          long startTick, long finishTick, boolean hasResult, float completionAge,
                          String activeFireId, long visualSeed, int tier, int grade) {
        public double progress(double gameTick) {
            return brewing ? Math.max(0, Math.min(1, (gameTick-startTick)/Math.max(1, finishTick-startTick))) : 0;
        }
        public boolean visible() { return brewing || hasResult; }
    }

    private static void discover() {
        if (checked) return;
        checked = true;
        try {
            furnaceClass = Class.forName(HOST+"AlchemyFurnaceBlockEntity");
            formed = Class.forName(HOST+"AlchemyFurnaceBlock").getMethod("isFormed", BlockState.class);
            inward = Class.forName(HOST+"AlchemyFurnaceStructure").getMethod("inwardOf", BlockState.class);
            rotation = Class.forName(HOST+"AlchemyFurnaceVisualLayout").getMethod("rotation", Direction.class);
            brewing=furnaceClass.getMethod("brewing"); start=furnaceClass.getMethod("startTick");
            finish=furnaceClass.getMethod("finishTick"); result=furnaceClass.getMethod("hasResult");
            age=furnaceClass.getMethod("completionAge",float.class); fire=furnaceClass.getMethod("activeFireId");
            seed=furnaceClass.getMethod("visualSeed"); tier=furnaceClass.getMethod("tier");
            completed=furnaceClass.getMethod("completedResult");
            available=true;
            try {
                Object version=Class.forName(HOST+"client.AlchemyFurnaceEffects").getMethod("externalVolumeHookVersion").invoke(null);
                hookInstalled=version instanceof Number n && n.intValue()==1;
            } catch (ReflectiveOperationException missingHook) { hookInstalled=false; }
        } catch (ReflectiveOperationException | LinkageError missingHost) { lastError=missingHost.toString(); }
    }
    static boolean available() { discover(); return available; }
    static boolean hookInstalled() { discover(); return hookInstalled; }
    public static Furnace read(Object value, float partial) {
        discover();
        if (!available || !furnaceClass.isInstance(value) || !(value instanceof BlockEntity be)
                || be.isRemoved() || be.getLevel()!=Minecraft.getInstance().level) return null;
        try {
            if (!(Boolean)formed.invoke(null,be.getBlockState())) return null;
            Direction direction=(Direction)inward.invoke(null,be.getBlockState());
            double yaw=Math.toRadians(((Number)rotation.invoke(null,direction)).doubleValue());
            BlockPos p=be.getBlockPos();
            return new Furnace(be,p,new Vec3(p.getX()+.5,p.getY(),p.getZ()+.5),yaw,
                    (Boolean)brewing.invoke(be),((Number)start.invoke(be)).longValue(),((Number)finish.invoke(be)).longValue(),
                    (Boolean)result.invoke(be),((Number)age.invoke(be,partial)).floatValue(),(String)fire.invoke(be),
                    ((Number)seed.invoke(be)).longValue(),((Number)tier.invoke(be)).intValue(),readGrade((ItemStack)completed.invoke(be)));
        } catch (ReflectiveOperationException failure) { lastError=failure.toString(); return null; }
    }
    private static int readGrade(ItemStack stack) {
        if (stack.isEmpty()) return 1;
        try {
            Object data=Class.forName(HOST+"alchemy.PillData").getMethod("read",ItemStack.class).invoke(null,stack);
            if (data==null) return 1;
            Object grade=data.getClass().getMethod("grade").invoke(data);
            return Math.max(1,Math.min(9,((Number)grade.getClass().getMethod("value").invoke(grade)).intValue()));
        } catch (ReflectiveOperationException absentGrade) { return 1; }
    }
    /** Scans already loaded client chunks; never asks the server to load a chunk. */
    static List<Furnace> loaded(float partial) {
        List<Furnace> list=new ArrayList<>();
        var mc=Minecraft.getInstance();
        if (!available() || mc.level==null || mc.player==null) return list;
        int radius=mc.options.renderDistance().get()+1;
        int cx=mc.player.blockPosition().getX()>>4,cz=mc.player.blockPosition().getZ()>>4;
        for (int x=cx-radius;x<=cx+radius;x++) for (int z=cz-radius;z<=cz+radius;z++) {
            var chunk=mc.level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);
            if (chunk==null) continue;
            for (var be:chunk.getBlockEntities().values()) {
                Furnace furnace=read(be,partial);
                if (furnace!=null && furnace.visible()) list.add(furnace);
            }
        }
        return list;
    }
    private HostAdapter() {}
}
