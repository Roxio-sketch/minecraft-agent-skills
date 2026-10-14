package dev.energyvfx;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.*;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.*;
import java.nio.file.*;

/** Same-frame comparison against the untouched density/kernel reference. */
final class QualityQa {
 static boolean enabled,done;private static int scenario,wait,sentinel;
 static final String[] NAMES={"blue-front","white-front","blue-back","near-plane-first-person","low-resolution-bloom","later-clock","strong-flow","forced-cache-off","trail-behind-camera","camera-near-trail","quarter-resolution-bloom","camera-inside-accumulated-trail","empty-emitter"};
 private static net.minecraft.world.phys.Vec3 selectedParcelPosition;private static float selectedParcelRadius;
 private static final JsonArray results=new JsonArray();private static TextureTarget source;
 static void tick()throws Exception{if(!enabled||done)return;var mc=Minecraft.getInstance();
  mc.options.setCameraType(scenario==2||scenario==8||scenario==9||scenario==11?CameraType.THIRD_PERSON_BACK:scenario==3?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_FRONT);
  if(wait++==0){var raw=Recipe.parse(Recipe.resource("presets/"+(scenario==1?15:16)+".json")).getAsJsonObject();if(scenario==4){raw.getAsJsonObject("params").addProperty("resolution",.5);raw.getAsJsonObject("params").addProperty("bloom",.35);}if(scenario==10){raw.getAsJsonObject("params").addProperty("resolution",.25);raw.getAsJsonObject("params").addProperty("bloom",.35);raw.getAsJsonObject("params").addProperty("bloomRadius",.1);}if(scenario==5)EnergyVfx.Client.ticks+=7;if(scenario==6){raw.getAsJsonObject("params").addProperty("speed",3);raw.getAsJsonObject("params").addProperty("swirl",4);}if(scenario==7)System.setProperty("energyvfx.disableFlowCache","true");else System.clearProperty("energyvfx.disableFlowCache");EnergyVfx.Client.recipe=Recipe.decode(raw);if(scenario==8)mc.player.setPos(.5,7,9.5);if(scenario==9)mc.player.setPos(.5,7,3);if(scenario==11)placeCameraInsideDetachedParcel(mc);if(scenario==12)AuraEmitter.reset();}
 }
 private static void placeCameraInsideDetachedParcel(Minecraft mc){
  // Previous frame's cached upload is relative to its recorded anchor; the frozen packets
  // themselves remain in world space when the QA player moves. Never reset or rebind here.
  if(AuraEmitter.uploadCount<1||EnergyVfx.Client.anchor==null)throw new IllegalStateException("Camera-inside QA requires an existing detached parcel");
  int largest=0;for(int i=1;i<AuraEmitter.uploadCount;i++)if(AuraEmitter.uploadData[i*4+3]>AuraEmitter.uploadData[largest*4+3])largest=i;
  int k=largest*4;selectedParcelRadius=AuraEmitter.uploadData[k+3];selectedParcelPosition=EnergyVfx.Client.anchor.add(AuraEmitter.uploadData[k],AuraEmitter.uploadData[k+1],AuraEmitter.uploadData[k+2]);
  // At yaw/pitch zero THIRD_PERSON_BACK puts the camera four metres behind the eye.
  // The normal four-tick QA settling period also settles Minecraft's camera interpolation.
  mc.player.setPos(selectedParcelPosition.x,selectedParcelPosition.y-mc.player.getEyeHeight(),selectedParcelPosition.z+4);
 }
 private static JsonObject cameraInsideParcel(RenderLevelStageEvent event){
  var camera=event.getCamera().getPosition();var anchor=EnergyVfx.Client.anchor;double cx=camera.x-anchor.x,cy=camera.y-anchor.y,cz=camera.z-anchor.z,stretch=EnergyVfx.Client.recipe.values[1],minimum=Double.POSITIVE_INFINITY;int inside=0;
  for(int i=0;i<AuraEmitter.uploadCount;i++){int k=i*4;double r=AuraEmitter.uploadData[k+3];if(!(r>0))continue;double x=cx-AuraEmitter.uploadData[k],y=cy-AuraEmitter.uploadData[k+1],z=cz-AuraEmitter.uploadData[k+2],fx=AuraEmitter.uploadFlows[k],fy=AuraEmitter.uploadFlows[k+1],fz=AuraEmitter.uploadFlows[k+2],norm=Math.sqrt(fx*fx+fy*fy+fz*fz),along=norm>.001?(x*fx+y*fy+z*fz)/norm:y;double distance=Math.sqrt(Math.max(0,x*x+y*y+z*z-along*along+along*along/(stretch*stretch)))/r;minimum=Math.min(minimum,distance);if(distance<=2.505)inside++;}
  if(inside<1)throw new IllegalStateException("Camera-inside QA did not enter an uploaded support ellipsoid; minimum normalized distance="+minimum);
  JsonObject evidence=new JsonObject();evidence.addProperty("cameraInsideParcel",inside);evidence.addProperty("minCameraParcelDistance",minimum);evidence.addProperty("selectedParcelRadius",selectedParcelRadius);evidence.addProperty("cameraDistanceToSelectedParcel",camera.distanceTo(selectedParcelPosition));JsonArray target=new JsonArray();target.add(selectedParcelPosition.x);target.add(selectedParcelPosition.y);target.add(selectedParcelPosition.z);evidence.add("selectedParcelWorldPosition",target);JsonArray position=new JsonArray();position.add(camera.x);position.add(camera.y);position.add(camera.z);evidence.add("cameraPosition",position);return evidence;
 }
 static void render(RenderLevelStageEvent event)throws Exception{
  var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
  if(wait<4){OptimizedGpu.render(event);return;}
  if(source==null)try(Gpu.State ignored=new Gpu.State()){source=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);sentinel=GL11.glGenTextures();com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13.GL_TEXTURE4);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,sentinel);GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,GL30.GL_RGBA32F,1,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,(java.nio.ByteBuffer)null);}
  int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);boolean oldScissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);int[] oldBox=new int[4];GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,oldBox);
  GL11.glDisable(GL11.GL_SCISSOR_TEST);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,source.frameBufferId);GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
  Gpu.render(event);PerformanceQa.screenshot(PerformanceQa.OUTPUT.resolve("quality-"+NAMES[scenario]+"-before.png"));
  GL11.glDisable(GL11.GL_SCISSOR_TEST);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source.frameBufferId);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,main.frameBufferId);GL30.glBlitFramebuffer(0,0,main.width,main.height,0,0,main.width,main.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
  // A non-default host scissor must be restored exactly, without clipping the copy/pass.
  int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13.GL_TEXTURE4);int oldArray=GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,sentinel);
  boolean compute=FlowCache.supported();int[] oldImage=new int[6];int[] imageKeys={GL42.GL_IMAGE_BINDING_NAME,GL42.GL_IMAGE_BINDING_LEVEL,GL42.GL_IMAGE_BINDING_LAYERED,GL42.GL_IMAGE_BINDING_LAYER,GL42.GL_IMAGE_BINDING_ACCESS,GL42.GL_IMAGE_BINDING_FORMAT};if(compute){for(int i=0;i<6;i++)oldImage[i]=GL30.glGetIntegeri(imageKeys[i],0);GL42.glBindImageTexture(0,sentinel,0,false,0,GL15.GL_READ_ONLY,GL30.GL_RGBA32F);}
  com.mojang.blaze3d.platform.GlStateManager._activeTexture(oldActive);
  GL11.glScissor(7,11,17,19);GL11.glEnable(GL11.GL_SCISSOR_TEST);OptimizedGpu.render(event);int[] actual=new int[4];GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,actual);boolean stateRestored=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)&&java.util.Arrays.equals(actual,new int[]{7,11,17,19});
  com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13.GL_TEXTURE4);boolean arrayRestored=GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY)==sentinel;GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,oldArray);com.mojang.blaze3d.platform.GlStateManager._activeTexture(oldActive);
  boolean imageRestored=!compute||(GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_NAME,0)==sentinel&&GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_ACCESS,0)==GL15.GL_READ_ONLY&&GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYERED,0)==0);if(compute)GL42.glBindImageTexture(0,oldImage[0],oldImage[1],oldImage[2]!=0,oldImage[3],oldImage[4],oldImage[5]);
  GL11.glScissor(oldBox[0],oldBox[1],oldBox[2],oldBox[3]);if(oldScissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);else GL11.glDisable(GL11.GL_SCISSOR_TEST);
  PerformanceQa.screenshot(PerformanceQa.OUTPUT.resolve("quality-"+NAMES[scenario]+"-after.png"));
  int error=GL11.glGetError();if(error!=0||!stateRestored||!arrayRestored||!imageRestored)throw new IllegalStateException("Quality/state error "+NAMES[scenario]+" gl="+error+" scissor="+stateRestored+" array="+arrayRestored+" image="+imageRestored);
  JsonObject row=new JsonObject();row.addProperty("case",NAMES[scenario]);row.addProperty("scissorRestored",stateRestored);row.addProperty("arrayTextureRestored",arrayRestored);row.addProperty("imageBindingRestored",imageRestored);row.addProperty("flowCacheEnabled",FlowCache.active);row.addProperty("glError",error);row.addProperty("volumeCoverage",OptimizedGpu.lastCoverage);row.addProperty("sceneCopyCoverage",OptimizedGpu.lastCopyCoverage);row.addProperty("packets",AuraEmitter.packetCount);row.addProperty("sameFrame",true);row.addProperty("sameDensityAndSamplePositions",true);if(scenario==11){var evidence=cameraInsideParcel(event);for(var entry:evidence.entrySet())row.add(entry.getKey(),entry.getValue());}results.add(row);Files.writeString(PerformanceQa.OUTPUT.resolve("quality.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));
  wait=0;if(++scenario>=NAMES.length){done=true;PerformanceQa.done=true;if(source!=null)source.destroyBuffers();GL11.glDeleteTextures(sentinel);mc.stop();}
 }
}
