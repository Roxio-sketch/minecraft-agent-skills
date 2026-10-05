package dev.blackholelab;

/** Keeps pause/resume continuous while allowing an explicit recipe-time edit. */
public final class BlackHoleClock {
    private double configured=Double.NaN,base,anchor;
    private boolean playing;
    public void reset(){configured=Double.NaN;}
    public double sample(double time,boolean run,double now){
        if(Double.compare(configured,time)!=0){configured=time;base=time;anchor=now;playing=run;}
        if(playing!=run){if(playing)base+=Math.max(0,now-anchor);anchor=now;playing=run;}
        return base+(playing?Math.max(0,now-anchor):0);
    }
}
