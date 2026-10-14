"""Compile and exercise the delivered pure Java particle simulation with JDK 17."""
import os,subprocess,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
jdk=Path(os.environ['JAVA_HOME'])/'bin'
source=root/'assets/forge-1.20.1/src/main/java/dev/rendercraft/particlelab/ParticleEngine.java'
harness=r'''
import dev.rendercraft.particlelab.ParticleEngine;
public class Check {
 static void require(boolean c,String m){if(!c)throw new AssertionError(m);}
 static ParticleEngine.Config config(String mode,int cap,double rate){return new ParticleEngine.Config(mode,cap,rate,2,1,.2,-.1,2,42);}
 public static void main(String[] args){
  var c=config("fountain",200,7);var a=new ParticleEngine(c);var b=new ParticleEngine(c);
  for(int i=0;i<20;i++){a.tick();b.tick();}require(a.points().size()==7,"fractional emission rate");
  for(int i=0;i<7;i++){var x=a.points().get(i);var y=b.points().get(i);require(x.x==y.x&&x.y==y.y&&x.z==y.z,"seed determinism");}
  double first=a.points().get(0).y;a.reset(c);for(int i=0;i<20;i++)a.tick();require(first==a.points().get(0).y,"reset reproducibility");
  a.reset(config("burst",12,100));require(a.points().size()==12&&a.rejected==88,"capacity saturation");for(int i=0;i<60;i++)a.tick();require(a.points().isEmpty(),"burst expires without new births");
  a.reset(config("orbit",100,20));for(int i=0;i<1000;i++){a.tick();require(a.points().size()<=100,"capacity invariant");for(var p:a.points()){require(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z),"finite state");require(Math.abs(Math.hypot(p.x,p.z)-1)<1e-9,"orbital radius");}}
  a.clear();require(a.points().isEmpty(),"lifecycle clear");System.out.println("PASS: emission, seed, reset, capacity, lifetime, finite orbit, clear");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='particle-core-') as temp:
 p=Path(temp);(p/'Check.java').write_text(harness,encoding='utf8')
 subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-d',str(p),str(source),str(p/'Check.java')],check=True)
 subprocess.run([str(jdk/'java.exe'),'-cp',str(p),'Check'],check=True)
