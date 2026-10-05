package dev.vfxworkbench.effects.blackhole;

import dev.vfxworkbench.client.VfxWorkbenchMod;
import dev.vfxworkbench.core.ParamRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.Difficulty;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in development-world verifier; never enters or edits the player's other saves. */
@Mod.EventBusSubscriber(modid=VfxWorkbenchMod.MODID,value=Dist.CLIENT)
public final class BlackHoleDemo {
 private static final String SAVE=Boolean.getBoolean("vfxworkbench.blackHolePresetRegression")?"VFX-BlackHole-Vertical-Presets-Check":Boolean.getBoolean("vfxworkbench.blackHolePlayerToggleRegression")?"VFX-BlackHole-Player-Lens-Toggle-Check":Boolean.getBoolean("vfxworkbench.blackHoleMotionRegression")?"VFX-BlackHole-Camera-Motion-Check":Boolean.getBoolean("vfxworkbench.blackHoleThirdPersonRegression")?"VFX-BlackHole-Third-Person-Lens-Check":Boolean.getBoolean("vfxworkbench.blackHoleVerticalRegression")?"VFX-BlackHole-Vertical-Lens-Check":"VFX-BlackHole-Workbench-Demo";
 private static boolean opened,setup,placed,done;
 private static int ticks,form;
 private static volatile int thirdPersonActorId=-1;
 private static final net.minecraft.world.phys.Vec3 MOTION_CENTER=new net.minecraft.world.phys.Vec3(.5,121.5,14.5);
 private static final String[] MOTION_CASES={"static-bob-on","walk-bob-on","walk-bob-off","sprint-bob-on","sprint-bob-off","hurt-static","hurt-walk-bob-on","third-strafe-bob-on","third-strafe-bob-off","third-rotate-strafe"};
 private static final java.util.Map<String,net.minecraft.world.phys.Vec3> MOTION_MARKERS=java.util.Map.of("center",MOTION_CENTER,"left",new net.minecraft.world.phys.Vec3(-2.5,121.5,14.5),"right",new net.minecraft.world.phys.Vec3(3.5,121.5,14.5),"ground",new net.minecraft.world.phys.Vec3(5.5,120.5,8.5));
 private static boolean motionOldBob,motionOldShadows,motionOldHideGui,motionOldPause;
 private static double motionOldDamageTilt;
 private static int motionOldRenderDistance,motionStage,motionPhase,motionTicks;
 private static net.minecraft.client.CloudStatus motionOldClouds;
 private static net.minecraft.client.CameraType motionOldCameraType;
 private static volatile boolean motionSceneReady;
 private static String motionCapture;
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
  if((!Boolean.getBoolean("vfxworkbench.blackHoleDemo")&&!Boolean.getBoolean("vfxworkbench.blackHoleThirdPersonRegression")&&!Boolean.getBoolean("vfxworkbench.blackHoleMotionRegression")&&!Boolean.getBoolean("vfxworkbench.blackHolePlayerToggleRegression")&&!Boolean.getBoolean("vfxworkbench.blackHolePresetRegression"))||e.phase!=TickEvent.Phase.END)return;
  var mc=Minecraft.getInstance();
  if(!opened&&mc.screen instanceof TitleScreen){
   opened=true;
   if(Boolean.getBoolean("vfxworkbench.blackHoleMotionRegression")||Boolean.getBoolean("vfxworkbench.blackHolePresetRegression"))saveMotionOptions(mc);
   if(java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve("level.dat")))mc.createWorldOpenFlows().loadLevel(mc.screen,SAVE);
   else mc.createWorldOpenFlows().createFreshLevel(SAVE,new LevelSettings("黑洞形态展示",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261004L,false,false),WorldPresets::createNormalWorldDimensions);
   return;
  }
  if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||done)return;
  if(!setup){
   setup=true;var server=mc.getSingleplayerServer();
   server.execute(()->{var level=server.overworld();level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
    for(int x=-12;x<=12;x++)for(int y=110;y<=132;y++)level.setBlock(new BlockPos(x,y,26),((x+y)&1)==0?Blocks.WHITE_CONCRETE.defaultBlockState():Blocks.BLUE_CONCRETE.defaultBlockState(),3);
    for(var player:server.getPlayerList().getPlayers()){player.setGameMode(GameType.SPECTATOR);player.teleportTo(level,0,120,0,0,0);}
   });return;
  }
  if(++ticks<100)return;
  mc.options.pauseOnLostFocus=false;
  if(Boolean.getBoolean("vfxworkbench.blackHoleMotionRegression")){motionRegression(mc);return;}
  if(Boolean.getBoolean("vfxworkbench.blackHoleThirdPersonRegression")||Boolean.getBoolean("vfxworkbench.blackHolePlayerToggleRegression")){thirdPersonRegression(mc);return;}
  if(Boolean.getBoolean("vfxworkbench.blackHoleVerticalRegression")){verticalRegression(mc);return;}
  if(Boolean.getBoolean("vfxworkbench.blackHoleRegression")){regression(mc);return;}
  if(form>=BlackHoleSkillSchema.FORMS.size()){
   if(Boolean.getBoolean("vfxworkbench.blackHolePresetRegression")&&ticks>=130+(form-1)*50){
    done=true;BlackHoleClient.stop();BlackHoleRender.frozenTime=-1;ParamRegistry.state(BlackHoleSkillSchema.ID).resetToDefaults();
    mc.options.bobView().set(motionOldBob);mc.options.cloudStatus().set(motionOldClouds);mc.options.entityShadows().set(motionOldShadows);mc.options.renderDistance().set(motionOldRenderDistance);
    mc.options.hideGui=motionOldHideGui;mc.options.pauseOnLostFocus=motionOldPause;mc.options.setCameraType(motionOldCameraType);
    System.out.println("[BH-PRESETS] COMPLETE: 11 vertical presets");mc.stop();
   }return;
  }
  if(!placed){
   mc.setScreen(null);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
   if(Boolean.getBoolean("vfxworkbench.blackHolePresetRegression")){
    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.options.bobView().set(false);
    mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);mc.options.entityShadows().set(false);mc.options.hideGui=true;mc.options.renderDistance().set(4);
   }
   BlackHoleClient.preview();
   if(Boolean.getBoolean("vfxworkbench.blackHolePresetRegression"))BlackHoleClient.center=new net.minecraft.world.phys.Vec3(0,121.62,14);
   BlackHoleRender.frozenTime=35;placed=true;ticks=100;
  }
  if(ticks==100+form*50){String f=BlackHoleSkillSchema.FORMS.get(form);ParamRegistry.state(BlackHoleSkillSchema.ID).set("form",f);System.out.println("[VFXWORKBENCH-DEMO] form="+f);}
  if(ticks==125+form*50){BlackHoleWorld.probe=(Boolean.getBoolean("vfxworkbench.blackHolePresetRegression")?"vertical-preset-":"workbench-")+BlackHoleSkillSchema.FORMS.get(form);form++;if(form==BlackHoleSkillSchema.FORMS.size()&&!Boolean.getBoolean("vfxworkbench.blackHolePresetRegression")){done=true;BlackHoleRender.frozenTime=-1;System.out.println("[VFXWORKBENCH-DEMO] forms queued; world stays open");}}
 }
 private static void saveMotionOptions(Minecraft mc){
  motionOldBob=mc.options.bobView().get();motionOldDamageTilt=mc.options.damageTiltStrength().get();motionOldShadows=mc.options.entityShadows().get();motionOldClouds=mc.options.cloudStatus().get();
  motionOldHideGui=mc.options.hideGui;motionOldPause=mc.options.pauseOnLostFocus;motionOldRenderDistance=mc.options.renderDistance().get();motionOldCameraType=mc.options.getCameraType();
 }
 private static void motionInput(Minecraft mc,boolean forward,boolean right,boolean sprint){
  mc.options.keyUp.setDown(forward);mc.options.keyDown.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(right);mc.options.keySprint.setDown(sprint);mc.options.keyJump.setDown(false);mc.options.keyShift.setDown(false);mc.player.setSprinting(sprint);
 }
 /** A stationary world target viewed through actual vanilla movement and camera effects. */
 private static void motionRegression(Minecraft mc){
  if(!placed){
   placed=true;mc.setScreen(null);mc.options.hideGui=true;mc.options.renderDistance().set(4);
   mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);mc.options.entityShadows().set(false);mc.options.damageTiltStrength().set(1.0);
   mc.getSingleplayerServer().execute(()->{
    var server=mc.getSingleplayerServer();var l=server.overworld();l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
    l.setWeatherParameters(6000,0,false,false);var remove=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
    for(var entity:l.getAllEntities())if(!(entity instanceof net.minecraft.world.entity.player.Player))remove.add(entity);for(var entity:remove)entity.discard();
    for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++)l.setBlock(new BlockPos(x,119,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
    for(int x=-24;x<=24;x++)for(int z=-20;z<=24;z++)for(int y=120;y<=128;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
    l.setBlock(new BlockPos(0,120,14),Blocks.WHITE_CONCRETE.defaultBlockState(),3);l.setBlock(new BlockPos(0,121,14),Blocks.GOLD_BLOCK.defaultBlockState(),3);
    l.setBlock(new BlockPos(-3,121,14),Blocks.MAGENTA_CONCRETE.defaultBlockState(),3);l.setBlock(new BlockPos(3,121,14),Blocks.CYAN_CONCRETE.defaultBlockState(),3);l.setBlock(new BlockPos(5,120,8),Blocks.WHITE_CONCRETE.defaultBlockState(),3);
    for(var player:server.getPlayerList().getPlayers()){
     player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=false;player.onUpdateAbilities();player.setNoGravity(false);player.setForcedPose(null);player.teleportTo(l,0,120,0,0,0);
    }
    motionSceneReady=true;
   });
   mc.player.setNoGravity(false);mc.player.getAbilities().flying=false;mc.player.setForcedPose(null);mc.player.setPose(net.minecraft.world.entity.Pose.STANDING);
   BlackHoleClient.preview();BlackHoleClient.center=MOTION_CENTER;
   var state=ParamRegistry.state(BlackHoleSkillSchema.ID);state.set("form","classic");state.set("scale","1.6");state.set("core",".58");state.set("inner",".94");state.set("outer","2.9");
   state.set("density","0");state.set("halo","0");state.set("bend","0");state.set("lensStrength","0");state.set("coreColor","#000000");state.set("coreGradientStrength","0");
   state.set("particles","true");state.set("particleCount","48");state.set("particleSpeed","0");state.set("particleOuter","3.2");state.set("particleSize",".07");state.set("particleBrightness","2.5");
   state.set("playing","false");state.set("time","35");BlackHoleRender.frozenTime=35;BlackHoleWorld.probeEntity=null;
  }
  if(!motionSceneReady||ticks<160)return;
  int stage=motionTicks/64,phase=motionTicks++%64;if(stage>=MOTION_CASES.length)return;
  motionStage=stage;motionPhase=phase;BlackHoleClient.center=MOTION_CENTER;
  if(phase==0){
   motionInput(mc,false,false,false);mc.options.bobView().set(stage!=2&&stage!=4&&stage!=8);
   mc.options.setCameraType(stage>=7?net.minecraft.client.CameraType.THIRD_PERSON_BACK:net.minecraft.client.CameraType.FIRST_PERSON);
   mc.player.setPos(0,120,0);mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;mc.player.hurtTime=0;
   mc.getSingleplayerServer().execute(()->{for(var player:mc.getSingleplayerServer().getPlayerList().getPlayers())player.teleportTo(mc.getSingleplayerServer().overworld(),0,120,0,0,0);});
  }
  boolean moving=phase>=10&&phase<=54,forward=moving&&(stage==1||stage==2||stage==3||stage==4||stage==6),side=moving&&stage>=7;
  motionInput(mc,forward,side,moving&&(stage==3||stage==4));
  if(stage==9&&moving){mc.player.setYRot(-15+(phase-10)*.65f);mc.player.setXRot((float)Math.sin((phase-10)*.13)*2.5f);}
  if((stage==5||stage==6)&&(phase==18||phase==34))mc.player.animateHurt(45);
  if(phase==20||phase==28||phase==36||phase==44||phase==52){
   String name="motion-"+MOTION_CASES[stage]+"-t"+phase;motionCapture=name;BlackHoleWorld.probe=name;
   System.out.println("[BH-MOTION] "+name+" center="+MOTION_CENTER+" player="+mc.player.position()+" walk="+mc.player.walkDist+" bob="+mc.player.bob+" hurt="+mc.player.hurtTime);
  }
  if(stage==MOTION_CASES.length-1&&phase==60){
   done=true;motionInput(mc,false,false,false);BlackHoleClient.stop();BlackHoleRender.frozenTime=-1;ParamRegistry.state(BlackHoleSkillSchema.ID).resetToDefaults();
   mc.options.bobView().set(motionOldBob);mc.options.damageTiltStrength().set(motionOldDamageTilt);mc.options.entityShadows().set(motionOldShadows);mc.options.cloudStatus().set(motionOldClouds);mc.options.renderDistance().set(motionOldRenderDistance);
   mc.options.hideGui=motionOldHideGui;mc.options.pauseOnLostFocus=motionOldPause;mc.options.setCameraType(motionOldCameraType);
   System.out.println("[BH-MOTION] COMPLETE: 10 movement cases / 50 same-frame probes; supplied recipe and options restored");mc.stop();
  }
 }
 /** Marker metadata belongs to the exact frame subsequently consumed by the world probe. */
 @SubscribeEvent public static void motionFrame(net.minecraftforge.client.event.RenderLevelStageEvent event){
  if(!Boolean.getBoolean("vfxworkbench.blackHoleMotionRegression")||motionCapture==null||event.getStage()!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
  String name=motionCapture;motionCapture=null;var mc=Minecraft.getInstance();
  try{
   var data=new java.util.LinkedHashMap<String,Object>();data.put("name",name);data.put("fixture","fixed-world-camera-motion/v1");data.put("case",MOTION_CASES[motionStage]);data.put("phaseTick",motionPhase);
   data.put("fixedCenter",MOTION_CENTER);data.put("centerWasFixed",MOTION_CENTER.equals(BlackHoleClient.center));data.put("markers",MOTION_MARKERS);data.put("pixelOrigin","bottom-left");
   data.put("playerPosition",mc.player.position());data.put("interpolatedPlayerPosition",mc.player.getPosition(event.getPartialTick()));data.put("rawCamera",event.getCamera().getPosition());data.put("partialTick",event.getPartialTick());
   data.put("walkDist",mc.player.walkDist);data.put("walkDistOld",mc.player.walkDistO);data.put("bob",mc.player.bob);data.put("bobOld",mc.player.oBob);data.put("bobView",mc.options.bobView().get());data.put("hurtTime",mc.player.hurtTime);data.put("hurtDuration",mc.player.hurtDuration);data.put("hurtDirection",mc.player.getHurtDir());
   data.put("cameraMode",mc.options.getCameraType().name());data.put("sprinting",mc.player.isSprinting());data.put("yaw",mc.player.getYRot());data.put("pitch",mc.player.getXRot());data.put("particlesFrozenTicks",BlackHoleRender.frozenTime);
   var vp=new org.joml.Matrix4f(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());data.put("terrainViewProjection",vp.get(new float[16]));
   var points=new java.util.LinkedHashMap<String,Object>();
   for(var marker:MOTION_MARKERS.entrySet()){
    var p=marker.getValue().subtract(event.getCamera().getPosition());var clip=new org.joml.Vector4f((float)p.x,(float)p.y,(float)p.z,1);vp.transform(clip);
    var projected=new java.util.LinkedHashMap<String,Object>();projected.put("clipW",clip.w);projected.put("ndcX",clip.x/clip.w);projected.put("ndcY",clip.y/clip.w);
    projected.put("pixelX",(clip.x/clip.w*.5+.5)*mc.getMainRenderTarget().width);projected.put("pixelY",(clip.y/clip.w*.5+.5)*mc.getMainRenderTarget().height);points.put(marker.getKey(),projected);
   }
   data.put("terrainMarkerProjections",points);var folder=mc.gameDirectory.toPath().resolve("black-hole-lab/world-probes");java.nio.file.Files.createDirectories(folder);
   java.nio.file.Files.writeString(folder.resolve(name+"-fixture.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data));
  }catch(Exception failure){throw new IllegalStateException("Camera-motion same-frame metadata failed: "+name,failure);}
 }
 /** Controlled actor/scene captures for a local player crossing the mass plane. */
 private static void thirdPersonRegression(Minecraft mc){
  if(!placed){
   placed=true;mc.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
   if(Boolean.getBoolean("vfxworkbench.stencilRegression"))mc.getMainRenderTarget().enableStencil();
   // All depth added after the terrain snapshot must belong to the test actor.
   mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
   mc.options.entityShadows().set(false);
   mc.options.renderDistance().set(4);mc.options.hideGui=true;
   mc.getSingleplayerServer().execute(()->{
    var l=mc.getSingleplayerServer().overworld();var server=mc.getSingleplayerServer();
    l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
    l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);l.setWeatherParameters(6000,0,false,false);
    var remove=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
    for(var entity:l.getAllEntities())if(!(entity instanceof net.minecraft.world.entity.player.Player))remove.add(entity);
    for(var entity:remove)entity.discard();
    for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++)l.setBlock(new BlockPos(x,119,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
    for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)for(int y=120;y<=134;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
    for(int t=-26;t<=26;t++)for(int y=112;y<=140;y++){
     var block=((Math.floorDiv(t,2)+Math.floorDiv(y,2))&1)==0?Blocks.WHITE_CONCRETE.defaultBlockState():Blocks.BLUE_CONCRETE.defaultBlockState();
     l.setBlock(new BlockPos(t,y,26),block,3);l.setBlock(new BlockPos(t,y,-26),block,3);
     l.setBlock(new BlockPos(26,y,t),block,3);l.setBlock(new BlockPos(-26,y,t),block,3);
    }
    for(var player:server.getPlayerList().getPlayers()){
     player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
     player.setNoGravity(true);player.teleportTo(l,0,120,0,0,0);
    }
   });
   BlackHoleClient.preview();var s=ParamRegistry.state(BlackHoleSkillSchema.ID);
   s.set("form","classic");s.set("playing","false");s.set("time","35");
  }
  String[] names={"front-off","front-on","local-entirely-behind","plane-through-chest","plane-through-feet","core-through-torso","camera-inside-core","close-yaw45-pitch20","yaw-minus70-pitch-minus25","crouching-at-plane","swimming-at-plane","held-item-swing-at-plane","other-entity-front","other-entity-behind"};
  boolean toggle=Boolean.getBoolean("vfxworkbench.blackHolePlayerToggleRegression");
  int index=(ticks-160)/45,phase=(ticks-160)%45,count=toggle?names.length*2:names.length;
  if(ticks<160||index>=count)return;
  int stage=index%names.length;
  float yaw=stage==7?45:stage==8?-70:0,pitch=stage==7?20:stage==8?-25:0;
  // Hold position and pose after the vanilla tick, avoiding a moving depth mask.
  mc.player.setPos(0,120,0);mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
  mc.player.setNoGravity(true);mc.player.getAbilities().flying=true;
  mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
  mc.player.setYBodyRot(yaw);mc.player.yBodyRotO=yaw;mc.player.setYHeadRot(yaw);mc.player.yHeadRotO=yaw;
  var pose=stage==9?net.minecraft.world.entity.Pose.CROUCHING:stage==10?net.minecraft.world.entity.Pose.SWIMMING:net.minecraft.world.entity.Pose.STANDING;
  mc.player.setForcedPose(pose);mc.player.setPose(pose);mc.player.setSwimming(stage==10);mc.player.setShiftKeyDown(stage==9);
  if(stage==11){mc.player.swinging=true;mc.player.swingingArm=net.minecraft.world.InteractionHand.MAIN_HAND;mc.player.swingTime=3;mc.player.attackAnim=.5f;mc.player.oAttackAnim=.5f;}
  var forward=mc.player.getLookAngle();var right=new net.minecraft.world.phys.Vec3(forward.z,0,-forward.x).normalize();
  if(phase==0){
   // Isolate the non-local actor depth delta from the local player model.
   mc.options.setCameraType(stage>=12?net.minecraft.client.CameraType.FIRST_PERSON:net.minecraft.client.CameraType.THIRD_PERSON_BACK);
   var s=ParamRegistry.state(BlackHoleSkillSchema.ID);
   s.set("lensAffectsPlayer",Boolean.toString(toggle&&index>=names.length));
   s.set("scale","1");s.set("core",".45");s.set("inner","1.1");s.set("outer","3");s.set("tilt","0");
   s.set("bend","1.1");s.set("lensRadius","6");s.set("lensStrength",stage==0?"0":"1");
   s.set("density","0");s.set("halo","0");s.set("particles","false");s.set("coreGradientStrength","0");s.set("coreColor","#000000");
   var main=stage==11?new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD):net.minecraft.world.item.ItemStack.EMPTY;
   var off=stage==11?new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD):net.minecraft.world.item.ItemStack.EMPTY;
   mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,main);mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,off);
   mc.getSingleplayerServer().execute(()->{
    if(stage<12&&thirdPersonActorId>=0){
     var previous=mc.getSingleplayerServer().overworld().getEntity(thirdPersonActorId);
     if(previous!=null)previous.discard();thirdPersonActorId=-1;
    }
    for(var player:mc.getSingleplayerServer().getPlayerList().getPlayers()){
     player.setForcedPose(pose);player.setPose(pose);player.setSwimming(stage==10);player.setShiftKeyDown(stage==9);
     player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);player.teleportTo(mc.getSingleplayerServer().overworld(),0,120,0,yaw,pitch);
     player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,main.copy());player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,off.copy());
    }
   });
   BlackHoleWorld.probeEntity=null;
   BlackHoleClient.center=switch(stage){
    case 0,1->new net.minecraft.world.phys.Vec3(0,121.1,3);
    case 2->new net.minecraft.world.phys.Vec3(1.2,121.1,toggle?-2.2:-1.6);
    case 3->new net.minecraft.world.phys.Vec3(.9,121.1,0);
    case 4->new net.minecraft.world.phys.Vec3(.8,120.15,0);
    case 5->new net.minecraft.world.phys.Vec3(0,121,0);
    case 6->mc.gameRenderer.getMainCamera().getPosition().add(forward.scale(.15)).add(right.scale(.1));
    case 7->mc.player.getEyePosition().add(forward.scale(-2.4)).add(right.scale(1.3));
    case 8->mc.player.getEyePosition().add(forward.scale(-.2)).add(right.scale(.9));
    case 9->new net.minecraft.world.phys.Vec3(.9,120.6,0);
    case 10->new net.minecraft.world.phys.Vec3(.9,120.3,0);
    case 11->new net.minecraft.world.phys.Vec3(.9,121.1,0);
    default->new net.minecraft.world.phys.Vec3(0,121.1,4);
   };
   if(stage==5||stage==6)s.set("core",".8");
   if(stage==12||stage==13){
    mc.getSingleplayerServer().execute(()->{
     var l=mc.getSingleplayerServer().overworld();
     if(thirdPersonActorId>=0){var previous=l.getEntity(thirdPersonActorId);if(previous!=null)previous.discard();}
     var actor=new net.minecraft.world.entity.decoration.ArmorStand(l,1.4,120,stage==12?2:8);
     actor.setNoGravity(true);actor.setShowArms(true);actor.setNoBasePlate(true);actor.setYRot(180);
     actor.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_HELMET));
     actor.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
     actor.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS));
     actor.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS));
     l.addFreshEntity(actor);thirdPersonActorId=actor.getId();
    });
   }
  }
  if(stage>=12)BlackHoleWorld.probeEntity=mc.level.getEntity(thirdPersonActorId);
  if(phase==35){
   if(stage>=12&&BlackHoleWorld.probeEntity==null)throw new IllegalStateException("Third-person regression target was not received by client");
   BlackHoleWorld.probe=(toggle?"player-toggle-"+(index>=names.length?"on-":"off-"):"third-person-")+names[stage];
   System.out.println("[BH-THIRD-PERSON] "+names[stage]+" pose="+mc.player.getPose()+" camera="+mc.gameRenderer.getMainCamera().getPosition()+" center="+BlackHoleClient.center+" target="+(BlackHoleWorld.probeEntity==null?"local-player":BlackHoleWorld.probeEntity.getId()));
  }
  if(index==count-1&&phase==40){done=true;System.out.println("[BH-THIRD-PERSON] COMPLETE");mc.stop();}
 }
 private static void verticalRegression(Minecraft mc){
  if(!placed){
   placed=true;mc.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
   mc.options.renderDistance().set(4);mc.options.hideGui=true;
   mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();
    for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++)l.setBlock(new BlockPos(x,150,z),((Math.floorDiv(x,2)+Math.floorDiv(z,2))&1)==0?Blocks.WHITE_CONCRETE.defaultBlockState():Blocks.BLUE_CONCRETE.defaultBlockState(),3);
    for(var p:mc.getSingleplayerServer().getPlayerList().getPlayers()){
     p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(0,170,0);
    }
   });
   BlackHoleClient.preview();var s=ParamRegistry.state(BlackHoleSkillSchema.ID);
   s.set("form","classic");s.set("playing","false");s.set("time","35");
  }
  String[] names={"grid-90-off","grid-90-on","grid-89_9-on","grid-75-on","grass-90-on","recipe-grid-90-on"};
  int stage=(ticks-120)/40;
  if(ticks<120||stage>=names.length)return;
  if((ticks-120)%40==0){
   var s=ParamRegistry.state(BlackHoleSkillSchema.ID);
   mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(stage==2?89.9f:stage==3?75f:90f);mc.player.xRotO=mc.player.getXRot();
   s.set("scale","1.6");s.set("core",".58");s.set("inner",".94");s.set("outer","2.9");s.set("tilt","0");
   s.set("bend","1.1");s.set("lensRadius","4");s.set("lensStrength",stage==0?"0":"1");
   s.set("density","0");s.set("halo","0");s.set("particles","false");s.set("coreGradientStrength","0");s.set("coreColor","#000000");
   double distance=10;
   if(stage==4)mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++)l.setBlock(new BlockPos(x,150,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);});
   if(stage==5){
    s.resetToDefaults();s.set("tilt","0");distance=26.576;
    mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();
     for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++)l.setBlock(new BlockPos(x,130,z),((Math.floorDiv(x,4)+Math.floorDiv(z,4))&1)==0?Blocks.WHITE_CONCRETE.defaultBlockState():Blocks.BLUE_CONCRETE.defaultBlockState(),3);
     for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++)l.setBlock(new BlockPos(x,150,z),Blocks.AIR.defaultBlockState(),3);
    });
   }
   BlackHoleClient.center=mc.player.getEyePosition().add(mc.player.getLookAngle().scale(distance));
  }
  if((ticks-120)%40==25){BlackHoleWorld.probe="vertical-"+names[stage];System.out.println("[BH-VERTICAL] "+names[stage]);}
  if(stage==names.length-1&&(ticks-120)%40==35){done=true;System.out.println("[BH-VERTICAL] COMPLETE");mc.stop();}
 }
 private static void regression(Minecraft mc){
  if(!placed){
   placed=true;mc.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
   mc.options.renderDistance().set(4);mc.options.hideGui=true;
   mc.getSingleplayerServer().execute(()->{for(var p:mc.getSingleplayerServer().getPlayerList().getPlayers()){
    p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(0,120,0);
   }});
   mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
   BlackHoleClient.preview();var s=ParamRegistry.state(BlackHoleSkillSchema.ID);
   s.set("form","classic");s.set("playing","false");s.set("time","35");s.set("scale","1");
   s.set("outer","3");s.set("inner","1.1");s.set("core",".8");s.set("tilt","-25");
   s.set("particles","false");s.set("halo","0");s.set("density","0");s.set("lensRadius","4");
   s.set("lensStrength","1");s.set("coreGradientStrength","0");s.set("coreColor","#000000");
  }
  String[] names={"core-player-behind","player-front-off","player-front-on","world-yaw-zero","world-yaw-25","world-pitch-20","foreground-wall","near-fullscreen","near-edge","near-offscreen","player-behind-offaxis","lens-outside-disk","default-recipe-front","horizon-first-person","horizon-third-person"};
  int stage=(ticks-120)/35;
  if(ticks<120||stage>=names.length)return;
  if((ticks-120)%35==0){
   var s=ParamRegistry.state(BlackHoleSkillSchema.ID);
   if(stage==0)mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();for(int x=-3;x<=3;x++)for(int y=118;y<=125;y++)l.setBlock(new BlockPos(x,y,5),Blocks.AIR.defaultBlockState(),3);});
   BlackHoleClient.center=new net.minecraft.world.phys.Vec3(0,121.2,stage==0?-1.8:stage<3?2:12);
   s.set("lensStrength",stage==1?"0":"1");
   s.set("density",stage>=3?"2":"0");s.set("halo",stage>=3?"1":"0");
   mc.player.setYRot(stage==4?25:0);mc.player.yRotO=mc.player.getYRot();
   mc.player.setXRot(stage==5?20:0);mc.player.xRotO=mc.player.getXRot();
   if(stage>=7){BlackHoleClient.center=new net.minecraft.world.phys.Vec3(stage==7?0:stage==8?3:8,123,1);s.set("lensRadius","12");s.set("outer","8");s.set("core",".2");s.set("density","0");s.set("halo","0");mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();for(int x=-3;x<=3;x++)for(int y=118;y<=125;y++)l.setBlock(new BlockPos(x,y,5),Blocks.AIR.defaultBlockState(),3);});}
   if(stage==10){BlackHoleClient.center=new net.minecraft.world.phys.Vec3(1.4,121.2,-1.8);s.set("lensRadius","4");}
   if(stage==11){BlackHoleClient.center=new net.minecraft.world.phys.Vec3(0,121.2,10);s.set("outer","2");s.set("lensRadius","8");}
   if(stage==12){s.resetToDefaults();BlackHoleClient.center=new net.minecraft.world.phys.Vec3(0,121.2,2);}
   if(stage>=13){
    mc.options.setCameraType(stage==13?net.minecraft.client.CameraType.FIRST_PERSON:net.minecraft.client.CameraType.THIRD_PERSON_BACK);
    BlackHoleClient.center=new net.minecraft.world.phys.Vec3(0,121.5,10);
    s.set("scale","1.6");s.set("core",".58");s.set("inner",".94");s.set("outer","2.9");
    s.set("bend","1.1");s.set("lensStrength","1");s.set("lensRadius","4");s.set("density","6");
    s.set("halo",".5");s.set("diskStyle","granular");s.set("particles","false");
    s.set("coreGradientStrength","0");s.set("coreColor","#000000");s.set("hot","#fff3c2");s.set("diskMid","#c78d4b");s.set("cool","#f04a0e");
    if(stage==13)mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();
     for(int x=-12;x<=12;x++)for(int y=110;y<=132;y++)l.setBlock(new BlockPos(x,y,26),Blocks.AIR.defaultBlockState(),3);
     for(int x=-48;x<=48;x++)for(int z=-16;z<=64;z++)l.setBlock(new BlockPos(x,119,z),Blocks.GRASS_BLOCK.defaultBlockState(),3);
    });
   }
   if(stage==6)mc.getSingleplayerServer().execute(()->{var l=mc.getSingleplayerServer().overworld();
    for(int x=-3;x<=3;x++)for(int y=118;y<=125;y++)l.setBlock(new BlockPos(x,y,5),Blocks.GREEN_CONCRETE.defaultBlockState(),3);
   });
  }
  if((ticks-120)%35==20){BlackHoleWorld.probe="regression-"+names[stage];System.out.println("[BH-REGRESSION] "+names[stage]);}
  if(stage==names.length-1&&(ticks-120)%35==30){done=true;System.out.println("[BH-REGRESSION] COMPLETE");mc.stop();}
 }
}
