package dev.trailbeam;

import com.google.gson.*;
import java.util.*;

/** Deterministic beam design paths. Coordinates are world-space; callers choose the basis. */
public final class Paths {
    private Paths() {}
    public record Spec(String kind,double length,double amplitude,double cycles,double speed,int seed,int samples,int branches,double branchLength) {
        public Spec {
            if(!Set.of("straight","arc","sine","helix","zigzag","lightning").contains(kind)
                ||!Double.isFinite(length)||length<0.1||length>32
                ||!Double.isFinite(amplitude)||amplitude<0||amplitude>8
                ||!Double.isFinite(cycles)||cycles<0||cycles>32
                ||!Double.isFinite(speed)||Math.abs(speed)>20
                ||samples<8||samples>256||branches<0||branches>4
                ||!Double.isFinite(branchLength)||branchLength<0.05||branchLength>1)
                throw new IllegalArgumentException("Invalid path design or budget");
        }
    }
    public record Strand(List<Geometry.V> points,double widthScale) {}
    private static void keys(JsonObject o,String... keys) {
        Set<String> allowed=Set.of(keys);
        for(String key:o.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown path field: "+key);
        for(String key:keys)if(!o.has(key))throw new IllegalArgumentException("Missing path field: "+key);
    }
    private static double number(JsonObject o,String key,double min,double max) {
        double n=o.get(key).getAsDouble();if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Invalid path "+key);return n;
    }
    private static int integer(JsonObject o,String key,int min,int max) {
        double n=number(o,key,min,max);if(n!=Math.rint(n))throw new IllegalArgumentException("Path "+key+" must be integer");return (int)n;
    }
    public static Spec parse(String json) {
        JsonObject o=JsonParser.parseString(json).getAsJsonObject();
        keys(o,"schema","kind","length","amplitude","cycles","speed","seed","samples","branches","branchLength");
        integer(o,"schema",1,1);
        return new Spec(o.get("kind").getAsString(),number(o,"length",0.1,32),number(o,"amplitude",0,8),
            number(o,"cycles",0,32),number(o,"speed",-20,20),integer(o,"seed",-1000000000,1000000000),
            integer(o,"samples",8,256),integer(o,"branches",0,4),number(o,"branchLength",0.05,1));
    }
    private static double hash(int i,int seed) {
        long x=(long)i*0x9e3779b9L+(long)seed*0x85ebca6bL;
        x^=x>>>16;x*=0x7feb352dL;x^=x>>>15;x*=0x846ca68bL;x^=x>>>16;
        return ((x&0xffffL)/32767.5)-1.0;
    }
    private static Geometry.V offset(Spec p,double s,double time) {
        double phase=time*p.speed*2*Math.PI;
        double a=p.amplitude,w=s*p.cycles*2*Math.PI+phase;
        return switch(p.kind) {
            case "arc" -> new Geometry.V(0,a*4*s*(1-s),0);
            case "sine" -> new Geometry.V(0,a*Math.sin(w)*Math.sin(Math.PI*s),0);
            case "helix" -> new Geometry.V(0,a*Math.cos(w),a*Math.sin(w));
            case "zigzag" -> new Geometry.V(0,a*(2/Math.PI)*Math.asin(Math.sin(w))*Math.sin(Math.PI*s),0);
            case "lightning" -> {
                int slice=(int)Math.floor(s*p.cycles*8),frame=(int)Math.floor(time*Math.abs(p.speed)*5);
                double fade=Math.sin(Math.PI*s);
                yield new Geometry.V(0,a*fade*hash(slice+frame*199,p.seed),a*.36*fade*hash(slice+811+frame*199,p.seed));
            }
            default -> new Geometry.V(0,0,0);
        };
    }
    private static Geometry.V point(Spec p,Geometry.V origin,Geometry.V forward,Geometry.V side,Geometry.V up,double s,double time) {
        Geometry.V d=offset(p,s,time);
        return origin.add(forward.mul(p.length*s)).add(side.mul(d.y())).add(up.mul(d.z()));
    }
    public static List<Strand> generate(Spec p,Geometry.V origin,Geometry.V forward,Geometry.V side,Geometry.V up,double time) {
        if(!Double.isFinite(time))throw new IllegalArgumentException("Path time");
        forward=forward.unit();side=side.sub(forward.mul(side.dot(forward))).unit();up=forward.cross(side).unit();
        if(forward.len()<0.9||side.len()<0.9||up.len()<0.9)throw new IllegalArgumentException("Path basis");
        List<Geometry.V> main=new ArrayList<>();
        for(int i=0;i<=p.samples;i++)main.add(point(p,origin,forward,side,up,(double)i/p.samples,time));
        List<Strand> out=new ArrayList<>();out.add(new Strand(List.copyOf(main),1));
        for(int b=0;b<p.branches;b++) {
            int start=Math.max(1,Math.min(p.samples-2,(int)Math.round(p.samples*(.32+.48*(b+1.0)/(p.branches+1.0)))));
            Geometry.V root=main.get(start);double direction=hash(b+201,p.seed)>=0?1:-1;
            double size=p.amplitude*Math.max(.18,p.branchLength);
            List<Geometry.V> fork=new ArrayList<>();
            for(int j=0;j<=12;j++) {
                double f=j/12.0;
                fork.add(root.add(forward.mul(p.length*p.branchLength*.35*f))
                    .add(side.mul(direction*size*f))
                    .add(up.mul(size*.38*f*Math.sin((b+1)*2.3))));
            }
            out.add(new Strand(List.copyOf(fork),.45));
        }
        return List.copyOf(out);
    }
}
