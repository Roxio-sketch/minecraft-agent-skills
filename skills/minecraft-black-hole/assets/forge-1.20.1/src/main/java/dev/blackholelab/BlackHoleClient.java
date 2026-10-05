package dev.blackholelab;

import com.google.gson.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.nio.file.*;
import static net.minecraft.commands.Commands.*;

@Mod.EventBusSubscriber(modid=BlackHoleLab.ID,value=Dist.CLIENT)
public final class BlackHoleClient {
    static Vec3 center;
    static int coreColor=0;
    static long worldDraws;
    static String failure="";
    static int ticks;
    private static net.minecraft.client.multiplayer.ClientLevel owner;
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){
        e.getDispatcher().register(literal("blackhole")
            .then(literal("showcase").executes(c->{Minecraft.getInstance().setScreen(new BlackHoleShowcase());return 1;}))
            .then(literal("world").executes(c->{place();return 1;}))
            .then(literal("off").executes(c->{stop();return 1;}))
            .then(literal("import").executes(c->{BlackHoleRecipe.load();return 1;}))
            .then(literal("export").executes(c->{BlackHoleRecipe.save();return 1;}))
            .then(literal("lens_players").then(argument("enabled",BoolArgumentType.bool()).executes(c->{BlackHoleRecipe.state().setBoolean("lensAffectsPlayer",BoolArgumentType.getBool(c,"enabled"));return 1;})))
            .then(literal("tilt").then(argument("degrees",FloatArgumentType.floatArg(-90,90)).executes(c->{BlackHoleWorld.tilt=FloatArgumentType.getFloat(c,"degrees");return 1;})))
            .then(literal("size").then(argument("scale",FloatArgumentType.floatArg(.2f,8f)).executes(c->{BlackHoleWorld.scale=FloatArgumentType.getFloat(c,"scale");return 1;})))
            .then(literal("lens").then(argument("enabled",BoolArgumentType.bool()).executes(c->{BlackHoleWorld.lens=BoolArgumentType.getBool(c,"enabled")?1:0;return 1;})))
            .then(literal("lens_strength").then(argument("strength",FloatArgumentType.floatArg(0,1)).executes(c->{BlackHoleWorld.lens=FloatArgumentType.getFloat(c,"strength");return 1;})))
            .then(literal("lens_radius").then(argument("radius",FloatArgumentType.floatArg(.2f,16f)).executes(c->{BlackHoleWorld.lensRadius=FloatArgumentType.getFloat(c,"radius");return 1;})))
            .then(literal("rotation").then(argument("speed",FloatArgumentType.floatArg(0,3)).executes(c->{BlackHoleWorld.rotation=FloatArgumentType.getFloat(c,"speed");return 1;})))
            .then(literal("infall").then(argument("speed",FloatArgumentType.floatArg(0,2)).executes(c->{BlackHoleWorld.infall=FloatArgumentType.getFloat(c,"speed");return 1;})))
            .then(literal("particles").then(argument("enabled",BoolArgumentType.bool()).executes(c->{BlackHoleParticles.enabled=BoolArgumentType.getBool(c,"enabled");return 1;})))
            .then(literal("particle_speed").then(argument("speed",FloatArgumentType.floatArg(0,4)).executes(c->{BlackHoleParticles.speed=FloatArgumentType.getFloat(c,"speed");return 1;})))
            .then(literal("halo").then(argument("strength",FloatArgumentType.floatArg(0,3)).executes(c->{BlackHoleWorld.halo=FloatArgumentType.getFloat(c,"strength");return 1;})))
            .then(literal("color").then(argument("hex",StringArgumentType.word()).executes(c->{coreColor=parseColor(StringArgumentType.getString(c,"hex"));return 1;}))));
    }
    static int parseColor(String s){s=s.replaceFirst("^#","");if(!s.matches("[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use RRGGBB, for example 7D3CFF");return Integer.parseInt(s,16);}
    static void place(){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        Vec3 eye=mc.player.getEyePosition(),end=eye.add(mc.player.getLookAngle().scale(BlackHoleWorld.distance));
        var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        center=hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
            ?hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(BlackHoleRecipe.state().getDouble("core")*BlackHoleWorld.scale+.05))
            :end;
        owner=mc.level;failure="";
    }
    static void stop(){center=null;BlackHoleWorld.release();BlackHoleRender.resetClock();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();if(owner!=mc.level){stop();owner=mc.level;}
        if(++ticks%20!=0||mc.level==null)return;
        Path dir=mc.gameDirectory.toPath().resolve("black-hole-lab"),request=dir.resolve("command.json");
        if(!Files.isRegularFile(request))return;
        try{
            JsonObject r=JsonParser.parseString(Files.readString(request)).getAsJsonObject();Files.delete(request);
            String action=r.get("action").getAsString();
            switch(action){
                case "showcase"->mc.setScreen(new BlackHoleShowcase());
                case "world"->{if(r.has("color"))coreColor=parseColor(r.get("color").getAsString());mc.setScreen(null);place();}
                case "color"->coreColor=parseColor(r.get("color").getAsString());
                case "settings"->settings(r);
                case "import"->BlackHoleRecipe.load();
                case "export"->BlackHoleRecipe.save();
                case "probe"->{String name=r.get("name").getAsString();if(!name.matches("[a-zA-Z0-9_-]{1,60}"))throw new IllegalArgumentException("Invalid probe name");BlackHoleWorld.probe=name;}
                case "view"->{float yaw=bounded(r,"yaw",-180,180),pitch=bounded(r,"pitch",-89,89);mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;}
                case "off"->stop();
                case "reload"->mc.reloadResourcePacks();
                case "close"->mc.setScreen(null);
                case "hold"->mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Black-hole capture")){
                    @Override public boolean isPauseScreen(){return false;}
                    @Override public void render(GuiGraphics graphics,int x,int y,float partial){}
                });
                case "status"->{}
                case "quit"->mc.stop();
                default->throw new IllegalArgumentException("Unknown action "+action);
            }
            JsonObject result=new JsonObject();result.addProperty("ok",true);result.addProperty("action",action);
            result.addProperty("generation",BlackHoleRender.generation);result.addProperty("sceneLoaded",BlackHoleRender.scene!=null);
            result.addProperty("worldDraws",worldDraws);result.addProperty("worldEnabled",center!=null);
            result.addProperty("worldShaderLoaded",BlackHoleRender.world!=null);result.addProperty("scale",BlackHoleWorld.scale);
            result.addProperty("lens",BlackHoleWorld.lens);result.addProperty("lensRadius",BlackHoleWorld.lensRadius);result.addProperty("rotation",BlackHoleWorld.rotation);result.addProperty("infall",BlackHoleWorld.infall);
            result.addProperty("worldGlError",BlackHoleWorld.lastGlError);result.addProperty("submitCpuMs",BlackHoleWorld.cpuMillis);
            result.addProperty("particlesEnabled",BlackHoleParticles.enabled);result.addProperty("particleCount",BlackHoleParticles.count);result.addProperty("particleDraws",BlackHoleParticles.draws);
            result.addProperty("particleShaderLoaded",BlackHoleParticles.shader!=null);
            result.addProperty("halo",BlackHoleWorld.halo);result.addProperty("lensAffectsPlayer",BlackHoleRecipe.state().getBool("lensAffectsPlayer"));
            if(center!=null)result.addProperty("center",center.toString());
            result.addProperty("coreColor",String.format("#%06X",coreColor));result.addProperty("failure",failure);
            result.addProperty("glError",GL11.glGetError());
            Files.writeString(dir.resolve("command-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        }catch(Exception ex){failure=ex.toString();System.err.println("[BLACK-HOLE] Control: "+failure);}
    }
    private static float bounded(JsonObject r,String key,float min,float max){float v=r.get(key).getAsFloat();if(!Float.isFinite(v)||v<min||v>max)throw new IllegalArgumentException(key+" outside "+min+".."+max);return v;}
    private static void settings(JsonObject r){
        if(r.has("scale"))BlackHoleWorld.scale=bounded(r,"scale",.2f,8);
        if(r.has("distance"))BlackHoleWorld.distance=bounded(r,"distance",2,128);
        if(r.has("lens"))BlackHoleWorld.lens=bounded(r,"lens",0,1);
        if(r.has("lensRadius"))BlackHoleWorld.lensRadius=bounded(r,"lensRadius",.2f,16f);
        if(r.has("lensAffectsPlayer"))BlackHoleRecipe.state().setBoolean("lensAffectsPlayer",r.get("lensAffectsPlayer").getAsBoolean());
        if(r.has("rotation"))BlackHoleWorld.rotation=bounded(r,"rotation",0,3);
        if(r.has("infall"))BlackHoleWorld.infall=bounded(r,"infall",0,2);
        if(r.has("exposure"))BlackHoleWorld.exposure=bounded(r,"exposure",.01f,2);
        if(r.has("tilt"))BlackHoleWorld.tilt=bounded(r,"tilt",-90,90);
        if(r.has("time"))BlackHoleRender.frozenTime=bounded(r,"time",-1,86400)*20;
        if(r.has("particles"))BlackHoleParticles.enabled=r.get("particles").getAsBoolean();
        if(r.has("particleCount"))BlackHoleParticles.count=(int)bounded(r,"particleCount",0,4096);
        if(r.has("particleSpeed"))BlackHoleParticles.speed=bounded(r,"particleSpeed",0,4);
        if(r.has("particleSize"))BlackHoleParticles.size=bounded(r,"particleSize",.005f,.15f);
        if(r.has("particleOuter"))BlackHoleParticles.outer=bounded(r,"particleOuter",3.2f,8f);
        if(r.has("particleBrightness"))BlackHoleParticles.brightness=bounded(r,"particleBrightness",0,5);
        if(r.has("particleDebug"))BlackHoleParticles.debug=(int)bounded(r,"particleDebug",0,2);
        if(r.has("halo"))BlackHoleWorld.halo=bounded(r,"halo",0,3);
        if(r.has("haloRing"))BlackHoleWorld.haloRing=bounded(r,"haloRing",0,3);
        if(r.has("haloDisk"))BlackHoleWorld.haloDisk=bounded(r,"haloDisk",0,3);
        if(r.has("haloAura"))BlackHoleWorld.haloAura=bounded(r,"haloAura",0,3);
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent e){
        Minecraft mc=Minecraft.getInstance();
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS){BlackHoleWorld.captureEnvironment(e);return;}
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES){BlackHoleWorld.captureCamera(e);return;}
        // Clouds render after the particle stage and do not provide reliable
        // scene depth. Composite after the whole level so their color is in the
        // snapshot and captured rays can occlude it before hand/HUD rendering.
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||center==null||BlackHoleRender.scene==null||!failure.isEmpty()||mc.level!=owner)return;
        BlackHoleWorld.render(e);
    }
    static void panel(GuiGraphics graphics,int x,int y,int size,int rgb){
        if(BlackHoleRender.scene==null)return;graphics.flush();
        try(State ignored=new State()){
            ShaderInstance s=BlackHoleRender.scene;BlackHoleRender.material(s,rgb);s.safeGetUniform("uWorld").set(0);
            RenderSystem.setShader(()->s);RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);
            BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);Matrix4f p=graphics.pose().last().pose();
            b.vertex(p,x,y+size,0).uv(0,0).endVertex();b.vertex(p,x+size,y+size,0).uv(1,0).endVertex();b.vertex(p,x+size,y,0).uv(1,1).endVertex();b.vertex(p,x,y,0).uv(0,1).endVertex();BufferUploader.drawWithShader(b.end());
        }
    }
    private static final class State implements AutoCloseable{
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final int func=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),eqr=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),eqa=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final ShaderInstance previous=RenderSystem.getShader();final float[] color=RenderSystem.getShaderColor().clone();
        public void close(){RenderSystem.setShader(()->previous);GlStateManager._glUseProgram(program);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);RenderSystem.depthFunc(func);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();RenderSystem.blendFuncSeparate(sr,dr,sa,da);GL20.glBlendEquationSeparate(eqr,eqa);if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();}
    }
}
