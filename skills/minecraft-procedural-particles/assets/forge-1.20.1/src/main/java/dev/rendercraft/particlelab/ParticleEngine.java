package dev.rendercraft.particlelab;
import java.util.*;
/** Independent fixed-step simulation. Positions in emitter-relative world axes. */
public final class ParticleEngine {
 public static final class Point {
  public double x,y,z,px,py,pz,vx,vy,vz,age,life;public final int id;
  Point(int id){this.id=id;}
 }
 public record Config(String motion,int capacity,double rate,double life,double speed,double drag,double gravity,double orbit,long seed) {
  public Config {if(!Set.of("orbit","burst","fountain","attract").contains(motion)||capacity<1||capacity>16384||!Double.isFinite(rate)||rate<0||rate>4096||!Double.isFinite(life)||life<=0||life>60||!Double.isFinite(speed)||!Double.isFinite(drag)||!Double.isFinite(gravity)||!Double.isFinite(orbit))throw new IllegalArgumentException("Invalid particle configuration");}
 }
 private final List<Point> points=new ArrayList<>();private Random random;private Config config;private double births;private int serial;public long rejected;
 public ParticleEngine(Config c){reset(c);}
 public void reset(Config c){config=c;random=new Random(c.seed);points.clear();births=0;serial=0;rejected=0;if(c.motion.equals("burst"))for(int i=0;i<(int)Math.ceil(c.rate);i++)spawn();}
 public List<Point> points(){return Collections.unmodifiableList(points);}
 public void clear(){points.clear();births=0;}
 private void spawn(){if(points.size()>=config.capacity){rejected++;return;}Point p=new Point(serial++);double theta=random.nextDouble()*Math.PI*2,y=random.nextDouble()*2-1,r=Math.sqrt(1-y*y);p.life=config.life*(.75+random.nextDouble()*.5);
  if(config.motion.equals("attract")){p.x=r*Math.cos(theta)*2;p.y=y*2;p.z=r*Math.sin(theta)*2;}else if(config.motion.equals("orbit")){p.x=Math.cos(theta);p.z=Math.sin(theta);p.y=y*.3;}else {p.vx=r*Math.cos(theta)*config.speed;p.vz=r*Math.sin(theta)*config.speed;p.vy=config.motion.equals("fountain")?config.speed*(1+random.nextDouble()):y*config.speed;}
  p.px=p.x;p.py=p.y;p.pz=p.z;points.add(p);
 }
 public void tick(){double dt=.05;for(Point p:points){p.px=p.x;p.py=p.y;p.pz=p.z;p.age+=dt;
  if(config.motion.equals("orbit")){double a=config.orbit*dt,c=Math.cos(a),s=Math.sin(a),x=p.x;p.x=c*x-s*p.z;p.z=s*x+c*p.z;p.y+=Math.sin(p.age*2+p.id)*dt*.12;}
  else {if(config.motion.equals("attract")){double norm=Math.sqrt(p.x*p.x+p.y*p.y+p.z*p.z);if(norm<.08){p.age=p.life;continue;}p.vx-=p.x/norm*config.speed*dt;p.vy-=p.y/norm*config.speed*dt;p.vz-=p.z/norm*config.speed*dt;}
   p.vy+=config.gravity*dt;double damping=Math.exp(-Math.max(0,config.drag)*dt);p.vx*=damping;p.vy*=damping;p.vz*=damping;p.x+=p.vx*dt;p.y+=p.vy*dt;p.z+=p.vz*dt;
  }
 }points.removeIf(p->p.age>=p.life);if(!config.motion.equals("burst")){births+=config.rate*dt;int n=(int)Math.floor(births+1e-9);births-=n;for(int i=0;i<n;i++)spawn();}}
}
