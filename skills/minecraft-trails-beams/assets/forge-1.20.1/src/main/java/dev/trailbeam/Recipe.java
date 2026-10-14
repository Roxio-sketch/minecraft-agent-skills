package dev.trailbeam;

import com.google.gson.*;
import java.util.*;

/** Versioned, strict authoring contract: unknown keys fail instead of silently doing nothing. */
public record Recipe(String name,String section,int sides,double lifetime,double minDistance,double maxStep,
                     double breakDistance,int maxPoints,List<Layer> layers) {
    public record Stop(double at,double value) {}
    public record ColorStop(double at,float r,float g,float b,float alpha) {}
    public record Layer(double width,String blend,String profile,String texture,double repeat,double scroll,
                        double gain,double fadePower,List<Stop> taper,List<ColorStop> colors) {
        public double widthAt(double t) {
            Stop prev=taper.get(0);for(Stop next:taper) {
                if(t<=next.at) {double f=next.at==prev.at?0:Geometry.clamp((t-prev.at)/(next.at-prev.at));return prev.value+(next.value-prev.value)*f;}
                prev=next;
            }return prev.value;
        }
        public float[] colorAt(double t,double age) {
            ColorStop prev=colors.get(0),last=prev;double f=0;
            for(ColorStop next:colors) {
                last=next;if(t<=next.at) {f=next.at==prev.at?0:Geometry.clamp((t-prev.at)/(next.at-prev.at));break;}
                prev=next;
            }
            return new float[]{(float)(prev.r+(last.r-prev.r)*f),(float)(prev.g+(last.g-prev.g)*f),
                (float)(prev.b+(last.b-prev.b)*f),(float)((prev.alpha+(last.alpha-prev.alpha)*f)*Math.pow(1-Geometry.clamp(age),fadePower))};
        }
    }
    private static void keys(JsonObject o,String... keys) {
        Set<String> allowed=Set.of(keys);
        for(String key:o.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown recipe field: "+key);
        for(String key:keys)if(!o.has(key))throw new IllegalArgumentException("Missing recipe field: "+key);
    }
    private static double num(JsonObject o,String key,double min,double max) {
        double n=o.get(key).getAsDouble();if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException(key+" outside budget");return n;
    }
    private static int integer(JsonObject o,String key,int min,int max) {
        double n=num(o,key,min,max);if(n!=Math.rint(n))throw new IllegalArgumentException(key+" must be integer");return (int)n;
    }
    private static String option(JsonObject o,String key,String... options) {
        String v=o.get(key).getAsString();if(!Set.of(options).contains(v))throw new IllegalArgumentException(key+": "+v);return v;
    }
    public static Recipe parse(String json) {
        JsonObject o=JsonParser.parseString(json).getAsJsonObject();
        keys(o,"schema","name","section","sides","sampling","layers");
        integer(o,"schema",1,1);String name=o.get("name").getAsString();
        String section=option(o,"section","ribbon","cross","tube","blade");int sides=integer(o,"sides",3,64);
        JsonObject s=o.getAsJsonObject("sampling");keys(s,"lifetime","minDistance","maxStep","breakDistance","maxPoints");
        double life=num(s,"lifetime",0.05,30),min=num(s,"minDistance",0,5),step=num(s,"maxStep",0.001,10),br=num(s,"breakDistance",0.001,10000);
        int cap=integer(s,"maxPoints",2,4096);if(step<=min||br<step)throw new IllegalArgumentException("sampling intervals");
        List<Layer> layers=new ArrayList<>();JsonArray array=o.getAsJsonArray("layers");
        if(array.size()<1||array.size()>16)throw new IllegalArgumentException("1..16 layers per recipe");
        for(JsonElement elem:array) {
            JsonObject l=elem.getAsJsonObject();keys(l,"width","blend","profile","texture","repeat","scroll","gain","fadePower","taper","colors");
            double width=num(l,"width",0.001,64),repeat=num(l,"repeat",0,100),scroll=num(l,"scroll",-100,100),gain=num(l,"gain",0,16),fade=num(l,"fadePower",0.01,16);
            String blend=option(l,"blend","alpha","additive"),profile=option(l,"profile","soft","solid","double","dash","pulse","plasma","rune");
            String tex=l.get("texture").getAsString();if(!tex.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||tex.contains(".."))throw new IllegalArgumentException("texture resource id");
            List<Stop> taper=new ArrayList<>();double previous=-1;
            for(JsonElement el:l.getAsJsonArray("taper")) {JsonObject a=el.getAsJsonObject();keys(a,"at","value");double at=num(a,"at",0,1);if(at<=previous)throw new IllegalArgumentException("taper order");previous=at;taper.add(new Stop(at,num(a,"value",0,8)));}
            List<ColorStop> colors=new ArrayList<>();previous=-1;
            for(JsonElement el:l.getAsJsonArray("colors")) {
                JsonObject a=el.getAsJsonObject();keys(a,"at","color","alpha");double at=num(a,"at",0,1);if(at<=previous)throw new IllegalArgumentException("color order");previous=at;
                String hex=a.get("color").getAsString();if(!hex.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("HEX color");int rgb=Integer.parseInt(hex.substring(1),16);
                colors.add(new ColorStop(at,((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,(float)num(a,"alpha",0,1)));
            }
            if(taper.size()<2||taper.size()>32||taper.get(0).at!=0||taper.get(taper.size()-1).at!=1)throw new IllegalArgumentException("taper must cover 0..1");
            if(colors.size()<2||colors.size()>32||colors.get(0).at!=0||colors.get(colors.size()-1).at!=1)throw new IllegalArgumentException("colors must cover 0..1");
            layers.add(new Layer(width,blend,profile,tex,repeat,scroll,gain,fade,List.copyOf(taper),List.copyOf(colors)));
        }
        return new Recipe(name,section,sides,life,min,step,br,cap,List.copyOf(layers));
    }
    public Geometry.History newHistory() {return new Geometry.History(lifetime,minDistance,maxStep,breakDistance,maxPoints);}
}
