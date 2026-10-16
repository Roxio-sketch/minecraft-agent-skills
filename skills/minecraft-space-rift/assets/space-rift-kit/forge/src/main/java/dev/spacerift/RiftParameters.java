package dev.spacerift;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Client-side runtime parameters. Same ranges/defaults as the browser schema. */
public final class RiftParameters {
    static final JsonObject SCHEMA;
    static {try(var in=Objects.requireNonNull(RiftParameters.class.getResourceAsStream("/assets/spacerift/parameters.json"))){SCHEMA=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    final Map<String,Float> values=new LinkedHashMap<>();
    String edge="#9270ff",inside="#171035";
    public RiftParameters(){for(var e:SCHEMA.getAsJsonObject("defaults").entrySet())if(e.getValue().getAsJsonPrimitive().isNumber())values.put(e.getKey(),e.getValue().getAsFloat());}
    public RiftParameters set(String name,float value){
        var range=SCHEMA.getAsJsonObject("ranges").getAsJsonArray(name);
        if(range==null||!Float.isFinite(value)||value<range.get(0).getAsFloat()||value>range.get(1).getAsFloat())throw new IllegalArgumentException("Invalid rift parameter "+name+"="+value);
        if(range.get(2).getAsFloat()==1&&value!=Math.round(value))throw new IllegalArgumentException("Expected integer "+name);
        values.put(name,value);return this;
    }
    public float get(String name){return values.getOrDefault(name,0f);}
    public RiftParameters colors(String edge,String inside){if(!edge.matches("#[0-9a-fA-F]{6}")||!inside.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Expected #RRGGBB");this.edge=edge;this.inside=inside;return this;}
    public RiftParameters copy(){var p=new RiftParameters();p.values.putAll(values);p.edge=edge;p.inside=inside;return p;}
    public static RiftParameters fromJson(String text){
        var raw=JsonParser.parseString(text).getAsJsonObject();
        if(raw.has("format")&&!Set.of("space-rift/2","ProceduralSpaceRiftKit.v1").contains(raw.get("format").getAsString()))throw new IllegalArgumentException("Unknown rift recipe version");
        var data=raw.has("parameters")?raw.getAsJsonObject("parameters"):raw;var p=new RiftParameters();
        for(var e:data.entrySet())if(p.values.containsKey(e.getKey())){if(!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected number "+e.getKey());p.set(e.getKey(),e.getValue().getAsFloat());}
        return p.colors(data.has("u_edgeColor")?data.get("u_edgeColor").getAsString():p.edge,data.has("u_insideColor")?data.get("u_insideColor").getAsString():p.inside);
    }
}
