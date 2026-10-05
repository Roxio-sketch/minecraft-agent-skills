package dev.vfxworkbench;

import dev.vfxworkbench.core.ParamState;
import dev.vfxworkbench.core.ParamRegistry;
import dev.vfxworkbench.effects.water.WaterSkillSchema;
import dev.vfxworkbench.effects.water.WaveField;

/** Non-GUI checks that HOT parameters re-read live state without rebuilding the
 *  wave field, exactly what the in-game workbench relies on. */
public final class WorkbenchWaveCheck {
    public static void main(String[] args) {
        WaterSkillSchema.register();
        ParamState state = ParamRegistry.state(WaterSkillSchema.ID);

        // 1. propagation + symmetry under defaults.
        WaveField f = new WaveField(state);
        f.impulse(32, 32, 4, 0.18);
        for (int i = 0; i < 12; i++) f.tick();
        if (Math.abs(f.sample(44, 32, 1)) < 1e-7) throw new AssertionError("wave did not propagate");
        for (int z = 0; z < 65; z++) for (int x = 0; x < 65; x++)
            if (Math.abs(f.sample(x, z, 1) - f.sample(z, x, 1)) > 1e-5) throw new AssertionError("symmetry");
        System.out.println("PASS: propagation + symmetry");

        // 2. HOT speed change is observed by the same (non-rebuilt) field.
        ParamState a = freshState(); WaveField fieldA = new WaveField(a);
        ParamState b = freshState(); WaveField fieldB = new WaveField(b);
        fieldA.impulse(32, 32, 4, 0.18); fieldB.impulse(32, 32, 4, 0.18);
        for (int i = 0; i < 10; i++) { fieldA.tick(); fieldB.tick(); }
        b.setDouble("speed", 0.1);   // HOT: no rebuild, solver re-reads next tick
        for (int i = 0; i < 10; i++) { fieldA.tick(); fieldB.tick(); }
        double centerA = fieldA.sample(40, 32, 1);
        double centerB = fieldB.sample(40, 32, 1);
        if (Math.abs(centerA - centerB) < 1e-4)
            throw new AssertionError("HOT speed change did not affect the running field");
        System.out.println("PASS: HOT speed applies to already-running field (delta=" + (centerA - centerB) + ")");

        // 3. HOT damping + maxHeight re-read live: extreme clamp binds values.
        ParamState c = freshState(); WaveField fieldC = new WaveField(c);
        fieldC.impulse(32, 32, 4, 0.18);
        c.setDouble("maxHeight", 0.05);  // HOT clamp
        for (int i = 0; i < 50; i++) fieldC.tick();
        for (int z = 0; z < 65; z++) for (int x = 0; x < 65; x++)
            if (Math.abs(fieldC.sample(x, z, 1)) > 0.0501f) throw new AssertionError("HOT maxHeight not honored");
        System.out.println("PASS: HOT maxHeight clamp re-read live");

        // 4. RESOURCE parameter fires a rebuild signal; grid change requires new field.
        final boolean[] fired = { false };
        b.onResourceChange(() -> fired[0] = true);
        b.set("grid", "97");
        if (!fired[0]) throw new AssertionError("RESOURCE change did not fire rebuild signal");
        WaveField rebuilt = new WaveField(b);
        if (rebuilt.size != 97) throw new AssertionError("RESOURCE grid change not rebuilt");
        System.out.println("PASS: RESOURCE params signal rebuild and new field honors grid");

        System.out.println("ALL WORKBENCH CORE CHECKS PASSED");
        WaveField world=new WaveField(freshState());
        world.impulseWorld(4,4,.45,.18);
        if(world.sample(32,32,1)<.17)throw new AssertionError("block-space center missing");
        if(Math.abs(world.sample(4,4,1))>1e-7)throw new AssertionError("block position treated as cell index");
        System.out.println("PASS: block-space impulse center and radius");
    }

    private static ParamState freshState() {
        return new ParamState(ParamRegistry.schema(WaterSkillSchema.ID));
    }
}
