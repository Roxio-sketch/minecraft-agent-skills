package dev.vfxworkbench.effects.lightning;

import net.minecraft.world.phys.Vec3;
import com.google.gson.*;
import dev.vfxworkbench.core.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

public final class LightningCheck {
    public static void main(String[] args) {
        ImpactColorCheck.run();
        checkContract();
        checkRasterCoverage();
        HaloGeometryCheck.run();
        GradientRatioCheck.run();
        double[][] vectors={{0,0,0},{.1,.2,.8285183685716719},{1.25,-3.75,.7282445621599388},{289.2,7.1,-.6433033650825536},{-500,120,-.2022023529315641}};
        for(double[] v:vectors)if(Math.abs(SimplexNoise.sample(v[0],v[1])-v[2])>1e-12)throw new AssertionError("simplex Java/JS vector mismatch");
        for(String mode:List.of("grow","ring"))for(int direction:new int[]{1,-1}){
            LightningRecipe fixed=new LightningRecipe();fixed.orbitAnimation=mode;fixed.orbitDirection=direction;fixed.throttle=1;
            Map<Integer,Vec3> roots=new HashMap<>();
            for(double tick=0;tick<48;tick+=.35)for(var seg:new OrbitField(fixed).sample(Vec3.ZERO,tick)){
                if(seg.alongA()!=0)continue;
                Vec3 prior=roots.putIfAbsent(seg.strand(),seg.a());
                if(prior!=null&&!prior.equals(seg.a()))throw new AssertionError(mode+" root drift");
            }
            if(roots.size()!=fixed.strands)throw new AssertionError("unobserved roots");
        }
        var points=List.of(new Vec3(0,3,0),new Vec3(.2,2,0),new Vec3(-.1,1,.1),Vec3.ZERO);
        for(Vec3 cam:List.of(new Vec3(0,1,-5),new Vec3(5,1,0),new Vec3(0,10,0),Vec3.ZERO)){
            var joins=RibbonGeometry.joins(points,cam);
            if(joins.size()!=points.size())throw new AssertionError("missing shared join");
            for(Vec3 join:joins)if(!Double.isFinite(join.x)||!Double.isFinite(join.y)||!Double.isFinite(join.z)||join.length()>2.000001||join.length()<.999999)throw new AssertionError("invalid ribbon miter");
        }
        LightningRecipe r = new LightningRecipe().validate();
        OrbitField o = new OrbitField(r);
        var orbit0 = o.sample(new Vec3(0,0,0), 0);
        var orbit1 = o.sample(new Vec3(0,0,0), .35);
        if (orbit0.isEmpty() || orbit0.size() > r.strands * r.samples || orbit1.isEmpty()) throw new AssertionError("orbit pulse count");
        if (!Double.isFinite(orbit0.get(0).a().x) || !Double.isFinite(orbit0.get(0).a().y)) throw new AssertionError("orbit emitter invalid");
        if (orbit0.equals(orbit1)) throw new AssertionError("orbit pulse is static");
        r.orbitAnimation = "spektr";
        var source0 = new OrbitField(r).sample(new Vec3(0,0,0), 0);
        var source1 = new OrbitField(r).sample(new Vec3(0,0,0), .35);
        if (source0.isEmpty() || source1.isEmpty() || source0.equals(source1)) throw new AssertionError("spektr axial pulse");
        r.orbitAnimation = "ring";
        var ring = new OrbitField(r).sample(new Vec3(0,0,0), .35);
        if (ring.isEmpty()) throw new AssertionError("ring arc pulse");
        r.orbitAnimation = "grow";
        BoltGeometry b = BoltGeometry.generate(new Vec3(0,18,0), new Vec3(0,0,0), r);
        if (b.segments().isEmpty() || b.segments().size() > r.maxSegments) throw new AssertionError("bolt budget");
        if (!b.segments().get(0).a().equals(new Vec3(0,18,0))) throw new AssertionError("endpoint moved");
        BoltGeometry same = BoltGeometry.generate(new Vec3(0,18,0), new Vec3(0,0,0), r);
        if (!b.segments().equals(same.segments())) throw new AssertionError("seed is not deterministic");
        r.seed += 1;
        BoltGeometry different = BoltGeometry.generate(new Vec3(0,18,0), new Vec3(0,0,0), r);
        if (b.segments().equals(different.segments())) throw new AssertionError("seed does not vary shape");
        for (BoltGeometry.Segment s : b.segments()) if (!Double.isFinite(s.a().x) || !Double.isFinite(s.b().x)) throw new AssertionError("nonfinite");
        LightningEnvelope env = new LightningEnvelope(r);
        if (env.propagation(0) != 0 || env.propagation(99) != 1 || env.brightness(99) != 0) throw new AssertionError("envelope");
        r.mode = "rain";
        var rain = RainGeometry.generate(r);
        if (rain.size() != r.rainCount) throw new AssertionError("rain count");
        for (RainGeometry.Bolt rb : rain) for (BoltGeometry.Segment s : rb.geometry().segments()) if (s.depth() > r.rainMaxBranchDepth) throw new AssertionError("rain branch depth");
        if (RainGeometry.finished(rain,r.durationTicks)) throw new AssertionError("staggered last bolt cleared early");
        if (!RainGeometry.finished(rain,r.durationTicks+(r.rainCount-1)*r.rainStagger)) throw new AssertionError("rain never cleared");
        for(int seed=0;seed<16;seed++){
            LightningRecipe extreme=new LightningRecipe();extreme.seed=seed;extreme.strikeRoughness=.85;extreme.strikeDisplacement=12;extreme.strikeGenerations=9;extreme.maxSegments=1600;
            var tree=BoltGeometry.generate(new Vec3(0,18,0),Vec3.ZERO,extreme);
            if(tree.segments().size()>extreme.maxSegments)throw new AssertionError("extreme budget");
            for(var seg:tree.segments())if(seg.depth()==0&&seg.b().y>=seg.a().y)throw new AssertionError("main channel folds back");
            extreme.branchProbability=1;extreme.rainBranchProbability=.12;extreme.rainMaxBranches=1;
            for(var rb:RainGeometry.generate(extreme))if(rb.geometry().segments().stream().filter(s->s.depth()>0).map(BoltGeometry.Segment::mask).distinct().count()>1)throw new AssertionError("rain hard branch cap");
        }
        for(String mode:List.of("grow","ring"))for(int direction:new int[]{1,-1})for(double speed:new double[]{.1,2,20,120})for(double pulse:new double[]{.5,6,20}){
            LightningRecipe speedRecipe=new LightningRecipe();speedRecipe.orbitAnimation=mode;speedRecipe.orbitDirection=direction;speedRecipe.orbitExtendSpeed=speed;speedRecipe.pulseInterval=pulse;speedRecipe.lengthRandomness=0;speedRecipe.orbitWobble=0;speedRecipe.throttle=1;
            OrbitField field=new OrbitField(speedRecipe);var timing=field.timing(speedRecipe.seed);
            double travel=mode.equals("ring")?Math.min(Math.PI*1.85*speedRecipe.orbitRadius,speedRecipe.arcLength):Math.min(speedRecipe.rodLength*.95,speedRecipe.arcLength);
            double start=timing.interval()*(2-LightningMath.unit(speedRecipe.seed,18)),age=timing.onset()+travel*20/speed*.25,dt=Math.min(1,travel*20/speed*.1);
            var first=field.sample(Vec3.ZERO,start+age).stream().filter(s->s.strand()==0).toList();
            var second=field.sample(Vec3.ZERO,start+age+dt).stream().filter(s->s.strand()==0).toList();
            Vec3 p=first.get(first.size()-1).b(),q=second.get(second.size()-1).b();
            double distance=mode.equals("grow")?Math.abs(q.x-p.x):Math.abs(Math.atan2(p.x*q.z-p.z*q.x,p.x*q.x+p.z*q.z))*speedRecipe.orbitRadius;
            if(Math.abs(distance/dt*20-speed)>1e-7||!first.get(0).a().equals(second.get(0).a()))throw new AssertionError("physical speed/root");
            var full=field.sample(Vec3.ZERO,start+timing.onset()+travel*20/speed+.000001).stream().filter(s->s.strand()==0).toList();
            if(full.size()!=speedRecipe.samples||full.get(full.size()-1).alongB()!=1)throw new AssertionError("slow extension cut off");
        }
        LightningRecipe cluster=new LightningRecipe();cluster.mode="rain";cluster.rainStyle="cluster";cluster.rainCount=9;cluster.rainSpread=3;cluster.rainDepthSpread=2.4;cluster.rainMaxBranchDepth=0;cluster.rainMaxBranches=0;cluster.impactDuration=40;
        var bundle=RainGeometry.generate(cluster);var replay=RainGeometry.generate(cluster);
        for(int i=0;i<bundle.size();i++){
            var rb=bundle.get(i);Vec3 hit=rb.impact().center();
            if(Math.pow(hit.x/(cluster.rainSpread*.5),2)+Math.pow(hit.z/(cluster.rainDepthSpread*.5),2)>1.000001)throw new AssertionError("cluster range");
            if(!rb.geometry().segments().equals(replay.get(i).geometry().segments()))throw new AssertionError("cluster replay");
            if(!rb.impact().sample(cluster.progressionTicks-.001).isEmpty()||!rb.impact().sample(cluster.progressionTicks+cluster.impactDuration).isEmpty())throw new AssertionError("impact timing");
            var hitSegments=rb.impact().sample(cluster.progressionTicks+2);
            if(hitSegments.isEmpty()||hitSegments.size()>cluster.impactRays*8+48+cluster.impactSparks+2)throw new AssertionError("impact budget");
            for(var seg:hitSegments)if(!Double.isFinite(seg.a().x)||!Double.isFinite(seg.b().y))throw new AssertionError("impact finite");
        }
        if(RainGeometry.finished(bundle,cluster.durationTicks+(cluster.rainCount-1)*cluster.rainStagger))throw new AssertionError("impact tail cut off");
        if(!RainGeometry.finished(bundle,cluster.progressionTicks+cluster.impactDuration+(cluster.rainCount-1)*cluster.rainStagger))throw new AssertionError("impact never expires");
        var elevated=RainGeometry.generate(cluster,Vec3.ZERO,(start,end)->new Vec3(end.x,2,end.z));
        if(elevated.get(0).impact().center().y!=2)throw new AssertionError("impact not at resolved ground");
        for(int seed=0;seed<16;seed++){
            LightningRecipe noJitter=new LightningRecipe();noJitter.seed=seed;noJitter.branchJitter=0;noJitter.branchProbability=1;noJitter.maxBranchDepth=1;
            var first=BoltGeometry.generate(new Vec3(0,18,0),Vec3.ZERO,noJitter);
            var repeat=BoltGeometry.generate(new Vec3(0,18,0),Vec3.ZERO,noJitter);
            if(!first.segments().equals(repeat.segments())||first.segments().stream().noneMatch(s->s.depth()>0))throw new AssertionError("zero-jitter branches are not stable");
            for(var segment:first.segments())if(!Double.isFinite(segment.a().x)||!Double.isFinite(segment.b().z))throw new AssertionError("nonfinite zero-jitter branch");
        }
        System.out.println("PASS: full skill field/default bridge, strict atomic import/export, preserved custom cluster, original appearance contract, projected subpixel coverage/energy and shared corners, supplied 51-block strike and zero-jitter replay; physical tip speed/root, geometry budget, ground impact and full tail lifetime");
    }

    private static final Gson GSON=new Gson();
    private static void checkRasterCoverage() {
        for(int[] viewport:List.of(new int[]{854,480},new int[]{1920,1080},new int[]{3840,2160}))
            for(double degrees:new double[]{40,70,110})for(double distance:new double[]{2,10,48,120,480})
                for(double physicalHalfWidth:new double[]{.001,.012,.0623,.076,.14,.4}) {
                    var projection=new Matrix4f().perspective((float)Math.toRadians(degrees),viewport[0]/(float)viewport[1],.05f,1024);
                    var center=new Vec3(0,0,-distance);
                    var side=new Vec3(physicalHalfWidth,0,0);
                    var covered=RibbonCoverage.cover(center,side,projection,viewport[0],viewport[1]);
                    double focal=viewport[1]/(2*Math.tan(Math.toRadians(degrees)*.5));
                    double nativePixels=physicalHalfWidth*focal/distance;
                    double renderedPixels=covered.side().x*focal/distance;
                    if(nativePixels<RibbonCoverage.MIN_HALF_PIXELS-1e-5) {
                        if(Math.abs(renderedPixels-RibbonCoverage.MIN_HALF_PIXELS)>2e-5||covered.alphaScale()<=0||covered.alphaScale()>1)throw new AssertionError("projected minimum coverage");
                        if(Math.abs(renderedPixels*covered.alphaScale()-nativePixels)>2e-5)throw new AssertionError("coverage changed integrated energy");
                    } else if(!covered.side().equals(side)||covered.alphaScale()!=1)throw new AssertionError("normal near width was changed");
                }
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),854f/480,.05f,1024);
        var points=List.of(new Vec3(-.8,-1.5,-48),new Vec3(0,0,-48),new Vec3(1.2,1.2,-48),new Vec3(.8,2.5,-48));
        var joins=RibbonGeometry.joins(points,Vec3.ZERO);
        double focal=480/(2*Math.tan(Math.toRadians(70)*.5));
        for(int i=0;i<points.size();i++) {
            Vec3 previous=i==0?null:points.get(i-1),next=i+1==points.size()?null:points.get(i+1);
            var covered=RibbonCoverage.cover(points.get(i),joins.get(i).scale(.006),previous,next,projection,854,480);
            for(Vec3 neighbor:new Vec3[]{previous,next}) {
                if(neighbor==null)continue;
                Vec3 tangent=neighbor.subtract(points.get(i));
                Vec3 p=points.get(i),s=covered.side();
                // Independent pinhole oracle, including the miter's depth component.
                double x=focal*((p.x+s.x)/(48-s.z)-(p.x-s.x)/(48+s.z))*.5;
                double y=focal*((p.y+s.y)/(48-s.z)-(p.y-s.y)/(48+s.z))*.5;
                double normalHalfPixels=Math.abs(x*tangent.y-y*tangent.x)/Math.hypot(tangent.x,tangent.y);
                if(normalHalfPixels<RibbonCoverage.MIN_HALF_PIXELS-2e-4)throw new AssertionError("shared corner loses adjacent segment coverage");
            }
        }
        Vec3 side=new Vec3(.01,0,0);
        for(Vec3 center:List.of(new Vec3(0,0,.01),new Vec3(0,0,0))) {
            var clipped=RibbonCoverage.cover(center,side,projection,854,480);
            if(!clipped.side().equals(side)||clipped.alphaScale()!=1)throw new AssertionError("near/behind camera expansion");
        }
        Vec3 facing=new Vec3(-.89432,0,-.44743);
        for(double[] sample:new double[][]{{.04997,.00005},{.05002,.00005},{.05002,.000005},{1024,.05},{1024.1,.05}}) {
            Vec3 center=new Vec3(sample[0]>1000?512:.025,0,-sample[0]),nativeSide=facing.scale(sample[1]);
            var clipped=RibbonCoverage.cover(center,nativeSide,projection,854,480);
            if(!clipped.side().equals(nativeSide)||clipped.alphaScale()!=1)throw new AssertionError("coverage crossed original/expanded near or far depth clipping: "+sample[0]);
        }
        var safeNear=RibbonCoverage.cover(new Vec3(.025,0,-.0505),facing.scale(.000005),projection,854,480);
        if(safeNear.alphaScale()>=1||safeNear.renderedHalfPixels()<RibbonCoverage.MIN_HALF_PIXELS-1e-4)throw new AssertionError("valid near-plane coverage disabled");
        var invalid=RibbonCoverage.cover(new Vec3(0,0,-48),side,projection,0,480);
        if(!invalid.side().equals(side)||invalid.alphaScale()!=1)throw new AssertionError("invalid viewport expansion");
        var orthographic=new Matrix4f().ortho(-4,4,-3,3,.05f,1024);
        var covered=RibbonCoverage.cover(new Vec3(0,0,-48),new Vec3(.003,0,0),orthographic,800,600);
        if(Math.abs(covered.side().x*100-RibbonCoverage.MIN_HALF_PIXELS)>1e-6||Math.abs(covered.alphaScale()-.4)>1e-6)throw new AssertionError("actual orthographic viewport coverage");
        var tiny=RibbonCoverage.cover(new Vec3(0,0,-480),new Vec3(.001,0,0),projection,854,480);
        if(tiny.alphaScale()>=1/255.0)throw new AssertionError("quantization regression sample is not thin enough");
        float envelope=.8f,ratio=(float)tiny.alphaScale();
        if((int)(envelope*ratio*255)!=0)throw new AssertionError("old alpha-byte transport would not reproduce this failure");
        var builder=new BufferBuilder(256);
        builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_TEX);
        for(int i=0;i<4;i++)RibbonCoverage.emit(builder,new Matrix4f(),new Vec3(i&1,i>>1,0),envelope,ratio,i&1,new float[]{1,1,1});
        var packed=builder.end();
        try {
            var bytes=packed.vertexBuffer().order(ByteOrder.nativeOrder());
            if(DefaultVertexFormat.POSITION_COLOR_TEX.getVertexSize()!=24)throw new AssertionError("unexpected POSITION_COLOR_TEX format");
            for(int i=0;i<4;i++) {
                int alpha=Byte.toUnsignedInt(bytes.get(i*24+15));float coverage=bytes.getFloat(i*24+16);
                if(alpha!=(int)(envelope*255)||coverage!=ratio||coverage<=0||alpha/255f*coverage<=0)throw new AssertionError("float coverage was lost in actual vertex packing");
            }
        } finally {packed.release();}
    }
    private static JsonObject resource(String name) {
        try(var stream=LightningCheck.class.getResourceAsStream("/assets/vfxworkbench/lightning/"+name)) {
            if(stream==null)throw new AssertionError("missing recipe resource: "+name);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException ex){throw new AssertionError(ex);}
    }
    private static void sameRecipe(LightningRecipe expected,LightningRecipe actual,String label){
        if(!GSON.toJsonTree(expected).equals(GSON.toJsonTree(actual)))throw new AssertionError(label+"\nexpected="+GSON.toJson(expected)+"\nactual="+GSON.toJson(actual));
    }
    private static void checkGradientRatioContract(LightningRecipe supplied) {
        var live=ParamRegistry.state(LightningSkillSchema.ID);
        var spec=live.schema.get("gradientRatio");
        if(spec==null||spec.type!=ParamType.FLOAT||spec.min!=0||spec.max!=100||spec.step!=1||spec.intStep
            ||spec.reload!=ReloadMode.HOT||!spec.group.equals("颜色与亮度")||!spec.label.contains("%")
            ||Double.parseDouble(spec.defaultValue)!=50)throw new AssertionError("gradient ratio must be a hot 0..100 percent slider with a 50 percent default");
        live.setBool("gradientEnabled",false);
        if(spec.isVisible(live))throw new AssertionError("gradient ratio is visible with gradient disabled");
        live.setBool("gradientEnabled",true);
        if(!spec.isVisible(live))throw new AssertionError("gradient ratio is hidden with gradient enabled");
        var legacy=resource("recipe.json");
        if(legacy.size()!=60||legacy.has("gradientRatio"))throw new AssertionError("original 60-field recipe resource was rewritten");
        var legacyParsed=LightningRecipe.parse(legacy);
        if(legacyParsed.gradientRatio!=50||new LightningRecipe().gradientRatio!=50)throw new AssertionError("legacy recipe omitted ratio must default to 50 percent");
        var originalFields=GSON.toJsonTree(legacyParsed).getAsJsonObject();originalFields.remove("gradientRatio");
        if(!originalFields.equals(legacy))throw new AssertionError("gradient extension changed an original legacy field");
        JsonObject oldWrapper=new JsonObject(),oldStrings=new JsonObject();
        for(var entry:legacy.entrySet())oldStrings.addProperty(entry.getKey(),entry.getValue().getAsString());
        oldWrapper.add("values",oldStrings);
        sameRecipe(legacyParsed,LightningRecipe.parse(oldWrapper),"legacy string wrapper without ratio remains compatible");
        for(double ratio:new double[]{0,50,75.5,100}) {
            var partial=new JsonObject();partial.addProperty("gradientRatio",ratio);
            if(LightningRecipe.parse(partial).gradientRatio!=ratio)throw new AssertionError("valid ratio endpoint or fractional value changed: "+ratio);
        }
        for(double ratio:new double[]{-.001,100.001,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            var invalid=new LightningRecipe();invalid.gradientRatio=ratio;
            boolean rejected=false;try{invalid.validate();}catch(IllegalArgumentException expected){rejected=true;}
            if(!rejected)throw new AssertionError("ratio validation accepted "+ratio);
        }
        final int[] hotChanges={0};live.onHotChange(()->hotChanges[0]++);
        String presetBefore=live.get("preset");
        live.setDouble("gradientRatio",75.5);
        if(hotChanges[0]!=1||live.getDouble("gradientRatio")!=75.5||!live.get("preset").equals(presetBefore))throw new AssertionError("ratio edit did not update immediately or changed the selected preset");
        var expected=LightningRecipe.parse(legacy);expected.gradientRatio=75.5;
        var typed=JsonParser.parseString(LightningSkillSchema.exportRecipe()).getAsJsonObject();
        if(typed.size()!=61||!typed.get("gradientRatio").getAsJsonPrimitive().isNumber()||typed.get("gradientRatio").getAsDouble()!=75.5)throw new AssertionError("extended export must retain all 60 original fields plus a typed ratio");
        sameRecipe(expected,LightningRecipe.parse(typed),"new ratio typed export round trip");
        LightningSkillSchema.importRecipe(typed);
        sameRecipe(expected,LightningSkillSchema.recipe(),"new ratio atomic import round trip");
        live.setBool("gradientEnabled",false);
        if(JsonParser.parseString(LightningSkillSchema.exportRecipe()).getAsJsonObject().get("gradientRatio").getAsDouble()!=75.5)throw new AssertionError("hiding the slider discarded its value");
        live.setBool("gradientEnabled",true);
        for(String bad:List.of("{\"coreColor\":\"#123456\",\"gradientRatio\":-0.001}","{\"coreColor\":\"#123456\",\"gradientRatio\":100.001}","{\"gradientRatio\":null}","{\"gradientRatio\":true}","{\"gradientRatio\":{}}","{\"gradientRatio\":[]}","{\"gradientRatio\":\"NaN\"}","{\"gradientRatio\":\"Infinity\"}","{\"gradientRatio\":1e309}")) {
            var before=live.snapshot();boolean rejected=false;
            try{LightningSkillSchema.importRecipe(bad);}catch(RuntimeException failure){rejected=true;}
            if(!rejected||!before.equals(live.snapshot()))throw new AssertionError("invalid ratio import mutated live state: "+bad);
        }
        try {
            var file=Files.createTempFile("lightning-gradient-ratio-",".json");
            try {
                Files.writeString(file,LightningSkillSchema.exportRecipe());
                LightningSkillSchema.importRecipe(legacy);
                sameRecipe(supplied,LightningSkillSchema.recipe(),"old JSON import resets missing ratio to 50 without changing legacy fields");
                LightningSkillSchema.importRecipe(file);
                sameRecipe(expected,LightningSkillSchema.recipe(),"new ratio save and file import round trip");
                var before=live.snapshot();Files.writeString(file,"{\"gradientRatio\":101,\"coreColor\":\"#123456\"}");
                boolean rejected=false;try{LightningSkillSchema.importRecipe(file);}catch(IllegalArgumentException failure){rejected=true;}
                if(!rejected||!before.equals(live.snapshot()))throw new AssertionError("invalid ratio file mutated live state");
            } finally {Files.deleteIfExists(file);}
        }catch(java.io.IOException failure){throw new AssertionError("gradient ratio save/import integration",failure);}
        LightningSkillSchema.importRecipe(GSON.toJson(supplied));
        sameRecipe(supplied,LightningSkillSchema.recipe(),"gradient ratio checks restore the supplied baseline");
        System.out.println("PASS: original 60-field recipes default to 50 percent; hot percent slider, strict atomic ratio validation and extended 61-field save/export round trips");
    }
    private static void checkContract() {
        LightningSkillSchema.register();
        var live=ParamRegistry.state(LightningSkillSchema.ID);live.resetToDefaults();
        var supplied=LightningRecipe.parse(resource("recipe.json"));
        sameRecipe(supplied,LightningSkillSchema.recipe(),"complete user recipe defaults");
        if(!supplied.mode.equals("strike")||supplied.seed!=-1468807610L||supplied.strikeHeight!=51||supplied.strikeDisplacement!=9.4||supplied.strikeGenerations!=8||supplied.maxBranchDepth!=1||supplied.maxSegments!=1200||supplied.strikeWidth!=.038||!supplied.gradientEnabled)throw new AssertionError("supplied single-strike defaults changed");
        for(var field:LightningRecipe.class.getFields()){
            if(field.getName().equals("schema"))continue;
            if(!live.schema.contains(field.getName()))throw new AssertionError("missing skill field: "+field.getName());
        }
        if(!live.schema.get("coreColor").isVisible(live)||!live.schema.get("glowColor").isVisible(live))throw new AssertionError("gradient base tints must stay editable");
        if(live.schema.get("glowWidth").min!=.01||live.schema.get("strikeHeight").max!=128||live.schema.get("strikeDisplacement").max!=12)throw new AssertionError("skill ranges changed");
        var exported=JsonParser.parseString(LightningSkillSchema.exportRecipe()).getAsJsonObject();
        if(exported.has("values")||exported.has("preset")||!exported.get("schema").getAsJsonPrimitive().isNumber()||!exported.get("gradientEnabled").getAsJsonPrimitive().isBoolean())throw new AssertionError("export must be typed raw skill JSON");
        if(exported.size()!=61||!exported.get("gradientRatio").getAsJsonPrimitive().isNumber()||exported.get("gradientRatio").getAsDouble()!=50)throw new AssertionError("default export must add only a typed 50 percent gradient ratio");
        sameRecipe(supplied,LightningRecipe.parse(exported),"typed round trip");
        for(String preset:live.schema.get("preset").enumValues){
            if(preset.equals("custom"))continue;
            live.set("preset",preset);
            sameRecipe(LightningRecipe.parse(resource("presets/"+preset+".json")),LightningSkillSchema.recipe(),"complete preset: "+preset);
        }
        live.set("preset","strike");live.set("coreColor","#FF3300");
        if(!live.get("preset").equals("strike"))throw new AssertionError("color edit changed selection and would restart the active event");
        LightningRecipe cluster=LightningRecipe.parse(exported);
        cluster.mode="rain";cluster.rainStyle="cluster";cluster.rainCount=7;cluster.rainSpread=8.4;cluster.rainDepthSpread=1.7;cluster.rainStagger=.9;cluster.rainMaxBranchDepth=1;cluster.rainMaxBranches=2;cluster.rainBranchProbability=.11;cluster.objectRadius=3.7;cluster.gradientRatio=72.5;
        LightningSkillSchema.importRecipe(GSON.toJson(cluster));
        sameRecipe(cluster,LightningSkillSchema.recipe(),"custom cluster import must suppress baselines");
        live.setDouble("glowGain",1.6);cluster.glowGain=1.6;
        sameRecipe(cluster,LightningSkillSchema.recipe(),"cluster import must sync listener selection");
        JsonObject wrapper=new JsonObject(),strings=new JsonObject();
        for(var entry:GSON.toJsonTree(cluster).getAsJsonObject().entrySet())strings.addProperty(entry.getKey(),entry.getValue().getAsString());
        strings.addProperty("preset","cluster");wrapper.addProperty("preset","last");wrapper.add("values",strings);
        LightningSkillSchema.importRecipe(wrapper);
        sameRecipe(cluster,LightningSkillSchema.recipe(),"old MOD string wrapper compatibility");
        for(String bad:List.of("[]","null","{\"unknown\":1}","{\"strikeHeight\":129}","{\"strikeDisplacement\":12.1}","{\"strands\":1.5}","{\"seed\":2147483648}","{\"seed\":1.000000000000000001}","{\"durationTicks\":1}","{\"schema\":2}","{\"gradientEnabled\":1}","{\"glowGain\":true}","{\"coreColor\":1}","{\"branchDecay\":\"NaN\"}","{\"strikeHeight\":{}}","{\"values\":[]}\n")){
            var before=live.snapshot();boolean rejected=false;
            try{LightningSkillSchema.importRecipe(bad);}catch(RuntimeException expected){rejected=true;}
            if(!rejected||!before.equals(live.snapshot()))throw new AssertionError("invalid import mutated/clamped: "+bad);
        }
        try {
            var directory=Files.createTempDirectory("lightning-file-check-");
            var file=LightningSkillSchema.skillRecipeFile(directory);Files.createDirectories(file.getParent());
            try {
                Files.writeString(file,GSON.toJson(cluster));
                LightningSkillSchema.importRecipe(file);
                sameRecipe(cluster,LightningSkillSchema.recipe(),"skill config file import");
                var before=live.snapshot();Files.writeString(file,"{\"strikeHeight\":129}");
                boolean rejected=false;try{LightningSkillSchema.importRecipe(file);}catch(IllegalArgumentException expected){rejected=true;}
                if(!rejected||!before.equals(live.snapshot()))throw new AssertionError("invalid file changed current GUI state");
                Files.delete(file);rejected=false;
                try{LightningSkillSchema.importRecipe(file);}catch(java.io.IOException expected){rejected=true;}
                if(!rejected||!before.equals(live.snapshot()))throw new AssertionError("missing file changed current GUI state");
            } finally {
                Files.deleteIfExists(file);Files.deleteIfExists(file.getParent());Files.deleteIfExists(file.getParent().getParent());Files.deleteIfExists(directory);
            }
        }catch(java.io.IOException ex){throw new AssertionError("recipe file integration",ex);}
        LightningRecipe baseline=new LightningRecipe();baseline.mode="strike";baseline.strikeHeight=51;
        LightningSkillSchema.importRecipe("{\"mode\":\"strike\",\"strikeHeight\":51}");
        sameRecipe(baseline,LightningSkillSchema.recipe(),"partial skill recipes merge original skill baseline");
        LightningSkillSchema.importRecipe(exported);
        sameRecipe(supplied,LightningSkillSchema.recipe(),"full recipe restoration");
        checkGradientRatioContract(supplied);
        double scale=LightningAppearance.ribbonScale(supplied,true,supplied.strikeWidth);
        if(Math.abs(scale-.07/.038)>1e-12||scale>=2.6||LightningAppearance.ribbonScale(supplied,false,supplied.strikeWidth)!=2)throw new AssertionError("halo width is silently forced");
        var redStart=LightningAppearance.colorAt(supplied,false,0);
        var redEnd=LightningAppearance.colorAt(supplied,false,1);
        if(redStart[0]!=212/255f||redStart[1]!=0||redStart[2]!=0||redEnd[0]!=1/255f||redEnd[1]!=0||redEnd[2]!=0)throw new AssertionError("original skill gradient must multiply red core tint");
        var cyanEnd=LightningAppearance.colorAt(supplied,true,1);
        if(cyanEnd[0]!=0||cyanEnd[1]!=164/255f||cyanEnd[2]!=254/255f)throw new AssertionError("glow uses its independent base tint");
        var tall=BoltGeometry.generate(new Vec3(0,51,0),Vec3.ZERO,supplied);
        if(tall.segments().size()>1200||tall.segments().isEmpty()||!tall.segments().get(0).a().equals(new Vec3(0,51,0)))throw new AssertionError("51-block strike start/budget");
        if(tall.segments().stream().anyMatch(s->s.depth()>1)||tall.segments().stream().noneMatch(s->s.depth()==0&&s.b().equals(Vec3.ZERO)))throw new AssertionError("supplied strike endpoint/depth");
        if(!tall.segments().equals(BoltGeometry.generate(new Vec3(0,51,0),Vec3.ZERO,supplied).segments()))throw new AssertionError("supplied strike not replayable");
        var envelope=new LightningEnvelope(supplied);
        if(envelope.propagation(1.199)>=1||envelope.propagation(1.2)!=1||!envelope.finished(14))throw new AssertionError("supplied strike timing changed");
        live.resetToDefaults();
        sameRecipe(supplied,LightningSkillSchema.recipe(),"reset returns supplied MOD defaults");
    }
}
