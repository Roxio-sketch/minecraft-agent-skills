package dev.cosmiclab;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in actual GPU experiments; creates a new isolated world, never opens a saved world. */
@Mod.EventBusSubscriber(modid=CosmicLab.ID, value=Dist.CLIENT)
public final class LabAdvancedValidation {
    private static final String SELECTION=System.getProperty("cosmiclab.advancedCases", "");
    private static final Path OUTPUT=Path.of(System.getProperty("cosmiclab.output", "validation-advanced"));
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> ALL=List.of(
        "physical-day","physical-sunset","physical-night","physical-multi-off","physical-multi-on",
        "physical-observer-high","physical-observer-outside","physical-density","physical-yaw","physical-reload",
        "transparent-clear","transparent-clear-fabulous","legacy-fog","native-fog-fancy","native-fog-fabulous",
        "native-fog-bob","native-fog-yaw45","native-fog-fov100","native-fog-resize",
        "legacy-rain","native-rain-fancy","native-rain-fabulous","native-snow-fancy","native-snow-fabulous",
        "weather-rain-time0","weather-rain-time2","weather-snow-time0","weather-snow-time2",
        "weather-roof-off","weather-roof-on","weather-biome-rain","weather-biome-snow","weather-biome-dry","weather-tiny-snow-half",
        "performance-low","performance-medium","performance-high","performance-half","performance-threequarter","performance-zero-full","performance-zero-half",
        "compat-off","compat-on","compat-reload","compat-off-again");
    private static final List<String> CELESTIAL=List.of(
        "giant-off","giant-gas-time0","giant-gas-time2","giant-ice-time0","giant-ice-time2",
        "giant-fragment-time0","giant-fragment-time2","giant-dark-time0","giant-dark-time2",
        "giant-binary-time0","giant-binary-time2","giant-flare-time0","giant-flare-time2",
        "giant-vortex-time0","giant-vortex-time2","giant-gas-yaw45","giant-gas-reload",
        "giant-gas-fov100","giant-gas-resize","giant-gas-backside","giant-gas-translation",
        "cinematic-lens-off","cinematic-lens-on","cinematic-lens-time2","cinematic-lens-yaw45","cinematic-lens-reload");
    private static final List<String> CASES=select();
    private static final JsonArray RESULTS=new JsonArray();
    private static JsonObject record;
    private static boolean started, configured, finished;
    private static int titleTicks, index=-1, ticks, frames;
    private static String world;
    private static CompletableFuture<Void> reload;
    private static float yaw,pitch;
    private static double cameraX,cameraY,cameraZ;
    private static int[] before;
    private static int[] counters(){return new int[]{LabAtmosphere.builds[0],LabAtmosphere.builds[1],LabAtmosphere.builds[2],LabEnvironment.earlyDraws,LabEnvironment.finalDraws,LabTranslucency.selections,LabTranslucency.vboDraws};}
    static JsonObject beginState(String pass){
        if(!active()||record==null||ticks<90)return null;
        if(!record.has("stateEvidence"))record.add("stateEvidence",new JsonObject());
        if(record.getAsJsonObject("stateEvidence").has(pass))return null;
        return glState();
    }
    static JsonObject beginSkyState(){
        return active() && index>=0 && CELESTIAL.contains(CASES.get(index)) ? beginState("sky") : null;
    }
    static void endState(String pass,JsonObject before){
        if(before==null||!active()||record==null)return;
        JsonObject evidence=new JsonObject();evidence.add("before",before);evidence.add("after",glState());
        record.getAsJsonObject("stateEvidence").add(pass,evidence);
    }
    private static JsonObject glState(){
        JsonObject out=new JsonObject();
        out.addProperty("drawFramebuffer",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));out.addProperty("readFramebuffer",GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));
        out.addProperty("program",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);out.addProperty("activeTexture",active);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);out.add("viewport",JSON.toJsonTree(viewport));
        out.addProperty("depthTest",GL11.glIsEnabled(GL11.GL_DEPTH_TEST));out.addProperty("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        out.addProperty("blend",GL11.glIsEnabled(GL11.GL_BLEND));out.addProperty("cull",GL11.glIsEnabled(GL11.GL_CULL_FACE));out.addProperty("scissor",GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
        out.add("blendFactors",JSON.toJsonTree(new int[]{GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)}));
        var mask=BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);boolean[] color=new boolean[4];for(int i=0;i<4;i++)color[i]=mask.get(i)!=0;out.add("colorMask",JSON.toJsonTree(color));
        int[] textures=new int[16],shaderTextures=new int[12];for(int i=0;i<16;i++){
            com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if(i<12)shaderTextures[i]=com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(i);
        }
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(active);out.add("textureBindings",JSON.toJsonTree(textures));out.add("shaderTextures",JSON.toJsonTree(shaderTextures));
        var shader=com.mojang.blaze3d.systems.RenderSystem.getShader();out.addProperty("renderSystemShader",shader==null?0:shader.getId());return out;
    }

    public static boolean active(){return !SELECTION.isEmpty()&&!finished;}
    private static List<String> select(){
        if(SELECTION.isEmpty())return List.of();
        if(SELECTION.equals("all"))return ALL.stream().filter(n->!n.startsWith("compat-")).toList();
        if(SELECTION.equals("celestial"))return CELESTIAL;
        Set<String> requested=new LinkedHashSet<>(Arrays.asList(SELECTION.split(",")));
        List<String> available=new ArrayList<>(ALL);available.addAll(CELESTIAL);
        if(!available.containsAll(requested))throw new IllegalArgumentException("Unknown advanced GPU cases "+SELECTION);
        return available.stream().filter(requested::contains).toList();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!active()||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try{
            if(!started){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null||++titleTicks<20)return;
                Files.createDirectories(OUTPUT);started=true;world="COSMIC_ADVANCED_"+System.currentTimeMillis();
                GameRules rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings(world,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(20261006L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
            }
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
            if(!configured){
                configured=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
                mc.options.bobView().set(false);mc.options.fovEffectScale().set(0d);mc.options.renderDistance().set(5);
                mc.options.simulationDistance().set(5);mc.options.cloudStatus().set(CloudStatus.FANCY);
                mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.options.fov().set(70);
                mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);
                var server=mc.getSingleplayerServer();server.execute(()->{
                    var level=server.overworld();level.setDayTime(6000);
                    for(BlockPos p:BlockPos.betweenClosed(-22,3,2,22,3,55))level.setBlockAndUpdate(p,Blocks.WHITE_CONCRETE.defaultBlockState());
                    for(BlockPos p:BlockPos.betweenClosed(-22,4,48,22,20,48))level.setBlockAndUpdate(p,Blocks.GOLD_BLOCK.defaultBlockState());
                    for(int z:new int[]{8,18,28})for(BlockPos p:BlockPos.betweenClosed(-18,4,z,18,18,z))level.setBlockAndUpdate(p,Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
                    for(BlockPos p:BlockPos.betweenClosed(10,4,9,17,10,24))level.setBlockAndUpdate(p,Blocks.WATER.defaultBlockState());
                    server.getPlayerList().getPlayers().forEach(p->{p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();});
                });next(mc);
            }
            if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;}
            hold(mc);if(++ticks>1800)throw new IllegalStateException("Advanced GPU case timeout "+CASES.get(index));
        }catch(Exception error){fail(error);}
    }
    private static void hold(Minecraft mc){
        mc.mouseHandler.releaseMouse();mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.yRotO=yaw;mc.player.xRotO=pitch;
        mc.player.setDeltaMovement(0,0,0);mc.player.setPos(cameraX,cameraY,cameraZ);mc.player.xo=cameraX;mc.player.yo=cameraY;mc.player.zo=cameraZ;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void frame(TickEvent.RenderTickEvent event){
        if(!active()||index<0||event.phase!=TickEvent.Phase.START||Minecraft.getInstance().player==null)return;
        if(CASES.get(index).equals("native-fog-bob")){
            var p=Minecraft.getInstance().player;p.walkDist=p.walkDistO=.35f;p.bob=p.oBob=.08f;
        }
    }
    private static void next(Minecraft mc)throws Exception{
        if(++index>=CASES.size()){finish(mc);return;}String name=CASES.get(index);
        System.out.println("[COSMICLAB_ADVANCED] Scenario "+name);ticks=frames=0;record=new JsonObject();
        record.addProperty("name",name);before=new int[]{LabAtmosphere.builds[0],LabAtmosphere.builds[1],LabAtmosphere.builds[2],LabEnvironment.earlyDraws,LabEnvironment.finalDraws,LabTranslucency.selections,LabTranslucency.vboDraws};
        record.add("lutBuildDraws",new JsonArray());
        record.add("countsBefore",JSON.toJsonTree(before));LabGpuTimings.clear();
        Map<String,Object> p=new LinkedHashMap<>();
        for(String key:new String[]{"galaxyStrength","nebulaStrength","auroraStrength","meteorRate","celestialStrength","blackHoleStrength","specialStrength","birdDensity","rainbowStrength","haloStrength","thinCloudStrength","volumeCloudStrength","fogStrength","cloudShadowStrength","lightShaftStrength","rainAmount","thunderAmount"})p.put(key,0);
        p.put("starDensity",0);p.put("atmosphereStrength",1);p.put("sunElevation",35);p.put("sunYaw",0);p.put("sunBrightness",1.3);p.put("cycleSpeed",0);
        p.put("atmosphereStyle",1);p.put("nativeTransparentMode",0);p.put("environmentRenderScale",1);p.put("precipitationMode",0);p.put("weatherFollowWorld",0);p.put("groundLevel",64);p.put("observerAltitude",.07);
        yaw=0;pitch=0;cameraX=cameraZ=0;cameraY=name.startsWith("physical-")||name.startsWith("performance-")||name.startsWith("compat-")?100:8;
        LabSky.enabled=true;LabSky.mode=2;LabSky.quality=name.endsWith("low")?1:name.endsWith("high")?3:2;
        LabSky.frozen=true;LabSky.timeOverride=name.endsWith("time2")?2:0;LabSky.actualTime=LabSky.timeOverride;
        if(name.startsWith("physical-")){
            p.put("atmosphereStyle",2);pitch=-25;
            if(name.equals("physical-sunset")){p.put("sunElevation",2);pitch=-5;}
            if(name.equals("physical-night"))p.put("sunElevation",-12);
            if(name.equals("physical-multi-off"))p.put("multipleScattering",0);
            if(name.equals("physical-observer-high"))p.put("observerAltitude",50);
            if(name.equals("physical-observer-outside")){p.put("observerAltitude",200);pitch=-10;}
            if(name.equals("physical-density"))p.put("mieDensity",3);
            if(name.equals("physical-yaw")){yaw=90;p.put("sunYaw",90);p.put("mieDensity",3);}
        }
        if(name.contains("fog")){p.put("fogStrength",1);p.put("fogDensity",.02);p.put("fogNoise",0);p.put("fogHeightFalloff",0);p.put("fogDistance",120);}
        if(name.startsWith("native-"))p.put("nativeTransparentMode",1);
        if(name.contains("rain")||name.contains("snow")||name.startsWith("weather-")||name.startsWith("compat-")){
            p.put("rainAmount",1);p.put("rainDensity",1.6);p.put("rainBrightness",1.5);p.put("precipitationMode",name.contains("snow")?3:2);
        }
        if(name.startsWith("weather-")){p.put("snowDensity",1.8);p.put("snowSize",.12);p.put("snowBrightness",1.2);yaw=180;}
        if(name.startsWith("weather-biome-")){p.put("precipitationMode",1);LabPrecipitation.ready=false;}
        if(name.equals("weather-roof-on"))p.put("precipitationRoof",1);
        if(name.equals("weather-tiny-snow-half")){p.put("snowSize",.015);p.put("environmentRenderScale",.5);}
        if(name.equals("native-fog-yaw45"))yaw=45;
        mc.options.bobView().set(name.equals("native-fog-bob"));mc.options.fov().set(name.endsWith("fov100")?100:70);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),name.endsWith("resize")?800:960,name.endsWith("resize")?600:540);
        if(name.startsWith("performance-")||name.startsWith("compat-")){
            pitch=-20;p.put("volumeCloudStrength",1);p.put("volumeCloudCoverage",.62);p.put("volumeCloudDensity",.055);
            p.put("volumeCloudBase",80);p.put("volumeCloudThickness",80);p.put("volumeCloudScale",.025);p.put("volumeCloudErosion",.75);p.put("volumeCloudDensity",.4);
            p.put("fogStrength",.2);p.put("fogDensity",.006);p.put("cloudShadowStrength",.7);p.put("lightShaftStrength",.6);
            if(name.endsWith("half"))p.put("environmentRenderScale",.5);
            if(name.endsWith("threequarter"))p.put("environmentRenderScale",.75);
            if(name.startsWith("performance-zero-")){p.put("volumeCloudDensity",0);p.put("fogDensity",0);p.put("cloudShadowStrength",0);p.put("lightShaftStrength",0);}
        }
        if(name.startsWith("compat-"))p.put("nativeTransparentMode",1);
        p.put("quality",LabSky.quality);LabSky.recipe=LabSky.defaults.with(p);
        if(CELESTIAL.contains(name))configureCelestial(name);
        mc.options.graphicsMode().set(name.endsWith("fabulous")?GraphicsStatus.FABULOUS:GraphicsStatus.FANCY);mc.levelRenderer.allChanged();
        hold(mc);record.add("recipe",LabSky.recipe.toJson());record.addProperty("graphics",mc.options.graphicsMode().get().toString());
        record.addProperty("generationBefore",LabSky.generation);
        var id=mc.player.getUUID();var server=mc.getSingleplayerServer();final double x=cameraX,y=cameraY,z=cameraZ;
        server.execute(()->{
            var level=server.overworld();var player=server.getPlayerList().getPlayer(id);if(player!=null)player.teleportTo(x,y,z);
            level.setWeatherParameters(10000,10000,false,false);
            for(BlockPos pos:BlockPos.betweenClosed(-20,12,-20,20,12,20))level.setBlockAndUpdate(pos,name.startsWith("weather-roof-")?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
            if(name.startsWith("weather-biome-")){
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"gamerule commandModificationBlockLimit 4000000");
                String biome=name.endsWith("snow")?"snowy_plains":name.endsWith("dry")?"desert":"plains";
                int result=server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"fillbiome -64 -64 -64 63 95 63 minecraft:"+biome);
                mc.execute(()->record.addProperty("fillBiomeResult",result));
            }
        });
        if(name.startsWith("compat-"))configureCompat(name);
        if(name.endsWith("reload"))reload=mc.reloadResourcePacks();
    }
    private static void configureCelestial(String name)throws Exception{
        String id=name.startsWith("cinematic-")?"cinematicblackhole":
            name.startsWith("giant-ice")?"icegiant":name.startsWith("giant-fragment")?"fragmentmoon":
            name.startsWith("giant-dark")?"glowingdarkdisk":name.startsWith("giant-binary")?"binarystars":
            name.startsWith("giant-flare")?"flaringstar":name.startsWith("giant-vortex")?"vortexdarkstar":"gasgiant";
        var resources=Minecraft.getInstance().getResourceManager();
        JsonObject presets;
        try(var reader=resources.getResourceOrThrow(new net.minecraft.resources.ResourceLocation(CosmicLab.ID,"sky/validation-presets.json")).openAsReader()){
            presets=JsonParser.parseReader(reader).getAsJsonObject();
        }
        SkyRecipe preset=SkyRecipe.read(new java.io.StringReader(presets.getAsJsonObject(id).toString()));
        Map<String,Object> p=new LinkedHashMap<>();
        for(String key:new String[]{"atmosphereStrength","thinCloudStrength","volumeCloudStrength","fogStrength","cloudShadowStrength","lightShaftStrength","rainAmount","thunderAmount","specialStrength","birdDensity","rainbowStrength","haloStrength","auroraStrength","meteorRate"})p.put(key,0);
        p.put("nativeTransparentMode",0);p.put("precipitationMode",0);p.put("weatherFollowWorld",0);p.put("cycleSpeed",0);p.put("environmentRenderScale",1);p.put("quality",2);
        p.put("twinkleAmount",0);p.put("nebulaSpeed",0);
        if(name.startsWith("giant-")){
            for(String key:new String[]{"starDensity","galaxyStrength","nebulaStrength","dustStrength","blackHoleStrength"})p.put(key,0);
            if(name.equals("giant-off"))p.put("celestialStrength",0);
        }else p.put("lensingStrength",name.equals("cinematic-lens-off")?0:preset.value("lensingStrength"));
        cameraY=100;pitch=-preset.value(name.startsWith("giant-")?"celestialPitch":"blackHolePitch");
        if(name.endsWith("yaw45")){yaw=45;p.put(name.startsWith("giant-")?"celestialYaw":"blackHoleYaw",-45);}
        if(name.endsWith("backside"))yaw=180;
        if(name.endsWith("translation")){cameraX=64;cameraZ=-128;}
        LabSky.recipe=preset.with(p);LabSky.quality=2;
        record.addProperty("referencePreset",id);record.add("validationOverrides",JSON.toJsonTree(p));
        record.addProperty("configuredYaw",yaw);record.addProperty("configuredPitch",pitch);
    }
    private static void configureCompat(String name)throws Exception{
        Class<?> iris=Class.forName("net.irisshaders.iris.Iris");Object config=iris.getMethod("getIrisConfig").invoke(null);
        config.getClass().getMethod("setShaderPackName",String.class).invoke(config,System.getProperty("cosmiclab.compatPack"));
        config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,name.equals("compat-on")||name.equals("compat-reload"));
        config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);
    }
    static void skyDraw(ShaderInstance shader,net.minecraft.client.Camera camera,Matrix4f projection,Matrix3f rotation){
        if(!active()||record==null)return;
        if(ticks<90||record.has("skyDraw"))return;
        JsonObject draw=program(shader);draw.add("projection",JSON.toJsonTree(projection.get(new float[16])));draw.add("inverseViewRotation",JSON.toJsonTree(rotation.get(new float[9])));
        record.add("skyDraw",draw);saveImage("sky");
    }
    static void environmentDraw(ShaderInstance shader,boolean early){
        if(!active()||record==null||ticks<90||record.has("environmentDraw"))return;
        JsonObject draw=program(shader);draw.addProperty("early",early);record.add("environmentDraw",draw);
    }
    static void lutBuildDraw(ShaderInstance shader,int pass){
        if(!active()||record==null)return;
        JsonObject draw=program(shader);draw.addProperty("pass",pass);
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        draw.add("cameraPosition",JSON.toJsonTree(new double[]{camera.x,camera.y,camera.z}));
        draw.addProperty("observerHeightKm",LabAtmosphere.observerHeight);draw.addProperty("actualTime",LabSky.actualTime);
        JsonArray previous=record.getAsJsonArray("lutBuildDraws"),current=new JsonArray();
        for(var value:previous)if(value.getAsJsonObject().get("pass").getAsInt()!=pass)current.add(value);
        current.add(draw);record.add("lutBuildDraws",current);
    }
    static void surfaceDraw(){
        String key=LabTranslucency.frameApplied?"surfaceDraw":"vanillaSurfaceDraw";
        if(!active()||record==null||ticks<90||record.has(key))return;
        ShaderInstance shader=LabTranslucency.frameApplied?LabTranslucency.shader:net.minecraft.client.renderer.GameRenderer.getRendertypeTranslucentShader();
        JsonObject draw=program(shader);draw.addProperty("vao",GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        int vbo=GL20.glGetVertexAttribi(0,GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);draw.addProperty("vertexBuffer",vbo);
        draw.addProperty("fbo",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
        float[] offset=new float[3],modelView=new float[16],projection=new float[16];
        GL20.glGetUniformfv(shader.getId(),GL20.glGetUniformLocation(shader.getId(),"ChunkOffset"),offset);
        GL20.glGetUniformfv(shader.getId(),GL20.glGetUniformLocation(shader.getId(),"ModelViewMat"),modelView);
        GL20.glGetUniformfv(shader.getId(),GL20.glGetUniformLocation(shader.getId(),"ProjMat"),projection);
        draw.add("chunkOffset",JSON.toJsonTree(offset));draw.add("modelView",JSON.toJsonTree(modelView));draw.add("projection",JSON.toJsonTree(projection));
        if(vbo>0){int previous=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);var first=BufferUtils.createFloatBuffer(3);GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER,0L,first);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,previous);draw.add("firstPosition",JSON.toJsonTree(new float[]{first.get(0),first.get(1),first.get(2)}));}
        draw.add("blendFactors",JSON.toJsonTree(new int[]{GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)}));
        draw.addProperty("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));record.add(key,draw);
    }
    static void environmentDepth(RenderTarget source,RenderTarget output){
        if(!active()||record==null||ticks<90||record.has("environmentDepthMaxDelta"))return;
        int n=source.width*source.height;var a=BufferUtils.createFloatBuffer(n);var b=BufferUtils.createFloatBuffer(n);
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source.frameBufferId);GL11.glReadPixels(0,0,source.width,source.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,a);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,output.frameBufferId);GL11.glReadPixels(0,0,output.width,output.height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,b);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);float delta=0;for(int i=0;i<n;i++)delta=Math.max(delta,Math.abs(a.get(i)-b.get(i)));
        record.addProperty("environmentDepthMaxDelta",delta);record.addProperty("snapshotFbo",source.frameBufferId);record.addProperty("mainFbo",output.frameBufferId);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void level(RenderLevelStageEvent event){
        if(!active()||record==null||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||Minecraft.getInstance().level==null)return;
        if(ticks>=90&&!record.has("stableCountsBefore"))record.add("stableCountsBefore",JSON.toJsonTree(counters()));
        if(++frames<150||ticks<100||reload!=null)return;
        try(LabEnvironment.State state=new LabEnvironment.State()){
            var mc=Minecraft.getInstance();var camera=event.getCamera();record.add("cameraPosition",JSON.toJsonTree(new double[]{camera.getPosition().x,camera.getPosition().y,camera.getPosition().z}));
            record.add("cameraLook",JSON.toJsonTree(new float[]{camera.getLookVector().x,camera.getLookVector().y,camera.getLookVector().z}));
            var inverseTranslation=LabSky.frameInverseViewTranslation;
            record.add("inverseViewTranslation",JSON.toJsonTree(new float[]{inverseTranslation.x,inverseTranslation.y,inverseTranslation.z}));
            var eyeH=LabSky.frameInverseProjection.transform(new org.joml.Vector4f(0,0,1,0));
            record.add("projectionEyeView",JSON.toJsonTree(new float[]{eyeH.x/eyeH.w,eyeH.y/eyeH.w,eyeH.z/eyeH.w}));
            record.add("opticalEyeOffset",JSON.toJsonTree(new float[]{LabSky.frameOpticalEyeOffset.x,LabSky.frameOpticalEyeOffset.y,LabSky.frameOpticalEyeOffset.z}));
            record.addProperty("frames",frames);record.addProperty("ticks",ticks);record.addProperty("generationAfter",LabSky.generation);
            record.addProperty("actualTime",LabSky.actualTime);record.addProperty("rendererReason",LabRenderOwner.reason);record.addProperty("pack",LabRenderOwner.packName);
            record.addProperty("pipeline",LabRenderOwner.pipelineName);record.addProperty("handoff",LabRenderOwner.handoff());
            float cloudHeight=mc.level.effects().getCloudHeight();if(Float.isFinite(cloudHeight))record.addProperty("cloudHeight",cloudHeight);else record.addProperty("cloudHeight","disabled");
            record.addProperty("surfaceReason",LabTranslucency.reason);record.addProperty("surfaceContract",LabTranslucency.resourceContract);record.addProperty("nativeFrameApplied",LabTranslucency.frameApplied);
            record.addProperty("requestedRenderScale",LabEnvironment.requestedScale);record.addProperty("effectiveRenderScale",LabEnvironment.effectiveScale);record.addProperty("renderScaleReason",LabEnvironment.scaleReason);
            record.add("countsAfter",JSON.toJsonTree(counters()));record.add("stableCountsAfter",JSON.toJsonTree(counters()));
            record.addProperty("lutReady",LabAtmosphere.ready);record.addProperty("observerHeightKm",LabAtmosphere.observerHeight);
            if(LabAtmosphere.ready){JsonArray luts=new JsonArray();for(int i=0;i<3;i++)luts.add(texture(LabAtmosphere.targets[i].texture,LabAtmosphere.WIDTH[i],LabAtmosphere.HEIGHT[i],LabAtmosphere.NAMES[i]));record.add("luts",luts);}
            if(LabPrecipitation.ready){record.add("weatherVolume",texture(LabPrecipitation.volumeTexture,64,64,"weather-volume"));record.add("weatherSurface",texture(LabPrecipitation.surfaceTexture,128,128,"weather-surface"));
                record.add("weatherCounts",JSON.toJsonTree(new int[]{LabPrecipitation.rainCells,LabPrecipitation.snowCells,LabPrecipitation.noneCells,LabPrecipitation.unknownCells}));
                record.add("weatherOrigin",JSON.toJsonTree(new int[]{LabPrecipitation.originX,LabPrecipitation.originY,LabPrecipitation.originZ,LabPrecipitation.surfaceX,LabPrecipitation.surfaceZ}));
                record.addProperty("weatherUpdateCpuMs",LabPrecipitation.lastCpuMs);record.addProperty("weatherUpdates",LabPrecipitation.updates);
            }
            LabGpuTimings.poll();JsonObject timing=new JsonObject();for(var e:LabGpuTimings.samples.entrySet()){
                var values=e.getValue().stream().sorted().toList();JsonObject t=new JsonObject();t.addProperty("samples",values.size());t.addProperty("medianMs",values.get(values.size()/2));t.addProperty("p95Ms",values.get((int)((values.size()-1)*.95)));timing.add(e.getKey(),t);
            }record.add("gpuTimings",timing);record.addProperty("glError",GL11.glGetError());saveImage("world");
            RESULTS.add(record);Files.writeString(OUTPUT.resolve("results.json"),JSON.toJson(RESULTS));
        }catch(Exception error){fail(error);return;}
        try{next(Minecraft.getInstance());}catch(Exception error){fail(error);}
    }
    private static JsonObject program(ShaderInstance shader){
        JsonObject result=new JsonObject();result.addProperty("program",shader.getId());result.addProperty("boundProgram",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        result.addProperty("fbo",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
        result.addProperty("boundColorTexture",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==0?0:GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME));
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);result.add("viewport",JSON.toJsonTree(viewport));
        JsonObject uniforms=new JsonObject(),samplers=new JsonObject();
        Set<String> names=new LinkedHashSet<>(Arrays.asList(("Quality Seed StarSettings MotionSettings GalaxySettings CloudSettings AuroraSettings MeteorSettings BodySettings BodySurface RingSettings BlackHoleSettings DiskSettings AtmosphereSettings SunSettings DaySettings MoonSettings MoonSurface ThinCloudSettings ThinCloudMotion WeatherSettings RainSettings WeatherMotion FogSettings FogMotion VolumeCloudSettings VolumeCloudGeometry VolumeCloudMotion CloudLighting LightingDetail RainbowSettings HaloSettings SpecialSettings SpecialShape BlackHoleAppearance BlackHoleCinematic AtmosphereGeometry AtmosphereComposition AtmospherePhysical SnowSettings PrecipitationSettings PipelineSettings BackgroundColor StarCoolColor StarWarmColor GalaxyColor NebulaColorA NebulaColorB AuroraColorA AuroraColorB BodyColorA BodyColorB RingColor DiskColorA DiskColorB SkyTint SunColor MoonColor ThinCloudColor FogColor EnvironmentFogColor VolumeCloudColor RainColor LightningColor SpecialColorA SpecialColorB SnowColor Time PhysicalObserverHeight AtmosphereLutReady AtmosphereSkyViewReady EnvironmentStage CameraPosition WorldPrecipOrigin WorldPrecipPresence WorldPrecipGeometry WorldSurfaceGrid WorldWeather InverseProjection InverseViewRotation InverseViewTranslation OutputDeltaEncoding AtmosphereLutSun AtmosphereTransmittance AtmosphereMultipleScattering AtmosphereSkyViewSampler WeatherVolumeSampler SurfaceSampler SceneSampler DepthSampler Sampler0 Sampler2 ChunkOffset ModelViewMat ProjMat").split(" ")));
        Map<String,int[]> active=new LinkedHashMap<>();
        names.add("GiantBodySettings");
        names.add("OpticalEyeOffset");
        for(int i=0;i<GL20.glGetProgrami(shader.getId(),GL20.GL_ACTIVE_UNIFORMS);i++){
            var size=BufferUtils.createIntBuffer(1);var type=BufferUtils.createIntBuffer(1);
            String name=GL20.glGetActiveUniform(shader.getId(),i,size,type);names.add(name);active.put(name,new int[]{size.get(0),type.get(0)});
        }
        int activeUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for(String name:names){
            int loc=GL20.glGetUniformLocation(shader.getId(),name);JsonObject u=new JsonObject();u.addProperty("location",loc);
            if(loc>=0){
                int[] spec=active.get(name);if(spec==null)throw new IllegalStateException("Active uniform metadata missing: "+name);
                int type=spec[1],count=components(type)*spec[0];u.addProperty("type",type);u.addProperty("count",count);
                if(integerType(type)){int[] values=new int[count];GL20.glGetUniformiv(shader.getId(),loc,values);u.add("values",JSON.toJsonTree(values));
                    if(type==GL20.GL_SAMPLER_2D){
                        com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13.GL_TEXTURE0+values[0]);
                        JsonObject binding=new JsonObject();binding.addProperty("unit",values[0]);binding.addProperty("texture",GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));binding.addProperty("target",GL11.GL_TEXTURE_2D);samplers.add(name,binding);
                    }
                }else{float[] values=new float[count];GL20.glGetUniformfv(shader.getId(),loc,values);u.add("values",JSON.toJsonTree(values));}
            }uniforms.add(name,u);
        }
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(activeUnit);
        result.add("uniforms",uniforms);result.add("samplers",samplers);return result;
    }
    private static int components(int type){return switch(type){
        case GL20.GL_FLOAT_VEC2,GL20.GL_INT_VEC2,GL20.GL_BOOL_VEC2->2;
        case GL20.GL_FLOAT_VEC3,GL20.GL_INT_VEC3,GL20.GL_BOOL_VEC3->3;
        case GL20.GL_FLOAT_VEC4,GL20.GL_INT_VEC4,GL20.GL_BOOL_VEC4,GL20.GL_FLOAT_MAT2->4;
        case GL20.GL_FLOAT_MAT3->9;case GL20.GL_FLOAT_MAT4->16;default->1;};}
    private static boolean integerType(int type){return type==GL11.GL_INT||type==GL20.GL_BOOL||type==GL20.GL_INT_VEC2||type==GL20.GL_INT_VEC3||type==GL20.GL_INT_VEC4||type==GL20.GL_BOOL_VEC2||type==GL20.GL_BOOL_VEC3||type==GL20.GL_BOOL_VEC4||type==GL20.GL_SAMPLER_2D;}
    private static JsonObject texture(int texture,int width,int height,String tag){
        com.mojang.blaze3d.platform.GlStateManager._activeTexture(GL13.GL_TEXTURE0);com.mojang.blaze3d.platform.GlStateManager._bindTexture(texture);var data=BufferUtils.createFloatBuffer(width*height*4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;int invalid=0;double sum=0;
        for(int i=0;i<data.capacity();i++){float v=data.get(i);if(!Float.isFinite(v))invalid++;else{min=Math.min(min,v);max=Math.max(max,v);sum+=v;}}
        JsonObject out=new JsonObject();out.addProperty("texture",texture);out.addProperty("width",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH));
        out.addProperty("height",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT));out.addProperty("format",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT));
        out.addProperty("minimum",min);out.addProperty("maximum",max);out.addProperty("mean",sum/data.capacity());out.addProperty("nonfinite",invalid);
        try{
            ByteBuffer bytes=ByteBuffer.allocate(data.capacity()*4).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<data.capacity();i++)bytes.putFloat(data.get(i));
            String file=CASES.get(index)+"-"+tag+".rgba32f";Files.write(OUTPUT.resolve(file),bytes.array());
            out.addProperty("readbackFile",file);out.addProperty("readbackType","float32-le-rgba");
            out.addProperty("readbackSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.array())));
        }catch(Exception error){throw new RuntimeException(error);}
        if(width==64&&height==64&&texture==LabPrecipitation.volumeTexture){int rain=0,snow=0,none=0,unknown=0;for(int i=0;i<4096;i++){if(data.get(i*4+3)==0)unknown++;else if(data.get(i*4)>0)rain++;else if(data.get(i*4+1)>0)snow++;else none++;}out.add("gpuWeatherCounts",JSON.toJsonTree(new int[]{rain,snow,none,unknown}));}
        return out;
    }
    private static void saveImage(String suffix){
        try(LabEnvironment.State state=new LabEnvironment.State();NativeImage image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
            image.writeToFile(OUTPUT.resolve(CASES.get(index)+"-"+suffix+".png"));record.addProperty("width",image.getWidth());record.addProperty("height",image.getHeight());
        }catch(Exception error){throw new RuntimeException(error);}
    }
    private static void finish(Minecraft mc)throws Exception{
        finished=true;JsonObject summary=new JsonObject();summary.addProperty("protocol","cosmic-advanced-sky-forge/1");summary.addProperty("version","0.6.0");summary.addProperty("schema",SkyRecipe.SCHEMA);
        summary.addProperty("fields",LabSky.defaults.toJson().getAsJsonObject("parameters").size());summary.addProperty("world",world);summary.addProperty("minecraft","1.20.1");summary.addProperty("forge","47.4.22");
        summary.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));summary.addProperty("cases",RESULTS.size());summary.add("caseNames",JSON.toJsonTree(CASES));
        JsonObject mods=new JsonObject();for(String name:new String[]{"oculus","embeddium"})ModList.get().getModContainerById(name).ifPresent(m->mods.addProperty(name,m.getModInfo().getVersion().toString()));summary.add("rendererMods",mods);
        summary.add("resourceHashes",resourceHashes());summary.add("classHashes",classHashes());
        Files.writeString(OUTPUT.resolve("summary.json"),JSON.toJson(summary));System.out.println("[COSMICLAB_ADVANCED] COMPLETE "+JSON.toJson(summary));
        if(Boolean.getBoolean("cosmiclab.exitOnComplete"))mc.stop();
    }
    private static String digest(byte[] bytes)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static JsonObject resourceHashes()throws Exception{
        JsonObject hashes=new JsonObject();var resources=Minecraft.getInstance().getResourceManager();
        for(String program:new String[]{"sky","environment","surface","environment_composite","atmosphere_transmittance","atmosphere_multiple","atmosphere_sky_view"})
            for(String extension:new String[]{"vsh","fsh","json"}){
                String path="shaders/core/"+program+"."+extension;
                try(var input=resources.getResourceOrThrow(new net.minecraft.resources.ResourceLocation(CosmicLab.ID,path)).open()){hashes.addProperty("assets/cosmiclab/"+path,digest(input.readAllBytes()));}
            }
        for(String path:new String[]{"cosmiclab.mixins.json","cosmiclab-vanilla-contract.properties"})
            try(var input=LabAdvancedValidation.class.getResourceAsStream("/"+path)){hashes.addProperty(path,digest(input.readAllBytes()));}
        for(String path:new String[]{"sky/default.json","sky/validation-presets.json"})
            try(var input=resources.getResourceOrThrow(new net.minecraft.resources.ResourceLocation(CosmicLab.ID,path)).open()){hashes.addProperty("assets/cosmiclab/"+path,digest(input.readAllBytes()));}
        return hashes;
    }
    private static JsonObject classHashes()throws Exception{
        JsonObject hashes=new JsonObject();
        for(String name:new String[]{"CosmicLab","SkyRecipe","LabSky","LabEnvironment","LabAtmosphere","LabPrecipitation","LabTranslucency","LabRenderOwner","LabGpuTimings","LabAdvancedValidation","mixin/NativeTranslucentMixin","mixin/NativePipelineMixinPlugin"}){
            String path="dev/cosmiclab/"+name+".class";try(var input=LabAdvancedValidation.class.getResourceAsStream("/"+path)){hashes.addProperty(path,digest(input.readAllBytes()));}
        }return hashes;
    }
    static void fail(Exception error){
        if(!active())return;finished=true;error.printStackTrace();try{Files.createDirectories(OUTPUT);Files.writeString(OUTPUT.resolve("failure.txt"),error.toString());}catch(Exception ignored){}
        if(Boolean.getBoolean("cosmiclab.exitOnComplete"))Minecraft.getInstance().stop();
    }
}
