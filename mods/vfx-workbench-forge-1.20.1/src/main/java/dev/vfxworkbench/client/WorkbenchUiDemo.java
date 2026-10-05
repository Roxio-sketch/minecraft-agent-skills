package dev.vfxworkbench.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.vfxworkbench.core.ParamRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in, title-screen UI interaction/capture; never loads a Minecraft save. */
@Mod.EventBusSubscriber(modid=VfxWorkbenchMod.MODID,value=Dist.CLIENT)
public final class WorkbenchUiDemo {
    private record Case(String name,int width,int height,String skill,String action) {}
    private static final Case[] CASES=Boolean.getBoolean("vfxworkbench.workbenchFeatureRegression")?new Case[]{
        new Case("lightning-gradient-ratio",854,480,"lightning","gradient"),
        new Case("black-hole-player-lens",854,480,"black-hole","player-toggle")
    }:new Case[]{
        new Case("lightning-854-top",854,480,"lightning","top"),
        new Case("lightning-854-bottom",854,480,"lightning","bottom"),
        new Case("lightning-854-number",854,480,"lightning","number"),
        new Case("lightning-854-enum",854,480,"lightning","enum"),
        new Case("black-hole-854-top",854,480,"black-hole","top"),
        new Case("water-854-top",854,480,"water-ripples","top"),
        new Case("color-854-hex",854,480,"water-ripples","color"),
        new Case("lightning-426-top",426,240,"lightning","top"),
        new Case("lightning-426-enum",426,240,"lightning","enum"),
        new Case("black-hole-426-bottom",426,240,"black-hole","bottom"),
        new Case("color-426-hex",426,240,"water-ripples","color"),
        new Case("lightning-320-top",320,240,"lightning","top"),
        new Case("lightning-320-number",320,240,"lightning","number"),
        new Case("lightning-320-enum",320,240,"lightning","enum"),
        new Case("black-hole-320-top",320,240,"black-hole","top"),
        new Case("water-320-top",320,240,"water-ripples","top"),
        new Case("water-320-bottom",320,240,"water-ripples","bottom"),
        new Case("color-320-hex",320,240,"water-ripples","color")
    };
    private static boolean opened,done,oldFullscreen,oldPause;
    private static int ticks,oldGuiScale,oldWidth,oldHeight,oldX,oldY,current;
    private static String pendingCapture;
    private static Path folder,preferenceFile;
    private static byte[] preferenceBackup;
    private static final Map<String,Map<String,String>> parameterBackup=new LinkedHashMap<>();
    private static final JsonArray checks=new JsonArray(),captures=new JsonArray();

    private WorkbenchUiDemo() {}
    private static boolean enabled(){return Boolean.getBoolean("vfxworkbench.workbenchUiRegression")||Boolean.getBoolean("vfxworkbench.workbenchFeatureRegression");}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||done||event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        try{
            if(!opened){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
                opened=true;var window=mc.getWindow();
                oldGuiScale=mc.options.guiScale().get();oldPause=mc.options.pauseOnLostFocus;
                oldWidth=window.getScreenWidth();oldHeight=window.getScreenHeight();oldX=window.getX();oldY=window.getY();oldFullscreen=window.isFullscreen();
                if(oldFullscreen)window.toggleFullScreen();
                mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);
                folder=mc.gameDirectory.toPath().resolve(Boolean.getBoolean("vfxworkbench.workbenchFeatureRegression")?"workbench-feature-probes":"workbench-ui-probes");Files.createDirectories(folder);
                preferenceFile=mc.gameDirectory.toPath().resolve("config/vfxworkbench/ui.json");
                if(Files.isRegularFile(preferenceFile))preferenceBackup=Files.readAllBytes(preferenceFile);
                for(var skill:ParamRegistry.skills())parameterBackup.put(skill.id,ParamRegistry.state(skill.id).snapshot());
                check("title-screen-without-save",mc.level==null&&mc.player==null);
                System.out.println("[WORKBENCH-UI] BEGIN cases="+CASES.length);return;
            }
            if(++ticks<20)return;
            current=(ticks-20)/45;int phase=(ticks-20)%45;
            if(current>=CASES.length)return;
            Case test=CASES[current];
            if(phase==0){
                // GUI scale 2 gives exact 320/426/854 logical widths without
                // shrinking Minecraft below its minimum logical GUI size.
                mc.getWindow().setWindowed(test.width*2,test.height*2);mc.resizeDisplay();
                ParamRegistry.state(test.skill).resetToDefaults();
                if(test.action.equals("gradient"))ParamRegistry.state(test.skill).set("gradientEnabled","true");
            }
            if(phase==8){
                mc.setScreen(new VfxWorkbenchScreen(test.skill));
                check(test.name+"-dimensions",mc.screen.width==test.width&&mc.screen.height==test.height);
                check(test.name+"-widgets-within-screen",widgetsInside(mc.screen));
                check(test.name+"-no-level",mc.level==null&&mc.player==null);
                if(test.action.equals("gradient")||test.action.equals("player-toggle")){
                    var screen=(VfxWorkbenchScreen)mc.screen;
                    focusParam(screen,rows(screen),test.action.equals("gradient")?"gradientRatio":"lensAffectsPlayer");
                    requestCapture(test.name+"-default");
                }
            }
            if(phase==16)interact(mc,test);
            if(phase==22&&(test.action.equals("number")||test.action.equals("gradient")||test.action.equals("enum")||test.action.equals("player-toggle")))requestCapture(test.name+(test.action.equals("enum")?"-dropdown":test.action.equals("player-toggle")?"-on":"-editing"));
            if(phase==28&&test.action.equals("number")){
                mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
                check(test.name+"-numeric-value-committed",Math.abs(ParamRegistry.state(test.skill).getDouble("objectRadius")-7.25)<.000001);
            }
            if(phase==28&&test.action.equals("gradient")){
                mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
                check(test.name+"-numeric-value-committed",ParamRegistry.state(test.skill).getDouble("gradientRatio")==75);
            }
            if(phase==28&&test.action.equals("player-toggle")){
                mc.screen.keyPressed(GLFW.GLFW_KEY_SPACE,0,0);
                check(test.name+"-toggle-disabled-again",!ParamRegistry.state(test.skill).getBool("lensAffectsPlayer"));
            }
            if(phase==28&&test.action.equals("enum")){
                var state=ParamRegistry.state(test.skill);var values=state.schema.get("mode").enumValues;
                int previous=Math.max(0,values.indexOf(state.get("mode"))),next=previous+1<values.size()?previous+1:previous-1;
                mc.screen.keyPressed(next>previous?GLFW.GLFW_KEY_DOWN:GLFW.GLFW_KEY_UP,0,0);mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
                check(test.name+"-enum-selection-committed",state.get("mode").equals(values.get(next))&&next!=previous);
            }
            if(phase==35){
                check(test.name+"-final-widgets-within-screen",widgetsInside(mc.screen));
                if(test.action.equals("color"))check(test.name+"-hex-value-committed",ParamRegistry.state(test.skill).getColor("color")==0x3355AA);
                requestCapture(test.name);System.out.println("[WORKBENCH-UI] "+test.name);
            }
            if(current==CASES.length-1&&phase==40)finish(mc,null);
        }catch(Throwable failure){finish(mc,failure);}
    }

    private static void interact(Minecraft mc,Case test)throws ReflectiveOperationException{
        var screen=(VfxWorkbenchScreen)mc.screen;var rows=rows(screen);
        if(test.action.equals("top")){
            var preview=screen.children().stream().filter(child->child instanceof AbstractWidget widget&&widget.getMessage().getString().equals("触发预览")).map(child->(AbstractWidget)child).findFirst().orElseThrow();
            check(test.name+"-preview-click-without-player",click(screen,preview.getX()+preview.getWidth()/2.0,preview.getY()+preview.getHeight()/2.0));
        }else if(test.action.equals("bottom")){
            check(test.name+"-wheel-event",screen.mouseScrolled(rows.getX()+rows.getWidth()/2.0,rows.getY()+rows.getHeight()/2.0,-1000));
            double maximum=Math.max(0,((Number)field(rows,"contentHeight")).doubleValue()-rows.getHeight());
            check(test.name+"-scroll-reaches-bottom",Math.abs(rows.viewState().scroll()-maximum)<.000001&&maximum>0);
        }else if(test.action.equals("number")||test.action.equals("gradient")){
            String key=test.action.equals("gradient")?"gradientRatio":"objectRadius";
            focusParam(screen,rows,key);double yy=rowY(rows,key);
            double xx=((Number)method(rows,"valueX")).doubleValue()+((Number)method(rows,"valueWidth")).doubleValue()/2;
            check(test.name+"-number-box-click",click(screen,xx,yy+32));
            check(test.name+"-number-box-remains-editing",key.equals(field(rows,"editingKey")));
            type(screen,test.action.equals("gradient")?"75":"7.25");
        }else if(test.action.equals("player-toggle")){
            focusParam(screen,rows,"lensAffectsPlayer");double yy=rowY(rows,"lensAffectsPlayer");
            double xx=((Number)method(rows,"cx")).doubleValue()+((Number)method(rows,"cw")).doubleValue()/2;
            check(test.name+"-toggle-control-click",click(screen,xx,yy+32));
            check(test.name+"-toggle-enabled",ParamRegistry.state(test.skill).getBool("lensAffectsPlayer"));
        }else if(test.action.equals("enum")){
            focusParam(screen,rows,"mode");double yy=rowY(rows,"mode");
            double xx=((Number)method(rows,"cx")).doubleValue()+((Number)method(rows,"cw")).doubleValue()/2;
            check(test.name+"-enum-control-click",click(screen,xx,yy+32));
            check(test.name+"-dropdown-open","mode".equals(field(rows,"enumKey")));
        }else if(test.action.equals("color")){
            focusParam(screen,rows,"color");double yy=rowY(rows,"color");
            double xx=((Number)method(rows,"cx")).doubleValue()+((Number)method(rows,"cw")).doubleValue()/2;
            check(test.name+"-color-control-click",click(screen,xx,yy+32));
            check(test.name+"-color-screen-open",mc.screen instanceof ColorPickerScreen);
            Screen picker=mc.screen;var entry=picker.children().stream().filter(child->child instanceof WorkbenchUi.Entry widget&&widget.getMessage().getString().equals("HEX颜色")).map(child->(WorkbenchUi.Entry)child).findFirst().orElseThrow();
            check(test.name+"-hex-entry-click",click(picker,entry.getX()+entry.getWidth()/2.0,entry.getY()+4));type(picker,"#3355AA");
        }
    }

    private static ScrollableRows rows(Screen screen){return screen.children().stream().filter(child->child instanceof ScrollableRows).map(child->(ScrollableRows)child).findFirst().orElseThrow();}
    private static void focusParam(Screen screen,ScrollableRows rows,String key)throws ReflectiveOperationException{
        screen.setFocused(rows);screen.keyPressed(GLFW.GLFW_KEY_HOME,0,0);
        for(int i=0;i<200&&!key.equals(field(rows,"focusKey"));i++)screen.keyPressed(GLFW.GLFW_KEY_DOWN,0,0);
        if(!key.equals(field(rows,"focusKey")))throw new IllegalStateException("Cannot focus visible UI parameter "+key);
    }
    // Read the actual rendered row coordinates; no hidden control-state writes.
    private static double rowY(ScrollableRows rows,String key)throws ReflectiveOperationException{
        for(Object visual:(java.util.List<?>)field(rows,"visuals"))if(key.equals(method(visual,"key")))
            return rows.getY()+((Number)method(visual,"offset")).doubleValue()-Math.round(rows.viewState().scroll());
        throw new IllegalStateException("Parameter is not rendered: "+key);
    }
    private static Object field(Object object,String name)throws ReflectiveOperationException{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static Object method(Object object,String name)throws ReflectiveOperationException{var method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(object);}
    private static boolean click(Screen screen,double x,double y){boolean handled=screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);return handled;}
    private static void type(Screen screen,String value){
        // EditBox queries physical modifier state for Ctrl+A. Plain End and
        // Backspace events exercise text editing without synthesizing globals.
        screen.keyPressed(GLFW.GLFW_KEY_END,0,0);for(int i=0;i<128;i++)screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE,0,0);
        for(char character:value.toCharArray())screen.charTyped(character,0);
    }
    private static boolean widgetsInside(Screen screen){
        for(var child:screen.children())if(child instanceof AbstractWidget widget){
            if(widget.getX()<0||widget.getY()<0||widget.getX()+widget.getWidth()>screen.width||widget.getY()+widget.getHeight()>screen.height)return false;
        }
        return true;
    }
    private static void check(String name,boolean passed){
        var result=new JsonObject();result.addProperty("name",name);result.addProperty("passed",passed);checks.add(result);
        if(!passed)throw new IllegalStateException("UI check failed: "+name);
    }
    private static void requestCapture(String name){if(pendingCapture!=null)throw new IllegalStateException("Previous UI frame not captured: "+pendingCapture);pendingCapture=name;}

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(!enabled()||done||event.phase!=TickEvent.Phase.END||pendingCapture==null)return;
        Minecraft mc=Minecraft.getInstance();String name=pendingCapture;pendingCapture=null;
        try{
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(name+".png"));}
            var capture=new JsonObject();capture.addProperty("name",name);capture.addProperty("screen",mc.screen.getClass().getSimpleName());
            capture.addProperty("guiWidth",mc.screen.width);capture.addProperty("guiHeight",mc.screen.height);
            capture.addProperty("framebufferWidth",mc.getMainRenderTarget().width);capture.addProperty("framebufferHeight",mc.getMainRenderTarget().height);
            capture.addProperty("guiScale",mc.getWindow().getGuiScale());capture.addProperty("playerPresent",mc.player!=null);capture.addProperty("levelPresent",mc.level!=null);
            if(mc.screen instanceof VfxWorkbenchScreen screen){var rows=rows(screen);capture.addProperty("scroll",rows.viewState().scroll());capture.addProperty("viewportHeight",rows.getHeight());capture.addProperty("visibleParameters",rows.visibleCount());}
            captures.add(capture);Files.writeString(folder.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(capture));
        }catch(Throwable failure){finish(mc,failure);}
    }

    private static void finish(Minecraft mc,Throwable failure){
        if(done)return;done=true;
        try{
            mc.setScreen(new TitleScreen());
            for(var entry:parameterBackup.entrySet())ParamRegistry.state(entry.getKey()).load(entry.getValue());
            mc.options.guiScale().set(oldGuiScale);mc.options.pauseOnLostFocus=oldPause;
            mc.getWindow().setWindowed(oldWidth,oldHeight);GLFW.glfwSetWindowPos(mc.getWindow().getWindow(),oldX,oldY);
            if(oldFullscreen&&!mc.getWindow().isFullscreen())mc.getWindow().toggleFullScreen();mc.resizeDisplay();
            if(preferenceFile!=null){if(preferenceBackup==null)Files.deleteIfExists(preferenceFile);else Files.write(preferenceFile,preferenceBackup);}
            var report=new JsonObject();report.addProperty("status",failure==null?"passed":"failed");report.addProperty("caseCount",CASES.length);
            report.addProperty("worldLoaded",mc.level!=null);report.addProperty("settingsRestored",true);report.add("checks",checks);report.add("captures",captures);
            if(failure!=null){report.addProperty("failure",failure.toString());failure.printStackTrace();}
            if(folder!=null)Files.writeString(folder.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("[WORKBENCH-UI] "+(failure==null?"COMPLETE":"FAILED: "+failure));
        }catch(Throwable restoreFailure){restoreFailure.printStackTrace();}
        finally{mc.stop();}
    }
}
