package dev.vfxworkbench.effects.lightning;

import com.google.gson.GsonBuilder;
import dev.vfxworkbench.client.VfxWorkbenchMod;
import dev.vfxworkbench.core.ParamRegistry;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Files;

/** Opt-in fixed-time GPU evidence in a dedicated development save. */
@Mod.EventBusSubscriber(modid=VfxWorkbenchMod.MODID, value=Dist.CLIENT)
public final class LightningDemo {
    private static final String SAVE=Boolean.getBoolean("vfxworkbench.lightningGradientRegression")?"VFX-Lightning-Gradient-Ratio-Check":Boolean.getBoolean("vfxworkbench.lightningGlowRegression")?"VFX-Lightning-Wide-Glow-Check":"VFX-Lightning-Skill-Integration-Check";
    private record Stage(String name,String preset,double time) {}
    private record GlowStage(String name,double width,int view,boolean sharp) {}
    private static final GlowStage[] GLOW_STAGES=glowStages();
    private static final Stage[] STAGES={
        new Stage("supplied-strike-peak",null,1.2),
        new Stage("supplied-strike-replay",null,1.2),
        new Stage("supplied-strike-half-leader",null,.6),
        new Stage("supplied-strike-tail",null,12.5),
        new Stage("supplied-strike-ended",null,14),
        new Stage("source","source",6.25),
        new Stage("short-source","shortSource",6.25),
        new Stage("grow","orbit",6.25),
        new Stage("ring","ring",6.25),
        new Stage("cluster-before-impact","cluster",.5),
        new Stage("cluster-impact","cluster",3.2),
        new Stage("cluster-impact-tail","cluster",16),
        new Stage("cluster-ended","cluster",22),
        new Stage("spread-rain","rain",3.2)
    };
    private static boolean opened,setup,done;
    private static volatile boolean sceneReady;
    private static int ticks;
    private static Vec3 camera;
    private static String capture;
    private static java.util.Map<String,Object> captureDetails=java.util.Map.of();
    private static boolean oldBob,oldShadows,oldHideGui,oldPause;
    private static int oldRenderDistance;
    private static net.minecraft.client.CloudStatus oldClouds;
    private static net.minecraft.client.CameraType oldCameraType;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if((!Boolean.getBoolean("vfxworkbench.lightningDemo")&&!Boolean.getBoolean("vfxworkbench.lightningGlowRegression")&&!Boolean.getBoolean("vfxworkbench.lightningGradientRegression")&&!Boolean.getBoolean("vfxworkbench.lightningImpactColorRegression"))||event.phase!=TickEvent.Phase.END||done)return;
        var mc=Minecraft.getInstance();
        if(!opened&&mc.screen instanceof TitleScreen){
            opened=true;
            if(Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve("level.dat")))mc.createWorldOpenFlows().loadLevel(mc.screen,SAVE);
            else mc.createWorldOpenFlows().createFreshLevel(SAVE,
                new LevelSettings("闪电技能接入验证",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(20261005L,false,false),WorldPresets::createNormalWorldDimensions);
            return;
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!setup){
            setup=true;
            if(Boolean.getBoolean("vfxworkbench.stencilRegression"))mc.getMainRenderTarget().enableStencil();
            oldBob=mc.options.bobView().get();oldShadows=mc.options.entityShadows().get();oldClouds=mc.options.cloudStatus().get();
            oldHideGui=mc.options.hideGui;oldPause=mc.options.pauseOnLostFocus;oldRenderDistance=mc.options.renderDistance().get();oldCameraType=mc.options.getCameraType();
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            mc.options.entityShadows().set(false);mc.options.renderDistance().set(8);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();
                level.setDayTime(6000);level.setWeatherParameters(60000,0,false,false);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++)level.setBlock(new BlockPos(x,199,z),Blocks.GRAY_CONCRETE.defaultBlockState(),3);
                for(int x=-40;x<=40;x++)for(int y=200;y<=271;y++)level.setBlock(new BlockPos(x,y,44),
                    x%8==0||y%8==0?Blocks.GRAY_CONCRETE.defaultBlockState():Blocks.BLACK_CONCRETE.defaultBlockState(),3);
                for(var player:server.getPlayerList().getPlayers()){
                    player.setGameMode(GameType.SPECTATOR);player.setNoGravity(true);player.teleportTo(level,0,222,-80,0,0);
                }
                sceneReady=true;
            });
            return;
        }
        if(!sceneReady)return;
        if(camera!=null){mc.player.setPos(camera);mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setNoGravity(true);}
        if(++ticks<100)return;
        if(Boolean.getBoolean("vfxworkbench.lightningImpactColorRegression")){impactColorRegression(mc);return;}
        if(Boolean.getBoolean("vfxworkbench.lightningGlowRegression")||Boolean.getBoolean("vfxworkbench.lightningGradientRegression")){glowRegression(mc);return;}
        int stage=(ticks-100)/40,phase=(ticks-100)%40;
        if(stage>=STAGES.length)return;
        if(phase==0){
            mc.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            var state=ParamRegistry.state(LightningSkillSchema.ID);state.resetToDefaults();
            if(STAGES[stage].preset()!=null)state.set("preset",STAGES[stage].preset());
            var recipe=LightningSkillSchema.recipe();
            Vec3 anchor=new Vec3(0,recipe.mode.equals("orbit")?201:200,0);
            double targetY=anchor.y+(recipe.mode.equals("orbit")?0:recipe.strikeHeight*.45);
            double distance=recipe.mode.equals("orbit")?(recipe.orbitAnimation.equals("ring")?Math.max(10,recipe.orbitRadius*3):Math.max(16,recipe.rodLength*1.05)):
                Math.max(20,recipe.strikeHeight*.95+(recipe.mode.equals("rain")?recipe.rainSpread*.5:0));
            double above=recipe.mode.equals("orbit")&&recipe.orbitAnimation.equals("ring")?5:0;
            camera=new Vec3(0,targetY+above-1.62,-distance);
            float pitch=(float)Math.toDegrees(Math.atan2(above,distance));
            mc.player.setPos(camera);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
            Vec3 position=camera;
            mc.getSingleplayerServer().execute(()->{
                for(var player:mc.getSingleplayerServer().getPlayerList().getPlayers())player.teleportTo(mc.getSingleplayerServer().overworld(),position.x,position.y,position.z,0,pitch);
            });
            LightningClient.previewAt(anchor,STAGES[stage].time());
            System.out.println("[LIGHTNING-DEMO] stage="+STAGES[stage].name()+" tick="+STAGES[stage].time());
        }
        if(phase==30)capture=STAGES[stage].name();
        if(stage==STAGES.length-1&&phase==36){
            done=true;LightningClient.stop();
            ParamRegistry.state(LightningSkillSchema.ID).resetToDefaults();
            System.out.println("[LIGHTNING-DEMO] COMPLETE: 14 fixed-time stages; supplied default restored");
            if(Boolean.getBoolean("vfxworkbench.lightningDemoExit"))mc.stop();
        }
    }

    private static GlowStage[] glowStages(){
        var result=new java.util.ArrayList<GlowStage>();String[] widths={"normal","user","limit"},views={"far","near-side","near-path","upward"};double[] values={.07,1.154,2};
        for(int width=0;width<values.length;width++)for(int view=0;view<views.length;view++)result.add(new GlowStage("glow-"+widths[width]+"-"+views[view],values[width],view,false));
        result.add(new GlowStage("glow-sharp-user-near-path",1.154,2,true));result.add(new GlowStage("glow-sharp-limit-near-path",2,2,true));result.add(new GlowStage("glow-sharp-limit-near-side",2,1,true));
        return result.toArray(GlowStage[]::new);
    }

    private static void impactColorRegression(Minecraft mc){
        int stage=(ticks-100)/40,phase=(ticks-100)%40;if(stage>=3)return;
        String name=stage==0?"impact-end-cyan":stage==1?"impact-ratio100-red":"impact-gradient-off-white";
        if(phase==0){
            mc.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.options.bobView().set(false);
            var s=ParamRegistry.state(LightningSkillSchema.ID);s.resetToDefaults();
            s.set("mode","rain");s.set("rainCount","1");s.set("rainStyle","cluster");s.set("rainSpread","1");s.set("rainDepthSpread","0");
            s.set("coreColor","#ffffff");s.set("glowColor","#ffffff");s.set("gradientStart","#ff0000");s.set("gradientEnd","#00ffff");
            s.set("gradientEnabled",Boolean.toString(stage!=2));s.set("gradientRatio",stage==1?"100":"50");
            s.set("strikeWidth",".09");s.set("glowWidth",".2");s.set("impactRadius","2.4");
            Vec3 anchor=new Vec3(0,200,0);camera=new Vec3(0,201.38,-7);float pitch=(float)Math.toDegrees(Math.atan2(2.9,7));
            mc.player.setPos(camera);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
            Vec3 pos=camera;mc.getSingleplayerServer().execute(()->{for(var p:mc.getSingleplayerServer().getPlayerList().getPlayers())p.teleportTo(mc.getSingleplayerServer().overworld(),pos.x,pos.y,pos.z,0,pitch);});
            LightningClient.previewAt(anchor,3.2);
            captureDetails=java.util.Map.of("fixture","impact-tip-color/v1","expectedTip",stage==0?"cyan":stage==1?"red":"white");
        }
        if(phase==30)capture=name;
        if(stage==2&&phase==36){
            done=true;LightningClient.stop();ParamRegistry.state(LightningSkillSchema.ID).resetToDefaults();
            mc.options.bobView().set(oldBob);mc.options.cloudStatus().set(oldClouds);mc.options.entityShadows().set(oldShadows);mc.options.renderDistance().set(oldRenderDistance);mc.options.hideGui=oldHideGui;mc.options.pauseOnLostFocus=oldPause;mc.options.setCameraType(oldCameraType);
            System.out.println("[LIGHTNING-IMPACT-COLOR] COMPLETE: cyan endpoint, ratio100 red endpoint and gradient-off white");mc.stop();
        }
    }

    /** Same seeded bolt at its visible peak; widths and viewpoints change independently. */
    private static void glowRegression(Minecraft mc){
        boolean gradient=Boolean.getBoolean("vfxworkbench.lightningGradientRegression");
        int count=gradient?5:GLOW_STAGES.length;
        int stage=(ticks-100)/40,phase=(ticks-100)%40;if(stage>=count)return;
        GlowStage test=gradient?new GlowStage("gradient-ratio-"+(stage*25),.35,0,false):GLOW_STAGES[stage];
        if(phase==0){
            mc.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.options.bobView().set(false);
            var state=ParamRegistry.state(LightningSkillSchema.ID);state.resetToDefaults();state.set("mode","strike");
            state.set("glowWidth",Double.toString(test.width));state.set("coreGain","16");state.set("glowGain","8.028");
            if(gradient){
                state.set("gradientEnabled","true");state.set("gradientRatio",Integer.toString(stage*25));
                state.set("coreColor","#ffffff");state.set("glowColor","#ffffff");
                state.set("gradientStart","#ff2040");state.set("gradientEnd","#2060ff");
                state.set("coreGain","1");state.set("glowGain","1");
            }
            if(test.sharp){state.set("strikeDisplacement","12");state.set("strikeRoughness",".85");}
            var recipe=LightningSkillSchema.recipe();Vec3 anchor=new Vec3(0,200,0);
            var geometry=BoltGeometry.generate(anchor.add(0,recipe.strikeHeight,0),anchor,recipe);
            var main=geometry.segments().stream().filter(segment->segment.depth()==0).toList();
            int joint=1;double sharpest=1;
            for(int i=1;i<main.size()-1;i++){
                if(main.get(i).alongA()<.2||main.get(i).alongA()>.8)continue;
                Vec3 incoming=main.get(i-1).b().subtract(main.get(i-1).a()).normalize(),outgoing=main.get(i).b().subtract(main.get(i).a()).normalize();
                double angle=incoming.dot(outgoing);if(angle<sharpest){sharpest=angle;joint=i;}
            }
            Vec3 corner=main.get(joint).a(),middle=main.get(main.size()/2).a();
            Vec3 focus=test.view==0?anchor.add(0,recipe.strikeHeight*.45,0):test.view==3?anchor.add(0,recipe.strikeHeight*.55,0):test.view==2?corner:middle;
            Vec3 eye=switch(test.view){
                case 0->focus.add(0,0,-Math.max(20,recipe.strikeHeight*.95));
                case 1->focus.add(8,1,-5);
                case 2->focus.add(.18,.1,-.9);
                default->anchor.add(3.8,2.2,-5.2);
            };
            Vec3 direction=focus.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
            float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
            camera=eye.add(0,-1.62,0);mc.player.setPos(camera);mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
            Vec3 position=camera;mc.getSingleplayerServer().execute(()->{for(var player:mc.getSingleplayerServer().getPlayerList().getPlayers())player.teleportTo(mc.getSingleplayerServer().overworld(),position.x,position.y,position.z,yaw,pitch);});
            LightningClient.previewAt(anchor,1.2);
            var details=new java.util.LinkedHashMap<String,Object>();details.put("fixture",gradient?"gradient-ratio/v1":"wide-glow/v1");details.put("view",test.view);details.put("sharpStress",test.sharp);
            details.put("focusPoint",focus);details.put("expectedCameraEye",eye);details.put("yaw",yaw);details.put("pitch",pitch);
            details.put("mainCornerTurnDegrees",Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,sharpest)))));details.put("mainCornerIndex",joint);details.put("mainCorner",corner);
            captureDetails=details;System.out.println("[LIGHTNING-GLOW] "+test.name+" width="+test.width+" peak=1.2 eye="+eye);
        }
        if(phase==30)capture=test.name;
        if(stage==count-1&&phase==36){
            done=true;LightningClient.stop();ParamRegistry.state(LightningSkillSchema.ID).resetToDefaults();
            mc.options.bobView().set(oldBob);mc.options.cloudStatus().set(oldClouds);mc.options.entityShadows().set(oldShadows);mc.options.renderDistance().set(oldRenderDistance);
            mc.options.hideGui=oldHideGui;mc.options.pauseOnLostFocus=oldPause;mc.options.setCameraType(oldCameraType);
            System.out.println("[LIGHTNING-GLOW] COMPLETE: "+count+" fixed-peak captures; supplied default and options restored");mc.stop();
        }
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||capture==null)return;
        var mc=Minecraft.getInstance();String name=capture;capture=null;
        try {
            var folder=mc.gameDirectory.toPath().resolve("lightning-lab/game-probes");Files.createDirectories(folder);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(name+".png"));}
            var stats=LightningClient.probeStats();stats.put("name",name);stats.put("camera",camera);stats.putAll(captureDetails);
            stats.put("actualCameraEye",mc.gameRenderer.getMainCamera().getPosition());
            stats.put("mainStencilEnabled",mc.getMainRenderTarget().isStencilEnabled());
            Files.writeString(folder.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(stats));
            System.out.println("[LIGHTNING-DEMO] capture="+name+" main="+stats.get("visibleMainSegments")+" impact="+stats.get("visibleImpactSegments"));
        }catch(Exception ex){throw new IllegalStateException("Lightning GPU evidence capture failed: "+name,ex);}
    }
}
