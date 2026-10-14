package dev.worldvolume;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Explicit opt-in integration acceptance; creates only a fresh isolated save. */
@Mod.EventBusSubscriber(modid=WorldVolume.ID,value=Dist.CLIENT)
public final class VolumeValidation {
    static final boolean AUTO=Boolean.getBoolean("worldvolume.autorun");
    static final Path OUT=Path.of(System.getProperty("worldvolume.output","../evidence/forge/runtime"));
    static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    static final String[] CASES={"off","volume","emission","flash-medium","flash-surface","behind-wall","sphere-visible","annulus","reload","resize","end-raw","end-cavity","end-torus","end-twist-off","end-time8","end-void","end-near-clear","end-void-low","purple-time8","purple-off","purple-glow"};
    static final JsonArray results=new JsonArray();
    static final ArrayDeque<Integer> queries=new ArrayDeque<>();
    static final List<Double> gpu=new ArrayList<>();
    static boolean started,configured,finished,captured;
    static int titleTicks,index=-1,ticks,frameDraws;
    static String worldName;
    static JsonObject record;
    static CompletableFuture<Void> reload;
    static final Map<String,int[]> images=new HashMap<>();

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!AUTO||finished||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try{
            if(!started){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null||++titleTicks<20)return;
                started=true;Files.createDirectories(OUT);worldName="WORLD_VOLUME_LAB_"+System.currentTimeMillis();
                GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel(worldName,new LevelSettings(worldName,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(20261006L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
            }
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
            if(!configured){
                configured=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.fovEffectScale().set(0.0);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.options.cloudStatus().set(CloudStatus.OFF);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(60);mc.options.fov().set(70);mc.setScreen(null);
                var server=mc.getSingleplayerServer();server.execute(()->{var level=server.overworld();level.setDayTime(6000);for(BlockPos pos:BlockPos.betweenClosed(-6,4,16,6,18,16))level.setBlockAndUpdate(pos,Blocks.GOLD_BLOCK.defaultBlockState());server.getPlayerList().getPlayers().forEach(player->{player.getAbilities().mayfly=true;player.getAbilities().flying=true;player.onUpdateAbilities();});});
                strictRecipeChecks();next(mc);return;
            }
            mc.mouseHandler.releaseMouse();mc.player.setYRot(0);mc.player.setXRot(0);mc.player.yRotO=0;mc.player.xRotO=0;mc.player.setDeltaMovement(0,0,0);mc.player.getAbilities().flying=true;
            if(reload!=null&&!reload.isDone())return;
            if(captured){next(mc);return;}
            if(++ticks>2400)throw new IllegalStateException("Scenario timeout: "+CASES[index]+" reason="+VolumeRender.reason);
        }catch(Exception e){fail(e);}
    }
    static void expectReject(String label,String source,boolean upgrade,JsonObject report){
        boolean rejected=false;try{if(upgrade)VolumeRecipe.upgradeV1(new java.io.StringReader(source));else VolumeRecipe.read(new java.io.StringReader(source));}catch(IllegalArgumentException expected){rejected=true;}
        report.addProperty(label,rejected);if(!rejected)throw new IllegalStateException("Strict path accepted malformed recipe: "+label);
    }
    static void strictRecipeChecks(){
        String[] tests={"unknown","missing","fractional-integer","out-of-range","zero-sun","annular-hole","duration","model-range","new-field-missing","unknown-envelope","boolean-number","long-name"};
        JsonObject report=new JsonObject();
        for(String name:tests){JsonObject bad=VolumeRender.defaults.json();JsonObject p=bad.getAsJsonObject("parameters");
            switch(name){case "unknown"->p.addProperty("oops",1);case "missing"->p.remove("density");case "fractional-integer"->p.addProperty("viewSteps",8.5);case "out-of-range"->p.addProperty("coverage",1.01);case "zero-sun"->{p.addProperty("sunX",0);p.addProperty("sunY",0);p.addProperty("sunZ",0);}case "annular-hole"->{p.addProperty("shape",2);p.addProperty("innerRadius",181);}case "duration"->{p.addProperty("flashDuration",4);p.addProperty("flashPeriod",1);}case "model-range"->p.addProperty("densityModel",3);case "new-field-missing"->p.remove("voidFloor");case "unknown-envelope"->bad.addProperty("oops",1);case "boolean-number"->p.addProperty("density",true);case "long-name"->bad.addProperty("name","x".repeat(121));}
            expectReject(name,bad.toString(),false,report);
        }
        JsonObject unicodeName=VolumeRender.defaults.json();unicodeName.addProperty("name","🚀".repeat(120));VolumeRecipe.read(new java.io.StringReader(unicodeName.toString()));unicodeName.addProperty("name","🚀".repeat(121));expectReject("long-unicode-name",unicodeName.toString(),false,report);
        String valid=VolumeRender.defaults.json().toString();
        expectReject("duplicate-envelope",valid.substring(0,valid.length()-1)+",\"schema\":\"world-volume/2\"}",false,report);
        expectReject("duplicate-parameter",valid.replace("\"parameters\":{","\"parameters\":{\"density\":1,"),false,report);
        expectReject("nonfinite-json",valid.replace("\"density\":0.65","\"density\":NaN"),false,report);
        expectReject("comments-json","/*comment*/"+valid,false,report);
        expectReject("trailing-json",valid+"{}",false,report);
        JsonObject legacy=VolumeRender.defaults.json();legacy.addProperty("schema","world-volume/1");JsonObject lp=legacy.getAsJsonObject("parameters");
        for(String key:new ArrayList<>(lp.keySet()))if(!VolumeRecipe.schemaV1.getAsJsonObject("fields").has(key))lp.remove(key);
        lp.addProperty("centerX",12345);lp.addProperty("centerY",73.125);lp.addProperty("density",0.87);
        expectReject("legacy-requires-explicit-upgrade",legacy.toString(),false,report);
        for(String kind:new String[]{"missing","unknown","fractional-integer","duplicate","duration"}){
            JsonObject bad=legacy.deepCopy();JsonObject values=bad.getAsJsonObject("parameters");
            switch(kind){case "missing"->values.remove("density");case "unknown"->values.addProperty("densityModel",1);case "fractional-integer"->values.addProperty("shape",0.0001);case "duration"->{values.addProperty("flashDuration",4);values.addProperty("flashPeriod",1);}}
            String text=kind.equals("duplicate")?bad.toString().replace("\"parameters\":{","\"parameters\":{\"density\":1,"):bad.toString();
            expectReject("legacy-"+kind,text,true,report);
        }
        VolumeRecipe upgraded=VolumeRecipe.upgradeV1(new java.io.StringReader(legacy.toString()));boolean preserved=true,defaults=true;int oldFields=0,newFields=0;
        for(String key:VolumeRecipe.schema.getAsJsonObject("fields").keySet()){
            if(lp.has(key)){oldFields++;preserved &= upgraded.value(key)==lp.get(key).getAsDouble();}
            else{newFields++;defaults &= upgraded.value(key)==VolumeRecipe.schema.getAsJsonObject("fields").getAsJsonObject(key).get("default").getAsDouble();}
        }
        if(!preserved||!defaults||oldFields!=50||newFields!=12||upgraded.value("densityModel")!=0)throw new IllegalStateException("Legacy migration lost values or defaults");
        JsonObject migration=new JsonObject();migration.addProperty("preservedOldFields",oldFields);migration.addProperty("addedDefaultFields",newFields);migration.addProperty("preservedAllOldValues",preserved);migration.addProperty("newDefaultsMatchSchema",defaults);migration.addProperty("densityModel",upgraded.value("densityModel"));migration.add("source",legacy);migration.add("upgraded",upgraded.json());
        try{Files.writeString(OUT.resolve("strict-recipe-checks.json"),JSON.toJson(report));Files.writeString(OUT.resolve("legacy-migration.json"),JSON.toJson(migration));}catch(Exception e){throw new RuntimeException(e);}
    }
    static void next(Minecraft mc){
        index++;if(index>=CASES.length){finish(mc);return;}
        ticks=frameDraws=0;captured=false;gpu.clear();while(!queries.isEmpty())GL15.glDeleteQueries(queries.remove());reload=null;String name=CASES[index];
        Map<String,Number> edits=new HashMap<>();
        edits.put("centerX",0);edits.put("centerY",12);edits.put("centerZ",0);edits.put("radius",64);edits.put("bottom",3);edits.put("height",28);edits.put("shape",0);edits.put("density",0.75);edits.put("coverage",0.95);edits.put("scale",0.03);edits.put("erosion",0.1);edits.put("extinction",0.045);edits.put("ambient",0.2);edits.put("sunGain",0.5);edits.put("emission",0);edits.put("flashEnabled",0);edits.put("viewSteps",32);edits.put("lightSteps",3);edits.put("surfaceGain",0);edits.put("mediumGain",0);edits.put("speed",0);edits.put("flashPeriod",1);edits.put("flashDuration",1);edits.put("flashStrength",3);edits.put("flashRadius",100);edits.put("flashPlacement",0);edits.put("densityModel",0);
        switch(name){
            case "off"->edits.put("density",0);
            case "emission"->{edits.put("emission",3);edits.put("extinction",0);edits.put("ambient",0);edits.put("sunGain",0);edits.put("emissionThreshold",0.15);}
            case "flash-medium"->{edits.put("flashEnabled",1);edits.put("mediumGain",1);edits.put("ambient",0);edits.put("sunGain",0);}
            case "flash-surface"->{edits.put("flashEnabled",1);edits.put("surfaceGain",0.25);edits.put("density",0);}
            case "sphere-visible"->{edits.put("shape",1);edits.put("centerY",10);edits.put("centerZ",10);edits.put("radius",8);edits.put("emission",3);edits.put("emissionThreshold",0.15);edits.put("extinction",0.0);}
            case "annulus"->{edits.put("shape",2);edits.put("innerRadius",20);}
            case "behind-wall"->{edits.put("shape",1);edits.put("centerY",10);edits.put("centerZ",32);edits.put("radius",8);edits.put("emission",3);edits.put("emissionThreshold",0.15);edits.put("extinction",0.0);}
        }
        if(name.startsWith("end-")||name.startsWith("purple-")){
            edits.put("densityModel",1);edits.put("verticalStretch",24);edits.put("twistAmount",0.05);edits.put("twistSpeed",0.02);edits.put("cavityRadius",0);edits.put("cavityAspect",0.8);edits.put("torusRadius",0);edits.put("torusWidth",0);edits.put("voidDensity",0);edits.put("voidFloor",30);edits.put("voidFalloff",0.7);edits.put("nearClearRadius",0);
            if(name.equals("end-cavity"))edits.put("cavityRadius",64);
            if(name.equals("end-torus")){edits.put("torusRadius",32);edits.put("torusWidth",12);}
            if(name.equals("end-twist-off"))edits.put("twistAmount",0);
            if(name.equals("end-void")||name.equals("end-near-clear")||name.equals("end-void-low")){
                edits.put("cavityRadius",64);edits.put("voidDensity",1);edits.put("extinction",0.08);edits.put("ambient",0.05);edits.put("sunGain",0);
                if(name.equals("end-near-clear"))edits.put("nearClearRadius",100);
                if(name.equals("end-void-low"))edits.put("voidFloor",-20);
            }
            if(name.startsWith("purple-")){
                edits.put("densityModel",2);edits.put("speed",2);edits.put("density",0.12);edits.put("coverage",0.85);edits.put("extinction",0.025);edits.put("ambient",0.6);edits.put("sunGain",0);edits.put("emissionThreshold",0.15);edits.put("emissionR",0.8);edits.put("emissionG",0.08);edits.put("emissionB",1);edits.put("emission",name.equals("purple-glow")?3:0);
            }
        }
        VolumeRender.recipe=VolumeRender.defaults.with(edits);VolumeRender.enabled=true;VolumeRender.failed=false;VolumeRender.setTime(name.equals("end-time8")||name.equals("purple-time8")?8.0f:0.5f);
        mc.player.setPos(0,8,0);mc.player.xo=0;mc.player.yo=8;mc.player.zo=0;var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);if(player!=null)player.connection.teleport(0,8,0,0,0);});
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),name.equals("resize")?800:640,name.equals("resize")?450:360);
        record=new JsonObject();record.addProperty("name",name);record.addProperty("generationBefore",VolumeRender.generation);record.add("recipe",VolumeRender.recipe.json());
        if(name.equals("reload"))reload=mc.reloadResourcePacks();
        System.out.println("[WORLDVOLUME] Scenario "+name);
    }
    static boolean wantCapture(){if(!AUTO||finished||index<0||captured)return false;frameDraws++;return ticks>=45&&frameDraws>=35&&(reload==null||reload.isDone());}
    static int beginGpu(){
        if(!AUTO||finished||index<0)return 0;
        while(!queries.isEmpty()&&GL15.glGetQueryObjecti(queries.peek(),GL15.GL_QUERY_RESULT_AVAILABLE)!=0){int q=queries.remove();gpu.add(GL33.glGetQueryObjectui64(q,GL15.GL_QUERY_RESULT)/1_000_000.0);GL15.glDeleteQueries(q);}
        if(queries.size()>8)return 0;int q=GL15.glGenQueries();GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,q);return q;
    }
    static void endGpu(int q){if(q!=0){GL15.glEndQuery(GL33.GL_TIME_ELAPSED);queries.add(q);}}
    static void drawBindings(int program,RenderTarget source,RenderTarget main){
        JsonObject binding=new JsonObject();int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        binding.addProperty("program",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));binding.addProperty("drawFbo",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
        for(String name:new String[]{"SceneSampler","DepthSampler"}){int loc=GL20.glGetUniformLocation(program,name);int unit=GL20.glGetUniformi(program,loc);com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13.GL_TEXTURE0+unit);binding.addProperty(name+"Unit",unit);binding.addProperty(name+"BoundTexture",GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));}
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(active);record.add("drawBindings",binding);
    }
    static float[] probe(RenderTarget main){
        int framebuffer=0,texture=0;int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);var shader=VolumeRender.shader;
        try{
            texture=GL11.glGenTextures();com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13.GL_TEXTURE0);com.mojang.blaze3d.platform.GlStateManager._bindTexture(texture);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,1,1,0,GL11.GL_RGBA,GL11.GL_FLOAT,(java.nio.ByteBuffer)null);
            framebuffer=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Probe FBO incomplete");
            com.mojang.blaze3d.systems.RenderSystem.viewport(0,0,1,1);com.mojang.blaze3d.systems.RenderSystem.setShader(()->shader);
            var b=com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();b.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION);b.vertex(-1,-1,0).endVertex();b.vertex(3,-1,0).endVertex();b.vertex(-1,3,0).endVertex();shader.apply();int mode=GL20.glGetUniformLocation(shader.getId(),"ValidationProbe");if(mode<0)throw new IllegalStateException("Probe uniform missing");GL20.glUniform1i(mode,1);com.mojang.blaze3d.vertex.BufferUploader.draw(b.end());var data=org.lwjgl.BufferUtils.createFloatBuffer(4);GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_FLOAT,data);float[] result=new float[4];data.get(result);return result;
        }finally{
            int mode=GL20.glGetUniformLocation(shader.getId(),"ValidationProbe");if(mode>=0)GL20.glUniform1i(mode,0);shader.clear();GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,main.frameBufferId);com.mojang.blaze3d.systems.RenderSystem.viewport(0,0,main.width,main.height);if(framebuffer!=0)GL30.glDeleteFramebuffers(framebuffer);if(texture!=0)GL11.glDeleteTextures(texture);
        }
    }
    static void capture(RenderTarget source,RenderTarget main,Vec3 camera){
        try{
            String name=CASES[index];record.add("centerRayProbe",JSON.toJsonTree(probe(main)));record.addProperty("generationAfter",VolumeRender.generation);record.addProperty("draws",frameDraws);record.addProperty("width",main.width);record.addProperty("height",main.height);record.addProperty("Time",VolumeRender.actualTime);record.addProperty("program",VolumeRender.shader.getId());record.addProperty("sourceFbo",source.frameBufferId);record.addProperty("outputFbo",main.frameBufferId);record.addProperty("sceneTexture",source.getColorTextureId());record.addProperty("depthTexture",source.getDepthTextureId());record.addProperty("outputColorTexture",main.getColorTextureId());record.add("camera",JSON.toJsonTree(new double[]{camera.x,camera.y,camera.z}));record.add("inverseProjection",JSON.toJsonTree(VolumeRender.inverseProjection.get(new float[16])));record.add("inverseRotation",JSON.toJsonTree(VolumeRender.inverseRotation.get(new float[9])));
            JsonObject uniforms=new JsonObject();int program=VolumeRender.shader.getId();
            for(var e:VolumeRecipe.contract.getAsJsonObject("uniforms").entrySet()){
                String key=e.getKey();int location=GL20.glGetUniformLocation(program,key);JsonObject u=new JsonObject();u.addProperty("location",location);
                if(location>=0){int count=e.getValue().getAsJsonArray().size();if(key.endsWith("Steps")){int[] v=new int[count];GL20.glGetUniformiv(program,location,v);u.add("value",JSON.toJsonTree(v));}else{float[] v=new float[count];GL20.glGetUniformfv(program,location,v);u.add("value",JSON.toJsonTree(v));}}uniforms.add(key,u);
            }record.add("uploadedUniforms",uniforms);
            try(NativeImage image=Screenshot.takeScreenshot(source)){image.writeToFile(OUT.resolve(name+"-scene.png"));}
            try(NativeImage image=Screenshot.takeScreenshot(main)){image.writeToFile(OUT.resolve(name+"-post.png"));int[] pixels=new int[image.getWidth()*image.getHeight()];for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)pixels[y*image.getWidth()+x]=image.getPixelRGBA(x,y);images.put(name,pixels);if(name.equals("reload")&&images.containsKey("volume")){int changed=0;int[] base=images.get("volume");for(int i=0;i<base.length;i++)if(base[i]!=pixels[i])changed++;record.addProperty("changedPixelsVsVolume",changed);}}
            int count=main.width*main.height;var a=org.lwjgl.BufferUtils.createByteBuffer(count*4);var b=org.lwjgl.BufferUtils.createByteBuffer(count*4);var da=org.lwjgl.BufferUtils.createFloatBuffer(count);var db=org.lwjgl.BufferUtils.createFloatBuffer(count);int old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source.frameBufferId);GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,a);GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,da);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);GL11.glReadPixels(0,0,main.width,main.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,db);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);
            int changed=0,geometry=0,sky=0,geomChanged=0,skyChanged=0,centerChanged=0;float depthDelta=0;long absoluteRGB=0;
            for(int i=0;i<count;i++){boolean d=false;for(int c=0;c<3;c++){int av=Byte.toUnsignedInt(a.get(i*4+c)),bv=Byte.toUnsignedInt(b.get(i*4+c));if(av!=bv)d=true;absoluteRGB+=Math.abs(av-bv);}if(d)changed++;boolean hit=da.get(i)<0.999999f;if(hit){geometry++;if(d)geomChanged++;}else{sky++;if(d)skyChanged++;}int x=i%main.width,y=i/main.width;if(d&&x>main.width*0.4&&x<main.width*0.6&&y>main.height*0.4&&y<main.height*0.6)centerChanged++;depthDelta=Math.max(depthDelta,Math.abs(da.get(i)-db.get(i)));}
            record.addProperty("sumAbsoluteRGBDelta",absoluteRGB);record.addProperty("changedPixels",changed);record.addProperty("geometryPixels",geometry);record.addProperty("skyPixels",sky);record.addProperty("geometryChangedPixels",geomChanged);record.addProperty("skyChangedPixels",skyChanged);record.addProperty("centerChangedPixels",centerChanged);record.addProperty("maxDepthDifference",depthDelta);record.addProperty("glError",GL11.glGetError());
            List<Double> sorted=gpu.stream().sorted().toList();record.addProperty("gpuSamples",sorted.size());if(!sorted.isEmpty()){record.addProperty("gpuMedianMs",sorted.get(sorted.size()/2));record.addProperty("gpuP95Ms",sorted.get((int)((sorted.size()-1)*0.95)));}
        }catch(Exception e){fail(e);}
    }
    static void restored(boolean restored){if(!AUTO||finished||record==null)return;record.addProperty("stateRestored",restored);results.add(record);captured=true;try{save(false);}catch(Exception e){fail(e);}}
    static void save(boolean complete) throws Exception{JsonObject root=new JsonObject();root.addProperty("schema","world-volume-forge-validation/2");root.addProperty("recipeSchema","world-volume/2");root.addProperty("modVersion","0.2.0");root.addProperty("complete",complete);root.addProperty("world",worldName);root.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));root.addProperty("vendor",GL11.glGetString(GL11.GL_VENDOR));root.addProperty("glVersion",GL11.glGetString(GL11.GL_VERSION));root.addProperty("minecraft","1.20.1");root.addProperty("forge","47.4.22");root.addProperty("java",System.getProperty("java.version"));root.add("results",results);Files.writeString(OUT.resolve("results.json"),JSON.toJson(root));}
    static void finish(Minecraft mc){try{save(true);finished=true;System.out.println("[WORLDVOLUME] Validation complete "+OUT);if(Boolean.getBoolean("worldvolume.exitOnComplete"))mc.stop();}catch(Exception e){fail(e);}}
    static void fail(Exception e){if(!AUTO||finished)return;finished=true;e.printStackTrace();try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("failure.txt"),e.toString());save(false);}catch(Exception ignored){}if(Boolean.getBoolean("worldvolume.exitOnComplete"))Minecraft.getInstance().stop();}
}
