package dev.shieldfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import dev.shieldfx.ShieldGeometry.V;
import dev.shieldfx.ShieldGeometry.Vertex;

/** Finite analytic spark trajectories in shield object space; no registered particles. */
public final class ShieldSparks {
    public record Spark(V origin,V velocity,double bornAt,double duration) {
        public double age(double time){return Math.max(0,time-bornAt);}
        public double alpha(double time){return Math.max(0,1-age(time)/duration);}
        public V position(double time){double age=age(time);return origin.add(velocity.mul(age)).add(new V(0,-1.4*age*age,0));}
        public V trail(double time){return position(Math.max(bornAt,time-.035));}
    }
    private final List<Spark> sparks=new ArrayList<>();
    private final Random random=new Random(20261012L);
    public void clear(){sparks.clear();}
    public void tick(double time){sparks.removeIf(s->s.age(time)>=s.duration());}
    public List<Spark> particles(){return List.copyOf(sparks);}
    private V direction(){return new V(random.nextDouble()*2-1,random.nextDouble()*2-1,random.nextDouble()*2-1).unit();}
    private void emit(V point,V normal,ShieldRecipe recipe,double time,double speed) {
        V velocity=direction().add(normal.mul(.7)).mul(recipe.sparkSpeed*speed*(.4+random.nextDouble()*.65));
        sparks.add(new Spark(point,velocity,time,recipe.sparkLife*(.6+random.nextDouble()*.8)));
    }
    public void spawn(V point,V normal,ShieldRecipe recipe,double time,boolean shattered) {
        for(int i=0;i<recipe.sparkCount;i++)emit(point,normal,recipe,time,shattered?1.5:1);
        if(shattered)for(int i=0;i<Math.min(80,recipe.sparkCount);i++){
            Vertex surface=ShieldGeometry.surface(recipe.shape,recipe.depth,random.nextDouble(),recipe.shape.equals("dome")?random.nextDouble():.15+random.nextDouble()*.7);
            emit(surface.pos(),surface.normal(),recipe,time,1.2);
        }
        if(sparks.size()>1200)sparks.subList(0,sparks.size()-1200).clear();
    }
}
