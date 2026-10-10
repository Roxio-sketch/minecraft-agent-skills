package dev.lightninglab;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/** Explicit opt-in development save; never runs in an ordinary installed client. */
@Mod.EventBusSubscriber(modid=LightningLab.MOD_ID,value=Dist.CLIENT)
public final class LightningGpuProbe {
    private static final boolean ENABLED=Boolean.getBoolean("lightninglab.gpuProbe");
    private static final String SAVE="LightningLab-Mature-GPU-Check";
    private static final String[] NAMES={"impact-tip-cyan","impact-ratio100-red","impact-gradient-off-white","thick-acute-halo","highgain-vivid","highgain-additive","highgain-glow-only","ended"};
    private static boolean opened,setup,done;
    private static volatile boolean ready;
    private static int ticks,stage=-1,capturedStage=-1;
    private static LightningRecipe recipe;
    private static List<OrbitField.Segment> segments=List.of();
    private static List<RainGeometry.Bolt> rain;
    private static BoltGeometry mainBolt;
    private static boolean oldBob,oldHide,oldPause,oldShadows;
    private static net.minecraft.client.CloudStatus oldClouds;
    private static net.minecraft.client.CameraType oldCameraType;
    private static int oldDistance;
    private static int glError;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!ENABLED||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(!opened&&mc.screen!=null&&mc.screen.getClass().getSimpleName().contains("Accessibility"))mc.setScreen(new TitleScreen());
        if(!opened&&mc.screen instanceof TitleScreen){
            opened=true;
            if(Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve("level.dat")))mc.createWorldOpenFlows().loadLevel(mc.screen,SAVE);
            else mc.createWorldOpenFlows().createFreshLevel(SAVE,
                new LevelSettings("闪电独立模板 GPU 验证",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(20261005L,false,false),WorldPresets::createNormalWorldDimensions);
            return;
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!setup){
            setup=true;if(Boolean.getBoolean("lightninglab.stencilProbe"))mc.getMainRenderTarget().enableStencil();
            oldBob=mc.options.bobView().get();oldHide=mc.options.hideGui;oldPause=mc.options.pauseOnLostFocus;
            oldClouds=mc.options.cloudStatus().get();oldShadows=mc.options.entityShadows().get();oldCameraType=mc.options.getCameraType();oldDistance=mc.options.renderDistance().get();
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.entityShadows().set(false);mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);mc.options.renderDistance().set(4);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var world=server.overworld();world.setDayTime(6000);world.setWeatherParameters(60000,0,false,false);
                world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++)world.setBlock(new BlockPos(x,199,z),Blocks.GRAY_CONCRETE.defaultBlockState(),3);
                for(var player:server.getPlayerList().getPlayers()){player.setGameMode(GameType.SPECTATOR);player.setNoGravity(true);player.teleportTo(world,0,201.38,-7,0,22.5f);}
                ready=true;
            });return;
        }
        if(!ready)return;
        mc.player.setPos(0,201.38,-7);mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setNoGravity(true);
        if(++ticks<100)return;
        int next=(ticks-100)/40,phase=(ticks-100)%40;
        if(next>=NAMES.length){finish(mc);return;}
        if(phase==0){
            stage=next;glError=0;mainBolt=null;mc.setScreen(null);mc.player.setYRot(0);mc.player.setXRot(stage==3?0:stage>=4&&stage<=6?-15:22.5f);
            recipe=new LightningRecipe();recipe.mode="rain";recipe.rainStyle="cluster";recipe.rainCount=1;recipe.rainSpread=1;recipe.rainDepthSpread=0;
            recipe.rainMaxBranches=0;recipe.rainMaxBranchDepth=0;recipe.rainBranchProbability=0;
            recipe.coreColor="#ffffff";recipe.glowColor="#ffffff";recipe.gradientEnabled=stage!=2;recipe.gradientStart="#ff0000";recipe.gradientEnd="#00ffff";recipe.gradientRatio=stage==1?100:50;
            recipe.strikeWidth=.09;recipe.glowWidth=.2;recipe.impactRadius=2.4;recipe.strikeHeight=51;
            rain=RainGeometry.generate(recipe,new Vec3(0,200,0),(start,end)->end);
            if(stage==3){
                recipe.gradientEnabled=false;recipe.glowWidth=1.2;recipe.glowGain=2.4;recipe.strikeWidth=.05;recipe.coreGain=.1;
                segments=List.of(new OrbitField.Segment(new Vec3(-.199,203,0),new Vec3(0,203,0),.05f,.8f,0,0,.5f),
                    new OrbitField.Segment(new Vec3(0,203,0),new Vec3(0,203.199,0),.05f,.8f,0,.5f,1));
            }else if(stage>=4){
                segments=List.of();
                if(stage<=6){
                    recipe.mode="strike";recipe.seed=90421;recipe.strikeWidth=.055;recipe.glowWidth=.23;
                    recipe.coreGain=3.8;recipe.glowGain=2.4;recipe.coreColor="#ffffff";recipe.glowColor="#895a75";
                    recipe.gradientEnabled=true;recipe.gradientStart="#e700f2";recipe.gradientEnd="#e3b026";recipe.gradientRatio=31;
                    recipe.gradientTarget=stage==6?"glow":"both";recipe.gradientCoreMode=stage==5?"additive":"vivid";
                    recipe.branchProbability=.35;recipe.maxBranchDepth=1;recipe.branchLimit=12;
                    mainBolt=BoltGeometry.generate(new Vec3(0,211,0),new Vec3(0,200,0),recipe);
                }
            }else segments=rain.get(0).impact().sample(3.2);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(!ENABLED||stage<0||done||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        if(stage<3)LightningRender.drawBolt(rain.get(0).geometry(),recipe,3.2,event.getPoseStack(),event.getCamera().getPosition());
        if(mainBolt!=null)LightningRender.drawBolt(mainBolt,recipe,3.2,event.getPoseStack(),event.getCamera().getPosition());
        LightningRender.drawOrbit(segments,recipe,event.getPoseStack(),event.getCamera().getPosition());
        glError|=GL11.glGetError();
    }
    @SubscribeEvent public static void capture(TickEvent.RenderTickEvent event){
        if(!ENABLED||done||event.phase!=TickEvent.Phase.END||stage<0||capturedStage==stage||(ticks-100)%40!=25)return;
        var mc=Minecraft.getInstance();
        try{
            var folder=mc.gameDirectory.toPath().resolve("lightning-lab/gpu-probes");Files.createDirectories(folder);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(NAMES[stage]+".png"));}
            var report=Map.of("fixture","lightninglab-mature-gpu/v2","name",NAMES[stage],"recipe",recipe,"time",3.2,
                "groundSegments",segments.size(),"allGroundMaterialAlongIsTip",segments.stream().allMatch(s->s.alongA()==1&&s.alongB()==1),
                "stencil",mc.getMainRenderTarget().isStencilEnabled(),"glError",glError,
                "mainSegments",mainBolt==null?0:mainBolt.segments().stream().filter(s->s.depth()==0).count(),
                "branchSegments",mainBolt==null?0:mainBolt.segments().stream().filter(s->s.depth()>0).count());
            Files.writeString(folder.resolve(NAMES[stage]+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("[LIGHTNINGLAB GPU PROBE] "+NAMES[stage]+" GL="+glError);
            if(glError!=0)throw new IllegalStateException("GPU probe GL error "+glError);
            capturedStage=stage;
        }catch(Exception failure){throw new RuntimeException("Lightning GPU probe failed",failure);}
    }
    private static void finish(Minecraft mc){
        done=true;LightningHalo.release();
        mc.options.bobView().set(oldBob);mc.options.hideGui=oldHide;mc.options.pauseOnLostFocus=oldPause;mc.options.entityShadows().set(oldShadows);mc.options.cloudStatus().set(oldClouds);mc.options.renderDistance().set(oldDistance);mc.options.setCameraType(oldCameraType);
        System.out.println("[LIGHTNINGLAB GPU PROBE] COMPLETE");mc.stop();
    }
}
