package dev.vfxworkbench.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Curated ranges and coordinated palettes; placement and rendering budgets stay under user control. */
public final class RandomSchemes {
    private static final String[][] PALETTES={
        {"#f1faff","#64cfff","#2356b8"},{"#fff6df","#ffad56","#aa3828"},
        {"#faf0ff","#bf84ff","#5531a0"},{"#eafff4","#65e1af","#136e82"},
        {"#fff0fa","#f87db8","#6745a8"}
    };
    public static Map<String,String> generate(ParamState state,long seed){
        Random r=new Random(seed);var values=new LinkedHashMap<>(state.snapshot());var colors=PALETTES[r.nextInt(PALETTES.length)];
        switch(state.schema.id){
            case "black-hole"->{
                boolean white=state.get("objectMode").equals("white");
                put(values,"form","recipe");put(values,"seed",r.nextInt(65536));
                double core=number(r,.3,.8),inner=core+number(r,.03,.55),outer=number(r,3.6,7.5);
                put(values,"core",core);put(values,"inner",inner);put(values,"outer",outer);
                put(values,"diskStyle",new String[]{"dust","cinematic","ribbons","classic","granular"}[r.nextInt(5)]);
                put(values,"thickness",number(r,.055,.22));put(values,"bend",number(r,.45,1.4));
                put(values,"density",number(r,2,6));put(values,"exposure",number(r,.35,1.1));
                put(values,"speed",number(r,.2,1.3));put(values,"infallSpeed",number(r,.08,.8));
                put(values,"hot",colors[0]);put(values,"diskMid",colors[1]);put(values,"cool",colors[2]);
                put(values,"dustScale",number(r,1.5,5));put(values,"dustFill",number(r,.45,.9));
                put(values,"filament",number(r,.2,1.5));put(values,"spiral",number(r,.1,1.1));
                put(values,"diskGradientPosition",number(r,.35,.7));
                put(values,"halo",number(r,0,.65));put(values,"haloGradientStart",colors[1]);put(values,"haloGradientEnd",colors[2]);
                put(values,"particleCold",colors[2]);put(values,"particleHot",colors[0]);
                put(values,"particleSize",number(r,.012,.035));put(values,"particleBrightness",number(r,.7,1.7));
                if(white){
                    put(values,"coreColor",colors[0]);put(values,"coreEdgeColor",colors[1]);
                    put(values,"coreEmission",number(r,2.5,4));put(values,"coreGlow",number(r,.4,1.1));
                    put(values,"coreGlowRadius",number(r,1.4,2.4));put(values,"jetStrength",r.nextBoolean()?number(r,.5,2):0);
                    put(values,"jetLength",number(r,4,10));put(values,"jetWidth",number(r,.12,.5));
                    put(values,"jetBaseColor",colors[0]);put(values,"jetTipColor",colors[1]);put(values,"shellColor",colors[1]);
                }
                put(values,"playing",true);put(values,"time",0);
            }
            case "lightning"->{
                put(values,"preset","custom");put(values,"seed",r.nextInt(65536));
                boolean orbit=state.get("mode").equals("orbit");
                double width=number(r,.009,.035);put(values,orbit?"currentWidth":"strikeWidth",width);
                put(values,"glowWidth",Math.max(.025,width*number(r,3,6)));
                put(values,"coreGain",number(r,3,9));put(values,"glowGain",number(r,.8,2.5));
                put(values,"coreColor","#ffffff");put(values,"glowColor",colors[1]);
                put(values,"gradientStart",colors[0]);put(values,"gradientEnd",colors[1]);
                put(values,"gradientEnabled",true);put(values,"gradientTarget","both");put(values,"gradientCoreMode","vivid");
                put(values,"gradientRatio",number(r,30,75));
                if(orbit){
                    put(values,"orbitWobble",number(r,.1,.45));put(values,"arcLength",number(r,1,3.5));
                    put(values,"orbitFlow",number(r,.5,2.5));put(values,"orbitNoiseFrequency",number(r,.4,1.2));
                    put(values,"orbitExtendSpeed",number(r,12,35));
                }else{
                    put(values,"strikeHeight",Math.max(12,Math.min(1024,state.getDouble("strikeHeight")*number(r,.65,1.35))));
                    put(values,"strikeDisplacement",number(r,2,10));put(values,"strikeRoughness",number(r,.5,.75));
                    put(values,"branchProbability",number(r,.4,.8));put(values,"branchDecay",number(r,.55,.8));
                    put(values,"branchLength",number(r,.25,.65));put(values,"branchAngle",number(r,.5,1.1));
                    put(values,"branchJitter",number(r,.12,.35));
                    if(state.get("mode").equals("rain")&&state.get("rainStyle").equals("spread")){
                        put(values,"rainSpread",number(r,6,18));put(values,"rainDepthSpread",number(r,2,8));
                        put(values,"rainStagger",number(r,.25,1.5));
                    }
                }
            }
            case "water-ripples"->{
                put(values,"color",colors[2]);put(values,"speed",number(r,.35,.95));
                put(values,"damping",number(r,.978,.998));put(values,"radius",number(r,.2,1.1));
                put(values,"strength",number(r,.1,.3));put(values,"maxHeight",number(r,.35,.7));
                put(values,"opacity",number(r,.15,.55));put(values,"normalStrength",number(r,.5,2));
                put(values,"shininess",number(r,32,160));put(values,"reflection",number(r,.4,1.5));
            }
            default->throw new IllegalArgumentException("该项目尚未配置随机方案");
        }
        return values;
    }
    private static double number(Random random,double min,double max){return Math.round((min+random.nextDouble()*(max-min))*10000)/10000.0;}
    private static void put(Map<String,String> values,String key,Object value){values.put(key,String.valueOf(value));}
    private RandomSchemes(){}
}
