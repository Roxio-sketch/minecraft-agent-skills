package dev.trailbeam;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;

/** Client render-thread API. Feed world-space sockets from the actual weapon/animation adapter. */
public final class Effects {
    private Effects() {}
    private record Trail(Recipe recipe,Geometry.History history) {}
    private record Beam(Recipe recipe,List<Geometry.V> path,double until) {}
    private static final Map<UUID,Trail> trails=new LinkedHashMap<>();
    private static final Map<UUID,Beam> beams=new LinkedHashMap<>();
    private static String world;
    public static final int MAX_EMITTERS=128;
    public static void world(String id) { if(!Objects.equals(world,id)){clear();world=id;} }
    public static void clear(){trails.clear();beams.clear();}
    public static void remove(UUID id){trails.remove(id);beams.remove(id);}
    public static void sample(UUID id,Recipe recipe,double seconds,Geometry.V root,Geometry.V tip) {
        Trail trail=trails.get(id);
        if(trail==null||trail.recipe!=recipe) {
            if(trail==null&&trails.size()+beams.size()>=MAX_EMITTERS)throw new IllegalStateException("Effect budget");
            trail=new Trail(recipe,recipe.newHistory());trails.put(id,trail);
        }
        trail.history.add(seconds,root,tip);
    }
    public static void beam(UUID id,Recipe recipe,List<Geometry.V> points,double expiresAt) {
        if(points.size()<2||points.size()>4096||!Double.isFinite(expiresAt))throw new IllegalArgumentException("beam path budget/time");
        if(recipe.section().equals("blade"))throw new IllegalArgumentException("Blade needs paired history sockets");
        if(!beams.containsKey(id)&&trails.size()+beams.size()>=MAX_EMITTERS)throw new IllegalStateException("Effect budget");
        beams.put(id,new Beam(recipe,List.copyOf(points),expiresAt));
    }
    public static void expire(double seconds) {
        trails.values().removeIf(t->{t.history.expire(seconds);return t.history.samples(seconds).isEmpty();});
        beams.values().removeIf(b->seconds>=b.until);
    }
    public static void render(PoseStack pose,Geometry.V camera,double seconds) {
        expire(seconds);
        // Far-to-near emitter approximation. Intersections between transparent ribbons still require care.
        List<RunnableDepth> work=new ArrayList<>();
        for(Trail t:trails.values()) {
            List<Geometry.Sample> samples=t.history.samples(seconds);if(samples.size()<2)continue;
            work.add(new RunnableDepth(samples.get(samples.size()/2).center().sub(camera).len(),()->{
                for(Recipe.Layer l:t.recipe.layers()) {
                    List<Geometry.Quad> mesh=t.recipe.section().equals("blade")?
                        Geometry.blade(samples,seconds,t.recipe.lifetime(),l.width(),t.history.arcOrigin(),l::widthAt):
                        Geometry.sweep(t.history.centerline(seconds),camera,t.recipe.section(),t.recipe.sides(),l.width(),l::widthAt);
                    BeamRender.draw(mesh,l,pose,camera,seconds);
                }
            }));
        }
        for(Beam b:beams.values())work.add(new RunnableDepth(b.path.get(b.path.size()/2).sub(camera).len(),()->{
            for(Recipe.Layer l:b.recipe.layers())BeamRender.draw(Geometry.sweep(Geometry.path(b.path),camera,b.recipe.section(),b.recipe.sides(),l.width(),l::widthAt),l,pose,camera,seconds);
        }));
        work.sort(Comparator.comparingDouble(RunnableDepth::depth).reversed());work.forEach(w->w.draw.run());
    }
    private record RunnableDepth(double depth,Runnable draw){}
}
