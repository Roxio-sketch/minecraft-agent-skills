package dev.vfxworkbench.client;

import dev.vfxworkbench.core.SkillSchema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/** A single-row project strip; adding projects never consumes parameter rows. */
final class ProjectTabs extends AbstractWidget {
    private static final int GAP=6,ARROW=20,INSET=5;
    private final List<SkillSchema> projects;
    private final Consumer<String> select;
    private final int[] offsets,widths;
    private final int selectedIndex,viewportX,viewportWidth,contentWidth;
    private final boolean overflow;
    private double scroll;
    private boolean pressed,dragged;
    private double pressX,startScroll;
    private int pressIndex=-1;
    private String tooltipText="";

    ProjectTabs(int x,int y,int width,int height,List<SkillSchema> projects,String selected,Consumer<String> select){
        super(x,y,width,height,Component.literal("特效项目"));
        this.projects=List.copyOf(projects);this.select=select;
        offsets=new int[projects.size()];widths=new int[projects.size()];
        var font=Minecraft.getInstance().font;
        int natural=0,current=0;
        for(int i=0;i<projects.size();i++){
            natural+=Math.min(200,Math.max(96,font.width(projects.get(i).name)+24))+(i==0?0:GAP);
            if(projects.get(i).id.equals(selected))current=i;
        }
        selectedIndex=current;overflow=natural>width;
        viewportX=x+(overflow?ARROW+INSET:0);
        viewportWidth=Math.max(1,width-(overflow?2*(ARROW+INSET):0));
        int total=0;
        for(int i=0;i<projects.size();i++){
            offsets[i]=total;
            widths[i]=Math.min(viewportWidth,Math.min(200,Math.max(96,font.width(projects.get(i).name)+24)));
            total+=widths[i]+(i+1<projects.size()?GAP:0);
        }
        contentWidth=total;
    }

    double scroll(){return scroll;}
    boolean pointerDown(){return pressed;}
    double maximumScroll(){return Math.max(0,contentWidth-viewportWidth);}
    void restoreScroll(double value){scroll=clamp(value);}
    private double clamp(double value){return Double.isFinite(value)?Math.max(0,Math.min(maximumScroll(),value)):0;}
    void revealSelected(){
        if(projects.isEmpty())return;
        int start=offsets[selectedIndex],end=start+widths[selectedIndex];
        if(start<scroll)scroll=start;
        else if(end>scroll+viewportWidth)scroll=end-viewportWidth;
        scroll=clamp(scroll);
    }
    boolean selectedVisible(){return projects.isEmpty()||offsets[selectedIndex]>=Math.round(scroll)&&offsets[selectedIndex]+widths[selectedIndex]<=Math.round(scroll)+viewportWidth;}

    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        var font=Minecraft.getInstance().font;
        int tabHeight=height-(overflow?4:0);
        g.enableScissor(viewportX,getY(),viewportX+viewportWidth,getY()+tabHeight);
        for(int i=0;i<projects.size();i++){
            int x=viewportX+offsets[i]-(int)Math.round(scroll),w=widths[i];
            if(x+w<=viewportX||x>=viewportX+viewportWidth)continue;
            boolean selected=i==selectedIndex,hover=hitTab(mx,my)==i;
            WorkbenchUi.frame(g,x,getY(),w,tabHeight,selected?WorkbenchUi.ACCENT_BG:hover?WorkbenchUi.HOVER:WorkbenchUi.SURFACE,selected||isFocused()&&i==selectedIndex?WorkbenchUi.ACCENT:WorkbenchUi.BORDER);
            if(selected)g.fill(x+5,getY()+tabHeight-2,x+w-5,getY()+tabHeight-1,WorkbenchUi.ACCENT);
            WorkbenchUi.centered(g,font,projects.get(i).name,x,getY(),w,tabHeight,selected?WorkbenchUi.ACCENT:WorkbenchUi.TEXT);
        }
        g.disableScissor();
        if(overflow){
            renderArrow(g,getX(),false,scroll>0,mx,my,tabHeight);
            renderArrow(g,getX()+width-ARROW,true,scroll<maximumScroll(),mx,my,tabHeight);
            int trackY=getY()+height-2;
            g.fill(viewportX,trackY,viewportX+viewportWidth,trackY+1,WorkbenchUi.TRACK);
            int thumb=Math.max(12,(int)Math.round((double)viewportWidth*viewportWidth/Math.max(1,contentWidth)));
            int thumbX=viewportX+(int)Math.round((viewportWidth-thumb)*scroll/Math.max(1,maximumScroll()));
            g.fill(thumbX,trackY,thumbX+thumb,trackY+1,WorkbenchUi.ACCENT);
        }
        int hovered=hitTab(mx,my);
        String tip=hovered>=0?projects.get(hovered).name+" · "+(hovered+1)+" / "+projects.size()+"\n滚轮或拖动浏览 · 左右键切换 · Ctrl+Tab 切换项目":isMouseOver(mx,my)?"滚轮或拖动浏览项目 · 左右箭头浏览 · Ctrl+Tab 切换项目":"";
        if(!tip.equals(tooltipText)){tooltipText=tip;setTooltip(tip.isEmpty()?null:Tooltip.create(Component.literal(tip)));}
    }
    private void renderArrow(GuiGraphics g,int x,boolean right,boolean enabled,int mx,int my,int tabHeight){
        boolean hovered=WorkbenchUi.inside(mx,my,x,getY(),ARROW,tabHeight);
        WorkbenchUi.frame(g,x,getY(),ARROW,tabHeight,enabled&&hovered?WorkbenchUi.HOVER:WorkbenchUi.SURFACE,WorkbenchUi.BORDER);
        int centerX=x+ARROW/2,centerY=getY()+tabHeight/2,color=enabled?WorkbenchUi.TEXT:WorkbenchUi.TRACK;
        for(int i=-3;i<=3;i++){
            int px=centerX+(right?-Math.abs(i):Math.abs(i));
            g.fill(px,centerY+i,px+1,centerY+i+1,color);
        }
    }
    private int hitTab(double mx,double my){
        int tabHeight=height-(overflow?4:0);
        if(!WorkbenchUi.inside(mx,my,viewportX,getY(),viewportWidth,tabHeight))return -1;
        double along=mx-viewportX+Math.round(scroll);
        for(int i=0;i<projects.size();i++)if(along>=offsets[i]&&along<offsets[i]+widths[i])return i;
        return -1;
    }
    private void page(boolean right){
        // Move to a tab boundary so a browse arrow never strands a thin slice.
        double target=scroll+(right?1:-1)*Math.max(48,viewportWidth*.75);
        if(right){for(int i=0;i<offsets.length;i++)if(offsets[i]>scroll+1&&offsets[i]>=target){scroll=clamp(offsets[i]);return;}scroll=maximumScroll();}
        else{for(int i=offsets.length-1;i>=0;i--)if(offsets[i]<scroll-1&&offsets[i]<=target){scroll=clamp(offsets[i]);return;}scroll=0;}
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0||!active||!visible||!isMouseOver(mx,my))return false;
        if(overflow&&WorkbenchUi.inside(mx,my,getX(),getY(),ARROW,height)){page(false);return true;}
        if(overflow&&WorkbenchUi.inside(mx,my,getX()+width-ARROW,getY(),ARROW,height)){page(true);return true;}
        pressed=true;dragged=false;pressX=mx;startScroll=scroll;pressIndex=hitTab(mx,my);return true;
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(button!=0||!pressed)return false;
        if(Math.abs(mx-pressX)>3)dragged=true;
        if(dragged)scroll=clamp(startScroll+pressX-mx);
        return true;
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(button!=0||!pressed)return false;
        boolean activate=!dragged&&pressIndex>=0&&hitTab(mx,my)==pressIndex;
        int index=pressIndex;pressed=false;pressIndex=-1;
        if(activate)select.accept(projects.get(index).id);
        return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double amount){
        if(!active||!visible||!isMouseOver(mx,my)||!overflow)return false;
        scroll=clamp(scroll-amount*64);return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!isFocused()||projects.isEmpty())return false;
        int next;
        if(key==GLFW.GLFW_KEY_HOME)next=0;
        else if(key==GLFW.GLFW_KEY_END)next=projects.size()-1;
        else if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT)next=Math.floorMod(selectedIndex+(key==GLFW.GLFW_KEY_LEFT?-1:1),projects.size());
        else if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE){revealSelected();return true;}
        else return false;
        if(next!=selectedIndex)select.accept(projects.get(next).id);else revealSelected();
        return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){
        String selected=projects.isEmpty()?"无可用项目":projects.get(selectedIndex).name+"，第 "+(selectedIndex+1)+" 项，共 "+projects.size()+" 项";
        output.add(NarratedElementType.TITLE,Component.literal("特效项目："+selected));
        output.add(NarratedElementType.USAGE,Component.literal("左右方向键切换项目，Home 与 End 跳至首尾，Ctrl 加 Tab 切换项目。"));
    }
}
