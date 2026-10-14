package dev.vfxworkbench.client;

import dev.vfxworkbench.core.SkillSchema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.function.Consumer;

/** One project button, with a modal, bounded and scrollable menu. */
public final class ProjectSelector extends AbstractWidget {
    private final List<SkillSchema> skills;
    private final Consumer<String> select;
    private final int screenHeight;
    private final String selected;
    private final String title;
    private boolean open;
    private int cursor,first;
    private static final int ROW=26;
    public ProjectSelector(int x,int y,int width,int height,List<SkillSchema> skills,String selected,int screenHeight,Consumer<String> select){
        super(x,y,width,height,Component.literal("选择项目"));this.skills=List.copyOf(skills);this.select=select;this.selected=selected;this.screenHeight=screenHeight;
        for(int i=0;i<skills.size();i++)if(skills.get(i).id.equals(selected))cursor=i;
        title=skills.stream().filter(s->s.id.equals(selected)).map(s->s.name).findFirst().orElse(selected);
    }
    public boolean isOpen(){return open;}
    public void close(){open=false;}
    public int menuY(){return getY()+getHeight()+4;}
    public int visibleRows(){return Math.max(1,Math.min(skills.size(),Math.min(8,(screenHeight-menuY()-10)/ROW)));}
    private void reveal(){first=Math.max(0,Math.min(first,skills.size()-visibleRows()));if(cursor<first)first=cursor;if(cursor>=first+visibleRows())first=cursor-visibleRows()+1;}
    private void toggle(){open=!open;if(open)reveal();}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        var f=Minecraft.getInstance().font;
        WorkbenchUi.frame(g,getX(),getY(),getWidth(),getHeight(),open||isHovered()?WorkbenchUi.HOVER:WorkbenchUi.SURFACE,isFocused()||open?WorkbenchUi.ACCENT:WorkbenchUi.BORDER);
        WorkbenchUi.text(g,f,"项目："+title,getX()+10,getY()+(getHeight()-9)/2,getWidth()-36,WorkbenchUi.ACCENT);
        int ax=getX()+getWidth()-18,ay=getY()+getHeight()/2;
        for(int r=0;r<3;r++)g.fill(ax+r,ay+(open?2-r:r),ax+7-r,ay+(open?2-r:r)+1,WorkbenchUi.ACCENT);
    }
    public void renderMenu(GuiGraphics g,int mx,int my){
        if(!open)return;var f=Minecraft.getInstance().font;
        g.pose().pushPose();g.pose().translate(0,0,300);
        WorkbenchUi.frame(g,getX(),menuY(),getWidth(),visibleRows()*ROW+2,WorkbenchUi.SURFACE,WorkbenchUi.BORDER);
        g.enableScissor(getX()+1,menuY()+1,getX()+getWidth()-1,menuY()+visibleRows()*ROW+1);
        for(int r=0;r<visibleRows();r++){
            int i=first+r,y=menuY()+1+r*ROW;var s=skills.get(i);
            boolean hover=WorkbenchUi.inside(mx,my,getX()+1,y,getWidth()-2,ROW);
            if(hover||i==cursor)g.fill(getX()+1,y,getX()+getWidth()-1,y+ROW,WorkbenchUi.HOVER);
            if(s.id.equals(selected))g.fill(getX()+2,y+5,getX()+5,y+ROW-5,WorkbenchUi.ACCENT);
            WorkbenchUi.text(g,f,s.name,getX()+12,y+9,getWidth()-28,s.id.equals(selected)?WorkbenchUi.ACCENT:WorkbenchUi.TEXT);
        }
        g.disableScissor();
        if(skills.size()>visibleRows()){
            int h=visibleRows()*ROW;int thumb=Math.max(12,h*visibleRows()/skills.size());int y=menuY()+1+(h-thumb)*first/(skills.size()-visibleRows());
            g.fill(getX()+getWidth()-5,y,getX()+getWidth()-3,y+thumb,WorkbenchUi.ACCENT);
        }
        g.pose().popPose();
    }
    /** The screen routes all clicks here while open, including outside dismissal. */
    public boolean menuClick(double x,double y,int button){
        if(!open)return false;
        if(WorkbenchUi.inside(x,y,getX(),getY(),getWidth(),getHeight())){close();return true;}
        if(button==0&&WorkbenchUi.inside(x,y,getX(),menuY()+1,getWidth(),visibleRows()*ROW)){
            int i=first+(int)((y-menuY()-1)/ROW);close();select.accept(skills.get(i).id);return true;
        }
        close();return true;
    }
    @Override public void onClick(double x,double y){toggle();}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(!open)return false;first=Math.max(0,Math.min(skills.size()-visibleRows(),first-(int)Math.signum(delta)*Math.max(1,(int)Math.abs(delta))));return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE&&open){close();return true;}
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE){if(open){close();select.accept(skills.get(cursor).id);}else toggle();return true;}
        if(key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_HOME||key==GLFW.GLFW_KEY_END||key==GLFW.GLFW_KEY_PAGE_DOWN||key==GLFW.GLFW_KEY_PAGE_UP){
            if(!open)toggle();cursor=switch(key){case GLFW.GLFW_KEY_HOME->0;case GLFW.GLFW_KEY_END->skills.size()-1;case GLFW.GLFW_KEY_PAGE_DOWN->Math.min(skills.size()-1,cursor+visibleRows());case GLFW.GLFW_KEY_PAGE_UP->Math.max(0,cursor-visibleRows());default->Math.floorMod(cursor+(key==GLFW.GLFW_KEY_DOWN?1:-1),skills.size());};reveal();return true;
        }
        return false;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out){out.add(NarratedElementType.TITLE,Component.literal("项目选择："+skills.get(cursor).name));out.add(NarratedElementType.USAGE,Component.literal("回车展开或选择，上下键浏览，Escape 关闭列表"));}
}
