package dev.firecoil;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Captures the final item pose, including inner baked renders of custom item renderers. */
public final class WeaponAttachment {
 private record Context(LivingEntity owner,ItemDisplayContext view){}
 private static final ThreadLocal<Context> CONTEXT=new ThreadLocal<>();
 private static final ThreadLocal<java.util.Deque<Context>> STACK=ThreadLocal.withInitial(java.util.ArrayDeque::new);
 public static int rendered,quads,unsupported,lastGl;public static String lastView="none",lastItem="";
 public static void enterItem(ItemStack stack,ItemDisplayContext view){
  var frames=STACK.get();Context previous=CONTEXT.get();frames.push(previous==null?new Context(null,ItemDisplayContext.NONE):previous);
  var player=Minecraft.getInstance().player;
  if(view==ItemDisplayContext.NONE&&previous!=null)return;
  boolean owned=player!=null&&(stack==player.getMainHandItem()||stack==player.getOffhandItem());
  CONTEXT.set(new Context(owned?player:null,view));
 }
 public static void exitItem(){var frames=STACK.get();if(frames.isEmpty()){CONTEXT.remove();return;}Context prior=frames.pop();if(prior.owner==null&&prior.view==ItemDisplayContext.NONE)CONTEXT.remove();else CONTEXT.set(prior);}
 public static void renderTracked(ItemRenderer renderer,ItemStack stack,ItemDisplayContext view,boolean left,
   PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model,LivingEntity owner){
  Context previous=CONTEXT.get();CONTEXT.set(new Context(owner,view));
  try{renderer.render(stack,view,left,pose,buffers,light,overlay,model);}finally{if(previous==null)CONTEXT.remove();else CONTEXT.set(previous);}
 }
 public static void afterItem(ItemStack stack,ItemDisplayContext view,PoseStack pose,MultiBufferSource buffers,BakedModel model){
  var mc=Minecraft.getInstance();Recipe r=Client.recipe;Context context=CONTEXT.get();
  if(r==null||r.i("attachment")==0||Client.failed||mc.player==null||!Client.active||context==null||context.owner!=mc.player)return;
  if(view==ItemDisplayContext.NONE)view=context.view;
  if(view!=ItemDisplayContext.FIRST_PERSON_LEFT_HAND&&view!=ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
    &&view!=ItemDisplayContext.THIRD_PERSON_LEFT_HAND&&view!=ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)return;
  // Outer BEWLR contributes no geometry; the actual inner baked renderer is captured once.
  if(model.isCustomRenderer())return;
  if(!(stack.getItem() instanceof net.minecraft.world.item.SwordItem)&&!stack.is(net.minecraft.tags.ItemTags.SWORDS))return;
  try{
   float[] lo={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},hi={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};int count=0;var points=new java.util.ArrayList<org.joml.Vector3f>();var surfaceQuads=new java.util.ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
   for(BakedModel pass:model.getRenderPasses(stack,true))for(int face=-1;face<6;face++)
    for(var q:pass.getQuads(null,face<0?null:Direction.values()[face],RandomSource.create(42))){int[] raw=q.getVertices();int stride=raw.length/4;if(stride<3)continue;count++;surfaceQuads.add(q);
     for(int i=0;i<4;i++){var vertex=new org.joml.Vector3f();for(int k=0;k<3;k++){float v=Float.intBitsToFloat(raw[i*stride+k]);if(!Float.isFinite(v))throw new IllegalArgumentException("Non-finite model vertex");lo[k]=Math.min(lo[k],v);hi[k]=Math.max(hi[k],v);vertex.setComponent(k,v);}points.add(vertex);}}
   if(count==0){unsupported++;return;}quads=count;
   // Generated sword front quads include transparent texture margins. Use their
   // actual opaque texels for the attachment extent, rather than the whole card.
   if(count<=128&&hi[2]-lo[2]<.09f){var opaque=new java.util.ArrayList<org.joml.Vector3f>();
    for(var q:surfaceQuads){if(q.getDirection()!=Direction.NORTH&&q.getDirection()!=Direction.SOUTH)continue;var sprite=q.getSprite();var image=sprite.contents().getOriginalImage();int iw=sprite.contents().width(),ih=sprite.contents().height();if(iw>256||ih>256)continue;
     int[] raw=q.getVertices();int stride=raw.length/4;float u0=Float.intBitsToFloat(raw[4]),v0=Float.intBitsToFloat(raw[5]),du=Float.intBitsToFloat(raw[stride+4])-u0,dv=Float.intBitsToFloat(raw[stride+5])-v0,eu=Float.intBitsToFloat(raw[3*stride+4])-u0,ev=Float.intBitsToFloat(raw[3*stride+5])-v0,det=du*ev-dv*eu;if(Math.abs(det)<1e-10f)continue;
     var a=new org.joml.Vector3f(Float.intBitsToFloat(raw[0]),Float.intBitsToFloat(raw[1]),Float.intBitsToFloat(raw[2]));org.joml.Vector3f d=new org.joml.Vector3f(Float.intBitsToFloat(raw[stride]),Float.intBitsToFloat(raw[stride+1]),Float.intBitsToFloat(raw[stride+2])).sub(a),e=new org.joml.Vector3f(Float.intBitsToFloat(raw[3*stride]),Float.intBitsToFloat(raw[3*stride+1]),Float.intBitsToFloat(raw[3*stride+2])).sub(a);
     for(int y=0;y<ih;y++)for(int x=0;x<iw;x++){if(((image.getPixelRGBA(x,y)>>>24)&255)<128)continue;float u=sprite.getU0()+(sprite.getU1()-sprite.getU0())*(x+.5f)/iw-u0,v=sprite.getV0()+(sprite.getV1()-sprite.getV0())*(y+.5f)/ih-v0,s=(u*ev-v*eu)/det,t=(du*v-dv*u)/det;if(s<0||s>1||t<0||t>1)continue;opaque.add(new org.joml.Vector3f(a).add(new org.joml.Vector3f(d).mul(s)).add(new org.joml.Vector3f(e).mul(t)));}
    }if(opaque.size()>8)points=opaque;
   }
   int major=0;for(int k=1;k<3;k++)if(hi[k]-lo[k]>hi[major]-lo[major])major=k;
   // Principal axis follows diagonal generated blades as well as long OBJ geometry.
   var center=new org.joml.Vector3f();for(var vertex:points)center.add(vertex);center.div(points.size());double[] covariance=new double[9];
   for(var vertex:points){org.joml.Vector3f d=new org.joml.Vector3f(vertex).sub(center);float[] a={d.x,d.y,d.z};for(int x=0;x<3;x++)for(int y=0;y<3;y++)covariance[x*3+y]+=a[x]*a[y];}
   var direction=new org.joml.Vector3f();direction.setComponent(major,1);
   for(int iteration=0;iteration<16;iteration++){var next=new org.joml.Vector3f((float)(covariance[0]*direction.x+covariance[1]*direction.y+covariance[2]*direction.z),(float)(covariance[3]*direction.x+covariance[4]*direction.y+covariance[5]*direction.z),(float)(covariance[6]*direction.x+covariance[7]*direction.y+covariance[8]*direction.z));if(next.lengthSquared()<1e-12f)break;direction=next.normalize();}
   if(direction.get(major)<0)direction.negate();float first=Float.POSITIVE_INFINITY,last=Float.NEGATIVE_INFINITY;for(var vertex:points){float s=new org.joml.Vector3f(vertex).sub(center).dot(direction);first=Math.min(first,s);last=Math.max(last,s);}
   var origin=new org.joml.Vector3f(direction).mul(first).add(center);var sideVector=new org.joml.Vector3f(Math.abs(direction.z)<.8f?0:1,0,Math.abs(direction.z)<.8f?1:0).cross(direction).normalize();
   float[] base={origin.x,origin.y,origin.z},axis={direction.x,direction.y,direction.z},side={sideVector.x,sideVector.y,sideVector.z};float radius=0;
   for(var vertex:points){var delta=new org.joml.Vector3f(vertex).sub(center);float axial=delta.dot(direction);delta.sub(new org.joml.Vector3f(direction).mul(axial));if(axial>first+(last-first)*.25f)radius=Math.max(radius,delta.length());}
   var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
   var profile=mc.getResourceManager().getResource(new net.minecraft.resources.ResourceLocation(id.getNamespace(),"weapon_flames/"+id.getPath()+".json"));
   if(profile.isPresent())try(var reader=profile.get().openAsReader()){
    var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();com.google.gson.JsonArray h=json.getAsJsonArray("hiltAnchor"),tip=json.getAsJsonArray("tipAnchor"),w=json.getAsJsonArray("widthAxis");
    origin=new org.joml.Vector3f(h.get(0).getAsFloat(),h.get(1).getAsFloat(),h.get(2).getAsFloat());var end=new org.joml.Vector3f(tip.get(0).getAsFloat(),tip.get(1).getAsFloat(),tip.get(2).getAsFloat());direction=end.sub(origin);last=direction.length();first=0;direction.normalize();sideVector=new org.joml.Vector3f(w.get(0).getAsFloat(),w.get(1).getAsFloat(),w.get(2).getAsFloat());sideVector.sub(new org.joml.Vector3f(direction).mul(sideVector.dot(direction))).normalize();radius=Math.max(json.get("bladeWidth").getAsFloat(),json.get("bladeThickness").getAsFloat())*.5f;
    base=new float[]{origin.x,origin.y,origin.z};axis=new float[]{direction.x,direction.y,direction.z};side=new float[]{sideVector.x,sideVector.y,sideVector.z};
   }
   // flush the real blade before sampling its opaque depth, without changing the original draw.
   if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();
   VolumeGpu.renderWeapon(r,new Matrix4f(pose.last().pose()),base,axis,side,last-first,radius);
   rendered++;lastView=view.name();lastItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();lastGl=VolumeGpu.lastGl;
  }catch(Exception ex){Client.lastError="Weapon attachment: "+ex.getMessage();unsupported++;}
 }
 private WeaponAttachment(){}
}
