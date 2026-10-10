package dev.vfxworkbench.client;

import dev.vfxworkbench.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Live schema editor with responsive, custom-drawn workbench controls. */
public final class VfxWorkbenchScreen extends Screen {
    private ScrollableRows rows;
    private ProjectSelector projectSelector;
    private String selectedSkill;
    private boolean revealProjectSelection;
    private int layoutWidth,layoutHeight;
    private int panelX,panelY,panelWidth,panelHeight,padding,footerY,rowsY;
    private boolean compact;
    private String status="";
    private int statusColor=WorkbenchUi.ACCENT,statusTicks;

    public VfxWorkbenchScreen(){this(WorkbenchPreferences.selectedSkill(),false);}
    /** Explicit skill selection also gives development capture code a real UI entry point. */
    public VfxWorkbenchScreen(String skill){this(skill,!Objects.equals(skill,WorkbenchPreferences.selectedSkill()));}
    private VfxWorkbenchScreen(String skill,boolean reveal){super(Component.literal("特效调试工作台"));selectedSkill=skill;revealProjectSelection=reveal;}

    @Override protected void init(){
        rememberUi();
        setFocused(null);setDragging(false);
        List<SkillSchema> skills=ParamRegistry.skills();
        if(skills.isEmpty()){dev.vfxworkbench.effects.water.WaterSkillSchema.register();skills=ParamRegistry.skills();}
        if(selectedSkill==null||!ParamRegistry.has(selectedSkill)){selectedSkill=skills.get(0).id;revealProjectSelection=true;}
        WorkbenchPreferences.remember(selectedSkill);
        compact=height<310;
        int target=width<560?Math.min(390,width-16):Math.max(360,Math.min(420,(int)(width*.44)));
        panelWidth=Math.max(1,Math.min(width-12,target));
        panelX=width<560?(width-panelWidth)/2:width-panelWidth-10;
        panelY=8;panelHeight=Math.max(1,height-16);padding=Math.min(12,Math.max(4,panelWidth/24));
        int innerX=panelX+padding,innerWidth=Math.max(1,panelWidth-2*padding);
        int tabHeight=compact?22:26,tabY=panelY+(compact?26:32);
        projectSelector=addRenderableWidget(new ProjectSelector(innerX,tabY,innerWidth,tabHeight,skills,selectedSkill,height,this::selectSkill));
        revealProjectSelection=false;layoutWidth=width;layoutHeight=height;
        int actionY=tabY+tabHeight+8,actionHeight=compact?20:24;
        addActionButton(innerX,innerWidth,actionY,actionHeight,0,"触发预览",true,()->preview(false));
        addActionButton(innerX,innerWidth,actionY,actionHeight,1,"停止效果",false,this::stopEffect);
        addActionButton(innerX,innerWidth,actionY,actionHeight,2,"重建效果",false,()->preview(true));
        addActionButton(innerX,innerWidth,actionY,actionHeight,3,"随机方案",false,this::randomScheme);
        rowsY=actionY+actionHeight+(compact?(selectedSkill.equals("lightning")?16:4):24);
        footerY=panelY+panelHeight-(compact?68:78);
        rows=addRenderableWidget(new ScrollableRows(ParamRegistry.state(selectedSkill),ParamRegistry.schema(selectedSkill),innerX,rowsY,innerWidth,Math.max(rowsY+1,footerY-8)));
        rows.restore(WorkbenchPreferences.rowView(selectedSkill));
        int buttonY=footerY+(compact?18:22),buttonH=compact?20:24,secondY=buttonY+buttonH+5;
        addGridButton(innerX,innerWidth,buttonY,buttonH,0,"恢复默认",false,()->{
            rows.finishEditing();var state=ParamRegistry.state(selectedSkill);
            if(selectedSkill.equals("black-hole")&&state.get("objectMode").equals("white")){
                state.set("form","white-ring");dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema.applyForm(state,"white-ring");
            }else state.resetToDefaults();
            notice("已恢复默认参数",false);
        });
        addGridButton(innerX,innerWidth,buttonY,buttonH,1,"保存预设",false,this::savePreset);
        addGridButton(innerX,innerWidth,buttonY,buttonH,2,"读取预设",false,this::loadPreset);
        addGridButton(innerX,innerWidth,secondY,buttonH,0,"导出配方",false,this::exportPreset);
        addGridButton(innerX,innerWidth,secondY,buttonH,1,"导入配方",false,this::importPreset);
        addGridButton(innerX,innerWidth,secondY,buttonH,2,"返回游戏",false,this::onClose);
    }

    private static int gridX(int x,int width,int columns,int column,int gap){return x+column*(width+gap)/columns;}
    private static int gridWidth(int width,int columns,int column,int gap){return (column+1)*(width+gap)/columns-column*(width+gap)/columns-gap;}
    private void addGridButton(int x,int width,int y,int height,int column,String label,boolean primary,Runnable action){
        addRenderableWidget(new WorkbenchUi.ActionButton(gridX(x,width,3,column,6),y,gridWidth(width,3,column,6),height,label,primary,action));
    }
    private void addActionButton(int x,int width,int y,int height,int column,String label,boolean primary,Runnable action){
        addRenderableWidget(new WorkbenchUi.ActionButton(gridX(x,width,4,column,6),y,gridWidth(width,4,column,6),height,label,primary,action));
    }
    private void randomScheme(){
        rows.finishEditing();
        try{
            var state=ParamRegistry.state(selectedSkill);
            var values=RandomSchemes.generate(state,java.util.concurrent.ThreadLocalRandom.current().nextLong());
            if(selectedSkill.equals("black-hole"))dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema.loadRecipe(state,values);
            else if(selectedSkill.equals("lightning")){
                var wrapper=new com.google.gson.JsonObject();wrapper.add("values",new com.google.gson.Gson().toJsonTree(values));
                dev.vfxworkbench.effects.lightning.LightningSkillSchema.importRecipe(wrapper);
            }else state.loadAtomic(values);
            if(minecraft.player!=null&&minecraft.level!=null)WorkbenchEffects.trigger(selectedSkill,false);
            notice("随机方案已生成 · 喜欢可保存预设",false);
        }catch(Exception ex){notice("随机失败："+ex.getMessage(),true);}
    }
    private void rememberUi(){
        if(rows!=null&&selectedSkill!=null){rows.finishEditing();WorkbenchPreferences.rememberRows(selectedSkill,rows.viewState());}
    }
    private void selectSkill(String skill){
        if(skill.equals(selectedSkill)){projectSelector.close();return;}
        boolean navigationFocused=getFocused()==projectSelector;
        rememberUi();rows=null;selectedSkill=skill;revealProjectSelection=true;WorkbenchPreferences.remember(skill);statusTicks=0;rebuildWidgets();
        if(navigationFocused)setFocused(projectSelector);
    }
    private void preview(boolean rebuild){
        rows.finishEditing();
        if(minecraft.player==null||minecraft.level==null){notice("请进入世界后预览效果",true);return;}
        WorkbenchEffects.trigger(selectedSkill,rebuild);
        notice(rebuild?"效果已重建":"效果已触发",false);
    }
    private void stopEffect(){
        WorkbenchEffects.stop(selectedSkill);
        notice("效果已停止",false);
    }
    private void notice(String message,boolean error){status=message;statusColor=error?WorkbenchUi.ERROR:WorkbenchUi.ACCENT;statusTicks=120;}
    private void savePreset(){
        rows.finishEditing();try{PresetManager.save(minecraft.gameDirectory.toPath(),selectedSkill,"last",ParamRegistry.state(selectedSkill));notice("预设已保存",false);}
        catch(Exception ex){notice("保存失败："+ex.getMessage(),true);}
    }
    private void loadPreset(){
        rows.finishEditing();try{
            if(selectedSkill.equals("lightning")){
                var file=PresetManager.presetDir(minecraft.gameDirectory.toPath(),selectedSkill).resolve("last.json");
                if(java.nio.file.Files.isRegularFile(file))dev.vfxworkbench.effects.lightning.LightningSkillSchema.importRecipe(java.nio.file.Files.readString(file));
                else ParamRegistry.state(selectedSkill).resetToDefaults();
            }else PresetManager.load(minecraft.gameDirectory.toPath(),selectedSkill,"last",ParamRegistry.state(selectedSkill));
            notice("预设已读取",false);
        }
        catch(Exception ex){notice("读取失败："+ex.getMessage(),true);}
    }
    private void exportPreset(){
        rows.finishEditing();String json=selectedSkill.equals("lightning")?dev.vfxworkbench.effects.lightning.LightningSkillSchema.exportRecipe()
            :selectedSkill.equals("black-hole")?dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema.exportRecipe(ParamRegistry.state(selectedSkill))
            :PresetManager.exportJson(ParamRegistry.state(selectedSkill));
        minecraft.keyboardHandler.setClipboard(json);notice("配方已复制到剪贴板",false);
    }
    private void importPreset(){
        rows.finishEditing();try{
            String text=minecraft.keyboardHandler.getClipboard().trim();
            if(selectedSkill.equals("lightning")){
                dev.vfxworkbench.effects.lightning.LightningSkillSchema.importRecipe(text);notice("闪电配方已导入",false);return;
            }
            if(selectedSkill.equals("black-hole")){
                dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema.importRecipe(text);notice("黑洞 / 白洞配方已导入",false);return;
            }
            var root=new com.google.gson.Gson().fromJson(text,com.google.gson.JsonObject.class);
            if(root==null)throw new IllegalArgumentException("剪贴板不是配方对象");
            if(root.has("parameters")&&root.get("parameters").isJsonObject())root=root.getAsJsonObject("parameters");
            if(root.has("values")&&root.get("values").isJsonObject())root=root.getAsJsonObject("values");
            Map<String,String> values=new LinkedHashMap<>();
            for(var e:root.entrySet())if(e.getValue().isJsonPrimitive())values.put(e.getKey(),e.getValue().getAsString());
            ParamRegistry.state(selectedSkill).load(values);notice("已导入 "+values.size()+" 项参数",false);
        }catch(Exception ex){notice("导入失败："+ex.getMessage(),true);}
    }

    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        if(minecraft.level==null)g.fill(0,0,width,height,0xFF0B1018);
        g.fill(panelX-4,panelY+3,panelX+panelWidth+4,panelY+panelHeight+4,0x40000000);
        WorkbenchUi.frame(g,panelX,panelY,panelWidth,panelHeight,WorkbenchUi.PANEL,WorkbenchUi.BORDER);
        int innerX=panelX+padding,innerWidth=panelWidth-padding*2;
        int badgeW=font.width("实时预览")+12;
        WorkbenchUi.text(g,font,"特效调试工作台",innerX,panelY+12,innerWidth-badgeW-8,WorkbenchUi.TEXT);
        WorkbenchUi.badge(g,font,"实时预览",panelX+panelWidth-padding-badgeW,panelY+8,badgeW,WorkbenchUi.ACCENT);
        if(selectedSkill.equals("lightning")){
            String hint=ParamRegistry.state(selectedSkill).get("mode").equals("orbit")?"环绕电流持续预览 · 调参即时更新":"短时落雷：调参后请重新触发预览";
            WorkbenchUi.text(g,font,hint,innerX,rowsY-12,innerWidth,WorkbenchUi.ACCENT);
        }else if(!compact){
            WorkbenchUi.text(g,font,"参数",innerX,rowsY-14,60,WorkbenchUi.TEXT);
            String hint="拖动调节 · 点击数值输入";
            WorkbenchUi.text(g,font,hint,innerX+innerWidth-font.width(hint),rowsY-14,innerWidth-65,WorkbenchUi.MUTED);
        }
        g.fill(innerX,footerY,innerX+innerWidth,footerY+1,WorkbenchUi.BORDER);
        String message=statusTicks>0?status:rows.visibleCount()+" 项参数 · 滚轮浏览 / Tab 切换";
        WorkbenchUi.text(g,font,message,innerX,footerY+5,innerWidth,statusTicks>0?statusColor:WorkbenchUi.MUTED);
        super.render(g,mx,my,partial);
        if(!projectSelector.isOpen())rows.renderTooltip(g,mx,my);
        projectSelector.renderMenu(g,mx,my);
    }
    @Override public void tick(){super.tick();if(rows!=null)rows.tickEntries();if(statusTicks>0)statusTicks--;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(projectSelector!=null&&projectSelector.isOpen())return projectSelector.menuClick(x,y,button);
        if(rows!=null&&WorkbenchUi.inside(x,y,rows.getX(),rows.getY(),rows.getWidth(),rows.getHeight())){
            // ContainerEventHandler re-focuses even an already-focused child
            // AFTER onClick; that would immediately blur a newly opened editor.
            if(getFocused()!=rows)setFocused(rows);
            if(rows.mouseClicked(x,y,button)){setDragging(button==0);return true;}
            return false;
        }
        if(rows!=null)rows.finishEditing();
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(projectSelector!=null&&projectSelector.isOpen())return projectSelector.mouseScrolled(x,y,delta);
        return super.mouseScrolled(x,y,delta);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        var openKey=VfxWorkbenchMod.ClientMod.OPEN_WORKBENCH;
        if(openKey.matches(key,scan)&&openKey.getKeyModifier().isActive(openKey.getKeyConflictContext())){onClose();return true;}
        if(projectSelector!=null&&projectSelector.isOpen()&&!(key==GLFW.GLFW_KEY_TAB&&(mods&GLFW.GLFW_MOD_CONTROL)!=0)){
            projectSelector.keyPressed(key,scan,mods);return true;
        }
        if(key==GLFW.GLFW_KEY_ESCAPE&&rows!=null&&rows.isFocused()&&rows.keyPressed(key,scan,mods))return true;
        if(key==GLFW.GLFW_KEY_TAB&&(mods&GLFW.GLFW_MOD_CONTROL)!=0){
            List<SkillSchema> skills=ParamRegistry.skills();int index=0;for(int i=0;i<skills.size();i++)if(skills.get(i).id.equals(selectedSkill))index=i;
            selectSkill(skills.get(Math.floorMod(index+((mods&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),skills.size())).id);return true;
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public void removed(){rememberUi();super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
