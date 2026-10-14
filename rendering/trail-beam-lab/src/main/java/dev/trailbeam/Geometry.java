package dev.trailbeam;

import java.util.*;

/** Original geometry core. Units: blocks and seconds. No Minecraft dependency. */
public final class Geometry {
    private Geometry() {}
    public record V(double x, double y, double z) {
        public V {
            if (!Double.isFinite(x+y+z)) throw new IllegalArgumentException("Non-finite position");
        }
        public V add(V b) { return new V(x+b.x,y+b.y,z+b.z); }
        public V sub(V b) { return new V(x-b.x,y-b.y,z-b.z); }
        public V mul(double s) { return new V(x*s,y*s,z*s); }
        public double dot(V b) { return x*b.x+y*b.y+z*b.z; }
        public V cross(V b) { return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x); }
        public double len() { return Math.sqrt(dot(this)); }
        public V unit() { double n=len(); return n>1e-9?mul(1/n):new V(0,0,0); }
        public V mix(V b,double t) { return mul(1-t).add(b.mul(t)); }
    }
    public record Sample(V a,V b,double time) {
        public V center() { return a.mix(b,0.5); }
    }
    public record Point(V position,double age,double distance) {}
    public record Vertex(V position,double u,double v,double along,double age) {}
    public record Quad(Vertex a,Vertex b,Vertex c,Vertex d) {}
    @FunctionalInterface public interface Curve { double at(double t); }
    public static double clamp(double n) { return Math.max(0,Math.min(1,n)); }

    /** The producer samples simulation time, not render FPS. expire must also run after emission stops. */
    public static final class History {
        private final ArrayDeque<Sample> points=new ArrayDeque<>();
        public final double lifetime,minDistance,maxStep,breakDistance;
        public final int maxPoints;
        private double lastTime=Double.NEGATIVE_INFINITY;
        private double arcOrigin;
        public History(double lifetime,double minDistance,double maxStep,double breakDistance,int maxPoints) {
            if (!(lifetime>0&&minDistance>=0&&maxStep>minDistance&&breakDistance>=maxStep&&maxPoints>=2))
                throw new IllegalArgumentException("Invalid history budget");
            this.lifetime=lifetime;this.minDistance=minDistance;this.maxStep=maxStep;
            this.breakDistance=breakDistance;this.maxPoints=maxPoints;
        }
        public void clear() { points.clear(); lastTime=Double.NEGATIVE_INFINITY;arcOrigin=0; }
        public double arcOrigin(){return arcOrigin;}
        private void dropFirst(){Sample removed=points.removeFirst();arcOrigin=points.isEmpty()?0:arcOrigin+removed.center().sub(points.getFirst().center()).len();}
        public void expire(double now) {
            while (!points.isEmpty()&&now-points.getFirst().time>lifetime) dropFirst();
        }
        public void add(double now,V a,V b) {
            if (!Double.isFinite(now)) throw new IllegalArgumentException("time");
            if (now<lastTime) clear();
            if(now==lastTime) return; // duplicate passes must not insert new history
            lastTime=now; expire(now);
            Sample previous=points.peekLast();
            if(previous==null) { points.add(new Sample(a,b,now)); return; }
            double distance=Math.max(a.sub(previous.a).len(),b.sub(previous.b).len());
            if(distance>breakDistance) { points.clear();arcOrigin=0; points.add(new Sample(a,b,now)); return; }
            if(distance<Math.max(minDistance,1e-8)) return;
            int count=Math.min(maxPoints,Math.max(1,(int)Math.ceil(distance/maxStep)));
            for(int i=1;i<=count;i++) {
                double f=(double)i/count;
                points.addLast(new Sample(previous.a.mix(a,f),previous.b.mix(b,f),previous.time+(now-previous.time)*f));
                while(points.size()>maxPoints)dropFirst();
            }
        }
        public List<Sample> samples(double now) { expire(now); return List.copyOf(points); }
        public List<Point> centerline(double now) {
            List<Sample> list=samples(now);List<Point> out=new ArrayList<>();double d=arcOrigin;V prev=null;
            for(Sample s:list) {V p=s.center(); if(prev!=null)d+=p.sub(prev).len();out.add(new Point(p,clamp((now-s.time)/lifetime),d));prev=p;}
            return out;
        }
    }

    /** Cull zero-length segments; arc-distance UV stays continuous under nonuniform point spacing. */
    public static List<Point> path(List<V> input) {
        List<Point> out=new ArrayList<>();V prev=null;double distance=0;
        for(V p:input) {
            double step=prev==null?0:p.sub(prev).len();
            if(prev!=null&&step<1e-7)continue;
            distance+=step;out.add(new Point(p,0,distance));prev=p;
        }
        return out;
    }
    private static V fallback(V tangent) {
        V axis=Math.abs(tangent.y)<0.8?new V(0,1,0):new V(1,0,0);
        return tangent.cross(axis).unit();
    }
    private static V tangent(List<Point> p,int i) {
        V t=p.get(Math.min(i+1,p.size()-1)).position.sub(p.get(Math.max(0,i-1)).position).unit();
        if(t.len()<1e-7)t=p.get(Math.min(i+1,p.size()-1)).position.sub(p.get(i).position).unit();
        return t.len()<1e-7?new V(0,0,1):t;
    }
    private static Vertex vertex(V p,Point c,double v,double along) {return new Vertex(p,c.distance,v,along,c.age);}
    /** section: ribbon (camera-facing), cross (two ribbons), tube (open ends). Width is full diameter. */
    public static List<Quad> sweep(List<Point> input,V camera,String section,int sides,double width,Curve taper) {
        if(!Set.of("ribbon","cross","tube").contains(section))throw new IllegalArgumentException("section");
        if(width<=0||!Double.isFinite(width)||sides<3||sides>64)throw new IllegalArgumentException("mesh budget");
        List<Point> p=new ArrayList<>();
        for(Point v:input)if(p.isEmpty()||v.position.sub(p.get(p.size()-1).position).len()>1e-7)p.add(v);
        List<Quad> out=new ArrayList<>();if(p.size()<2)return out;
        boolean tube=section.equals("tube");int n=tube?sides:section.equals("cross")?4:2;
        Vertex[][] rings=new Vertex[p.size()][n];V previousSide=null;
        double start=p.get(0).distance,total=p.get(p.size()-1).distance-start;
        for(int i=0;i<p.size();i++) {
            Point c=p.get(i);V t=tangent(p,i),side;
            if(tube&&previousSide!=null)side=previousSide.sub(t.mul(previousSide.dot(t)));
            else side=t.cross(camera.sub(c.position));
            if(side.len()<1e-6)side=previousSide==null?fallback(t):previousSide.sub(t.mul(previousSide.dot(t)));
            if(side.len()<1e-6)side=fallback(t);
            side=side.unit();if(previousSide!=null&&side.dot(previousSide)<0)side=side.mul(-1);
            previousSide=side;V up=t.cross(side).unit();double s=total>1e-9?(c.distance-start)/total:0;
            double radius=0.5*width*Math.max(0,taper.at(s));
            for(int j=0;j<n;j++) {
                V offset;double v;
                if(tube) {double angle=2*Math.PI*j/n;offset=side.mul(Math.cos(angle)*radius).add(up.mul(Math.sin(angle)*radius));v=(double)j/n;}
                else {offset=(j<2?side:up).mul((j%2==0?-1:1)*radius);v=j%2;}
                rings[i][j]=vertex(c.position.add(offset),c,v,s);
            }
        }
        for(int i=1;i<p.size();i++) {
            if(tube)for(int j=0;j<n;j++) {
                int k=(j+1)%n;Vertex a=rings[i-1][j],b=rings[i-1][k],c=rings[i][k],d=rings[i][j];
                if(k==0) {b=new Vertex(b.position,b.u,1,b.along,b.age);c=new Vertex(c.position,c.u,1,c.along,c.age);}
                out.add(new Quad(a,b,c,d));
            } else for(int j=0;j<n;j+=2)out.add(new Quad(rings[i-1][j],rings[i-1][j+1],rings[i][j+1],rings[i][j]));
        }
        return out;
    }
    /** Blade history uses two genuine sockets; keep both endpoints rather than rebuilding a billboard. */
    public static List<Quad> blade(List<Sample> p,double now,double lifetime,double scale,Curve taper) {
        return blade(p,now,lifetime,scale,0,taper);
    }
    public static List<Quad> blade(List<Sample> p,double now,double lifetime,double scale,double uOffset,Curve taper) {
        List<Quad> out=new ArrayList<>();if(p.size()<2)return out;
        double[] ds=new double[p.size()];for(int i=1;i<p.size();i++)ds[i]=ds[i-1]+p.get(i).center().sub(p.get(i-1).center()).len();
        Vertex[][] edge=new Vertex[p.size()][2];
        for(int i=0;i<p.size();i++) {
            Sample s=p.get(i);double along=ds[p.size()-1]>1e-7?ds[i]/ds[p.size()-1]:(double)i/(p.size()-1);
            V center=s.center();double factor=scale*Math.max(0,taper.at(along));double age=clamp((now-s.time)/lifetime);
            edge[i][0]=new Vertex(center.add(s.a.sub(center).mul(factor)),ds[i]+uOffset,0,along,age);
            edge[i][1]=new Vertex(center.add(s.b.sub(center).mul(factor)),ds[i]+uOffset,1,along,age);
        }
        for(int i=1;i<p.size();i++)out.add(new Quad(edge[i-1][0],edge[i-1][1],edge[i][1],edge[i][0]));
        return out;
    }
}
