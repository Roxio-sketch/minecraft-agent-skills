package dev.vfxworkbench.effects.blackhole;

import dev.vfxworkbench.client.VfxWorkbenchMod;
import dev.vfxworkbench.core.ParamRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.Difficulty;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.google.gson.*;
import java.nio.file.*;

/** Explicit, disposable world fixture; never operates in the user's current save. */
@Mod.EventBusSubscriber(modid=VfxWorkbenchMod.MODID,value=Dist.CLIENT)
public final class RibbonReferenceRegression {
    private static final Path MARKER=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("config/vfxworkbench/run-ribbon-reference.once");
    private static final boolean ENABLED=Files.isRegularFile(MARKER);
    private static final String SAVE="VFX-Ribbon-Reference-Check";
    private static final String[] CASES={"blue-oblique-bloom","blue-oblique-no-bloom","blue-top-bloom","blue-top-no-bloom","orange-oblique-bloom","orange-top-bloom","blue-side-bloom","blue-late-bloom","blue-density-zero","blue-effect-off"};
    private static boolean opened,setup,done,oldHide,oldBob,oldPause,oldFullscreen;
    private static int ticks,stage,phase,oldWidth,oldHeight,oldDistance;
    private static CloudStatus oldCloud;
    private static CameraType oldCamera;
    private static volatile boolean ready;
    private static Vec3 eye;
    private static final Vec3 CENTER=new Vec3(0,140,0);
    private static Path out;
    private static final JsonArray reports=new JsonArray();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(!ENABLED||done||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try{
            if(!opened){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
                opened=true;out=mc.gameDirectory.toPath().resolve("ribbon-reference-probes");Files.createDirectories(out);Files.deleteIfExists(MARKER);
                oldHide=mc.options.hideGui;oldBob=mc.options.bobView().get();oldPause=mc.options.pauseOnLostFocus;oldDistance=mc.options.renderDistance().get();oldCloud=mc.options.cloudStatus().get();oldCamera=mc.options.getCameraType();
                oldWidth=mc.getWindow().getWidth();oldHeight=mc.getWindow().getHeight();oldFullscreen=mc.getWindow().isFullscreen();
                if(oldFullscreen)mc.getWindow().toggleFullScreen();mc.getWindow().setWindowed(960,600);mc.resizeDisplay();
                if(Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves/"+SAVE+"/level.dat")))mc.createWorldOpenFlows().loadLevel(mc.screen,SAVE);
                else mc.createWorldOpenFlows().createFreshLevel(SAVE,new LevelSettings(SAVE,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261009L,false,false),WorldPresets::createNormalWorldDimensions);
                System.out.println("[RIBBON-REFERENCE] BEGIN");return;
            }
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
            if(!setup){
                setup=true;mc.setScreen(null);mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.getSingleplayerServer().execute(()->{var server=mc.getSingleplayerServer();var level=server.overworld();level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                    for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++)level.setBlock(new BlockPos(x,119,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
                    for(var player:server.getPlayerList().getPlayers()){player.setGameMode(GameType.SPECTATOR);player.teleportTo(level,0,145,-32,0,10);}
                    ready=true;});return;
            }
            if(!ready||++ticks<300)return;
            if(stage>=CASES.length){finish(mc,null);return;}
            if(phase==0){
                var s=ParamRegistry.state(BlackHoleSkillSchema.ID);s.set("form","recipe");BlackHoleSkillSchema.applyForm(s,"ribbons");s.set("scale","1.6");s.set("distance","32");s.set("playing","false");s.set("time",stage==7?"277.85":"1.7");s.set("lensAffectsPlayer","false");
                if(stage==1||stage==3)s.set("diskBloom","0");
                if(stage==4||stage==5){s.set("hot","#fff3b0");s.set("diskMid","#ffa126");s.set("cool","#a63304");}
                if(stage==8)s.set("density","0");
                BlackHoleClient.preview();BlackHoleClient.center=CENTER;BlackHoleRender.frozenTime=-1;
                if(stage==9)BlackHoleClient.stop();
                eye=CENTER.add(0,stage==2||stage==3||stage==5?31:stage==6?1:3.4,stage==2||stage==3||stage==5?-4:-19);
                phase=1;
            }
            Vec3 direction=CENTER.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
            mc.player.setPos(eye.add(0,-mc.player.getEyeHeight(),0));mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setNoGravity(true);mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
            if(!BlackHoleClient.failure.isEmpty())throw new IllegalStateException(BlackHoleClient.failure);
            if(phase==25){if(stage==9){try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(out.resolve(CASES[stage]+".png"));}}else BlackHoleWorld.probe="reference-"+CASES[stage];}
            if(phase++>=30){
                var r=new JsonObject();r.addProperty("case",CASES[stage]);
                if(stage!=9){Path folder=mc.gameDirectory.toPath().resolve("black-hole-lab/world-probes");Path meta=folder.resolve("reference-"+CASES[stage]+".json");if(!Files.isRegularFile(meta))throw new IllegalStateException("Missing probe "+meta);r=JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
                    if(r.get("glError").getAsInt()!=0||r.get("maxDepthDifference").getAsDouble()!=0)throw new IllegalStateException("GL/depth regression");
                    for(String suffix:new String[]{"-after.png","-before.png","-field.png","-core-emission.png","-pre-core-bloom.png",".json"}){Path from=folder.resolve("reference-"+CASES[stage]+suffix);if(Files.exists(from))Files.copy(from,out.resolve(CASES[stage]+suffix),StandardCopyOption.REPLACE_EXISTING);}
                }
                reports.add(r);System.out.println("[RIBBON-REFERENCE] captured "+CASES[stage]);stage++;phase=0;
            }
        }catch(Throwable failure){finish(mc,failure);}
    }
    private static void finish(Minecraft mc,Throwable failure){
        done=true;BlackHoleClient.stop();BlackHoleRender.frozenTime=-1;
        var report=new JsonObject();report.addProperty("passed",failure==null);report.add("cases",reports);if(failure!=null){report.addProperty("failure",failure.toString());failure.printStackTrace();}
        try{Files.writeString(out.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));Files.deleteIfExists(MARKER);}catch(Exception ignored){}
        mc.options.hideGui=oldHide;mc.options.bobView().set(oldBob);mc.options.pauseOnLostFocus=oldPause;mc.options.renderDistance().set(oldDistance);mc.options.cloudStatus().set(oldCloud);mc.options.setCameraType(oldCamera);mc.getWindow().setWindowed(oldWidth,oldHeight);if(oldFullscreen&&!mc.getWindow().isFullscreen())mc.getWindow().toggleFullScreen();mc.resizeDisplay();
        System.out.println("[RIBBON-REFERENCE] COMPLETE passed="+(failure==null));mc.stop();
    }
}
