package dev.blackholelab;

/** Integrates hot speed edits while explicit recipe-time changes remain reproducible. */
public final class BlackHoleFlowClock {
    private double configured=Double.NaN,last=Double.NaN,phase,previousSpeed;
    public void reset(){configured=last=Double.NaN;}
    public double sample(double elapsed,double speed,double recipeTime){
        if(Double.compare(configured,recipeTime)!=0||!Double.isFinite(last)){
            phase=elapsed*speed;configured=recipeTime;last=elapsed;
        }else{
            // Partial ticks can briefly move backwards across client pause or
            // tick delivery. Only an explicit recipe-time edit is a seek.
            phase+=Math.max(0,elapsed-last)*previousSpeed;
            last=Math.max(last,elapsed);
        }
        previousSpeed=speed;
        return phase;
    }
}
