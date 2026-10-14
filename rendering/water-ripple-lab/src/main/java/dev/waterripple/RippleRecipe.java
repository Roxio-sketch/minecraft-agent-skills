package dev.waterripple;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
/** Versioned shared contract for the browser workbench and Forge. */
public final class RippleRecipe {
    public int schema=1, grid=65;
    public String solver="velocity", boundary="absorb", color="#08616f";
    public double width=8, speed=1, damping=0.995, radius=0.45, strength=0.18,
        maxHeight=0.5, opacity=0.24, normalStrength=1, shininess=96, reflection=1, rainRate=3;
    public RippleRecipe validate() {
        if(schema!=1)throw new IllegalArgumentException("schema must be 1");
        if(grid!=33&&grid!=65&&grid!=97&&grid!=129)throw new IllegalArgumentException("grid: 33,65,97,129");
        if(!solver.equals("velocity")&&!solver.equals("history"))throw new IllegalArgumentException("solver: velocity/history");
        if(!boundary.equals("absorb")&&!boundary.equals("reflect"))throw new IllegalArgumentException("boundary: absorb/reflect");
        if(color==null||!color.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("color: #RRGGBB");
        range("width",width,2,32);range("speed",speed,0.1,1);range("damping",damping,0.90,0.9995);
        range("radius",radius,0.05,2);range("strength",strength,-0.5,0.5);range("maxHeight",maxHeight,0.05,1);
        range("opacity",opacity,0,1);range("normalStrength",normalStrength,0,4);range("shininess",shininess,8,256);
        range("reflection",reflection,0,2);range("rainRate",rainRate,0,20);return this;
    }
    private static void range(String name,double v,double min,double max) {
        if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException(name+": "+min+".."+max);
    }
    public static RippleRecipe read(Path p)throws IOException {
        if(!Files.exists(p))return new RippleRecipe();
        RippleRecipe r=new Gson().fromJson(Files.readString(p),RippleRecipe.class);
        if(r==null)throw new IllegalArgumentException("empty recipe");return r.validate();
    }
}
