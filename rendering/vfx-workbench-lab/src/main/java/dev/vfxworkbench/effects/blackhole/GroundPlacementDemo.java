package dev.vfxworkbench.effects.blackhole;

import com.google.gson.GsonBuilder;
import dev.vfxworkbench.client.VfxWorkbenchMod;
import dev.vfxworkbench.core.ParamRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import org.joml.Matrix4f;

/** Opt-in real ground preview; its center is never rewritten after preview(). */
@Mod.EventBusSubscriber(modid=VfxWorkbenchMod.MODID,value=Dist.CLIENT)
public final class GroundPlacementDemo {
    private static final String SAVE="VFX-BlackHole-Ground-Placement-Check";
    private static final String[] NAMES={"placed-down","side-right","side-left","vertical","oblique","hurt-down"};
    private static boolean opened,setup,done,oldBob,oldHide,oldPause,oldShadows;
    private static volatile boolean ready;
    private static int ticks,oldDistance;
    private static net.minecraft.client.CloudStatus oldClouds;
    private static net.minecraft.client.CameraType oldCamera;
    private static Vec3 expectedCenter,expectedHit;
    private static String capture;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("vfxworkbench.blackHoleGroundRegression")||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(!opened&&mc.screen instanceof TitleScreen){
            opened=true;
            if(Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve("level.dat")))mc.createWorldOpenFlows().loadLevel(mc.screen,SAVE);
            else mc.createWorldOpenFlows().createFreshLevel(SAVE,new LevelSettings("地面黑洞定位验收",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261005L,false,false),WorldPresets::createNormalWorldDimensions);
            return;
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!setup){
            setup=true;oldBob=mc.options.bobView().get();oldHide=mc.options.hideGui;oldPause=mc.options.pauseOnLostFocus;oldShadows=mc.options.entityShadows().get();oldClouds=mc.options.cloudStatus().get();oldCamera=mc.options.getCameraType();oldDistance=mc.options.renderDistance().get();
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.entityShadows().set(false);mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.options.renderDistance().set(4);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)level.setBlock(new BlockPos(x,119,z),((x+z)&1)==0?Blocks.WHITE_CONCRETE.defaultBlockState():Blocks.YELLOW_CONCRETE.defaultBlockState(),3);
                for(var p:server.getPlayerList().getPlayers()){p.setGameMode(GameType.SPECTATOR);p.teleportTo(level,0,120,0,0,60);}
                ready=true;
            });return;
        }
        if(!ready||++ticks<120)return;
        int stage=(ticks-120)/40,phase=(ticks-120)%40;if(stage>=NAMES.length)return;
        if(phase==0){
            mc.setScreen(null);var p=ParamRegistry.state(BlackHoleSkillSchema.ID);
            if(stage==0){p.resetToDefaults();p.set("scale","1.6");p.set("core",".58");p.set("distance","26.576");p.set("density","0");p.set("halo","0");p.set("particles","false");p.set("lens","false");p.set("bend","0");p.set("coreGradientStrength","0");p.set("coreColor","#000000");}
            Vec3 position=switch(stage){case 1->new Vec3(.45,120,0);case 2->new Vec3(-.45,120,0);case 3->new Vec3(expectedCenter.x,122,expectedCenter.z);case 4->new Vec3(.45,120,-.3);default->new Vec3(0,120,0);};
            float yaw=stage==4?45:0,pitch=stage==3?90:60;
            mc.player.setPos(position);mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
            mc.getSingleplayerServer().execute(()->{for(var player:mc.getSingleplayerServer().getPlayerList().getPlayers())player.teleportTo(mc.getSingleplayerServer().overworld(),position.x,position.y,position.z,yaw,pitch);});
            if(stage==0){
                Vec3 eye=mc.player.getEyePosition(),direction=mc.player.getLookAngle();
                expectedHit=eye.add(direction.scale((120-eye.y)/direction.y));
                expectedCenter=expectedHit.add(0,.58*1.6+.05,0);
                BlackHoleClient.preview();BlackHoleRender.frozenTime=35;
                if(BlackHoleClient.center.distanceTo(expectedCenter)>1e-5)throw new IllegalStateException("Preview did not anchor on the analytic ground surface: "+BlackHoleClient.center+" expected "+expectedCenter);
            }
        }
        if(stage==5&&phase==28)mc.player.animateHurt(45);
        if(phase==30){capture="ground-motion-"+NAMES[stage];BlackHoleWorld.probe=capture;}
        if(stage==NAMES.length-1&&phase==36){
            done=true;BlackHoleClient.stop();BlackHoleRender.frozenTime=-1;ParamRegistry.state(BlackHoleSkillSchema.ID).resetToDefaults();
            mc.options.bobView().set(oldBob);mc.options.hideGui=oldHide;mc.options.pauseOnLostFocus=oldPause;mc.options.entityShadows().set(oldShadows);mc.options.cloudStatus().set(oldClouds);mc.options.setCameraType(oldCamera);mc.options.renderDistance().set(oldDistance);
            System.out.println("[BH-GROUND] COMPLETE: six viewpoints after one real downward preview");mc.stop();
        }
    }
    @SubscribeEvent public static void frame(RenderLevelStageEvent event){
        if(!Boolean.getBoolean("vfxworkbench.blackHoleGroundRegression")||capture==null||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        String name=capture;capture=null;var mc=Minecraft.getInstance();var data=new LinkedHashMap<String,Object>();
        data.put("fixture","ground-preview/v1");data.put("expectedHit",expectedHit);data.put("fixedCenter",expectedCenter);data.put("radius",.58*1.6);data.put("centerWasFixed",BlackHoleClient.center.distanceTo(expectedCenter)<1e-5);
        data.put("rawCamera",event.getCamera().getPosition());data.put("terrainViewProjection",new Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose()).get(new float[16]));
        data.put("playerPosition",mc.player.position());data.put("pitch",mc.player.getXRot());data.put("yaw",mc.player.getYRot());data.put("hurtTime",mc.player.hurtTime);
        try{var out=mc.gameDirectory.toPath().resolve("black-hole-lab/world-probes");Files.createDirectories(out);Files.writeString(out.resolve(name+"-fixture.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}catch(Exception failure){throw new IllegalStateException(failure);}
    }
}
