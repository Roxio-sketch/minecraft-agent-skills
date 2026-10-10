package dev.vfxworkbench.client;

import dev.vfxworkbench.core.*;
import dev.vfxworkbench.effects.lightning.LightningSkillSchema;
import dev.vfxworkbench.effects.blackhole.BlackHoleSkillSchema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.math.BigDecimal;
import java.util.*;

/** Schema-driven controls in one clipped, keyboard-accessible viewport. */
final class ScrollableRows extends AbstractWidget {
    // At the minimum 240px GUI height the lightning parameter viewport is
    // 56px tall. Keep all three samples, including their HEX labels, inside
    // one viewport rather than adding padding that no scroll can reveal.
    private static final int ROW_H=46, GROUP_H=25, COLOR_PREVIEW_H=56, GRADIENT_NOTE_H=48;
    private final ParamState state;
    private final List<ParamSpec> specs;
    private final Map<String,WorkbenchUi.Entry> entries=new HashMap<>();
    private record NumericText(String raw,String formatted) {}
    private final Map<String,NumericText> numericTexts=new HashMap<>();
    private String numericText(ParamSpec spec,String value){
        var cached=numericTexts.get(spec.key);
        if(cached==null||!cached.raw().equals(value)){cached=new NumericText(value,numericText(value));numericTexts.put(spec.key,cached);}
        return cached.formatted();
    }
    private final Set<String> collapsed=new HashSet<>();
    private List<String> visibleKeys=List.of();
    private List<Visual> visuals=List.of();
    private long visibilityRevision=Long.MIN_VALUE;
    private double scroll, scrollbarGrab;
    private int contentHeight, enumIndex, enumOffset;
    private String focusKey, draggingKey, editingKey, editStart, enumKey, tooltip;
    private boolean draggingScrollbar;
    record ViewState(double scroll, Set<String> collapsed) {}
    private record Visual(String key, ParamSpec spec, String group, int offset, int count) {
        boolean info(){return key.startsWith("@lightning/");}
        boolean header(){return spec==null&&!info();}
        int height(){return info()?(key.endsWith("gradient-note")?GRADIENT_NOTE_H:COLOR_PREVIEW_H):header()?GROUP_H:ROW_H;}
    }
    private record Popup(int x,int y,int width,int height,int count) {}

    ScrollableRows(ParamState state,SkillSchema schema,int x,int y,int width,int bottom){
        super(x,y,width,Math.max(1,bottom-y),Component.literal("参数"));
        this.state=state;specs=new ArrayList<>(schema.params);refreshVisibility();
    }
    ViewState viewState(){return new ViewState(scroll,Set.copyOf(collapsed));}
    void restore(ViewState view){if(view!=null){collapsed.clear();collapsed.addAll(view.collapsed());rebuildVisuals();scroll=clamp(view.scroll());}}
    int visibleCount(){refreshVisibility();return visibleKeys.size();}
    void finishEditing(){finishEdit(true);enumKey=null;}
    void tickEntries(){for(var e:entries.values())if(e.isFocused())e.tick();}
    private Font font(){return Minecraft.getInstance().font;}
    private int contentWidth(){return Math.max(40,width-10);}
    private int rowY(Visual v){return getY()+v.offset()-(int)Math.round(scroll);}
    private int cx(){return getX()+10;}
    private int cw(){return Math.max(24,contentWidth()-20);}
    private int valueWidth(){return Math.max(62,Math.min(88,cw()/3));}
    private int valueX(){return cx()+cw()-valueWidth();}
    private int sliderWidth(){return Math.max(16,cw()-valueWidth()-9);}
    private double maxScroll(){return Math.max(0,contentHeight-height);}
    private double clamp(double value){return Math.max(0,Math.min(maxScroll(),value));}
    private Visual visual(String key){return visuals.stream().filter(v->v.key().equals(key)).findFirst().orElse(null);}
    private void clearHiddenFocus(){var v=visual(focusKey);if(v!=null&&(v.offset()+v.height()<=scroll||v.offset()>=scroll+height))focusKey=null;}
    private void focusViewport(){for(var v:visuals)if(!v.info()&&v.offset()>=scroll&&v.offset()+v.height()<=scroll+height){focusKey=v.key();return;}focusKey=null;}

    private void refreshVisibility(){
        if(visibilityRevision==state.revision()){scroll=clamp(scroll);return;}
        visibilityRevision=state.revision();
        List<String> now=specs.stream().filter(p->p.isVisible(state)).map(p->p.key).toList();
        if(!now.equals(visibleKeys)){
            visibleKeys=now;
            if(editingKey!=null&&!now.contains(editingKey))finishEdit(true);
            draggingKey=null;enumKey=null;rebuildVisuals();
        }
        scroll=clamp(scroll);
    }
    private void rebuildVisuals(){
        Map<String,List<ParamSpec>> groups=new LinkedHashMap<>();
        for(var p:specs)if(p.isVisible(state))groups.computeIfAbsent(p.group==null||p.group.isBlank()?"通用参数":p.group,k->new ArrayList<>()).add(p);
        List<Visual> next=new ArrayList<>();int offset=0;
        for(var group:groups.entrySet()){
            next.add(new Visual("@"+group.getKey(),null,group.getKey(),offset,group.getValue().size()));offset+=GROUP_H;
            if(collapsed.contains(group.getKey()))continue;
            boolean lightning=LightningSkillSchema.ID.equals(state.schema.id);
            if(lightning&&group.getValue().stream().anyMatch(p->p.key.equals("gradientEnabled"))){
                next.add(new Visual("@lightning/gradient-note",null,group.getKey(),offset,0));offset+=GRADIENT_NOTE_H;
            }
            for(var p:group.getValue()){next.add(new Visual(p.key,p,group.getKey(),offset,0));offset+=ROW_H;}
            if(lightning)for(String layer:List.of("core","glow"))if(group.getValue().stream().anyMatch(p->p.key.equals(layer+"Color"))){
                next.add(new Visual("@lightning/"+layer+"-preview",null,group.getKey(),offset,0));offset+=COLOR_PREVIEW_H;
            }
        }
        visuals=next;contentHeight=offset;
        if(focusKey!=null&&visual(focusKey)==null)focusKey=null;
        scroll=clamp(scroll);
    }

    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        refreshVisibility();tooltip=null;
        g.enableScissor(getX(),getY(),getX()+width,getY()+height);
        for(Visual v:visuals){
            int yy=rowY(v);if(yy+v.height()<=getY()||yy>=getY()+height)continue;
            boolean hovered=WorkbenchUi.inside(mx,my,getX(),Math.max(getY(),yy),contentWidth(),Math.min(yy+v.height(),getY()+height)-Math.max(getY(),yy));
            boolean focused=isFocused()&&v.key().equals(focusKey);
            if(v.info()){
                if(v.key().endsWith("gradient-note"))renderGradientNote(g,yy);
                else renderColorPreview(g,yy,v.key().contains("/glow-"));
                continue;
            }
            if(v.header()){
                WorkbenchUi.round(g,getX(),yy+2,contentWidth(),GROUP_H-5,focused||hovered?WorkbenchUi.HOVER:0xFF131D2B);
                WorkbenchUi.chevron(g,getX()+7,yy+9,!collapsed.contains(v.group()),WorkbenchUi.MUTED);
                WorkbenchUi.text(g,font(),v.group(),getX()+20,yy+7,contentWidth()-51,WorkbenchUi.MUTED);
                String count=Integer.toString(v.count());g.drawString(font(),count,getX()+contentWidth()-font().width(count)-8,yy+7,WorkbenchUi.MUTED,false);continue;
            }
            ParamSpec p=v.spec();WorkbenchUi.round(g,getX(),yy+1,contentWidth(),ROW_H-3,hovered?0xFF1C293A:0xFF17202E);
            if(focused)g.fill(getX(),yy+6,getX()+2,yy+ROW_H-7,WorkbenchUi.ACCENT);
            String badge=switch(p.reload){case HOT->"即时";case RESOURCE->"重建";case RESTART->"重启";};
            int badgeColor=switch(p.reload){case HOT->WorkbenchUi.ACCENT;case RESOURCE->0xFFE4C988;case RESTART->WorkbenchUi.ERROR;};
            int badgeW=font().width(badge)+12,badgeX=getX()+contentWidth()-badgeW-8;
            WorkbenchUi.text(g,font(),p.label,getX()+10,yy+7,badgeX-getX()-18,WorkbenchUi.TEXT);
            WorkbenchUi.badge(g,font(),badge,badgeX,yy+3,badgeW,badgeColor);
            int cy=yy+22;String value=state.get(p.key);
            switch(p.type){
                case FLOAT,INT->{
                    double n=number(value,p.min),span=p.max-p.min;
                    WorkbenchUi.slider(g,cx(),cy,sliderWidth(),20,span<=0?0:(n-p.min)/span,WorkbenchUi.ACCENT,focused);
                    var e=entry(p);e.place(valueX(),cy,valueWidth(),20);if(!p.key.equals(editingKey))sync(e,numericText(p,value));e.render(g,mx,my,partial);
                }
                case BOOL->{
                    boolean on=state.getBool(p.key);WorkbenchUi.frame(g,cx(),cy+2,34,16,on?WorkbenchUi.ACCENT_BG:WorkbenchUi.SURFACE,focused?WorkbenchUi.ACCENT:WorkbenchUi.BORDER);
                    WorkbenchUi.round(g,cx()+(on?19:3),cy+5,11,10,on?WorkbenchUi.ACCENT:WorkbenchUi.MUTED);
                    WorkbenchUi.text(g,font(),on?"已启用":"已关闭",cx()+42,cy+6,cw()-42,on?WorkbenchUi.ACCENT:WorkbenchUi.MUTED);
                }
                case ENUM->{WorkbenchUi.control(g,font(),enumLabel(p,value),cx(),cy,cw(),20,hovered,focused||p.key.equals(enumKey));WorkbenchUi.chevron(g,cx()+cw()-13,cy+8,true,WorkbenchUi.MUTED);}
                case COLOR->{
                    WorkbenchUi.frame(g,cx(),cy,cw(),20,WorkbenchUi.SURFACE,focused?WorkbenchUi.ACCENT:WorkbenchUi.BORDER);
                    WorkbenchUi.round(g,cx()+5,cy+4,12,12,0xFF000000|state.getColor(p.key));
                    WorkbenchUi.text(g,font(),value.toUpperCase(Locale.ROOT),cx()+24,cy+6,cw()-45,WorkbenchUi.TEXT);g.drawString(font(),"…",cx()+cw()-13,cy+6,WorkbenchUi.MUTED,false);
                }
                default->{var e=entry(p);e.place(cx(),cy,cw(),20);if(!p.key.equals(editingKey))sync(e,value);e.render(g,mx,my,partial);}
            }
            if(hovered&&my<cy&&enumKey==null)tooltip=p.label+(p.help==null||p.help.isBlank()?"":"\n"+p.help)+switch(p.reload){case HOT->"\n调整后即时更新";case RESOURCE->"\n调整后需重建效果";case RESTART->"\n调整后需重启客户端";};
        }
        renderScrollbar(g,mx,my);
        if(enumKey!=null){
            // EditBox text renders at an elevated GUI depth. Merely drawing
            // the popup last does not cover it: place the entire popup above
            // the controls, including its opaque background.
            g.flush();g.pose().pushPose();g.pose().translate(0,0,400);
            try{renderPopup(g,mx,my);g.flush();}finally{g.pose().popPose();}
        }
        g.disableScissor();
    }
    private boolean gradientApplies(boolean halo){
        String target=state.get("gradientTarget");
        return state.getBool("gradientEnabled")&&(target.equals("both")||target.equals(halo?"glow":"core"));
    }
    private void renderColorPreview(GuiGraphics g,int y,boolean halo){
        WorkbenchUi.frame(g,getX(),y+1,contentWidth(),COLOR_PREVIEW_H-3,0xFF111C29,WorkbenchUi.BORDER);
        String layer=halo?"光晕":"核心",source=gradientApplies(halo)?"已叠渐变":"基础色";
        WorkbenchUi.text(g,font(),layer+" · 实际材质色",cx(),y+6,cw()-font().width(source)-8,WorkbenchUi.TEXT);
        WorkbenchUi.text(g,font(),source,cx()+cw()-font().width(source),y+6,font().width(source),gradientApplies(halo)?WorkbenchUi.ACCENT:WorkbenchUi.MUTED);
        String[] labels={state.get("mode").equals("orbit")?"起始端":"起始云端","中点",state.get("mode").equals("orbit")?"终止端":"终止地面"};
        int gap=6;
        for(int i=0;i<3;i++){
            int x=cx()+i*(cw()+gap)/3,w=(i+1)*(cw()+gap)/3-i*(cw()+gap)/3-gap;
            int rgb=LightningSkillSchema.previewColor(state,halo,i*.5);
            WorkbenchUi.text(g,font(),labels[i],x,y+19,w,WorkbenchUi.MUTED);
            WorkbenchUi.round(g,x,y+30,w,9,0xFF000000|rgb);
            WorkbenchUi.text(g,font(),String.format(Locale.ROOT,"#%06X",rgb),x,y+43,w,WorkbenchUi.TEXT);
        }
    }
    private void renderGradientNote(GuiGraphics g,int y){
        WorkbenchUi.round(g,getX(),y+1,contentWidth(),GRADIENT_NOTE_H-3,0xFF111C29);
        String target=state.getBool("gradientEnabled")?enumLabel(state.get("gradientTarget")):"已关闭";
        WorkbenchUi.text(g,font(),"当前渐变："+target,cx(),y+6,cw(),WorkbenchUi.ACCENT);
        WorkbenchUi.text(g,font(),"选中层：渐变色 × 本层基础色",cx(),y+19,cw(),WorkbenchUi.MUTED);
        WorkbenchUi.text(g,font(),"主干与分叉共用同一组渐变",cx(),y+32,cw(),WorkbenchUi.MUTED);
    }
    void renderTooltip(GuiGraphics g,int mx,int my){if(tooltip!=null)g.renderComponentTooltip(font(),Arrays.stream(tooltip.split("\n")).<Component>map(Component::literal).toList(),mx,my);}
    private int thumbHeight(){return Math.min(height,Math.max(22,(int)(height*(double)height/Math.max(height,contentHeight))));}
    private int thumbY(){return getY()+(int)Math.round(maxScroll()<=0?0:scroll/maxScroll()*(height-thumbHeight()));}
    private void renderScrollbar(GuiGraphics g,int mx,int my){
        if(maxScroll()<=0)return;
        WorkbenchUi.round(g,getX()+width-5,getY(),4,height,0xFF202C3D);
        WorkbenchUi.round(g,getX()+width-5,thumbY(),4,thumbHeight(),draggingScrollbar||WorkbenchUi.inside(mx,my,getX()+width-7,getY(),7,height)?WorkbenchUi.ACCENT:0xFF677A91);
    }
    private WorkbenchUi.Entry entry(ParamSpec p){
        return entries.computeIfAbsent(p.key,key->{
            var e=new WorkbenchUi.Entry(font(),p.label);
            e.setResponder(value->{
                if(!key.equals(editingKey))return;
                if(numeric(p)){double n=number(value,Double.NaN);boolean valid=Double.isFinite(n)&&n>=p.min&&n<=p.max;e.invalid(!valid);if(valid)storeNumeric(p,n);}
                else state.set(key,value);
            });return e;
        });
    }
    private static void sync(WorkbenchUi.Entry e,String value){if(!e.getValue().equals(value))e.setValue(value);}
    private static boolean numeric(ParamSpec p){return p.type==ParamType.FLOAT||p.type==ParamType.INT;}
    private static double number(String value,double fallback){try{return Double.parseDouble(value);}catch(RuntimeException ex){return fallback;}}
    private static String numericText(String value){try{return new BigDecimal(value).stripTrailingZeros().toPlainString();}catch(RuntimeException ex){return value;}}
    private void storeNumeric(ParamSpec p,double value){
        if(!Double.isFinite(value))return;double n=Math.max(p.min,Math.min(p.max,value));
        if(p.intStep||p.type==ParamType.INT)state.setIntValue(p.key,(int)Math.round(n));
        else{if(p.step>0)n=p.min+Math.round((n-p.min)/p.step)*p.step;state.setDouble(p.key,Math.max(p.min,Math.min(p.max,Math.round(n*1e9)/1e9)));}
    }
    private void startEdit(ParamSpec p,double mx,double my){
        finishEdit(true);editingKey=p.key;editStart=state.get(p.key);var e=entry(p);e.invalid(false);sync(e,numeric(p)?numericText(editStart):editStart);e.setFocused(true);
        if(!Double.isNaN(mx))e.onClick(mx,my);else{e.moveCursorToEnd();e.setHighlightPos(0);}
    }
    private void finishEdit(boolean commit){
        if(editingKey==null)return;String key=editingKey;var e=entries.get(key);ParamSpec p=state.schema.get(key);
        if(p!=null&&e!=null){if(!commit)state.set(key,editStart);else if(numeric(p))storeNumeric(p,number(e.getValue(),number(editStart,p.min)));else state.set(key,e.getValue());e.setFocused(false);e.invalid(false);}
        editingKey=null;editStart=null;
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        refreshVisibility();if(!active||!visible||!WorkbenchUi.inside(mx,my,getX(),getY(),width,height))return false;
        if(enumKey!=null&&clickPopup(mx,my,button))return true;enumKey=null;if(button!=0)return false;
        if(maxScroll()>0&&mx>=getX()+width-8){finishEdit(true);draggingScrollbar=true;scrollbarGrab=my>=thumbY()&&my<thumbY()+thumbHeight()?my-thumbY():thumbHeight()/2.0;dragScrollbar(my);return true;}
        for(Visual v:visuals){
            int yy=rowY(v);if(!WorkbenchUi.inside(mx,my,getX(),yy,contentWidth(),v.height()))continue;
            if(v.info()){finishEdit(true);focusKey=null;return true;}
            focusKey=v.key();setMessage(Component.literal(v.header()?v.group():v.spec().label));
            if(v.header()){finishEdit(true);toggleGroup(v.group());return true;}
            ParamSpec p=v.spec();int cy=yy+22;
            if(my<cy||my>=cy+20||mx<cx()||mx>=cx()+cw()){finishEdit(true);return true;}
            if(numeric(p)){var e=entry(p);e.place(valueX(),cy,valueWidth(),20);if(e.hit(mx,my))startEdit(p,mx,my);else{finishEdit(true);draggingKey=p.key;applySlider(p,mx);}}
            else{finishEdit(true);switch(p.type){case BOOL->state.setBool(p.key,!state.getBool(p.key));case ENUM->openEnum(p);case COLOR->Minecraft.getInstance().setScreen(new ColorPickerScreen(p,state));default->{var e=entry(p);e.place(cx(),cy,cw(),20);startEdit(p,mx,my);}}}
            return true;
        }
        finishEdit(true);return true;
    }
    private void toggleGroup(String group){if(!collapsed.add(group))collapsed.remove(group);rebuildVisuals();draggingKey=null;enumKey=null;}
    private void applySlider(ParamSpec p,double mx){double t=Math.max(0,Math.min(1,(mx-cx()-4)/Math.max(1,sliderWidth()-8)));storeNumeric(p,p.min+t*(p.max-p.min));}
    private void dragScrollbar(double my){scroll=clamp((my-getY()-scrollbarGrab)/Math.max(1,height-thumbHeight())*maxScroll());}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(button!=0)return false;if(draggingScrollbar){dragScrollbar(my);return true;}
        if(draggingKey!=null){var p=state.schema.get(draggingKey);if(p!=null&&p.isVisible(state))applySlider(p,mx);return true;}return false;
    }
    @Override public boolean mouseReleased(double mx,double my,int button){boolean handled=draggingScrollbar||draggingKey!=null;draggingScrollbar=false;draggingKey=null;return handled;}
    @Override public boolean mouseScrolled(double mx,double my,double delta){
        refreshVisibility();if(!WorkbenchUi.inside(mx,my,getX(),getY(),width,height))return false;
        if(enumKey!=null){var p=state.schema.get(enumKey);Popup popup=popup();
            if(p!=null&&popup!=null&&WorkbenchUi.inside(mx,my,popup.x(),popup.y(),popup.width(),popup.height())){enumOffset=Math.max(0,Math.min(Math.max(0,enumChoices(p).size()-popup.count()),enumOffset-(int)Math.signum(delta)));return true;}enumKey=null;
        }
        finishEdit(true);scroll=clamp(scroll-delta*30);clearHiddenFocus();return true;
    }
    @Override public void setFocused(boolean value){super.setFocused(value);if(!value){finishEdit(true);enumKey=null;}else if(focusKey==null)focusViewport();}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!isFocused())return false;refreshVisibility();
        if(enumKey!=null){var p=state.schema.get(enumKey);
            if(key==GLFW.GLFW_KEY_ESCAPE){enumKey=null;return true;}
            if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){enumIndex=Math.max(0,Math.min(enumChoices(p).size()-1,enumIndex+(key==GLFW.GLFW_KEY_UP?-1:1)));keepEnumVisible();return true;}
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE){state.set(p.key,enumChoices(p).get(enumIndex));enumKey=null;return true;}
        }
        if(editingKey!=null){
            if(key==GLFW.GLFW_KEY_ESCAPE){finishEdit(false);return true;}
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){finishEdit(true);return true;}
            if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){finishEdit(true);return stepFocus(key==GLFW.GLFW_KEY_UP?-1:1);}
            if(key!=GLFW.GLFW_KEY_TAB&&key!=GLFW.GLFW_KEY_PAGE_UP&&key!=GLFW.GLFW_KEY_PAGE_DOWN)return entries.get(editingKey).keyPressed(key,scan,mods);finishEdit(true);
        }
        if(key==GLFW.GLFW_KEY_TAB){enumKey=null;return stepFocus((mods&GLFW.GLFW_MOD_SHIFT)!=0?-1:1);}
        if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN)return stepFocus(key==GLFW.GLFW_KEY_UP?-1:1);
        if(key==GLFW.GLFW_KEY_PAGE_UP||key==GLFW.GLFW_KEY_PAGE_DOWN){scroll=clamp(scroll+(key==GLFW.GLFW_KEY_PAGE_UP?-1:1)*Math.max(ROW_H,height-ROW_H));focusViewport();return true;}
        if(key==GLFW.GLFW_KEY_HOME||key==GLFW.GLFW_KEY_END){scroll=key==GLFW.GLFW_KEY_HOME?0:maxScroll();focusViewport();return true;}
        Visual v=visual(focusKey);if(v==null)return false;
        if(v.header()){if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE||key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT){toggleGroup(v.group());return true;}return false;}
        var p=v.spec();
        if((key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT)&&numeric(p)){double step=p.intStep?1:p.step>0?p.step:(p.max-p.min)/100;if((mods&GLFW.GLFW_MOD_SHIFT)!=0)step*=10;storeNumeric(p,number(state.get(p.key),p.min)+(key==GLFW.GLFW_KEY_LEFT?-step:step));return true;}
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE){
            if(p.type==ParamType.BOOL)state.setBool(p.key,!state.getBool(p.key));else if(p.type==ParamType.ENUM)openEnum(p);else if(p.type==ParamType.COLOR)Minecraft.getInstance().setScreen(new ColorPickerScreen(p,state));else startEdit(p,Double.NaN,Double.NaN);return true;
        }return false;
    }
    @Override public boolean charTyped(char character,int mods){return editingKey!=null&&entries.get(editingKey).charTyped(character,mods);}
    private boolean stepFocus(int direction){
        int current=-1;for(int i=0;i<visuals.size();i++)if(visuals.get(i).key().equals(focusKey))current=i;
        int next=current+direction;
        if(current<0){
            next=direction>0?0:visuals.size()-1;
            while(next>=0&&next<visuals.size()){
                var candidate=visuals.get(next);
                if(!candidate.info()&&(direction>0?candidate.offset()>=scroll:candidate.offset()+candidate.height()<=scroll+height))break;
                next+=direction;
            }
        }else while(next>=0&&next<visuals.size()&&visuals.get(next).info())next+=direction;
        if(next<0||next>=visuals.size())return false;
        var v=visuals.get(next);focusKey=v.key();setMessage(Component.literal(v.header()?v.group():v.spec().label));
        if(v.offset()<scroll)scroll=v.offset();else if(v.offset()+v.height()>scroll+height)scroll=clamp(v.offset()+v.height()-height);return true;
    }
    private List<String> enumChoices(ParamSpec p){
        return state.schema.id.equals(BlackHoleSkillSchema.ID)&&p.key.equals("form")
            ?BlackHoleSkillSchema.PRESET_FORMS:state.schema.id.equals("lightning")&&p.key.equals("preset")
            ?dev.vfxworkbench.effects.lightning.LightningSkillSchema.PRESET_CHOICES:p.enumValues;
    }
    private void openEnum(ParamSpec p){if(enumChoices(p).isEmpty())return;enumKey=p.key;enumIndex=Math.max(0,enumChoices(p).indexOf(state.get(p.key)));enumOffset=0;keepEnumVisible();}
    private Popup popup(){
        var v=visual(enumKey);if(v==null||enumChoices(v.spec()).isEmpty())return null;
        int count=Math.min(enumChoices(v.spec()).size(),Math.max(1,Math.min(7,(height-8)/20))),h=count*20+4,below=rowY(v)+44;
        int yy=below+h<=getY()+height?below:rowY(v)+19-h;yy=Math.max(getY()+2,Math.min(getY()+height-h-2,yy));return new Popup(cx(),yy,cw(),h,count);
    }
    private void keepEnumVisible(){var p=popup();if(p==null)return;if(enumIndex<enumOffset)enumOffset=enumIndex;if(enumIndex>=enumOffset+p.count())enumOffset=enumIndex-p.count()+1;}
    private void renderPopup(GuiGraphics g,int mx,int my){
        var popup=popup();if(popup==null)return;var p=state.schema.get(enumKey);WorkbenchUi.frame(g,popup.x(),popup.y(),popup.width(),popup.height(),0xFF0F1825,WorkbenchUi.ACCENT);
        for(int i=0;i<popup.count();i++){int index=enumOffset+i;if(index>=enumChoices(p).size())break;int yy=popup.y()+2+i*20;
            boolean hover=WorkbenchUi.inside(mx,my,popup.x()+2,yy,popup.width()-4,20);if(hover||index==enumIndex)g.fill(popup.x()+2,yy,popup.x()+popup.width()-2,yy+20,WorkbenchUi.ACCENT_BG);
            WorkbenchUi.text(g,font(),enumLabel(p,enumChoices(p).get(index)),popup.x()+8,yy+6,popup.width()-16,index==enumIndex?WorkbenchUi.ACCENT:WorkbenchUi.TEXT);
        }
    }
    private boolean clickPopup(double mx,double my,int button){
        var popup=popup();if(popup==null||!WorkbenchUi.inside(mx,my,popup.x(),popup.y(),popup.width(),popup.height()))return false;
        if(button==0){var p=state.schema.get(enumKey);int index=enumOffset+(int)(my-popup.y()-2)/20;if(index>=0&&index<enumChoices(p).size())state.set(p.key,enumChoices(p).get(index));enumKey=null;}return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){defaultButtonNarrationText(output);}
    private String enumLabel(ParamSpec p,String v){
        return state.schema.id.equals(BlackHoleSkillSchema.ID)&&p.key.equals("form")&&v.equals("classic")
            ?"程序光带 · 基础结构":enumLabel(v);
    }
    private static String enumLabel(String v){return switch(v){
            case "white-ring"->"白洞 · 蓝白光团";case "black"->"黑洞（内流）";case "white"->"白洞（外流）";
        case "classic"->"基础薄盘";case "granular"->"颗粒吸积";case "ribbons"->"层叠光带";case "dust"->"体积尘埃";case "cinematic"->"电影光带";
        case "self"->"玩家自身";case "forward"->"前方落点";
        case "relativeGround"->"距落点高度";case "absoluteWorld"->"固定世界高度";
        case "custom"->"自定义";case "source"->"原版长脉冲";case "shortSource"->"原版短脉冲";case "dense"->"稠密电流";case "tribulation"->"雷劫";case "1"->"正向";case "-1"->"反向";case "recipe"->"自定义 / 导入配方";
        case "gold"->"金色薄盘";case "ice"->"冰蓝正面盘";case "violet"->"紫色厚盘";case "lens"->"无盘透镜";case "crimson"->"赤红漩涡";
        case "orbit"->"环绕电流";case "strike"->"单道落雷";case "rain"->"多道落雷";case "grow"->"延伸";case "ring"->"圆环";case "spektr"->"短段脉冲";
        case "both"->"核心和光晕";case "core"->"仅核心";case "glow"->"仅光晕";case "vivid"->"保留色相（推荐）";case "additive"->"叠加高亮（易趋白）";
        case "spread"->"散布";case "cluster"->"集束";case "velocity"->"高度与速度";case "history"->"双高度历史";case "absorb"->"吸收";case "reflect"->"反射";default->v;};}
}
