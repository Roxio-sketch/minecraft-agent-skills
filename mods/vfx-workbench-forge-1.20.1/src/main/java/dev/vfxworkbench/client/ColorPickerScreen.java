package dev.vfxworkbench.client;

import dev.vfxworkbench.core.ParamSpec;
import dev.vfxworkbench.core.ParamState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Responsive RGB and HEX editing with the same workbench controls. */
public final class ColorPickerScreen extends Screen {
    private final ParamSpec spec;
    private final ParamState state;
    private final Screen parent;
    private final int original;
    private final int[] channels=new int[3];
    private final WorkbenchUi.Entry[] channelEntries=new WorkbenchUi.Entry[3];
    private WorkbenchUi.Entry hex;
    private int cardX,cardY,cardWidth,cardHeight;
    private static final String[] LABELS={"红","绿","蓝"};
    private int formattedRgb=-1;
    private String formattedHex;

    ColorPickerScreen(ParamSpec spec,ParamState state){
        super(Component.literal("颜色 - "+spec.label));this.spec=spec;this.state=state;
        parent=Minecraft.getInstance().screen;original=state.getColor(spec.key);setRgb(original);
    }
    private void setRgb(int rgb){channels[0]=(rgb>>16)&255;channels[1]=(rgb>>8)&255;channels[2]=rgb&255;}
    private int rgb(){return (channels[0]<<16)|(channels[1]<<8)|channels[2];}
    private String hexValue(){int value=rgb();if(value!=formattedRgb){formattedHex=String.format(Locale.ROOT,"#%06X",value);formattedRgb=value;}return formattedHex;}
    private void apply(){state.set(spec.key,hexValue());syncFields();}
    private void syncFields(){
        if(hex!=null&&!hex.isFocused()){hex.invalid(false);if(!hex.getValue().equals(hexValue()))hex.setValue(hexValue());}
        for(int i=0;i<3;i++)if(channelEntries[i]!=null&&!channelEntries[i].isFocused()){
            channelEntries[i].invalid(false);
            String value=Integer.toString(channels[i]);if(!channelEntries[i].getValue().equals(value))channelEntries[i].setValue(value);
        }
    }
    @Override protected void init(){
        setFocused(null);setDragging(false);
        cardWidth=Math.min(336,Math.max(1,width-24));cardHeight=Math.min(220,Math.max(1,height-16));
        cardX=(width-cardWidth)/2;cardY=(height-cardHeight)/2;
        hex=addRenderableWidget(new WorkbenchUi.Entry(font,"HEX颜色"));hex.place(cardX+62,cardY+69,cardWidth-78,20);hex.setMaxLength(7);hex.setValue(hexValue());
        hex.setResponder(value->{
            if(!hex.isFocused())return;String normalized=value.startsWith("#")?value.substring(1):value;
            boolean valid=normalized.matches("[0-9a-fA-F]{6}");hex.invalid(!valid);
            if(valid){setRgb(Integer.parseInt(normalized,16));apply();}
        });
        String[] labels=LABELS;int[] colors={0xFFEC9B9F,0xFF85D6AA,0xFF8EBBF0};
        for(int i=0;i<3;i++){
            final int channel=i;int yy=cardY+98+i*26;
            addRenderableWidget(new WorkbenchUi.ValueSlider(cardX+40,yy,cardWidth-115,20,labels[i]+"色通道",colors[i],1.0/255,()->channels[channel]/255.0,value->{channels[channel]=(int)Math.round(value*255);apply();}));
            var entry=addRenderableWidget(new WorkbenchUi.Entry(font,labels[i]+"色数值"));channelEntries[i]=entry;
            entry.place(cardX+cardWidth-67,yy,51,20);entry.setMaxLength(3);entry.setValue(Integer.toString(channels[i]));
            entry.setResponder(value->{
                if(!entry.isFocused())return;
                try{int n=Integer.parseInt(value);boolean valid=n>=0&&n<=255;entry.invalid(!valid);if(valid){channels[channel]=n;apply();}}
                catch(NumberFormatException ex){entry.invalid(true);}
            });
        }
        int ww=(cardWidth-38)/2,buttonY=cardY+cardHeight-33;
        addRenderableWidget(new WorkbenchUi.ActionButton(cardX+16,buttonY,ww,22,"恢复原色",false,()->{setFocused(null);setRgb(original);apply();}));
        addRenderableWidget(new WorkbenchUi.ActionButton(cardX+22+ww,buttonY,ww,22,"返回工作台",true,this::onClose));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0x8C070D16);
        WorkbenchUi.frame(g,cardX,cardY,cardWidth,cardHeight,WorkbenchUi.PANEL,WorkbenchUi.BORDER);
        WorkbenchUi.text(g,font,"颜色 · "+spec.label,cardX+16,cardY+15,cardWidth-78,WorkbenchUi.TEXT);
        WorkbenchUi.badge(g,font,"即时",cardX+cardWidth-48,cardY+10,32,WorkbenchUi.ACCENT);
        int color=rgb();WorkbenchUi.round(g,cardX+16,cardY+37,cardWidth-32,24,0xFF000000|color);
        int luminance=channels[0]*299+channels[1]*587+channels[2]*114;
        WorkbenchUi.text(g,font,"当前颜色",cardX+25,cardY+45,cardWidth-50,luminance>145000?0xFF15202B:0xFFF4F7FC);
        g.drawString(font,"HEX",cardX+16,cardY+75,WorkbenchUi.MUTED,false);
        for(int i=0;i<3;i++)g.drawString(font,LABELS[i],cardX+16,cardY+104+i*26,WorkbenchUi.MUTED,false);
        syncFields();super.render(g,mx,my,partial);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(hex!=null&&hex.hit(mx,my)&&button==0){setFocused(hex);return hex.click(mx,my,button);}
        for(var entry:channelEntries)if(entry!=null&&entry.hit(mx,my)&&button==0){setFocused(entry);return entry.click(mx,my,button);}
        return super.mouseClicked(mx,my,button);
    }
    @Override public void tick(){super.tick();if(hex!=null)hex.tick();for(var e:channelEntries)if(e!=null)e.tick();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
