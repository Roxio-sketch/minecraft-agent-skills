package dev.blackholelab;

/** A speed slider must freeze the current phase, rather than rewind elapsed*speed. */
public final class BlackHoleFlowClockCheck {
    private static void equal(double actual,double expected){if(Math.abs(actual-expected)>1e-9)throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        var clock=new BlackHoleFlowClock();
        equal(clock.sample(8,1.15,8),9.2);
        equal(clock.sample(8,0,8),9.2);
        equal(clock.sample(28,0,8),9.2);
        equal(clock.sample(27.8,0,8),9.2);
        equal(clock.sample(28,2,8),9.2);
        equal(clock.sample(29,2,8),11.2);
        equal(clock.sample(29,2,8),11.2);
        equal(clock.sample(3,.5,3),1.5);
        equal(clock.sample(4,.5,3),2);
        clock.reset();equal(clock.sample(4,1,3),4);
        System.out.println("PASS: hot zero-speed freeze/resume, backward partial-tick tolerance, shared-frame sampling, explicit recipe-time seek and reset");
    }
}
