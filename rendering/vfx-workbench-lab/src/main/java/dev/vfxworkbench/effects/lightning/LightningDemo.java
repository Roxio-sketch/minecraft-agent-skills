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
    // Explicit, temporary marker supports the same opt-in fixture in a
    // launcher-managed release instance without changing launcher JVM settings.
    private static final boolean GLOW_REGRESSION=Boolean.getBoolean("vfxworkbench.lightningGlowRegression")||java.nio.file.Files.isRegularFile(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("config/vfxworkbench/run-lightning-regression.once"));
    static boolean glowRegressionEnabled(){return GLOW_REGRESSION;}
    private static final String SAVE=Boolean.getBoolean("vfxworkbench.lightningGradientClarityRegression")?"VFX-Lightning-Gradient-Clarity-Check":Boolean.getBoolean("vfxworkbench.lightningGradientRegression")?"VFX-Lightning-Gradient-Ratio-Check":glowRegressionEnabled()?"VFX-Lightning-Wide-Glow-Check":"VFX-Lightning-Skill-Integration-Check";
    private record Stage(String name,String preset,double time) {}
    private record GlowStage(String name,double width,int view,boolean sharp) {}
    private static final GlowStage[] GLOW_STAGES=glowStages();
    private static final String[] CLARITY_STAGES={"clarity-empty","clarity-user-additive-both31","clarity-user-vivid-both31","clarity-user-vivid-core31","clarity-user-vivid-glow31","clarity-user-vivid-both75","clarity-user-vivid-both0","clarity-user-vivid-both100","clarity-rain-before-hot","clarity-rain-after-hot"};
    private static BoltGeometry clarityProjectionTree;
    private static java.util.Map<String,Object> clarityProjection=java.util.Map.of();
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
        if((!Boolean.getBoolean("vfxworkbench.lightningDemo")&&!glowRegressionEnabled()&&!Boolean.getBoolean("vfxworkbench.lightningGradientRegression")&&!Boolean.getBoolean("vfxworkbench.lightningGradientClarityRegression")&&!Boolean.getBoolean("vfxworkbench.lightningImpactColorRegression"))||event.phase!=TickEvent.Phase.END||done)return;
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
            mc.options.entityShadows().set(false);mc.options.renderDistance().set(Boolean.getBoolean("vfxworkbench.lightningGradientClarityRegression")?4:8);
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();
                level.setDayTime(6000);level.setWeatherParameters(60000,0,false,false);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++)level.setBlock(new BlockPos(x,199,z),Blocks.GRAY_CONCRETE.defaultBlockState(),3);
                int boardZ=Boolean.getBoolean("vfxworkbench.lightningGradientClarityRegression")?8:44;
                for(int x=-40;x<=40;x++)for(int y=200;y<=271;y++)level.setBlock(new BlockPos(x,y,boardZ),
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
        if(Boolean.getBoolean("vfxworkbench.lightningGradientClarityRegression")){gradientClarityRegression(mc);return;}
        if(Boolean.getBoolean("vfxworkbench.lightningImpactColorRegression")){impactColorRegression(mc);return;}
        if(glowRegressionEnabled()||Boolean.getBoolean("vfxworkbench.lightningGradientRegression")){glowRegression(mc);return;}
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
        result.add(new GlowStage("core-upward-yellow-only",.07,3,true));
        result.add(new GlowStage("halo-upward-blue-only",.07,3,true));
        result.add(new GlowStage("core-third-person-yellow",.07,3,true));
        result.add(new GlowStage("tribulation-thin-branching-far",.09,0,false));
        result.add(new GlowStage("water-grid65",0,0,false));
        result.add(new GlowStage("water-grid129",0,0,false));
        return result.toArray(GlowStage[]::new);
    }

    /** User's actual high-gain palette, and changes applied to a still-active event. */
    private static void gradientClarityRegression(Minecraft mc){
        int stage=(ticks-100)/40,phase=(ticks-100)%40;if(stage>=CLARITY_STAGES.length)return;
        if(phase==0){
            mc.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.options.bobView().set(false);
            var state=ParamRegistry.state(LightningSkillSchema.ID);
            java.util.Map<String,Object> hot=java.util.Map.of();
            if(stage==0){
                state.resetToDefaults();state.set("mode","strike");state.set("gradientEnabled","true");
                state.set("gradientRatio","31");state.set("gradientTarget","both");state.set("gradientCoreMode","additive");
                state.set("coreColor","#FFFFFF");state.set("glowColor","#895A75");
                state.set("gradientStart","#E700F2");state.set("gradientEnd","#E3B026");
                // Keep the supplied gain, width, seed and channel geometry unchanged.
                var r=LightningSkillSchema.recipe();Vec3 anchor=new Vec3(0,200,0);
                clarityProjectionTree=BoltGeometry.generate(anchor.add(0,r.strikeHeight,0),anchor,r);
                if(clarityProjectionTree.segments().stream().noneMatch(s->s.depth()>0))throw new AssertionError("User fixture needs actual branches");
                Vec3 focus=anchor.add(0,r.strikeHeight*.45,0),eye=focus.add(0,0,-Math.max(20,r.strikeHeight*.9));
                camera=eye.add(0,-1.62,0);mc.player.setPos(camera);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
                Vec3 position=camera;mc.getSingleplayerServer().execute(()->{for(var p:mc.getSingleplayerServer().getPlayerList().getPlayers())p.teleportTo(mc.getSingleplayerServer().overworld(),position.x,position.y,position.z,0,0);});
                LightningClient.stop();
            }else if(stage==1){
                LightningClient.previewAt(new Vec3(0,200,0),1.2);
                clarityRuntimeClock();
            }else if(stage==8){
                state.set("mode","rain");state.set("rainStyle","cluster");state.set("rainCount","3");state.set("rainSpread","3");state.set("rainStagger","0");
                state.set("gradientRatio","31");state.set("gradientTarget","both");state.set("gradientCoreMode","vivid");
                LightningClient.previewAt(new Vec3(0,200,0),1.2);
                clarityRuntimeClock();
            }else{
                RuntimeSnapshot before=RuntimeSnapshot.read();
                switch(stage){
                    case 2->state.set("gradientCoreMode","vivid");
                    case 3->state.set("gradientTarget","core");
                    case 4->state.set("gradientTarget","glow");
                    case 5->{state.set("gradientTarget","both");state.set("gradientRatio","75");}
                    case 6->state.set("gradientRatio","0");
                    case 7->state.set("gradientRatio","100");
                    case 9->{
                        state.set("gradientRatio","75");state.set("gradientTarget","core");state.set("gradientCoreMode","additive");
                        state.set("coreColor","#D0E0F0");state.set("glowColor","#687898");
                        state.set("gradientStart","#CC00EE");state.set("gradientEnd","#F0B020");
                        state.set("coreGain","4.2");state.set("glowGain","2.8");
                    }
                    default->throw new AssertionError("Unknown clarity stage");
                }
                invokeRefresh();hot=before.assertHotUpdate();
            }
            var details=new java.util.LinkedHashMap<String,Object>();details.put("fixture","gradient-clarity/v1");
            details.put("stageIndex",stage);details.put("runtimeHotUpdate",hot);details.put("paletteSource","User screenshot #E700F2 -> #E3B026, core #FFFFFF, glow #895A75, ratio31; supplied gains3.8/2.4");
            details.put("expectedCameraEye",camera.add(0,1.62,0));captureDetails=details;
            System.out.println("[LIGHTNING-GRADIENT-CLARITY] stage="+CLARITY_STAGES[stage]+" HOT="+!hot.isEmpty());
        }
        if(phase==30)capture=CLARITY_STAGES[stage];
        if(stage==CLARITY_STAGES.length-1&&phase==36){
            done=true;LightningClient.stop();ParamRegistry.state(LightningSkillSchema.ID).resetToDefaults();
            mc.options.bobView().set(oldBob);mc.options.cloudStatus().set(oldClouds);mc.options.entityShadows().set(oldShadows);mc.options.renderDistance().set(oldRenderDistance);
            mc.options.hideGui=oldHideGui;mc.options.pauseOnLostFocus=oldPause;mc.options.setCameraType(oldCameraType);
            System.out.println("[LIGHTNING-GRADIENT-CLARITY] COMPLETE: 10 high-gain captures; 7 actual HOT refresh checks; active trees, clocks, anchors and rain children preserved");mc.stop();
        }
    }

    // Development-only reflection verifies the real client refresh without adding a production debug API.
    private static Object clientField(String name){
        try{var f=LightningClient.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Clarity runtime probe: "+name,failure);}
    }
    private static void invokeRefresh(){
        try{var method=LightningClient.class.getDeclaredMethod("refresh");method.setAccessible(true);method.invoke(null);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Clarity fixture failed to execute actual client refresh",failure);}
    }
    private static void clarityRuntimeClock(){
        // Keep both clock fields nontrivial: the render stays fixed at its peak,
        // while resetting the ordinary runtime clock is independently detectable.
        try{var field=LightningClient.class.getDeclaredField("time");field.setAccessible(true);field.setDouble(null,7.25);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Clarity runtime clock setup failed",failure);}
    }
    private record RuntimeSnapshot(Object orbit,Object bolt,Object rain,Object anchor,double time,double fixedTime,java.util.List<Object> rainTrees,java.util.List<Object> rainImpacts){
        static RuntimeSnapshot read(){
            var children=(java.util.List<RainGeometry.Bolt>)clientField("rain");
            java.util.List<Object> trees=children==null?java.util.List.of():children.stream().map(b->(Object)b.geometry()).toList();
            java.util.List<Object> impacts=children==null?java.util.List.of():children.stream().map(b->(Object)b.impact()).toList();
            return new RuntimeSnapshot(clientField("orbit"),clientField("bolt"),children,clientField("anchor"),(double)clientField("time"),(double)clientField("fixedTime"),trees,impacts);
        }
        java.util.Map<String,Object> assertHotUpdate(){
            var after=read();
            if(orbit!=after.orbit||bolt!=after.bolt||rain!=after.rain||anchor!=after.anchor||time!=after.time||fixedTime!=after.fixedTime)throw new AssertionError("Appearance HOT refresh rebuilt geometry, moved anchor or reset clock");
            if(orbit==null&&bolt==null&&rain==null)throw new AssertionError("HOT verification must use an active event");
            var expected=LightningSkillSchema.recipe();var actual=(LightningRecipe)clientField("recipe");assertAppearance(expected,actual);
            int count=0;
            if(rain!=null)for(var child:(java.util.List<RainGeometry.Bolt>)rain){
                if(child.geometry()!=rainTrees.get(count)||child.impact()!=rainImpacts.get(count))throw new AssertionError("Rain child geometry/impact object changed under HOT refresh");
                assertAppearance(expected,child.recipe());count++;
            }
            var report=new java.util.LinkedHashMap<String,Object>();report.put("passed",true);report.put("actualRefreshInvoked",true);report.put("geometryObjectIdentical",true);report.put("anchorObjectIdentical",true);report.put("elapsedClockIdentical",true);
            report.put("beforeTime",time);report.put("afterTime",after.time);report.put("beforeFixedTime",fixedTime);report.put("afterFixedTime",after.fixedTime);report.put("rainChildrenAppearanceChecked",count);
            report.put("checkedFields",java.util.List.of("coreColor","glowColor","coreGain","glowGain","gradientEnabled","gradientStart","gradientEnd","gradientRatio","gradientTarget","gradientCoreMode"));
            return report;
        }
    }
    private static void assertAppearance(LightningRecipe expected,LightningRecipe actual){
        for(String name:java.util.List.of("coreColor","glowColor","coreGain","glowGain","gradientEnabled","gradientStart","gradientEnd","gradientRatio","gradientTarget","gradientCoreMode")){
            try{var field=LightningRecipe.class.getField(name);if(!java.util.Objects.equals(field.get(expected),field.get(actual)))throw new AssertionError("HOT appearance mismatch: "+name);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Missing appearance contract: "+name,failure);}
        }
    }

    /** Exact GPU clip convention, attached to the screenshot from the same rendered frame. */
    @SubscribeEvent public static void clarityFrame(net.minecraftforge.client.event.RenderLevelStageEvent event){
        if(!Boolean.getBoolean("vfxworkbench.lightningGradientClarityRegression")||clarityProjectionTree==null||event.getStage()!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();var vp=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(event.getPoseStack().last().pose());
        Vec3 eye=event.getCamera().getPosition();var r=LightningSkillSchema.recipe();var segments=new java.util.ArrayList<java.util.Map<String,Object>>();
        for(var s:clarityProjectionTree.segments()){
            var row=new java.util.LinkedHashMap<String,Object>();row.put("depth",s.depth());row.put("path",s.mask());row.put("alongA",s.alongA());row.put("alongB",s.alongB());row.put("intensity",s.intensity());
            row.put("a",projectClarity(s.a(),eye,vp,mc.getMainRenderTarget().width,mc.getMainRenderTarget().height));row.put("b",projectClarity(s.b(),eye,vp,mc.getMainRenderTarget().width,mc.getMainRenderTarget().height));
            row.put("coreMaterialA",LightningAppearance.colorAt(r,false,s.alongA()));row.put("coreMaterialB",LightningAppearance.colorAt(r,false,s.alongB()));
            row.put("glowMaterialA",LightningAppearance.colorAt(r,true,s.alongA()));row.put("glowMaterialB",LightningAppearance.colorAt(r,true,s.alongB()));segments.add(row);
        }
        var out=new java.util.LinkedHashMap<String,Object>();out.put("pixelOrigin","top-left");out.put("clipMatrix",vp.get(new float[16]));out.put("width",mc.getMainRenderTarget().width);out.put("height",mc.getMainRenderTarget().height);out.put("segments",segments);clarityProjection=out;
    }
    private static java.util.Map<String,Object> projectClarity(Vec3 world,Vec3 eye,org.joml.Matrix4f vp,int width,int height){
        Vec3 p=world.subtract(eye);var clip=new org.joml.Vector4f((float)p.x,(float)p.y,(float)p.z,1);vp.transform(clip);
        var out=new java.util.LinkedHashMap<String,Object>();out.put("clipW",clip.w);out.put("pixelX",(clip.x/clip.w*.5+.5)*width);out.put("pixelY",(.5-clip.y/clip.w*.5)*height);out.put("ndcZ",clip.z/clip.w);return out;
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
        if(phase==0 && test.name.startsWith("water-")) {
            LightningClient.stop();mc.setScreen(null);
            var state=ParamRegistry.state(dev.vfxworkbench.effects.water.WaterSkillSchema.ID);state.resetToDefaults();
            state.set("grid",test.name.endsWith("129")?"129":"65");
            Vec3 waterOrigin=new Vec3(0,200.08,0),eye=waterOrigin.add(0,6,-10);
            camera=eye.add(0,-1.62,0);mc.player.setPos(camera);
            float pitch=(float)Math.toDegrees(Math.atan2(6,10));mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
            dev.vfxworkbench.effects.water.WaterEffect.create(waterOrigin);dev.vfxworkbench.effects.water.WaterEffect.impulseCenter();
            captureDetails=new java.util.LinkedHashMap<>();captureDetails.put("fixture","water-performance/v1");captureDetails.put("grid",state.getInt("grid"));
            captureDetails.put("drawsBefore",dev.vfxworkbench.effects.water.WaterRenderer.draws);
        } else if(phase==0){
            mc.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.options.bobView().set(false);
            var state=ParamRegistry.state(LightningSkillSchema.ID);state.resetToDefaults();state.set("mode","strike");
            // Historical halo/ratio fixtures keep the original additive core contract.
            // The separate clarity fixture measures the new default at the user's high gain.
            state.set("gradientCoreMode","additive");
            state.set("glowWidth",Double.toString(test.width));state.set("coreGain","16");state.set("glowGain","8.028");
            if(gradient){
                state.set("gradientEnabled","true");state.set("gradientRatio",Integer.toString(stage*25));
                state.set("coreColor","#ffffff");state.set("glowColor","#ffffff");
                state.set("gradientStart","#ff2040");state.set("gradientEnd","#2060ff");
                state.set("coreGain","1");state.set("glowGain","1");
            }
            if(test.sharp){state.set("strikeDisplacement","12");state.set("strikeRoughness",".85");}
            if(test.name.startsWith("core-")||test.name.startsWith("halo-")){
                state.set("gradientEnabled","false");state.set("coreColor","#ffff00");state.set("glowColor","#0088ff");
                state.set("coreGain","3.8");
                if(test.name.startsWith("halo-"))state.set("coreColor","#000000");
                state.set("glowGain",test.name.startsWith("core-")?"0":"2.4");
            }
            if(test.name.startsWith("tribulation-")){state.set("preset","tribulation");mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().overworld().setDayTime(18000));}
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
            mc.options.hideGui=!test.name.contains("third-person");
            if(test.name.contains("third-person")){
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                mc.getSingleplayerServer().execute(()->{for(var player:mc.getSingleplayerServer().getPlayerList().getPlayers()){
                    player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.setNoGravity(true);
                }});
            }
            LightningClient.previewAt(anchor,1.2);
            var details=new java.util.LinkedHashMap<String,Object>();details.put("fixture",gradient?"gradient-ratio/v1":"wide-glow/v1");details.put("view",test.view);details.put("sharpStress",test.sharp);
            details.put("focusPoint",focus);details.put("expectedCameraEye",eye);details.put("yaw",yaw);details.put("pitch",pitch);
            details.put("mainCornerTurnDegrees",Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,sharpest)))));details.put("mainCornerIndex",joint);details.put("mainCorner",corner);
            if(test.name.startsWith("tribulation-")) {
                // Exercise the ordinary F7 entry point, then freeze only its animation clock.
                LightningClient.preview();
                Vec3 actual=(Vec3)LightningClient.probeStats().get("anchor");
                if(Math.abs(Math.hypot(actual.x-camera.x,actual.z-camera.z)-90)>1e-5
                    || Math.abs(actual.y-200)>1e-5)throw new AssertionError("normal forward trigger missed fixture ground: "+actual);
                LightningClient.freezeForProbe(1.2);
                details.put("normalPreviewEntryPoint",true);details.put("triggerDistance",90);
                details.put("resolvedTriggerAnchor",actual);
            }
            captureDetails=details;System.out.println("[LIGHTNING-GLOW] "+test.name+" width="+test.width+" peak=1.2 eye="+eye);
        }
        if(phase==30){
            if(test.name.startsWith("water-")) {
                long before=((Number)captureDetails.get("drawsBefore")).longValue();
                long draws=dev.vfxworkbench.effects.water.WaterRenderer.draws-before;
                int error=org.lwjgl.opengl.GL11.glGetError();
                if(draws<=0||error!=0)throw new AssertionError("water render regression: draws="+draws+" GL="+error);
                captureDetails.put("waterDraws",draws);captureDetails.put("waterGlError",error);
            }
            capture=test.name;
        }
        if(stage==count-1&&phase==36){
            done=true;LightningClient.stop();dev.vfxworkbench.effects.water.WaterEffect.clear();ParamRegistry.state(dev.vfxworkbench.effects.water.WaterSkillSchema.ID).resetToDefaults();ParamRegistry.state(LightningSkillSchema.ID).resetToDefaults();
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
            var stats=LightningClient.probeStats();stats.put("shaderCompatibilityActive",LightningShaderBridge.active());stats.put("hudHidden",mc.options.hideGui);stats.put("captureGlError",org.lwjgl.opengl.GL11.glGetError());stats.put("lateDraws",LightningShaderBridge.draws);stats.put("name",name);stats.put("camera",camera);stats.putAll(captureDetails);
            if(Boolean.getBoolean("vfxworkbench.lightningGradientClarityRegression"))stats.put("projection",clarityProjection);
            stats.put("actualCameraEye",mc.gameRenderer.getMainCamera().getPosition());
            stats.put("mainStencilEnabled",mc.getMainRenderTarget().isStencilEnabled());
            var tree=(BoltGeometry)clientField("bolt");
            if(tree!=null){
                var roots=new java.util.LinkedHashMap<Long,BoltGeometry.Segment>();
                for(var segment:tree.segments())if(segment.depth()==1)roots.putIfAbsent(segment.mask(),segment);
                stats.put("primaryBranchRoots",roots.values().stream().map(segment->java.util.Map.of("along",segment.alongA(),"position",segment.a())).toList());
                stats.put("branchPaths",tree.segments().stream().filter(segment->segment.depth()>0).map(BoltGeometry.Segment::mask).distinct().count());
            }
            stats.put("cameraType",mc.options.getCameraType().name());stats.put("spectator",mc.player.isSpectator());
            Files.writeString(folder.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(stats));
            System.out.println("[LIGHTNING-DEMO] capture="+name+" main="+stats.get("visibleMainSegments")+" impact="+stats.get("visibleImpactSegments"));
        }catch(Exception ex){throw new IllegalStateException("Lightning GPU evidence capture failed: "+name,ex);}
    }
}
