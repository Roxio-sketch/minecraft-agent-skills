package dev.shieldfx;

/** Fixed tick lifecycle: the hit reaching zero leaves a red shell; the next hit breaks it. */
public final class ShieldLifecycle {
    public enum State { INTACT, SHATTERING, BROKEN }
    private State state=State.INTACT;
    private double life=1, age, duration;
    public void reset(double initialLife){state=State.INTACT;life=Math.max(0,Math.min(1,initialLife));age=0;duration=0;}
    public boolean hit(double damage,double shatterDuration){
        if(state!=State.INTACT)return false;
        if(life>1e-6){life=Math.max(0,life-damage);if(life<1e-6)life=0;}
        else {state=State.SHATTERING;age=0;duration=shatterDuration;}
        return true;
    }
    public void tick(double dt){if(state==State.SHATTERING){age=Math.min(duration,age+Math.max(0,dt));if(age>=duration-1e-9){age=duration;state=State.BROKEN;}}}
    public State state(){return state;}
    public double life(){return life;}
    public double age(){return age;}
    public double duration(){return duration;}
}
