package dev.cosmicmaterial;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Live Forge renderer preview; changes only local screen state. */
public final class OutlineScreen extends Screen {
    private static final Item[] ITEMS={Items.DIAMOND_SWORD,Items.DIAMOND_AXE,Items.BOW,
        Items.APPLE,Items.DIAMOND_BLOCK,Items.OAK_STAIRS};
    private static final String[] LABELS={"Sword","Axe","Bow","Apple","Block","Stairs"};
    private static final int[] MODES={0,1,3,4};
    private static final String[] MODE_NAMES={"Solid","Stars","Three colors","Rainbow"};
    private float outlineWidth=.008f;
    private int modeIndex=0,color=0xff55e6ff;
    private EditBox hex;
    private String message="";

    public OutlineScreen(){super(Component.literal("Outline Material / Forge 1.20.1"));}
    @Override public boolean isPauseScreen(){return false;}
    @Override protected void init(){
        int x=Math.max(8,width/2-210),y=Math.max(30,height-48);
        addRenderableWidget(new AbstractSliderButton(x,y,160,20,Component.empty(),(outlineWidth-.002f)/.038f){
            {updateMessage();}
            @Override protected void updateMessage(){setMessage(Component.literal(String.format("Width %.4f",outlineWidth)));}
            @Override protected void applyValue(){outlineWidth=.002f+(float)value*.038f;updateMessage();}
        });
        addRenderableWidget(Button.builder(Component.literal(MODE_NAMES[modeIndex]),b->{
            modeIndex=(modeIndex+1)%MODES.length;b.setMessage(Component.literal(MODE_NAMES[modeIndex]));
        }).bounds(x+165,y,105,20).build());
        hex=new EditBox(font,x+275,y,80,20,Component.literal("ARGB"));
        hex.setMaxLength(8);hex.setValue(String.format("%08X",color));addRenderableWidget(hex);
        addRenderableWidget(Button.builder(Component.literal("Apply"),b->{
            try {if(!hex.getValue().matches("[0-9a-fA-F]{8}"))throw new IllegalArgumentException();
                color=(int)Long.parseLong(hex.getValue(),16);message="Color applied";
            } catch(Exception ex){message="Use 8-digit AARRGGBB";}
        }).bounds(x+360,y,60,20).build());
    }
    private ItemStack outlined(Item item) {
        ItemStack stack=new ItemStack(item);
        CompoundTag tag=new CompoundTag();tag.putFloat("Width",outlineWidth);
        tag.putInt("Type",MODES[modeIndex]);tag.putInt("Color",color);
        stack.getOrCreateTag().put("OutlineMaterial",tag);
        return stack;
    }
    private void item(GuiGraphics g,ItemStack stack,float x,float y,float size){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(size/16f,size/16f,1);
        g.renderItem(stack,0,0);g.pose().popPose();
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0xff101820);
        g.drawCenteredString(font,"ARCANEVORTEX-STYLE GEOMETRY / LIVE FORGE 1.20.1",width/2,7,0xffdbeaff);
        g.drawCenteredString(font,"Original  |  8-corner outline",width/2,20,0xffa9c1d6);
        int columns=3,top=36,cellW=width/columns,cellH=Math.max(55,(height-top-65)/2);
        for(int i=0;i<ITEMS.length;i++){
            int x=(i%columns)*cellW,y=top+(i/columns)*cellH;
            g.fill(x+3,y,x+cellW-3,y+cellH-3,0xff1d2a36);
            float size=Math.min(Math.min(180,cellW*.29f),cellH*.58f);
            float middle=x+cellW*.5f;
            float itemY=y+(cellH-size)/2f-8;
            item(g,new ItemStack(ITEMS[i]),middle-size-8,itemY,size);
            item(g,outlined(ITEMS[i]),middle+8,itemY,size);
            g.drawCenteredString(font,LABELS[i],x+cellW/2,y+cellH-14,0xffc6d9e8);
        }
        g.drawString(font,message,8,height-17,0xffffd489);
        g.flush();super.render(g,mouseX,mouseY,partial);
    }
}
