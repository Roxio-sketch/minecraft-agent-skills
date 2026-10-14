package dev.energyvfx;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;

/** Conservative per-packet screen bounds; detached packets behind the camera do not expand the pass. */
final class PacketBounds {
 private static final float[] bounds=new float[32*4];
 private static final Vector4f[] corners=new Vector4f[8];
 static double screenOverlap;static int fullScreenPackets;
 static {for(int i=0;i<8;i++)corners[i]=new Vector4f();}
 static boolean enabled(){return !Boolean.getBoolean("energyvfx.disablePacketBounds")&&!(PerformanceQa.MOTION&&!PerformanceQa.optimized);}
 static OptimizedGpu.Rect prepare(int program,Matrix4f vp,Vector3f camera,int w,int h,float stretch){
  boolean enabled=enabled();GL20.glUniform1i(Uniforms.location(program,"puffBoundsEnabled"),enabled?1:0);
  if(!enabled)return OptimizedGpu.project(vp,camera,w,h);
  int x0=w,y0=h,x1=0,y1=0;screenOverlap=0;fullScreenPackets=0;
  for(int i=0;i<AuraEmitter.uploadCount;i++){
   int k=i*4;float fx=AuraEmitter.uploadFlows[k],fy=AuraEmitter.uploadFlows[k+1],fz=AuraEmitter.uploadFlows[k+2];
   double norm=Math.sqrt((double)fx*fx+(double)fy*fy+(double)fz*fz),radius=AuraEmitter.uploadData[k+3];
   double ax=norm>.001?fx/norm:0,ay=norm>.001?fy/norm:1,az=norm>.001?fz/norm:0;
   double pad=stretch*stretch-1;
   float ex=(float)(radius*2.506*Math.sqrt(1+pad*ax*ax))+.0001f,ey=(float)(radius*2.506*Math.sqrt(1+pad*ay*ay))+.0001f,ez=(float)(radius*2.506*Math.sqrt(1+pad*az*az))+.0001f;
   OptimizedGpu.Rect r=project(vp,camera,w,h,AuraEmitter.uploadData[k],AuraEmitter.uploadData[k+1],AuraEmitter.uploadData[k+2],ex,ey,ez);
   bounds[k]=(float)r.x0()/w;bounds[k+1]=(float)r.y0()/h;bounds[k+2]=(float)r.x1()/w;bounds[k+3]=(float)r.y1()/h;
   if(!r.empty()){double coverage=r.coverage(w,h);screenOverlap+=coverage;if(coverage>=.99999)fullScreenPackets++;x0=Math.min(x0,r.x0());y0=Math.min(y0,r.y0());x1=Math.max(x1,r.x1());y1=Math.max(y1,r.y1());}
  }
  GL20.glUniform4fv(Uniforms.location(program,"puffScreenBounds[0]"),bounds);
  return new OptimizedGpu.Rect(x0,y0,x1,y1);
 }
 private static OptimizedGpu.Rect project(Matrix4f vp,Vector3f camera,int w,int h,float x,float y,float z,float ex,float ey,float ez){
  double loX=Double.POSITIVE_INFINITY,loY=loX,hiX=Double.NEGATIVE_INFINITY,hiY=hiX;boolean front=false;
  for(int i=0;i<8;i++){
   Vector4f p=corners[i].set(x+((i&1)==0?-ex:ex)-camera.x,y+((i&2)==0?-ey:ey)-camera.y,z+((i&4)==0?-ez:ez)-camera.z,1);vp.transform(p);
   if(!Float.isFinite(p.w)||!Float.isFinite(p.x)||!Float.isFinite(p.y))return OptimizedGpu.Rect.full(w,h);
   if(Math.abs(p.w)<.0001f)return OptimizedGpu.Rect.full(w,h);
   if(p.w>0){front=true;double px=p.x/p.w,py=p.y/p.w;loX=Math.min(loX,px);hiX=Math.max(hiX,px);loY=Math.min(loY,py);hiY=Math.max(hiY,py);}
  }
  if(!front)return new OptimizedGpu.Rect(0,0,0,0);
  // Perspective limits along each edge crossing w=0 cover density before the world near plane too.
  for(int i=0;i<8;i++)for(int bit=1;bit<=4;bit*=2){int j=i^bit;if(j<i)continue;Vector4f a=corners[i],b=corners[j];if((a.w>0)==(b.w>0))continue;
   double t=(double)a.w/(a.w-b.w),px=a.x+t*(b.x-a.x),py=a.y+t*(b.y-a.y);
   if(Math.abs(px)<.0001||Math.abs(py)<.0001)return OptimizedGpu.Rect.full(w,h);
   if(px>0)hiX=Double.POSITIVE_INFINITY;else loX=Double.NEGATIVE_INFINITY;
   if(py>0)hiY=Double.POSITIVE_INFINITY;else loY=Double.NEGATIVE_INFINITY;
  }
  // Clamp before integer conversion so infinite perspective limits cannot overflow padding.
  int x0=(int)Math.floor((Math.max(-2,Math.min(2,loX))+1)*w*.5)-5,y0=(int)Math.floor((Math.max(-2,Math.min(2,loY))+1)*h*.5)-5;
  int x1=(int)Math.ceil((Math.max(-2,Math.min(2,hiX))+1)*w*.5)+5,y1=(int)Math.ceil((Math.max(-2,Math.min(2,hiY))+1)*h*.5)+5;
  if(x1<=0||y1<=0||x0>=w||y0>=h)return new OptimizedGpu.Rect(0,0,0,0);
  return new OptimizedGpu.Rect(Math.max(0,x0),Math.max(0,y0),Math.min(w,x1),Math.min(h,y1));
 }
}
