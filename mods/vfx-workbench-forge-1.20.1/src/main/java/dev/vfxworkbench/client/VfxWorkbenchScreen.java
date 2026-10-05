package dev.vfxworkbench.client;

import dev.vfxworkbench.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Live schema editor with responsive, custom-drawn workbench controls. */
public final class VfxWorkbenchScreen extends Screen {
    private ScrollableRows rows;
    private String selectedSkill;
    private final Map<String,ScrollableRows.ViewState> views=new HashMap<>();
    private int panelX,panelY,panelWidth,panelHeight,padding,footerY,rowsY;
    private boolean compact;
    private String status="";
    private int statusColor=WorkbenchUi.ACCENT,statusTicks;

    public VfxWorkbenchScreen(){this(WorkbenchPreferences.selectedSkill());}
    /** Explicit skill selection also gives development capture code a real UI entry point. */
    public VfxWorkbenchScreen(String skill){super(Component.literal("特效调试工作台"));selectedSkill=skill;}

    @Override protected void init(){
        rememberRows();
        setFocused(null);setDragging(false);
        List<SkillSchema> skills=ParamRegistry.skills();
        if(skills.isEmpty()){dev.vfxworkbench.effects.water.WaterSkillSchema.register();skills=ParamRegistry.skills();}
        if(selectedSkill==null||!ParamRegistry.has(selectedSkill))selectedSkill=skills.get(0).id;
        WorkbenchPreferences.remember(selectedSkill);
        compact=height<310;
        int target=width<560?Math.min(390,width-16):Math.max(360,Math.min(420,(int)(width*.44)));
        panelWidth=Math.max(1,Math.min(width-12,target));
        panelX=width<560?(width-panelWidth)/2:width-panelWidth-10;
        panelY=8;panelHeight=Math.max(1,height-16);padding=Math.min(12,Math.max(4,panelWidth/24));
        int innerX=panelX+padding,innerWidth=Math.max(1,panelWidth-2*padding);
        int gap=6,tabHeight=compact?18:22,tabY=panelY+(compact?26:32);
        int columns=Math.max(1,Math.min(skills.size(),innerWidth/86));
        int tabRows=(skills.size()+columns-1)/columns;
        for(int i=0;i<skills.size();i++){
            SkillSchema skill=skills.get(i);int column=i%columns,yy=tabY+i/columns*(tabHeight+gap);
            int xx=gridX(innerX,innerWidth,columns,column,gap),ww=gridWidth(innerWidth,columns,column,gap);
            var tab=new WorkbenchUi.ActionButton(xx,yy,ww,tabHeight,skill.name,false,()->selectSkill(skill.id)).selected(skill.id.equals(selectedSkill));
            tab.setTooltip(Tooltip.create(Component.literal(skill.name)));addRenderableWidget(tab);
        }
        int actionY=tabY+tabRows*tabHeight+(tabRows-1)*gap+8,actionHeight=compact?20:24;
        addGridButton(innerX,innerWidth,actionY,actionHeight,0,"触发预览",true,()->preview(false));
        addGridButton(innerX,innerWidth,actionY,actionHeight,1,"停止效果",false,this::stopEffect);
        addGridButton(innerX,innerWidth,actionY,actionHeight,2,"重建效果",false,()->preview(true));
        rowsY=actionY+actionHeight+(compact?4:24);
        footerY=panelY+panelHeight-(compact?68:78);
        rows=addRenderableWidget(new ScrollableRows(ParamRegistry.state(selectedSkill),ParamRegistry.schema(selectedSkill),innerX,rowsY,innerWidth,Math.max(rowsY+1,footerY-8)));
        rows.restore(views.get(selectedSkill));
        int buttonY=footerY+(compact?18:22),buttonH=compact?20:24,secondY=buttonY+buttonH+5;
        addGridButton(innerX,innerWidth,buttonY,buttonH,0,"恢复默认",false,()->{rows.finishEditing();ParamRegistry.state(selectedSkill).resetToDefaults();notice("已恢复默认参数",false);});
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
    private void rememberRows(){if(rows!=null&&selectedSkill!=null){rows.finishEditing();views.put(selectedSkill,rows.viewState());}}
    private void selectSkill(String skill){
        if(skill.equals(selectedSkill))return;
        rememberRows();rows=null;selectedSkill=skill;WorkbenchPreferences.remember(skill);statusTicks=0;rebuildWidgets();
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
        rows.finishEditing();String json=selectedSkill.equals("lightning")?dev.vfxworkbench.effects.lightning.LightningSkillSchema.exportRecipe():PresetManager.exportJson(ParamRegistry.state(selectedSkill));
        minecraft.keyboardHandler.setClipboard(json);notice("配方已复制到剪贴板",false);
    }
    private void importPreset(){
        rows.finishEditing();try{
            String text=minecraft.keyboardHandler.getClipboard().trim();
            if(selectedSkill.equals("lightning")){
                dev.vfxworkbench.effects.lightning.LightningSkillSchema.importRecipe(text);notice("闪电配方已导入",false);return;
            }
            var root=new com.google.gson.Gson().fromJson(text,com.google.gson.JsonObject.class);
            if(root==null)throw new IllegalArgumentException("剪贴板不是配方对象");
            if(root.has("parameters")&&root.get("parameters").isJsonObject())root=root.getAsJsonObject("parameters");
            if(root.has("values")&&root.get("values").isJsonObject())root=root.getAsJsonObject("values");
            Map<String,String> values=new LinkedHashMap<>();
            for(var e:root.entrySet())if(e.getValue().isJsonPrimitive())values.put(e.getKey(),e.getValue().getAsString());
            if(selectedSkill.equals("black-hole")&&root.has("viewDistance"))values.put("distance",Double.toString(root.get("viewDistance").getAsDouble()*1.6));
            if(selectedSkill.equals("black-hole")&&root.has("lensBackground"))values.put("lens",Boolean.toString(root.get("lensBackground").getAsDouble()>0));
            ParamRegistry.state(selectedSkill).load(selectedSkill.equals(dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema.ID)
                ?dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema.recipeParameters(values):values);notice("已导入 "+values.size()+" 项参数",false);
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
        if(!compact){
            WorkbenchUi.text(g,font,"参数",innerX,rowsY-14,60,WorkbenchUi.TEXT);
            String hint="拖动调节 · 点击数值输入";
            WorkbenchUi.text(g,font,hint,innerX+innerWidth-font.width(hint),rowsY-14,innerWidth-65,WorkbenchUi.MUTED);
        }
        g.fill(innerX,footerY,innerX+innerWidth,footerY+1,WorkbenchUi.BORDER);
        String message=statusTicks>0?status:rows.visibleCount()+" 项参数 · 滚轮浏览 / Tab 切换";
        WorkbenchUi.text(g,font,message,innerX,footerY+5,innerWidth,statusTicks>0?statusColor:WorkbenchUi.MUTED);
        super.render(g,mx,my,partial);
        rows.renderTooltip(g,mx,my);
    }
    @Override public void tick(){super.tick();if(rows!=null)rows.tickEntries();if(statusTicks>0)statusTicks--;}
    @Override public boolean mouseClicked(double x,double y,int button){
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
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE&&rows!=null&&rows.isFocused()&&rows.keyPressed(key,scan,mods))return true;
        if(key==GLFW.GLFW_KEY_TAB&&(mods&GLFW.GLFW_MOD_CONTROL)!=0){
            List<SkillSchema> skills=ParamRegistry.skills();int index=0;for(int i=0;i<skills.size();i++)if(skills.get(i).id.equals(selectedSkill))index=i;
            selectSkill(skills.get(Math.floorMod(index+((mods&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),skills.size())).id);return true;
        }
        var openKey=VfxWorkbenchMod.ClientMod.OPEN_WORKBENCH;
        if(openKey.matches(key,scan)&&openKey.getKeyModifier().isActive(openKey.getKeyConflictContext())){onClose();return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public void removed(){rememberRows();super.removed();}
    @Override public void onClose(){rememberRows();super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
}
