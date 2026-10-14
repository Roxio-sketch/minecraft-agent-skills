package dev.shieldfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Loader independent surface geometry and exact Voronoi cuts for rigid curved fragments. */
public final class ShieldGeometry {
    public record V(double x,double y,double z) {
        public V add(V b){return new V(x+b.x,y+b.y,z+b.z);} public V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
        public V mul(double s){return new V(x*s,y*s,z*s);} public double dot(V b){return x*b.x+y*b.y+z*b.z;}
        public V cross(V b){return new V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
        public double length(){return Math.sqrt(dot(this));} public V unit(){double n=length();return n>1e-12?mul(1/n):new V(0,0,1);}
        public V rotate(V axis,double angle){double c=Math.cos(angle),s=Math.sin(angle);return mul(c).add(axis.cross(this).mul(s)).add(axis.mul(axis.dot(this)*(1-c)));}
    }
    public record Vertex(V pos,V normal,double u,double v) {
        Vertex mix(Vertex b,double t){return new Vertex(pos.add(b.pos.sub(pos).mul(t)),normal.add(b.normal.sub(normal).mul(t)),u+(b.u-u)*t,v+(b.v-v)*t);}
    }
    public record Triangle(Vertex a,Vertex b,Vertex c) {
        public double area(){return b.pos.sub(a.pos).cross(c.pos.sub(a.pos)).length()*.5;}
        public V center(){return a.pos.add(b.pos).add(c.pos).mul(1.0/3);}
        public List<Vertex> vertices(){return List.of(a,b,c);}
        double radius(){V q=center();return Math.max(a.pos.sub(q).length(),Math.max(b.pos.sub(q).length(),c.pos.sub(q).length()));}
    }
    public record Edge(Vertex a,Vertex b) {}
    public record Patch(List<Triangle> triangles,List<Edge> edges,V center,V velocity,V axis,double delay,double spin) {
        public V point(V source,double age,ShieldRecipe r){double t=Math.max(0,age-delay);double shrink=1-.035*Math.min(1,t/.12);return source.sub(center).mul(shrink).rotate(axis,t*r.shatterSpin*spin).add(center).add(velocity.mul(t)).add(new V(0,-.5*r.shatterGravity*t*t,0));}
        public V normal(V source,double age,ShieldRecipe r){return source.rotate(axis,Math.max(0,age-delay)*r.shatterSpin*spin);}
    }
    private ShieldGeometry() {}
    public static Vertex surface(String shape,double depth,double u,double v) {
        if(shape.equals("sphere")||shape.equals("dome")){
            double ph=v*Math.PI*(shape.equals("dome")?.5:1),th=u*Math.PI*2;
            V q=new V(2*Math.sin(ph)*Math.cos(th),2*Math.cos(ph),2*Math.sin(ph)*Math.sin(th)*depth);
            return new Vertex(q,new V(q.x,q.y,q.z/(depth*depth)).unit(),u,v);
        }
        double a=(u-.5)*Math.PI,b=(v-.5)*Math.PI,k=shape.equals("convex")?depth:0;
        V q=new V((u-.5)*4.4,(.5-v)*2.8,k*Math.cos(a)*Math.cos(b));
        double dx=-k*Math.PI/4.4*Math.sin(a)*Math.cos(b),dy=k*Math.PI/2.8*Math.cos(a)*Math.sin(b);
        return new Vertex(q,new V(-dx,-dy,1).unit(),u,v);
    }
    public static List<Triangle> mesh(ShieldRecipe r) {
        int cols=r.segments,rows=(int)Math.round(cols*.55);Vertex[][] vs=new Vertex[rows+1][cols+1];
        for(int j=0;j<=rows;j++)for(int i=0;i<=cols;i++)vs[j][i]=surface(r.shape,r.depth,i/(double)cols,j/(double)rows);
        boolean closed=r.shape.equals("sphere")||r.shape.equals("dome");List<Triangle> out=new ArrayList<>();
        for(int j=0;j<rows;j++)for(int i=0;i<cols;i++){
            Vertex a=vs[j][i],b=vs[j][i+1],c=vs[j+1][i],d=vs[j+1][i+1];
            Triangle t1=closed?new Triangle(a,b,c):new Triangle(a,c,b),t2=closed?new Triangle(b,d,c):new Triangle(b,c,d);
            if(t1.area()>1e-9)out.add(t1);if(t2.area()>1e-9)out.add(t2);
        }
        return out;
    }
    public static List<Triangle> freezeSurface(List<Triangle> mesh,ShieldRecipe r,double time) {
        List<Triangle> out=new ArrayList<>(mesh.size());
        for(Triangle t:mesh)out.add(new Triangle(displace(t.a,r,time),displace(t.b,r,time),displace(t.c,r,time)));
        return out;
    }
    private static Vertex displace(Vertex v,ShieldRecipe r,double time){
        double d=(noise(v.pos.mul(r.distortionScale).add(new V(time*r.distortionSpeed,time*r.distortionSpeed,time*r.distortionSpeed)))-.5)*r.distortion+Math.sin(time*r.pulseFrequency*Math.PI*2)*r.pulseAmplitude;
        return new Vertex(v.pos.add(v.normal.mul(d)),v.normal,v.u,v.v);
    }
    private static double fract(double x){return x-Math.floor(x);}
    private static double hash(V q){double x=fract(q.x*.1031),y=fract(q.y*.1031),z=fract(q.z*.1031);double d=x*(y+33.33)+y*(z+33.33)+z*(x+33.33);x+=d;y+=d;z+=d;return fract((x+y)*z);}
    private static double mix(double a,double b,double t){return a+(b-a)*t;}
    private static double noise(V q){
        V i=new V(Math.floor(q.x),Math.floor(q.y),Math.floor(q.z));double x=fract(q.x),y=fract(q.y),z=fract(q.z);x=x*x*(3-2*x);y=y*y*(3-2*y);z=z*z*(3-2*z);
        double a=mix(hash(i),hash(i.add(new V(1,0,0))),x),b=mix(hash(i.add(new V(0,1,0))),hash(i.add(new V(1,1,0))),x),c=mix(hash(i.add(new V(0,0,1))),hash(i.add(new V(1,0,1))),x),d=mix(hash(i.add(new V(0,1,1))),hash(i.add(new V(1,1,1))),x);return mix(mix(a,b,y),mix(c,d,y),z);
    }
    private static List<Vertex> clip(List<Vertex> poly,V n,double d) {
        List<Vertex> out=new ArrayList<>();for(int i=0;i<poly.size();i++){
            Vertex a=poly.get(i),b=poly.get((i+1)%poly.size());double da=a.pos.dot(n)-d,db=b.pos.dot(n)-d;
            if(da<=0)out.add(a);if((da<=0)!=(db<=0))out.add(a.mix(b,da/(da-db)));
        }return out;
    }
    private static String key(V p){return Math.round(p.x*1e5)+","+Math.round(p.y*1e5)+","+Math.round(p.z*1e5);}
    private static V randomVector(Random rng){return new V(rng.nextDouble()*2-1,rng.nextDouble()*2-1,rng.nextDouble()*2-1).unit();}
    public static List<Patch> fracture(List<Triangle> mesh,V hit,ShieldRecipe r,long seed) {
        if(mesh.isEmpty())return List.of();Random rng=new Random(seed);int count=Math.min(r.shardCount,mesh.size());
        List<V> centers=new ArrayList<>();centers.add(mesh.get(rng.nextInt(mesh.size())).center());double[] distance=new double[mesh.size()];java.util.Arrays.fill(distance,Double.POSITIVE_INFINITY);
        for(int s=1;s<count;s++){int best=0;double far=-1;V last=centers.get(centers.size()-1);for(int j=0;j<mesh.size();j++){V q=mesh.get(j).center().sub(last);distance[j]=Math.min(distance[j],q.dot(q));double score=distance[j]*(.88+rng.nextDouble()*.24);if(score>far){far=score;best=j;}}centers.add(mesh.get(best).center());}
        List<List<Triangle>> groups=new ArrayList<>();for(int i=0;i<count;i++)groups.add(new ArrayList<>());
        for(Triangle tri:mesh){V center=tri.center();double nearest=Double.POSITIVE_INFINITY;double[] ds=new double[count];for(int i=0;i<count;i++){ds[i]=centers.get(i).sub(center).length();nearest=Math.min(nearest,ds[i]);}
            double triRadius=tri.radius();List<Integer> candidates=new ArrayList<>();for(int i=0;i<count;i++)if(ds[i]<=nearest+2*triRadius+1e-6)candidates.add(i);
            for(int i:candidates){List<Vertex> poly=tri.vertices();V a=centers.get(i);for(int j:candidates){if(poly.size()<3)break;if(i!=j){V b=centers.get(j);poly=clip(poly,b.sub(a),(b.dot(b)-a.dot(a))*.5);}}
                for(int j=1;j+1<poly.size();j++){Triangle t=new Triangle(poly.get(0),poly.get(j),poly.get(j+1));if(t.area()>1e-10)groups.get(i).add(t);}
            }
        }
        List<Patch> patches=new ArrayList<>();
        for(List<Triangle> triangles:groups){if(triangles.isEmpty())continue;double area=0;V center=new V(0,0,0),normal=new V(0,0,0);Map<String,Edge> edges=new HashMap<>();Map<String,Integer> counts=new HashMap<>();
            for(Triangle t:triangles){double a=t.area();area+=a;center=center.add(t.center().mul(a));normal=normal.add(t.a.normal.add(t.b.normal).add(t.c.normal).mul(a/3));List<Vertex> vs=t.vertices();for(int j=0;j<3;j++){Vertex v=vs.get(j),w=vs.get((j+1)%3);String ka=key(v.pos),kb=key(w.pos);if(ka.equals(kb))continue;String k=ka.compareTo(kb)<0?ka+"|"+kb:kb+"|"+ka;counts.merge(k,1,Integer::sum);edges.putIfAbsent(k,new Edge(v,w));}}
            center=center.mul(1/area);normal=normal.unit();V tangent=randomVector(rng),away=center.sub(hit).unit();V velocity=normal.mul(.6+rng.nextDouble()*.5).add(away.mul(.35)).add(tangent.mul(.28)).mul(r.shatterSpeed);
            V axis=randomVector(rng);double delay=.045+Math.min(1,center.sub(hit).length()/4.5)*.22,spin=(rng.nextBoolean()?-1:1)*(.45+rng.nextDouble());List<Edge> outline=new ArrayList<>();for(var e:edges.entrySet())if(counts.get(e.getKey())==1)outline.add(e.getValue());
            patches.add(new Patch(List.copyOf(triangles),List.copyOf(outline),center,velocity,axis,delay,spin));
        }
        return List.copyOf(patches);
    }
}
