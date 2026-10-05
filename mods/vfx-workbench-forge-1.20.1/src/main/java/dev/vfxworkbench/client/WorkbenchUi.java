package dev.vfxworkbench.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shared, texture-free workbench controls. No vanilla widget atlas is used. */
final class WorkbenchUi {
    static final int PANEL = 0xF2111722, SURFACE = 0xFF192231, HOVER = 0xFF233246;
    static final int BORDER = 0xFF334155, TEXT = 0xFFE8EDF5, MUTED = 0xFF91A0B5;
    static final int ACCENT = 0xFF67DCC5, ACCENT_BG = 0xFF183B3B, ERROR = 0xFFF39B9B;
    static final int TRACK = 0xFF354154;

    private WorkbenchUi() {}

    static boolean inside(double mx, double my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    static void round(GuiGraphics g, int x, int y, int width, int height, int color) {
        if (width < 3 || height < 3) { g.fill(x, y, x + width, y + height, color); return; }
        g.fill(x + 2, y, x + width - 2, y + height, color);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        g.fill(x, y + 2, x + width, y + height - 2, color);
    }

    static void frame(GuiGraphics g, int x, int y, int width, int height, int background, int border) {
        round(g, x, y, width, height, border);
        round(g, x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2), background);
    }

    static String fit(Font font, String value, int width) {
        if (width <= 0) return "";
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }

    static void text(GuiGraphics g, Font font, String value, int x, int y, int width, int color) {
        g.drawString(font, fit(font, value, width), x, y, color, false);
    }

    static void centered(GuiGraphics g, Font font, String value, int x, int y, int width, int height, int color) {
        String shown = fit(font, value, Math.max(0, width - 10));
        g.drawString(font, shown, x + (width - font.width(shown)) / 2, y + (height - font.lineHeight) / 2 + 1, color, false);
    }

    static void badge(GuiGraphics g, Font font, String value, int x, int y, int width, int color) {
        frame(g, x, y, width, 15, SURFACE, BORDER);
        centered(g, font, value, x, y, width, 15, color);
    }

    static void control(GuiGraphics g, Font font, String value, int x, int y, int width, int height, boolean hovered, boolean focused) {
        frame(g, x, y, width, height, hovered ? HOVER : SURFACE, focused ? ACCENT : BORDER);
        text(g, font, value, x + 8, y + (height - font.lineHeight) / 2 + 1, Math.max(0, width - 24), TEXT);
    }

    static void chevron(GuiGraphics g, int x, int y, boolean down, int color) {
        if (down) {
            g.fill(x, y, x + 7, y + 1, color); g.fill(x + 1, y + 1, x + 6, y + 2, color); g.fill(x + 2, y + 2, x + 5, y + 3, color);
        } else {
            g.fill(x, y, x + 1, y + 7, color); g.fill(x + 1, y + 1, x + 2, y + 6, color); g.fill(x + 2, y + 2, x + 3, y + 5, color);
        }
    }

    static void slider(GuiGraphics g, int x, int y, int width, int height, double value, int color, boolean focused) {
        double t = Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
        int trackX = x + 4, trackW = Math.max(1, width - 8), centerY = y + height / 2;
        round(g, trackX, centerY - 2, trackW, 4, TRACK);
        int fill = (int) Math.round(t * trackW);
        if (fill > 0) round(g, trackX, centerY - 2, fill, 4, color);
        int knobX = trackX + fill - 3;
        round(g, knobX - 1, centerY - 5, 8, 10, focused ? TEXT : BORDER);
        round(g, knobX, centerY - 4, 6, 8, color);
    }

    static final class ActionButton extends AbstractButton {
        private final Runnable action;
        private final boolean primary;
        private boolean selected;

        ActionButton(int x, int y, int width, int height, String label, boolean primary, Runnable action) {
            super(x, y, width, height, Component.literal(label));
            this.primary = primary;
            this.action = action;
        }

        ActionButton selected(boolean value) { selected = value; return this; }

        @Override public void onPress() { if (active) action.run(); }

        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int background = selected || primary ? ACCENT_BG : isHoveredOrFocused() ? HOVER : SURFACE;
            int border = selected || isFocused() ? ACCENT : isHovered ? 0xFF617389 : BORDER;
            frame(g, getX(), getY(), width, height, background, border);
            if (selected) g.fill(getX() + 5, getY() + height - 2, getX() + width - 5, getY() + height - 1, ACCENT);
            centered(g, font, getMessage().getString(), getX(), getY(), width, height, !active ? MUTED : primary || selected ? ACCENT : TEXT);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    static final class ValueSlider extends AbstractWidget {
        private final java.util.function.DoubleSupplier value;
        private final java.util.function.DoubleConsumer change;
        private final double step;
        private final int color;
        ValueSlider(int x,int y,int width,int height,String label,int color,double step,java.util.function.DoubleSupplier value,java.util.function.DoubleConsumer change) {
            super(x,y,width,height,Component.literal(label));this.color=color;this.step=step;this.value=value;this.change=change;
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) { slider(g,getX(),getY(),width,height,value.getAsDouble(),color,isHoveredOrFocused()); }
        private void apply(double mx) { change.accept(Math.max(0,Math.min(1,(mx-getX()-4)/Math.max(1,width-8)))); }
        @Override public void onClick(double mx,double my) { apply(mx); }
        @Override protected void onDrag(double mx,double my,double dx,double dy) { apply(mx); }
        @Override public boolean keyPressed(int key,int scan,int mods) {
            if(!isFocused())return false;
            if(key==GLFW.GLFW_KEY_HOME||key==GLFW.GLFW_KEY_END){change.accept(key==GLFW.GLFW_KEY_HOME?0:1);return true;}
            if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT){change.accept(Math.max(0,Math.min(1,value.getAsDouble()+(key==GLFW.GLFW_KEY_LEFT?-1:1)*step*((mods&GLFW.GLFW_MOD_SHIFT)!=0?10:1))));return true;}
            return false;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    /** Vanilla text editing behavior with a fully custom frame and no atlas. */
    static final class Entry extends EditBox {
        private int boxX, boxY, boxWidth, boxHeight;
        private boolean invalid;

        Entry(Font font, String label) {
            super(font, 0, 0, 40, 10, Component.literal(label));
            setBordered(false); setTextColor(TEXT); setTextColorUneditable(MUTED); setMaxLength(128);
        }

        void place(int x, int y, int width, int height) {
            boxX = x; boxY = y; boxWidth = Math.max(20, width); boxHeight = height;
            setX(x + 6); setY(y + (height - 8) / 2); setWidth(Math.max(8, width - 12));
        }

        void invalid(boolean value) { invalid = value; }
        boolean hit(double mx, double my) { return inside(mx, my, boxX, boxY, boxWidth, boxHeight); }
        boolean click(double mx, double my, int button) {
            if (button != 0 || !hit(mx, my)) return false;
            setFocused(true); onClick(mx, my); return true;
        }

        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            frame(g, boxX, boxY, boxWidth, boxHeight, 0xFF111A27, invalid ? ERROR : isFocused() ? ACCENT : BORDER);
            g.enableScissor(boxX + 3, boxY + 2, boxX + boxWidth - 3, boxY + boxHeight - 2);
            super.renderWidget(g, mouseX, mouseY, partialTick);
            g.disableScissor();
        }
    }
}
