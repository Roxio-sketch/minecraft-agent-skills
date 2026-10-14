package dev.trailbeam;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static net.minecraft.commands.Commands.literal;

/** Optional lab: client-only analytic emitters. No entities, blocks or inventory are changed. */
@Mod.EventBusSubscriber(modid="trailbeam",value=Dist.CLIENT)
public final class ClientLab {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static Recipe blade,beam,custom;
    private static Paths.Spec customPath;
    private static boolean worldDemo;
    private static long poll;
    private static int audit=-1,frames;
    private static Path run;
    private static boolean oldPause;
    private static Screen oldScreen;
    private static final JsonArray captures=new JsonArray();
    private static CompletableFuture<Void> reload;
    private static Geometry.V anchor,right,up,forward;
    private static final Geometry.History actualHistory=new Geometry.History(0.85,0.015,0.1,4,256);
    private static Object previousLevel;
    private static final String[] cases={"gallery-t0","gallery-t1","world-effects","reloaded","expired"};
    private static double lastSampleTime;
    private static final UUID TRAIL_ID=UUID.fromString("b0a4ed40-7e82-4084-923c-2b75bfc94c6a");
    static Path root(){return Minecraft.getInstance().gameDirectory.toPath().resolve("trail-beam-lab");}
    private static Recipe resource(String name)throws Exception {
        var manager=Minecraft.getInstance().getResourceManager();
        try(var in=manager.open(new ResourceLocation("trailbeam","recipes/"+name+".json"))){return Recipe.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
    }
    static void load()throws Exception {
        Recipe a=resource("blade"),b=resource("beam"),c=resource("helix");
        Path user=Minecraft.getInstance().gameDirectory.toPath().resolve("config/trailbeam/recipe.json");
        if(Files.isRegularFile(user))c=Recipe.parse(Files.readString(user));
        Paths.Spec path;
        try(var in=managerPath()) {path=Paths.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        Path userPath=Minecraft.getInstance().gameDirectory.toPath().resolve("config/trailbeam/path.json");
        if(Files.isRegularFile(userPath))path=Paths.parse(Files.readString(userPath));
        blade=a;beam=b;custom=c;customPath=path;BeamRender.clearTypes();
    }
    private static java.io.InputStream managerPath()throws Exception {
        return Minecraft.getInstance().getResourceManager().open(new ResourceLocation("trailbeam","paths/custom.json"));
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){e.getDispatcher().register(literal("trailbeam")
        .then(literal("showcase").executes(c->{try{load();Minecraft.getInstance().setScreen(new Gallery());}catch(Exception ex){throw new RuntimeException(ex);}return 1;}))
        .then(literal("world").executes(c->{try{load();place();worldDemo=true;Minecraft.getInstance().setScreen(null);}catch(Exception ex){throw new RuntimeException(ex);}return 1;}))
        .then(literal("off").executes(c->{worldDemo=false;Effects.clear();return 1;}))
        .then(literal("reload").executes(c->{try{load();}catch(Exception ex){throw new RuntimeException(ex);}return 1;}))
        .then(literal("verify").executes(c->{try{start();}catch(Exception ex){throw new RuntimeException(ex);}return 1;})));}
    private static Geometry.V v(Vec3 p){return new Geometry.V(p.x,p.y,p.z);}
    private static void place() {
        var cam=Minecraft.getInstance().gameRenderer.getMainCamera();
        forward=v(Vec3.directionFromRotation(cam.getXRot(),cam.getYRot()));
        right=forward.cross(new Geometry.V(0,1,0)).unit();if(right.len()<0.001)right=new Geometry.V(1,0,0);
        up=right.cross(forward).unit();anchor=v(cam.getPosition()).add(forward.mul(5));actualHistory.clear();
    }
    private static Geometry.V local(double x,double y,double z){return anchor.add(right.mul(x)).add(up.mul(y)).add(forward.mul(z));}
    private static Geometry.V[] bladeSockets(double t) {
        double a=t*4.0;return new Geometry.V[]{local(-1.6+0.35*Math.cos(a),0.3+0.35*Math.sin(a),0),local(-1.6+1.1*Math.cos(a),0.3+1.1*Math.sin(a),0.2*Math.sin(a))};
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getInstance();
        if(previousLevel!=mc.level){Effects.clear();worldDemo=false;actualHistory.clear();previousLevel=mc.level;}
        if(mc.level!=null) {
            double now=mc.level.getGameTime()/20.0;Effects.world(mc.level.dimension().location().toString());Effects.expire(now);
            if(worldDemo&&!mc.isPaused()) {Geometry.V[] s=bladeSockets(now);actualHistory.add(now,s[0],s[1]);lastSampleTime=now;Effects.sample(TRAIL_ID,blade,now,s[0],s[1]);}
        }
        if(++poll%10!=0)return;
        Path request=root().resolve("request.json");if(!Files.isRegularFile(request))return;
        try {
            String action=JsonParser.parseString(Files.readString(request)).getAsJsonObject().get("action").getAsString();Files.delete(request);
            if(audit>=0)return;
            switch(action){case "verify"->start();case "showcase"->{load();mc.setScreen(new Gallery());}case "off"->{worldDemo=false;Effects.clear();mc.setScreen(null);}default->throw new IllegalArgumentException("Unknown action");}
        }catch(Exception ex){fail(ex);}
    }
    @SubscribeEvent public static void worldRender(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        double t=(mc.level.getGameTime()+e.getPartialTick())/20.0;Geometry.V camera=v(e.getCamera().getPosition());
        Effects.render(e.getPoseStack(),camera,t);
        if(!worldDemo||beam==null)return;
        List<Geometry.V> path=new ArrayList<>();for(int i=0;i<=80;i++){double s=i/80.0;path.add(local(-0.3+3.0*s,-0.5+0.23*Math.sin(s*9+t),0));}
        for(Recipe.Layer l:beam.layers())BeamRender.draw(Geometry.sweep(Geometry.path(path),camera,beam.section(),beam.sides(),l.width(),l::widthAt),l,e.getPoseStack(),camera,t);
        for(Paths.Strand strand:Paths.generate(customPath,local(-0.3,0.7,0),right,up,forward,t))
            for(Recipe.Layer l:custom.layers())BeamRender.draw(Geometry.sweep(Geometry.path(strand.points()),camera,custom.section().equals("blade")?"ribbon":custom.section(),custom.sides(),l.width()*strand.widthScale(),l::widthAt),l,e.getPoseStack(),camera,t);
    }
    private static void start()throws Exception {
        Minecraft mc=Minecraft.getInstance();
        if(mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals("ASTRA_VFX_TEST"))throw new IllegalStateException("verify requires ASTRA_VFX_TEST");
        load();oldPause=mc.options.pauseOnLostFocus;oldScreen=mc.screen;mc.options.pauseOnLostFocus=false;
        run=root().resolve("runs").resolve(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(run);captures.asList().clear();audit=0;frames=0;worldDemo=false;mc.setScreen(new Gallery());
        write("running");
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||audit<0)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(reload!=null){if(!reload.isDone())return;reload.join();reload=null;load();frames=0;}
            // The reload future can finish while LoadingOverlay is still fading out.
            if(mc.getOverlay()!=null){frames=0;return;}
            if(audit==2?mc.screen!=null:!(mc.screen instanceof Gallery))throw new IllegalStateException("Audit screen changed externally");
            if(++frames<80)return;
            JsonObject record=new JsonObject();record.addProperty("case",cases[audit]);record.addProperty("generation",BeamRender.generation);record.addProperty("glError",GL11.glGetError());record.addProperty("quadsTotal",BeamRender.quadsDrawn);
            record.addProperty("historyPoints",actualHistory.samples(lastSampleTime).size());
            record.addProperty("overlayVisible",mc.getOverlay()!=null);
            try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(run.resolve(cases[audit]+".png"));record.addProperty("width",image.getWidth());record.addProperty("height",image.getHeight());}
            captures.add(record);audit++;frames=0;
            if(audit==2){place();worldDemo=true;mc.setScreen(null);}
            if(audit==3){worldDemo=false;Effects.clear();mc.setScreen(new Gallery());reload=mc.reloadResourcePacks();}
            if(audit>=cases.length){audit=-1;worldDemo=false;Effects.clear();mc.options.pauseOnLostFocus=oldPause;mc.setScreen(oldScreen);write("completed");}else write("running");
        }catch(Exception ex){fail(ex);}
    }
    private static void write(String status)throws Exception {
        JsonObject r=new JsonObject();r.addProperty("status",status);r.addProperty("minecraft","1.20.1");r.addProperty("forge",net.minecraftforge.versions.forge.ForgeVersion.getVersion());
        r.addProperty("gpu",GL11.glGetString(GL11.GL_RENDERER));r.addProperty("world",Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName());
        r.addProperty("scope","Client visual analytic blade sockets, world beams, GUI material, shader reload. No actual weapon attachment or shaderpack test.");r.add("captures",captures);
        r.addProperty("worldInventoryUntouched",true);r.addProperty("pauseRestored",status.equals("completed")&&Minecraft.getInstance().options.pauseOnLostFocus==oldPause);
        Files.writeString(run.resolve("results.json"),JSON.toJson(r));
    }
    private static void fail(Exception e){e.printStackTrace();if(audit>=0){audit=-1;worldDemo=false;Effects.clear();Minecraft.getInstance().options.pauseOnLostFocus=oldPause;Minecraft.getInstance().setScreen(oldScreen);}try{Files.createDirectories(root());Files.writeString(root().resolve("error.txt"),e.toString());}catch(Exception ignored){}}
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("TRAIL / BEAM WORKBENCH"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            g.fill(0,0,width,height,0xff090d18);g.drawCenteredString(font,"TRAIL / BEAM   -   Forge 1.20.1",width/2,13,0xe4eefc);
            g.drawCenteredString(font,"paired blade sockets  /  layered beam  /  designed path",width/2,29,0x8c9eb8);
            double t=audit==0||audit==3||audit==4?0:audit==1?1:System.nanoTime()/1e9;
            double content=Math.max(40,height-90),y1=60+content*.25,y2=60+content*.74;
            g.fill(12,49,width-12,height-28,0xff101b2b);g.flush();
            double scale=Math.min(width*.14,content*.22);Geometry.V camera=new Geometry.V(width/2.0,height/2.0,1000);PoseStack pose=g.pose();pose.pushPose();pose.translate(0,0,400);
            List<Geometry.Sample> bladePoints=new ArrayList<>();
            for(int i=0;i<=45;i++){double s=i/45.0,angle=-1.3+s*2.5;double age=(1-s)*0.85;
                bladePoints.add(new Geometry.Sample(new Geometry.V(width*.19+Math.cos(angle)*scale*.3,y1+Math.sin(angle)*scale*.3,0),new Geometry.V(width*.19+Math.cos(angle)*scale*1.0,y1+Math.sin(angle)*scale*1.0,0),-age));}
            if(audit!=4)for(Recipe.Layer l:blade.layers())BeamRender.draw(Geometry.blade(bladePoints,0,blade.lifetime(),l.width(),l::widthAt),l,pose,new Geometry.V(0,0,0),t);
            List<Geometry.V> line=new ArrayList<>();
            for(int i=0;i<=100;i++){double s=i/100.0;line.add(new Geometry.V(width*.48+s*width*.43,y1+Math.sin(s*8+t)*scale*.14,0));}
            if(audit!=4){for(Recipe.Layer l:beam.layers())drawScreen(line,l,beam,camera,pose,t,scale);
                for(Paths.Strand strand:Paths.generate(customPath,new Geometry.V(0,0,0),new Geometry.V(1,0,0),new Geometry.V(0,1,0),new Geometry.V(0,0,1),t)) {
                    List<Geometry.V> projected=new ArrayList<>();
                    for(Geometry.V p:strand.points())projected.add(new Geometry.V(width*.12+p.x()*width*.76/customPath.length(),y2+p.y()*scale*.36,p.z()*scale*.36));
                    for(Recipe.Layer l:custom.layers())drawScreen(projected,l,custom,camera,pose,t,scale*strand.widthScale());
                }
            }
            pose.popPose();g.drawString(font,"01  BLADE SWEEP",22,54,0x65eaf1);g.drawString(font,"02  CORE + SHELL",width/2,54,0xbcb2ff);g.drawString(font,"03  CUSTOM RECIPE / "+customPath.kind().toUpperCase(Locale.ROOT),22,(int)(60+content*.55),0xffc969);
            g.drawCenteredString(font,audit==4?"Emission stopped: geometry cleared":"Custom HEX gradients / width curves / textures / signed flow / Esc to return",width/2,height-18,0x98aac2);
        }
        private void drawScreen(List<Geometry.V> path,Recipe.Layer l,Recipe r,Geometry.V camera,PoseStack pose,double t,double scale){
            List<Geometry.Point> points=Geometry.path(path);List<Geometry.Point> scaled=new ArrayList<>();for(Geometry.Point p:points)scaled.add(new Geometry.Point(p.position(),p.age(),p.distance()/scale));
            BeamRender.draw(Geometry.sweep(scaled,camera,r.section().equals("blade")?"ribbon":r.section(),r.sides(),l.width()*scale,l::widthAt),l,pose,new Geometry.V(0,0,0),t);
        }
    }
}
