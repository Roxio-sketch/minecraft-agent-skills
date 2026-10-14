package dev.blackholelab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

public final class BlackHoleShowcase extends Screen {
    private static final int[] COLORS={0,0x7d3cff,0x21d9ff,0xff244c,0xffffff};
    private static final String[] NAMES={"黑色","紫色","青色","红色","白色"};
    private int selection;
    public BlackHoleShowcase(){super(Component.literal("黑洞与吸积盘"));for(int i=0;i<COLORS.length;i++)if(COLORS[i]==BlackHoleClient.coreColor)selection=i;}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        int x=width-174,y=38;
        addRenderableWidget(Button.builder(Component.literal("核心颜色："+NAMES[selection]),b->{selection=(selection+1)%COLORS.length;BlackHoleClient.coreColor=COLORS[selection];b.setMessage(Component.literal("核心颜色："+NAMES[selection]));}).bounds(x,y,162,20).build());
        addRenderableWidget(Button.builder(Component.literal("在面前展现黑洞"),b->{BlackHoleClient.coreColor=COLORS[selection];BlackHoleClient.place();onClose();}).bounds(x,y+25,162,20).build());
        addRenderableWidget(Button.builder(Component.literal("移除世界黑洞"),b->BlackHoleClient.center=null).bounds(x,y+50,162,20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭 · Esc"),b->onClose()).bounds(x,height-30,162,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xff0b101b);g.drawString(font,"黑洞与吸积盘 / Forge 1.20.1 实时渲染",12,10,0xe8ecff);
        int size=Math.min(height-44,width-196);BlackHoleClient.panel(g,12,30,size,COLORS[selection]);
        int x=width-174;
        g.drawString(font,"同一内核 · 真实物品模型",x,124,0xc9d9ee);
        Item[] items={Items.DIAMOND_SWORD,Items.APPLE,Items.DIAMOND_BLOCK};
        for(int i=0;i<3;i++){
            ItemStack stack=new ItemStack(items[i]);stack.getOrCreateTagElement("BlackHoleMaterial").putInt("CoreColor",COLORS[selection]);
            g.pose().pushPose();g.pose().translate(x+i*53,145,0);g.pose().scale(2.6f,2.6f,1);g.renderItem(stack,0,0);g.pose().popPose();
        }
        if(height>=270){g.drawString(font,"核心选色与吸积盘独立",x,201,0xa8b9d2);g.drawString(font,"世界演示仅在本客户端显示",x,216,0xa8b9d2);}
        if(height>=290)g.drawString(font,"命令 /blackhole showcase",x,231,0xa8b9d2);
        g.flush();super.render(g,mx,my,partial);
    }
}
