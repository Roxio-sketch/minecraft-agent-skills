package dev.firecoil;

import java.util.*;

/** Fixed-step world simulation. Render never advances or emits particles. */
public final class FireCore {
 public static final int CAPACITY=512;
 public record V(double x,double y,double z) {
  public V add(V b){return new V(x+b.x,y+b.y,z+b.z);}public V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
  public V mul(double n){return new V(x*n,y*n,z*n);}public double length(){return Math.sqrt(x*x+y*y+z*z);}
  public V normalized(){double l=length();return l<1e-10?new V(0,1,0):mul(1/l);}
  public V cross(V b){return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
  public V lerp(V b,double p){return mul(1-p).add(b.mul(p));}
 }
 public static final class Particle {
  public boolean alive;public int kind;public double age,life,size,seed;public V position,previous,velocity;
 }
 public final Particle[] pool=new Particle[CAPACITY];
 private long randomState=17;private V lastAnchor;private Object world;
 private double emberRemainder,wispRemainder,time,ramp;
 public long spawnRejected;public int teleports;
 public FireCore(){for(int i=0;i<CAPACITY;i++)pool[i]=new Particle();}
 public void resetSeed(long seed){randomState=seed==0?1:seed;}
 private double random(){randomState=(1664525*randomState+1013904223L)&0xffffffffL;return randomState/4294967296.0;}
 public int count(){int n=0;for(Particle p:pool)if(p.alive)n++;return n;}
 public double time(){return time;}public double ramp(){return ramp;}
 public void clear(){for(Particle p:pool)p.alive=false;emberRemainder=wispRemainder=0;ramp=0;lastAnchor=null;}
 public void world(Object identity){if(world!=identity){clear();world=identity;}}
 public void tick(Recipe r,V anchor,double yaw,boolean active,double firstPersonScale) {
  final double dt=.05;
  if(lastAnchor!=null&&anchor.sub(lastAnchor).length()>8){clear();teleports++;}
  V motion=lastAnchor==null?new V(0,0,0):anchor.sub(lastAnchor).mul(20*r.d("motionInheritance"));lastAnchor=anchor;
  time+=dt;ramp=active?Math.min(1,ramp+dt/r.d("rampTime")):Math.max(0,ramp-dt/r.d("rampTime"));
  for(Particle p:pool)if(p.alive) {
   p.previous=p.position;p.age+=dt;
   if(p.age>=p.life){p.alive=false;continue;}
   double noise=Math.sin(p.seed*13.7+time*3.1+p.age*6),curl=Math.cos(p.seed*17.9+time*2.7+p.age*5);
   p.velocity=p.velocity.add(new V(noise*r.d("turbulence")*.18*dt,r.d("lift")*.22*dt,curl*r.d("turbulence")*.18*dt));
   p.position=p.position.add(p.velocity.mul(dt));
  }
  if(!active||r.i("attachment")==1)return;
  emberRemainder+=r.d("emberRate")*dt*firstPersonScale*ramp;wispRemainder+=r.d("wispRate")*dt*firstPersonScale*ramp;
  while(emberRemainder>=1){spawn(r,anchor,yaw,2,motion);emberRemainder--;}
  while(wispRemainder>=1){spawn(r,anchor,yaw,1,motion);wispRemainder--;}
 }
 private void spawn(Recipe r,V anchor,double yaw,int kind,V inherited) {
  Particle p=null;for(Particle slot:pool)if(!slot.alive){p=slot;break;}if(p==null){spawnRejected++;return;}
  double s=random();int strand=(int)(random()*r.i("strands"));p.seed=random();
  p.kind=kind;p.age=0;p.life=r.d(kind==2?"emberLife":"wispLife");p.size=r.d(kind==2?"emberSize":"wispSize");
  p.position=coil(r,anchor,yaw,time,strand,s);p.previous=p.position;
  V radial=new V(p.position.x-anchor.x,0,p.position.z-anchor.z).normalized(),tangent=new V(-radial.z,0,radial.x);
  double outward=r.d("outwardSpeed")*(.65+random()*.7),vertical=r.d("lift")*(.55+random()*.7)+(kind==2?.18:0);
  p.velocity=radial.mul(outward).add(tangent.mul(r.d("rotationSpeed")*.35)).add(new V(0,vertical,0)).add(inherited);p.alive=true;
 }
 /** Body path; radius, height, turns, wave, speed and body yaw all have real consumers. */
 public static V coil(Recipe r,V anchor,double yaw,double t,int strand,double s) {
  double seed=(r.i("seed")%997)*.071;
  double wave=s*9.3+t*r.d("waveSpeed")*1.4+seed*3.1+strand*2.9;
  double a=2*Math.PI*(s*r.d("turns")+strand/(double)r.i("strands")+t*r.d("rotationSpeed"))+.13*r.d("turbulence")*Math.sin(wave*.71+strand)+yaw;
  double radius=r.d("radius")+r.d("waveAmplitude")*(.62*Math.sin(wave)+.38*Math.sin(wave*1.73-1.1));
  return anchor.add(new V(Math.cos(a)*radius,.06+s*r.d("height")+r.d("waveAmplitude")*.45*Math.sin(wave*1.31),Math.sin(a)*radius));
 }
}
