package dev.shieldfx;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Shared schema 2 JSON contract. Missing fields retain their compatible defaults. */
public final class ShieldRecipe {
    public int schema=2, segments=56;
    public String shape="sphere", pattern="hex", color="#268eff", edgeColor="#a2d4ff", sparkColor="#ffb44e";
    public double depth=.8, opacity=.88, centerOpacity=.24, edgeOpacity=1, fresnelStrength=2.2, fresnelPower=2.2, life=1;
    public double hexScale=3.2, hexOpacity=.68, lineWidth=.045, flashSpeed=.7, flashIntensity=.13;
    public double flowScale=2.4, flowSpeed=.65, flowIntensity=2.1, reveal=1, noiseScale=1.7, noiseEdgeWidth=.07, noiseEdgeIntensity=2.2;
    public double scanSpeed=.25, scanStrength=.3, scanWidth=.05, distortion=.035, distortionScale=2.2, distortionSpeed=.5, pulseAmplitude=.015, pulseFrequency=.35;
    public double hitSpeed=2.6, hitWidth=.11, hitDuration=1.8, hitIntensity=2.6, hitRadius=3.4, sparkSpeed=3.2, sparkLife=.7, damage=.08;
    public int sparkCount=65, shardCount=110;
    public double shatterDuration=2.4, shatterSpeed=2.8, shatterSpin=3, shatterGravity=1.8, shatterFlash=1.8;
    public boolean showHex=true, showFlow=true, showDissolve=true, showScan=false, wireframe=false, background=true;
    // Retained for old recipes and the standalone height field utility.
    public int grid=65; public String solver="velocity", boundary="absorb", emission="#8ff5ff";
    public double maxHeight=.5, speed=1, damping=.995, radius=.45, strength=.18, rainRate=0;
    public double edgePower=2.8, edgeStrength=1.8, hexStrength=.55, scanlineStrength=.18, scanlineSpeed=.65, scanlineThickness=.06, glowNoiseScale=5, glowSpeed=1.2;
    public double rippleThickness=.05, rippleSpeed=2.5, rippleTime=2, rippleX=.5, rippleY=.5, intersectionStrength=1, intersectionThickness=.08;
    public double impactStrength=1.8, impactDuration=.9, impactX=.5, impactY=.5;
    public boolean hexagons=true, scanlines=true, bigScanline=true, ripples=true, intersections=true;
    public ShieldRecipe validate() {
        if(schema!=1&&schema!=2)throw new IllegalArgumentException("schema must be 1 or 2");
        if(!Set.of("sphere","dome","convex","flat").contains(shape))throw new IllegalArgumentException("unsupported shape");
        if(!Set.of("hex","diamond","dots","grid","none").contains(pattern))throw new IllegalArgumentException("unsupported pattern");
        color(color);color(edgeColor);color(sparkColor);color(emission);
        range("segments",segments,16,80);range("depth",depth,.15,1.6);
        range("life",life,0,1);range("opacity",opacity,0,1);range("centerOpacity",centerOpacity,0,1);range("edgeOpacity",edgeOpacity,0,1);
        range("fresnelPower",fresnelPower,.2,8);range("fresnelStrength",fresnelStrength,0,8);range("hexScale",hexScale,.6,80);range("hexOpacity",hexOpacity,0,2);
        range("lineWidth",lineWidth,.005,.15);range("flashSpeed",flashSpeed,0,4);range("flashIntensity",flashIntensity,0,1);
        range("flowScale",flowScale,.2,8);range("flowSpeed",flowSpeed,0,4);range("flowIntensity",flowIntensity,0,5);
        range("reveal",reveal,0,1);range("noiseScale",noiseScale,.3,6);range("noiseEdgeWidth",noiseEdgeWidth,.01,.25);range("noiseEdgeIntensity",noiseEdgeIntensity,0,5);
        range("scanSpeed",scanSpeed,0,2);range("scanStrength",scanStrength,0,2);range("scanWidth",scanWidth,.005,.2);
        range("distortion",distortion,0,.4);range("distortionScale",distortionScale,.3,6);range("distortionSpeed",distortionSpeed,0,3);range("pulseAmplitude",pulseAmplitude,0,.2);range("pulseFrequency",pulseFrequency,0,2);
        range("hitSpeed",hitSpeed,.1,7);range("hitWidth",hitWidth,.03,.45);range("hitDuration",hitDuration,.3,4);range("hitIntensity",hitIntensity,0,8);range("hitRadius",hitRadius,.5,6);
        range("sparkCount",sparkCount,0,160);range("sparkSpeed",sparkSpeed,.3,7);range("sparkLife",sparkLife,.2,2);range("damage",damage,0,.5);
        range("shatterDuration",shatterDuration,.6,5);range("shardCount",shardCount,24,240);range("shatterSpeed",shatterSpeed,.3,7);range("shatterSpin",shatterSpin,0,8);range("shatterGravity",shatterGravity,0,6);range("shatterFlash",shatterFlash,0,5);
        return this;
    }
    private static void range(String n,double v,double a,double b){if(!Double.isFinite(v)||v<a||v>b)throw new IllegalArgumentException(n+": "+a+".."+b);}
    private static void color(String v){if(v==null||!v.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("color must be #RRGGBB");}
    public static ShieldRecipe read(Path p)throws IOException {
        if(!Files.exists(p))return new ShieldRecipe();
        ShieldRecipe r=new Gson().fromJson(Files.readString(p),ShieldRecipe.class);
        if(r==null)throw new IllegalArgumentException("empty recipe");
        if(r.schema==1){r.edgeColor=r.emission;r.fresnelPower=r.edgePower;r.fresnelStrength=r.edgeStrength;r.hexScale=Math.min(12,r.hexScale/5);r.hexOpacity=r.hexStrength;r.showHex=r.hexagons;r.showScan=r.scanlines;r.scanSpeed=Math.min(2,r.scanlineSpeed);r.hitIntensity=r.impactStrength;r.schema=2;}
        return r.validate();
    }
}
