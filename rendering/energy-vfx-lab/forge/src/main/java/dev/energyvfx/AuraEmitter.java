package dev.energyvfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;

/**
 * Original, client-only, world-space density packet simulation. No billboard is drawn here.
 * A no-draw player layer captures the actual rendered model matrix and animated limb cubes;
 * the volume pass consumes the resulting detached packets. Vanilla baked held models also
 * retain their arm and third-person item display transforms. An arbitrary BEWLR/GeckoLib
 * skeleton needs its own surface provider; its fallback emits near the actual held mount.
 */
@Mod.EventBusSubscriber(modid = EnergyVfx.ID, value = Dist.CLIENT)
public final class AuraEmitter {
    static final int CAPACITY = 32;
    private static final int SOURCE_CAPACITY = 512;
    private static final double DT = 1.0 / 20.0;
    private static final Packet[] packets = new Packet[CAPACITY];
    private static AbstractClientPlayer target;
    private static Vec3 noiseBase = Vec3.ZERO;
    private static Vec3 lastPlayerPosition = Vec3.ZERO;
    private static Matrix4f inverseView = new Matrix4f();
    private static Vec3 renderCamera = Vec3.ZERO;
    private static boolean viewCaptured;
    private static Frame frame, pendingFrame;
    private static PlayerModel<AbstractClientPlayer> fallbackModel;
    private static HumanoidModel<AbstractClientPlayer> innerArmor, outerArmor;
    private static Random random = new Random(42);
    private static int randomSeed = -1;
    private static double emissionRemainder;
    private static long serial,firstBodySerial,firstArmorSerial,firstHandSerial;
    static int packetCount, sourceCount, equippedSources;
    static int bakedGearSources, fallbackGearSources;
    static int bodyPacketCount, gearPacketCount;
    static double maxPacketDistance, maxDriftDistance;
    static boolean usingRenderedPose;
    static final float[] uploadMin=new float[3],uploadMax=new float[3];
    static int uploadCount,nearEyeHidden,nearEyeFaded,firstPersonThinned,nearViewThinned;
    static double cameraDistance,nearViewWeight;
    static final float[] uploadData=new float[CAPACITY*4],uploadStrengths=new float[CAPACITY],uploadFlows=new float[CAPACITY*4],uploadSeeds=new float[CAPACITY];

    private static final class Packet {
        Vec3 previous, position, birthPosition, velocity, inherited, phase, bias;
        Vec3 previousFlow, flow;
        double previousAge, age, life, strength, riseFactor;
        long birth;
        boolean gear,firstPersonEligible;
    }

    private static final class Source {
        final Vec3 a, b, c, normal;
        final double area, weight;
        final EquipmentSlot slot; // null means the player body.
        Source(Vec3 a, Vec3 b, Vec3 c, double weight, EquipmentSlot slot) {
            this.a = a; this.b = b; this.c = c; this.slot = slot; this.weight = weight;
            Vec3 cross = b.subtract(a).cross(c.subtract(a));
            area = cross.length() * .5;
            normal = cross.normalize();
        }
    }

    private static final class Frame {
        final List<Source> sources;
        final long tick;
        final int equipmentKey, baked, fallback;
        final EnumSet<EquipmentSlot> slots;
        Frame(Builder builder) {
            sources = List.copyOf(builder.sources);
            tick = EnergyVfx.Client.ticks;
            equipmentKey = equipmentKey(target);
            slots = builder.slots.clone();
            baked = builder.baked; fallback = builder.fallback;
        }
    }

    /** Keeps all sampled positions relative to the player's double-precision origin. */
    private static final class Builder {
        final Matrix4f fromRender;
        final Vec3 offset;
        final List<Source> sources = new ArrayList<>();
        final EnumSet<EquipmentSlot> slots = EnumSet.noneOf(EquipmentSlot.class);
        int baked, fallback;
        Builder(Matrix4f fromRender, Vec3 offset) {
            this.fromRender = new Matrix4f(fromRender); this.offset = offset;
        }
        Vec3 transform(Matrix4f part, float x, float y, float z) {
            Vector3f point = new Matrix4f(fromRender).mul(part).transformPosition(new Vector3f(x, y, z));
            return offset.add(point.x, point.y, point.z);
        }
        void triangle(Vec3 a, Vec3 b, Vec3 c, double weight, EquipmentSlot slot) {
            if (sources.size() >= SOURCE_CAPACITY || weight <= 0 || !finite(a) || !finite(b) || !finite(c)) return;
            // Reject malformed/unbounded mod meshes rather than expanding a full-screen ray volume.
            if (a.lengthSqr() > 144 || b.lengthSqr() > 144 || c.lengthSqr() > 144) return;
            Source source = new Source(a, b, c, weight, slot);
            if (source.area > .0000001 && Double.isFinite(source.area)) sources.add(source);
        }
        void quad(Matrix4f matrix, Vec3[] vertices, double weight, EquipmentSlot slot) {
            Vec3[] v = new Vec3[4];
            for (int i = 0; i < 4; i++) v[i] = transform(matrix, (float) vertices[i].x, (float) vertices[i].y, (float) vertices[i].z);
            triangle(v[0], v[1], v[2], weight, slot);
            triangle(v[0], v[2], v[3], weight, slot);
        }
    }

    static void bind(AbstractClientPlayer player) {
        if (target == player) return;
        reset();
        target = player;
        if (player != null) {
            lastPlayerPosition = player.position();
            noiseBase = lastPlayerPosition;
        }
    }

    static void reset() {
        for (int i = 0; i < CAPACITY; i++) packets[i] = null;
        target = null; frame = pendingFrame = null;
        fallbackModel = null; innerArmor = outerArmor = null;
        packetCount = sourceCount = equippedSources = bakedGearSources = fallbackGearSources = 0;
        bodyPacketCount = gearPacketCount = 0; maxPacketDistance = maxDriftDistance = 0;
        emissionRemainder = 0; serial = firstBodySerial = firstArmorSerial = firstHandSerial = 0; randomSeed = -1;
        usingRenderedPose = false; viewCaptured = false;
    }

    static Vec3 anchor(float partial) {
        if (target == null) return EnergyVfx.Client.anchor == null ? Vec3.ZERO : EnergyVfx.Client.anchor;
        return target.getPosition(Mth.clamp(partial, 0, 1)).add(0, target.getBbHeight() * .5, 0);
    }

    /** Called once at the end of a non-paused client tick; simulation is always 20 Hz. */
    static void tick() {
        Recipe recipe = EnergyVfx.Client.recipe;
        if (target == null || recipe == null || recipe.values.length < 48) return;
        if (target.isRemoved() || target.level() != Minecraft.getInstance().level) { reset(); return; }
        float[] u = recipe.values;
        Vec3 playerPosition = target.position();
        Vec3 travel = playerPosition.subtract(lastPlayerPosition);
        // A teleport is a new emitter origin, rather than a kilometre-long stale bounding volume.
        if (travel.lengthSqr() > 256) {
            for (int i = 0; i < CAPACITY; i++) packets[i] = null;
            noiseBase = playerPosition; frame = pendingFrame = null;
        }
        Vec3 inherited = travel.scale(20 * u[47]);
        if (inherited.lengthSqr() > 144) inherited = inherited.normalize().scale(12);
        lastPlayerPosition = playerPosition;
        int seed = Math.round(u[35]);
        if (seed != randomSeed) { random = new Random(seed); randomSeed = seed; }
        double time = EnergyVfx.Client.ticks * DT;
        for (int i = 0; i < CAPACITY; i++) {
            Packet packet = packets[i];
            if (packet == null) continue;
            packet.previous = packet.position;
            packet.previousAge = packet.age;
            packet.previousFlow = packet.flow;
            packet.age += DT;
            if (packet.age >= packet.life) { packets[i] = null; continue; }
            // Each packet has its own seeded phase; separated wisps do not move as one rigid cloud.
            // RK2 samples the divergence-free fields at the interval midpoint. Velocity is m/s.
            Vec3 k1 = velocity(packet, packet.position, time - DT, packet.previousAge, u);
            Vec3 midpoint = packet.position.add(k1.scale(DT * .5));
            Vec3 k2 = velocity(packet, midpoint, time - DT * .5, packet.previousAge + DT * .5, u);
            packet.position = packet.position.add(k2.scale(DT));
            packet.flow = velocity(packet, packet.position, time, packet.age, u);
            if (!finite(packet.position)) packets[i] = null;
        }
        boolean firstPerson = target == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        usingRenderedPose = !firstPerson && frame != null && EnergyVfx.Client.ticks - frame.tick <= 2 && frame.equipmentKey == equipmentKey(target);
        Frame current = usingRenderedPose ? frame : fallbackFrame(u);
        sourceCount = current.sources.size(); equippedSources = current.slots.size();
        bakedGearSources = current.baked; fallbackGearSources = current.fallback;
        emissionRemainder += u[41] * DT;
        int births = Math.min(CAPACITY, (int) emissionRemainder);
        emissionRemainder -= births;
        if (sourceCount == 0 || u[39] < .5) emissionRemainder = 0;
        else for (int i = 0; i < births; i++) {
            Source source = chooseSource(current, u);
            if (source != null) birth(source, playerPosition, inherited, u);
        }
        packetCount = bodyPacketCount = gearPacketCount = 0;
        maxPacketDistance = maxDriftDistance = 0;
        for (Packet packet : packets) if (packet != null) {
            packetCount++; if (packet.gear) gearPacketCount++; else bodyPacketCount++;
            maxPacketDistance = Math.max(maxPacketDistance, packet.position.distanceTo(anchor(1)));
            maxDriftDistance = Math.max(maxDriftDistance, packet.position.distanceTo(packet.birthPosition));
        }
    }

    private static Vec3 velocity(Packet packet, Vec3 position, double time, double age, float[] u) {
        Vec3 p = position.subtract(noiseBase);
        double t = time * u[7];
        Vec3 phase = packet.phase;
        Vec3 curl = new Vec3(Math.cos(p.y * 1.6 + t + phase.x),
                            .25 * Math.sin(p.z * 1.6 + .8 * t + phase.y),
                            Math.sin(p.x * 1.6 + .7 * t + phase.z)).scale(u[8] * .14);
        Vec3 warped = new Vec3(Math.sin(p.z * 2.3 - .6 * t + phase.z),
                              .2 * Math.cos(p.x * 2.1 - .75 * t + phase.x),
                              Math.cos(p.y * 2.3 + .55 * t + phase.y)).scale(u[9] * .10);
        // Outflow remains the main detached motion. Inherited walking speed fades more quickly.
        return packet.velocity.scale(Math.exp(-age * .12))
                .add(packet.inherited.scale(Math.exp(-age * 1.2)))
                .add(packet.bias).add(0, u[11] * .7 * packet.riseFactor, 0)
                .add(curl).add(warped);
    }

    private static void birth(Source source, Vec3 origin, Vec3 inherited, float[] u) {
        int slot = -1;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < CAPACITY; i++) {
            if (packets[i] == null) { slot = i; break; }
            if (packets[i].birth < oldest) { oldest = packets[i].birth; slot = i; }
        }
        double a = Math.sqrt(random.nextDouble()), b = random.nextDouble();
        Vec3 sample = source.a.scale(1 - a).add(source.b.scale(a * (1 - b))).add(source.c.scale(a * b));
        Vec3 jitter = new Vec3(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5).scale(u[43] * .15);
        Packet packet = new Packet();
        packet.position = origin.add(sample).add(source.normal.scale(.025)).add(jitter);
        packet.previous = packet.birthPosition = packet.position;
        // Uniform upper hemisphere mixed with the actual surface normal opens distinct paths.
        double y = random.nextDouble(), angle = random.nextDouble() * Math.PI * 2;
        double horizontal = Math.sqrt(Math.max(0, 1 - y * y));
        Vec3 hemisphere = new Vec3(horizontal * Math.cos(angle), y, horizontal * Math.sin(angle));
        Vec3 direction = source.normal.scale(.35).add(hemisphere.scale(.7)).add(0,.25,0).normalize();
        packet.velocity = direction.scale(u[40] * (.8 + random.nextDouble() * .4));
        packet.inherited = inherited;
        packet.phase = new Vec3(random.nextDouble(),random.nextDouble(),random.nextDouble()).scale(Math.PI * 2);
        packet.bias = new Vec3(Math.cos(packet.phase.x),.15 + .15 * Math.sin(packet.phase.y),Math.sin(packet.phase.z)).scale(u[40] * .18);
        packet.riseFactor = .7 + random.nextDouble() * .45;
        packet.life = u[42]; packet.strength = source.weight; packet.birth = serial++;
        packet.gear = source.slot != null;
        // Stable camera-only visibility: birth cadence avoids frame-to-frame selection popping.
        // Simulation and third-person rendering still retain every packet.
        boolean held=source.slot==EquipmentSlot.MAINHAND||source.slot==EquipmentSlot.OFFHAND;
        double gearFraction=u[39]>2.5?u[45]/Math.max(.001,u[44]+u[45]):1;
        int handStride=Math.max(1,(int)Math.ceil(Math.min(CAPACITY,u[41]*u[42])*gearFraction/Math.max(1,equippedSources)/3));
        packet.firstPersonEligible=held?(firstHandSerial++%handStride==0):packet.gear?(firstArmorSerial++%8==0):(firstBodySerial++%8==0);
        packet.flow = velocity(packet, packet.position, EnergyVfx.Client.ticks * DT, 0, u);
        packet.previousFlow = packet.flow;
        packets[slot] = packet;
    }

    /** Body/gear weights choose their group; each occupied slot gets a fair share of gear births. */
    private static Source chooseSource(Frame current, float[] u) {
        boolean body = u[39] < 1.5 || u[39] > 2.5;
        boolean gear = u[39] > 1.5;
        double bodyGain = body ? u[44] : 0, gearGain = gear && !current.slots.isEmpty() ? u[45] : 0;
        if (bodyGain + gearGain <= 0) return null;
        EquipmentSlot chosenSlot = null;
        if (random.nextDouble() * (bodyGain + gearGain) >= bodyGain) {
            EquipmentSlot[] slots = current.slots.toArray(EquipmentSlot[]::new);
            chosenSlot = slots[random.nextInt(slots.length)];
        }
        double total = 0;
        for (Source source : current.sources) if (source.slot == chosenSlot) total += source.area;
        if (total <= 0) return null;
        double pick = random.nextDouble() * total;
        for (Source source : current.sources) if (source.slot == chosenSlot && (pick -= source.area) <= 0) return source;
        return null;
    }

    /** Uploads world-space centres relative to the moving anchor; detached packets do not follow it. */
    static void upload(int program, float partial) {
        upload(program,partial,false);
    }
    static void uploadCached(int program,float partial){upload(program,partial,true);}
    private static int uniform(int program,String name,boolean cached){return cached?Uniforms.location(program,name):GL20.glGetUniformLocation(program,name);}
    private static void upload(int program,float partial,boolean cached){
        Recipe recipe = EnergyVfx.Client.recipe;
        float[] data=cached?uploadData:new float[CAPACITY*4],strengths=cached?uploadStrengths:new float[CAPACITY],flows=cached?uploadFlows:new float[CAPACITY*4],seeds=cached?uploadSeeds:new float[CAPACITY];
        java.util.Arrays.fill(uploadMin,Float.POSITIVE_INFINITY);java.util.Arrays.fill(uploadMax,Float.NEGATIVE_INFINITY);
        Vec3 centre = anchor(partial);
        float[] u = recipe == null ? null : recipe.values;
        var mc=Minecraft.getInstance();boolean selfFirst=target==mc.player&&mc.options.getCameraType().isFirstPerson();
        cameraDistance=mc.player==null?Double.POSITIVE_INFINITY:mc.gameRenderer.getMainCamera().getPosition().distanceTo(mc.player.getEyePosition(partial));
        // Begin the third-person budget before the camera enters a support ellipsoid.
        // This keeps the zoom transition continuous instead of leaving every packet
        // fully integrated until the final metres.
        double closeView=Math.max(0,Math.min(1,(4.2-cameraDistance)/2.2));closeView=closeView*closeView*(3-2*closeView);
        nearViewWeight=selfFirst?1:closeView;
        boolean nearPolicy=cached&&target==mc.player&&nearViewWeight>0&&!Boolean.getBoolean("energyvfx.disableNearEye")&&!QualityQa.enabled&&!(PerformanceQa.ENABLED&&(PerformanceQa.FLOW||(PerformanceQa.MOTION&&!selfFirst))&&!PerformanceQa.optimized);
        // Detached smoke can cross a third-person camera even when the camera stays
        // four metres from the player. Evaluate visibility against each real parcel.
        boolean parcelNearPolicy=cached&&target==mc.player&&!QualityQa.enabled
            &&!Boolean.getBoolean("energyvfx.disableNearEye")&&!Boolean.getBoolean("energyvfx.disableParcelNearEye")
            &&!(PerformanceQa.ENABLED&&(PerformanceQa.FLOW||(PerformanceQa.MOTION&&!selfFirst))&&!PerformanceQa.optimized);
        if(!nearPolicy)nearViewWeight=0;
        nearEyeHidden=nearEyeFaded=firstPersonThinned=nearViewThinned=0;
        int count = 0;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        double blend = Mth.clamp(partial, 0, 1);
        if (u != null && u.length >= 48 && u[39] > .5) for (Packet packet : packets) {
            if (packet == null) continue;
            Vec3 point = packet.previous.lerp(packet.position, blend).subtract(centre);
            double age = Mth.lerp(blend, packet.previousAge, packet.age);
            double fraction = Math.min(1, Math.max(0, age / packet.life));
            double radius = Math.max(.01, (.035 + u[43] * (.18 + fraction * .58)) * u[0]);
            double strength = Math.min(1, age / .12) * Math.pow(1 - fraction, 1.5) * packet.strength;
            // World velocity in m/s plus age supports independent shader backtracing inside each wisp.
            // The shader normalizes xyz only when constructing its flow-aligned local frame.
            Vec3 flow = packet.previousFlow.lerp(packet.flow, blend);
            if (!finite(point) || !finite(flow) || !Double.isFinite(radius) || !Double.isFinite(strength)) continue;
            if(nearPolicy&&!packet.firstPersonEligible){
                if(nearViewWeight>=1){nearViewThinned++;if(selfFirst)firstPersonThinned++;continue;}
                strength*=1-nearViewWeight;
                // Fading alone still pays the full per-packet ray cost. Once a
                // stable packet is visually negligible, compact it before bounds
                // and shader upload; newborn packets naturally fade in below this.
                if(!selfFirst&&nearViewWeight>.15&&strength<.06){nearEyeHidden++;continue;}
            }
            if(cached&&selfFirst&&u[46]<=0){nearEyeHidden++;continue;}
            if(nearPolicy||parcelNearPolicy){
                Vec3 eyeDelta=centre.add(point).subtract(mc.gameRenderer.getMainCamera().getPosition()).scale(1/radius);
                Vec3 axis=flow.length()>.001?flow.normalize():new Vec3(0,1,0);double along=eyeDelta.dot(axis);
                double distance=Math.sqrt(Math.max(0,eyeDelta.lengthSqr()-along*along+along*along/(u[1]*u[1])));
                double originalLow=packet.gear?2.8:4,originalHigh=packet.gear?4:6;
                // Wide projected old wisps become expensive before the camera enters
                // their support. Blend to a wider camera envelope at normal third-person
                // distance, while retaining the existing first-person/close-view budget.
                double low=parcelNearPolicy?Mth.lerp(nearViewWeight,packet.gear?6:8,originalLow):originalLow;
                double high=parcelNearPolicy?Mth.lerp(nearViewWeight,packet.gear?8:10,originalHigh):originalHigh;
                double fade=Mth.clamp((distance-low)/(high-low),0,1);fade=fade*fade*(3-2*fade);
                // In the zoom band, a nearly invisible parcel is more expensive
                // than useful. Remove only the stable low-opacity tail before
                // PacketBounds; the physical packet remains simulated.
                double zoomCullFade=.25+.35*Mth.clamp((3.25-cameraDistance)/1.25,0,1);
                if(!selfFirst&&parcelNearPolicy&&!Boolean.getBoolean("energyvfx.disableZoomParcelCull")&&cameraDistance<3.25&&fade<zoomCullFade){nearEyeHidden++;continue;}
                double fadeWeight=parcelNearPolicy?1:nearViewWeight;
                fade=1-fadeWeight+fadeWeight*fade;
                if(fade<=0){nearEyeHidden++;continue;}if(fade<1)nearEyeFaded++;strength*=fade;
            }
            data[count * 4] = (float) point.x; data[count * 4 + 1] = (float) point.y;
            data[count * 4 + 2] = (float) point.z; data[count * 4 + 3] = (float) radius;
            flows[count * 4] = (float) flow.x; flows[count * 4 + 1] = (float) flow.y;
            flows[count * 4 + 2] = (float) flow.z; flows[count * 4 + 3] = (float) age;
            seeds[count] = (float) packet.phase.x;
            strengths[count++] = (float) strength;
            if(cached){
                // Tight projection bounds only: shader bounds and sample positions stay unchanged.
                double norm=Math.sqrt((double)flows[(count-1)*4]*flows[(count-1)*4]+(double)flows[(count-1)*4+1]*flows[(count-1)*4+1]+(double)flows[(count-1)*4+2]*flows[(count-1)*4+2]);
                for(int axis=0;axis<3;axis++){
                    double direction=norm>.001?flows[(count-1)*4+axis]/norm:(axis==1?1:0);
                    float extent=(float)(data[(count-1)*4+3]*2.505*Math.sqrt(1+(u[1]*u[1]-1)*direction*direction));
                    uploadMin[axis]=Math.min(uploadMin[axis],data[(count-1)*4+axis]-extent);
                    uploadMax[axis]=Math.max(uploadMax[axis],data[(count-1)*4+axis]+extent);
                }
            }
            // A flow-aligned ellipsoid may point along any world axis; keep a conservative bound.
            double extent = radius * 2.65 * Math.max(1, u[1]);
            minX = Math.min(minX, point.x - extent); maxX = Math.max(maxX, point.x + extent);
            minY = Math.min(minY, point.y - extent); maxY = Math.max(maxY, point.y + extent);
            minZ = Math.min(minZ, point.z - extent); maxZ = Math.max(maxZ, point.z + extent);
        }
        if (count == 0) { minX = minY = minZ = -.1; maxX = maxY = maxZ = .1; }
        uploadCount=count;
        GL20.glUniform4fv(uniform(program,"puffs[0]",cached), data);
        GL20.glUniform1fv(uniform(program,"puffStrength[0]",cached), strengths);
        GL20.glUniform4fv(uniform(program,"puffFlow[0]",cached), flows);
        GL20.glUniform1fv(uniform(program,"puffSeed[0]",cached), seeds);
        GL20.glUniform1i(uniform(program,"puffCount",cached), count);
        GL20.glUniform3f(uniform(program,"auraMin",cached), (float) minX, (float) minY, (float) minZ);
        GL20.glUniform3f(uniform(program,"auraMax",cached), (float) maxX, (float) maxY, (float) maxZ);
        Vec3 noiseOffset = centre.subtract(noiseBase);
        GL20.glUniform3f(uniform(program,"noiseOrigin",cached), (float) noiseOffset.x, (float) noiseOffset.y, (float) noiseOffset.z);
        float opacity = target == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson() && u != null && u.length >= 48 ? u[46] : 1;
        GL20.glUniform1f(uniform(program,"auraOpacity",cached), opacity);
    }

    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static int equipmentKey(AbstractClientPlayer player) {
        if (player == null) return 0;
        int hash = 1;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            hash = hash * 31 + System.identityHashCode(stack.getItem());
            hash = hash * 31 + (stack.hasTag() ? stack.getTag().hashCode() : 0);
            hash = hash * 31 + (stack.isEmpty() ? 0 : 1);
        }
        return hash;
    }

    /** The level pose is the actual world view rotation, including camera effects. */
    @SubscribeEvent public static void view(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;
        if (PerformanceQa.ZOOM) ZoomMotionQa.forceCamera(event.getCamera());
        inverseView = new Matrix4f(event.getPoseStack().last().pose()).invert();
        renderCamera = event.getCamera().getPosition(); viewCaptured = true;
    }

    @SubscribeEvent public static void playerRendered(RenderPlayerEvent.Post event) {
        if (event.getEntity() == target && pendingFrame != null) {
            frame = pendingFrame; pendingFrame = null;
        }
    }

    @Mod.EventBusSubscriber(modid = EnergyVfx.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Layers {
        @SubscribeEvent public static void add(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) renderer.addLayer(new SurfaceCapture(renderer));
            }
        }
    }

    private static final class SurfaceCapture extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        SurfaceCapture(PlayerRenderer parent) { super(parent); }
        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                     float limbSwing, float limbAmount, float partial, float age, float yaw, float pitch) {
            Recipe recipe = EnergyVfx.Client.recipe;
            if (player != target || !viewCaptured || recipe == null || recipe.values.length < 48 || recipe.values[39] < .5) return;
            Builder builder = new Builder(inverseView, renderCamera.subtract(player.getPosition(partial)));
            samplePlayer(builder, pose, getParentModel(), recipe.values);
            pendingFrame = new Frame(builder);
        }
    }

    private static PoseStack copy(PoseStack source) {
        PoseStack result = new PoseStack(); result.last().pose().set(source.last().pose());
        result.last().normal().set(source.last().normal()); return result;
    }

    private static void part(Builder builder, PoseStack base, ModelPart part, double weight, EquipmentSlot slot) {
        if (!part.visible || weight <= 0) return;
        part.visit(copy(base), (pose, name, index, cube) -> {
            float x0 = cube.minX / 16, x1 = cube.maxX / 16;
            float y0 = cube.minY / 16, y1 = cube.maxY / 16;
            float z0 = cube.minZ / 16, z1 = cube.maxZ / 16;
            Vec3[] v = {new Vec3(x0,y0,z0),new Vec3(x1,y0,z0),new Vec3(x1,y1,z0),new Vec3(x0,y1,z0),
                        new Vec3(x0,y0,z1),new Vec3(x1,y0,z1),new Vec3(x1,y1,z1),new Vec3(x0,y1,z1)};
            int[][] faces = {{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
            for (int[] face : faces) builder.quad(pose.pose(), new Vec3[]{v[face[0]],v[face[1]],v[face[2]],v[face[3]]}, weight, slot);
        });
    }

    private static void armorModels() {
        if (outerArmor != null) return;
        var models = Minecraft.getInstance().getEntityModels();
        innerArmor = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        outerArmor = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
    }

    private static void samplePlayer(Builder builder, PoseStack base, PlayerModel<AbstractClientPlayer> model, float[] u) {
        boolean body = u[39] < 1.5 || u[39] > 2.5, gear = u[39] > 1.5;
        if (body) for (ModelPart part : new ModelPart[]{model.head,model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg})
            part(builder, base, part, u[44], null);
        if (!gear || u[45] <= 0) return;
        armorModels();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            int before = builder.sources.size();
            HumanoidModel<AbstractClientPlayer> vanilla = slot == EquipmentSlot.LEGS ? innerArmor : outerArmor;
            model.copyPropertiesTo(vanilla);
            vanilla.setAllVisible(true);
            var custom = IClientItemExtensions.of(stack).getGenericArmorModel(target, stack, slot, vanilla);
            HumanoidModel<?> armor = custom instanceof HumanoidModel<?> humanoid ? humanoid : vanilla;
            // Non-humanoid armour and non-armour slot items have an explicit mount-shaped proxy.
            boolean proxy = !(custom instanceof HumanoidModel<?>) || !(stack.getItem() instanceof net.minecraft.world.item.ArmorItem);
            switch (slot) {
                case HEAD -> part(builder, base, armor.head, u[45], slot);
                case CHEST -> { part(builder,base,armor.body,u[45],slot); part(builder,base,armor.leftArm,u[45],slot); part(builder,base,armor.rightArm,u[45],slot); }
                case LEGS -> { part(builder,base,armor.body,u[45],slot); part(builder,base,armor.leftLeg,u[45],slot); part(builder,base,armor.rightLeg,u[45],slot); }
                case FEET -> { part(builder,base,armor.leftLeg,u[45],slot); part(builder,base,armor.rightLeg,u[45],slot); }
                default -> { }
            }
            if (builder.sources.size() > before) { builder.slots.add(slot); if (proxy) builder.fallback++; }
        }
        held(builder, base, model, EquipmentSlot.MAINHAND, target.getMainArm(), u[45]);
        held(builder, base, model, EquipmentSlot.OFFHAND, target.getMainArm() == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT, u[45]);
    }

    private static void held(Builder builder, PoseStack base, PlayerModel<AbstractClientPlayer> model,
                             EquipmentSlot slot, HumanoidArm arm, double weight) {
        ItemStack stack = target.getItemBySlot(slot);
        if (stack.isEmpty()) return;
        PoseStack hand = copy(base);
        if (model.young) { hand.translate(0,.75,0); hand.scale(.5f,.5f,.5f); }
        model.translateToHand(arm, hand);
        hand.mulPose(Axis.XP.rotationDegrees(-90)); hand.mulPose(Axis.YP.rotationDegrees(180));
        boolean left = arm == HumanoidArm.LEFT;
        hand.translate((left ? -1 : 1) / 16.0, .125, -.625);
        PoseStack mount = copy(hand);
        int before = builder.sources.size();
        try {
            var itemRenderer = Minecraft.getInstance().getItemRenderer();
            BakedModel baked = itemRenderer.getModel(stack, target.level(), target, target.getId());
            ItemDisplayContext context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            baked = ForgeHooksClient.handleCameraTransforms(hand, baked, context, left);
            hand.translate(-.5,-.5,-.5);
            if (!baked.isCustomRenderer()) {
                int budget = 96;
                for (BakedModel pass : baked.getRenderPasses(stack, true)) {
                    for (int face = -1; face < 6 && budget > 0; face++) {
                        RandomSource quadRandom = RandomSource.create(42);
                        for (BakedQuad quad : pass.getQuads(null, face == -1 ? null : Direction.values()[face], quadRandom)) {
                            if (--budget < 0) break;
                            int[] raw = quad.getVertices(); int stride = raw.length / 4;
                            if (stride < 3) continue;
                            Vec3[] vertices = new Vec3[4];
                            for (int i = 0; i < 4; i++) vertices[i] = new Vec3(Float.intBitsToFloat(raw[i*stride]),Float.intBitsToFloat(raw[i*stride+1]),Float.intBitsToFloat(raw[i*stride+2]));
                            builder.quad(hand.last().pose(), vertices, weight, slot);
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Custom model APIs can decline surface queries. A bounded mount fallback remains valid.
        }
        if (builder.sources.size() == before) {
            // Four finite triangles around the actual hand mount, explicitly not custom bone coverage.
            float size = .1f;
            Vec3[] corners = {new Vec3(-size,-size,-size),new Vec3(size,-size,-size),new Vec3(0,size,-size),new Vec3(0,0,size)};
            int[][] faces = {{0,2,1},{0,1,3},{1,2,3},{2,0,3}};
            for (int[] face : faces) {
                Vec3 a = builder.transform(mount.last().pose(),(float)corners[face[0]].x,(float)corners[face[0]].y,(float)corners[face[0]].z);
                Vec3 b = builder.transform(mount.last().pose(),(float)corners[face[1]].x,(float)corners[face[1]].y,(float)corners[face[1]].z);
                Vec3 c = builder.transform(mount.last().pose(),(float)corners[face[2]].x,(float)corners[face[2]].y,(float)corners[face[2]].z);
                builder.triangle(a,b,c,weight,slot);
            }
            builder.fallback++;
        } else builder.baked++;
        if (builder.sources.size() > before) builder.slots.add(slot);
    }

    /** First-person vanilla omits the player's world model; this builds its current humanoid pose. */
    private static Frame fallbackFrame(float[] u) {
        if (fallbackModel == null) {
            boolean slim = target.getModelName().equals("slim");
            fallbackModel = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
        }
        PlayerModel<AbstractClientPlayer> model = fallbackModel;
        model.setAllVisible(true); model.crouching = target.isCrouching(); model.riding = target.isPassenger();
        model.young = target.isBaby(); model.attackTime = target.getAttackAnim(1);
        model.leftArmPose = model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        if (!target.getMainHandItem().isEmpty()) {
            if (target.getMainArm() == HumanoidArm.RIGHT) model.rightArmPose = HumanoidModel.ArmPose.ITEM;
            else model.leftArmPose = HumanoidModel.ArmPose.ITEM;
        }
        if (!target.getOffhandItem().isEmpty()) {
            if (target.getMainArm() == HumanoidArm.RIGHT) model.leftArmPose = HumanoidModel.ArmPose.ITEM;
            else model.rightArmPose = HumanoidModel.ArmPose.ITEM;
        }
        float limb = target.walkAnimation.position(1), amount = target.walkAnimation.speed(1);
        model.prepareMobModel(target,limb,amount,1);
        model.setupAnim(target,limb,amount,target.tickCount + 1,target.yHeadRot-target.yBodyRot,target.getXRot());
        PoseStack base = new PoseStack();
        if (target.isCrouching()) base.translate(0,-.125,0);
        base.mulPose(Axis.YP.rotationDegrees(180-target.yBodyRot));
        if (target.isFallFlying()) base.mulPose(Axis.XP.rotationDegrees(-90-target.getXRot()));
        else if (target.isVisuallySwimming()) base.mulPose(Axis.XP.rotationDegrees(-90-target.getXRot()));
        base.scale(-.9375f,-.9375f,.9375f); base.translate(0,-1.501,0);
        Builder builder = new Builder(new Matrix4f(), Vec3.ZERO);
        samplePlayer(builder,base,model,u);
        return new Frame(builder);
    }
}
