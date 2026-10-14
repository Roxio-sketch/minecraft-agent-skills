package dev.vfxworkbench.client;
import com.google.gson.*;
import dev.vfxworkbench.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;

/** Opt-in dropdown test. Extra projects exist only inside this fixture process. */
@Mod.EventBusSubscriber(modid=VfxWorkbenchMod.MODID,value=Dist.CLIENT)
public final class ProjectTabsDemo {
    private static final String[] CASES={"real-projects-closed","real-projects-dropdown","many-projects-scroll","keyboard-selection","outside-dismiss","f6-row-persistence","project-row-persistence","narrow-dropdown","wide-dropdown","shell-switch-ui","lightning-orbit-layout","lightning-strike-layout","lightning-rain-layout","parameter-popup-depth"};
    private static final JsonArray checks=new JsonArray(),captures=new JsonArray();
    private static boolean opened,done;
    private static int ticks,oldScale,oldWidth,oldHeight,oldX,oldY;
    private static boolean oldPause,oldFullscreen;
    private static byte[] preferenceBackup;
    private static Path folder,preferenceFile;
    private static String pending;
    private static boolean enabled(){return Boolean.getBoolean("vfxworkbench.workbenchNavigationRegression");}
    private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static ProjectSelector selector(Minecraft mc)throws Exception{return (ProjectSelector)field(mc.screen,"projectSelector");}
    private static ScrollableRows rows(Minecraft mc)throws Exception{return (ScrollableRows)field(mc.screen,"rows");}
    private static String selected(Minecraft mc)throws Exception{return (String)field(mc.screen,"selectedSkill");}
    private static void check(String name,boolean pass){var c=new JsonObject();c.addProperty("name",name);c.addProperty("pass",pass);checks.add(c);if(!pass)throw new AssertionError(name);}
    private static void open(Minecraft mc)throws Exception{var s=selector(mc);mc.screen.setFocused(s);mc.screen.mouseClicked(s.getX()+20,s.getY()+10,0);mc.screen.mouseReleased(s.getX()+20,s.getY()+10,0);check("selector-opens",s.isOpen());}
    private static void prepareRows(Minecraft mc)throws Exception{var r=rows(mc);mc.screen.setFocused(r);mc.screen.keyPressed(GLFW.GLFW_KEY_HOME,0,0);String key=(String)field(r,"focusKey");if(key!=null&&key.startsWith("@")&&!r.viewState().collapsed().contains(key.substring(1)))mc.screen.keyPressed(GLFW.GLFW_KEY_SPACE,0,0);mc.screen.mouseScrolled(r.getX()+r.getWidth()/2.0,r.getY()+r.getHeight()/2.0,-8);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        try{
            if(!opened){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;opened=true;
                var w=mc.getWindow();oldWidth=w.getScreenWidth();oldHeight=w.getScreenHeight();oldX=w.getX();oldY=w.getY();oldFullscreen=w.isFullscreen();oldScale=mc.options.guiScale().get();oldPause=mc.options.pauseOnLostFocus;
                if(oldFullscreen)w.toggleFullScreen();mc.options.guiScale().set(2);mc.options.pauseOnLostFocus=false;
                folder=mc.gameDirectory.toPath().resolve("workbench-navigation-probes");Files.createDirectories(folder);preferenceFile=mc.gameDirectory.toPath().resolve("config/vfxworkbench/ui.json");if(Files.exists(preferenceFile))preferenceBackup=Files.readAllBytes(preferenceFile);return;
            }
            int i=ticks/45,p=ticks++%45;if(i>=CASES.length){finish(mc,null);return;}
            if(p==0){
                if(i==2)for(int k=0;k<24;k++)ParamRegistry.register(SkillSchema.of("dropdown-probe-"+k,"扩展项目 "+(k+1)+" · 测试长名称",ParamSpec.hotFloat("example","示例","测试",0,100,1,50)));
                mc.getWindow().setWindowed(i==7?640:i==8?1920:1280,i==7?480:i==8?1080:720);mc.resizeDisplay();mc.setScreen(new VfxWorkbenchScreen(i==9?"black-hole":"lightning"));
            }
            if(p==8){
                if(i>=10&&i<=12){
                    var state=ParamRegistry.state("lightning");String mode=java.util.List.of("orbit","strike","rain").get(i-10);state.set("mode",mode);
                    var r=rows(mc);r.visibleCount();var keys=(java.util.List<String>)field(r,"visibleKeys");
                    check("mode-core-width-"+mode,keys.contains("currentWidth")==mode.equals("orbit")&&keys.contains("strikeWidth")!=mode.equals("orbit"));
                    check("mode-rain-fields-"+mode,keys.contains("rainCount")==mode.equals("rain"));
                    var order=state.schema.params.stream().filter(spec->spec.isVisible(state)).map(spec->spec.group).distinct().toList();
                    check("width-before-color-"+mode,order.indexOf("粗细")<order.indexOf("颜色与亮度"));
                    if(!mode.equals("orbit"))check("color-before-branch-"+mode,order.indexOf("颜色与亮度")<order.indexOf("分叉"));
                    mc.screen.setFocused(r);mc.screen.keyPressed(GLFW.GLFW_KEY_HOME,0,0);
                }

                if(i==13){
                    var r=rows(mc);var state=ParamRegistry.state("lightning");state.set("mode","strike");r.visibleCount();
                    mc.screen.setFocused(r);mc.screen.keyPressed(GLFW.GLFW_KEY_HOME,0,0);
                    for(int k=0;k<20&&!"preset".equals(field(r,"focusKey"));k++){
                        String key=(String)field(r,"focusKey");if(key!=null&&key.startsWith("@")&&r.viewState().collapsed().contains(key.substring(1)))mc.screen.keyPressed(GLFW.GLFW_KEY_SPACE,0,0);
                        mc.screen.keyPressed(GLFW.GLFW_KEY_DOWN,0,0);
                    }
                    check("preset-popup-focus","preset".equals(field(r,"focusKey")));
                    var lookup=r.getClass().getDeclaredMethod("visual",String.class);lookup.setAccessible(true);var visual=lookup.invoke(r,"preset");
                    var offset=visual.getClass().getDeclaredMethod("offset");offset.setAccessible(true);
                    var scroll=r.getClass().getDeclaredField("scroll");scroll.setAccessible(true);scroll.setDouble(r,((Number)offset.invoke(visual)).doubleValue());
                    mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
                    check("preset-popup-open","preset".equals(field(r,"enumKey")));
                }
                var s=selector(mc);check(CASES[i]+"-one-project-button",mc.screen.children().stream().filter(c->c instanceof ProjectSelector).count()==1&&mc.screen.children().stream().noneMatch(c->c instanceof ProjectTabs));
                check(CASES[i]+"-widgets-bounded",mc.screen.children().stream().filter(c->c instanceof AbstractWidget).map(c->(AbstractWidget)c).allMatch(w->w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=mc.screen.width&&w.getY()+w.getHeight()<=mc.screen.height));
                if(i==1||i==7||i==8){open(mc);check("menu-bounded-"+i,s.menuY()+s.visibleRows()*26+2<=mc.screen.height);}
                if(i==2){open(mc);var r=rows(mc).viewState();mc.screen.mouseScrolled(s.getX()+20,s.menuY()+10,-1000);check("scroll-does-not-change-rows",rows(mc).viewState().equals(r));check("scroll-reaches-last",(int)field(s,"first")==ParamRegistry.skills().size()-s.visibleRows());}
                if(i==3){open(mc);mc.screen.keyPressed(GLFW.GLFW_KEY_END,0,0);mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);check("keyboard-selects-last",selected(mc).equals("dropdown-probe-23")&&!selector(mc).isOpen());check("keyboard-focus-retained",mc.screen.getFocused()==selector(mc));}
                if(i==4){var before=rows(mc).viewState();open(mc);mc.screen.mouseClicked(5,5,0);check("outside-dismiss-consumes-click",!s.isOpen()&&rows(mc).viewState().equals(before));}
                if(i==5){prepareRows(mc);var view=rows(mc).viewState();check("rows-scrolled-and-collapsed",view.scroll()>0&&!view.collapsed().isEmpty());open(mc);mc.screen.keyPressed(GLFW.GLFW_KEY_F6,0,0);check("f6-closes-open-dropdown",!(mc.screen instanceof VfxWorkbenchScreen));mc.setScreen(new VfxWorkbenchScreen());check("f6-restores-rows",selected(mc).equals("lightning")&&rows(mc).viewState().equals(view)&&!selector(mc).isOpen());}
                if(i==6){prepareRows(mc);var view=rows(mc).viewState();mc.screen.keyPressed(GLFW.GLFW_KEY_TAB,0,GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SHIFT);check("ctrl-tab-selects-black-hole",selected(mc).equals("black-hole"));prepareRows(mc);var other=rows(mc).viewState();mc.screen.keyPressed(GLFW.GLFW_KEY_TAB,0,GLFW.GLFW_MOD_CONTROL);check("lightning-view-retained",rows(mc).viewState().equals(view));mc.screen.keyPressed(GLFW.GLFW_KEY_TAB,0,GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SHIFT);check("black-hole-view-retained",rows(mc).viewState().equals(other));}
                if(i==9){var state=ParamRegistry.state("black-hole");state.set("form","white-ring");state.set("shellEnabled","false");var r=rows(mc);mc.screen.setFocused(r);mc.screen.keyPressed(GLFW.GLFW_KEY_HOME,0,0);for(int k=0;k<240&&!"shellEnabled".equals(field(r,"focusKey"));k++){String key=(String)field(r,"focusKey");if(key!=null&&key.startsWith("@")&&r.viewState().collapsed().contains(key.substring(1)))mc.screen.keyPressed(GLFW.GLFW_KEY_SPACE,0,0);mc.screen.keyPressed(GLFW.GLFW_KEY_DOWN,0,0);}check("shell-switch-focusable","shellEnabled".equals(field(r,"focusKey")));mc.screen.keyPressed(GLFW.GLFW_KEY_SPACE,0,0);check("shell-switch-retains-strength",state.getBool("shellEnabled")&&state.getDouble("shellStrength")==.28);}
                check(CASES[i]+"-no-save-loaded",mc.level==null&&mc.player==null);
            }
            if(p==29&&i==9)ParamRegistry.state("black-hole").set("shellEnabled","false");
            if(p==30)pending=CASES[i];
            if(p==35&&i==2){var s=selector(mc);mc.screen.mouseClicked(s.getX()+30,s.menuY()+1+(s.visibleRows()-1)*26+10,0);check("mouse-selects-last-project",selected(mc).equals("dropdown-probe-23")&&!selector(mc).isOpen());}
            if(p==35&&i==13)finish(mc,null);
            if(p==35&&i==9){
                var state=ParamRegistry.state("black-hole");state.set("core",".5");state.set("time","123");state.set("shellEnabled","false");
                var restore=mc.screen.children().stream().filter(c->c instanceof AbstractWidget).map(c->(AbstractWidget)c).filter(w->w.getMessage().getString().equals("恢复默认")).findFirst().orElseThrow();
                mc.screen.mouseClicked(restore.getX()+10,restore.getY()+10,0);
                check("white-reset-keeps-white-and-restores-user-values",state.get("form").equals("white-ring")&&state.getDouble("core")==.28&&state.getDouble("time")==37862.8&&state.getDouble("tilt")==-1&&state.getDouble("distance")==39&&!state.getBool("shellEnabled")&&state.getBool("lensAffectsPlayer")&&!state.getBool("particles"));
            }
        }catch(Throwable failure){finish(mc,failure);}
    }
    @SubscribeEvent public static void capture(TickEvent.RenderTickEvent event){if(!enabled()||done||pending==null||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();String name=pending;pending=null;try{try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(folder.resolve(name+".png"));}var c=new JsonObject();c.addProperty("name",name);c.addProperty("selected",selected(mc));c.addProperty("menuOpen",selector(mc).isOpen());c.addProperty("width",mc.screen.width);c.addProperty("height",mc.screen.height);c.addProperty("worldLoaded",false);captures.add(c);}catch(Throwable failure){finish(mc,failure);}}
    private static void finish(Minecraft mc,Throwable failure){if(done)return;done=true;try{
        mc.setScreen(new TitleScreen());mc.options.guiScale().set(oldScale);mc.options.pauseOnLostFocus=oldPause;mc.getWindow().setWindowed(oldWidth,oldHeight);GLFW.glfwSetWindowPos(mc.getWindow().getWindow(),oldX,oldY);if(oldFullscreen&&!mc.getWindow().isFullscreen())mc.getWindow().toggleFullScreen();mc.resizeDisplay();if(preferenceFile!=null){if(preferenceBackup==null)Files.deleteIfExists(preferenceFile);else Files.write(preferenceFile,preferenceBackup);}
        var r=new JsonObject();r.addProperty("fixture","project-dropdown/v1");r.addProperty("status",failure==null?"passed":"failed");r.addProperty("caseCount",CASES.length);r.addProperty("worldLoaded",false);r.addProperty("virtualProjectCount",24);r.add("checks",checks);r.add("captures",captures);if(failure!=null){r.addProperty("failure",failure.toString());failure.printStackTrace();}Files.writeString(folder.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));System.out.println("[WORKBENCH-NAV] "+(failure==null?"COMPLETE":"FAILED"));
    }catch(Throwable error){error.printStackTrace();}finally{mc.stop();}}
}
