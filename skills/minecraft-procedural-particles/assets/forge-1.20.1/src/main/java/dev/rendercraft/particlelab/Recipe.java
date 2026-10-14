package dev.rendercraft.particlelab;
/** Public mutable authoring fields, validated before replacing current recipe. */
final class Recipe {
 float[] color={.25f,.65f,1},accent={1,.35f,.12f};float radius=7,intensity=1.4f,width=3,rate=32,lifetime=2.5f,size=.06f,speed=1.6f,drag=.3f,gravity=-.4f,orbit=1.4f,amplitude=.55f,frequency=.7f;int capacity=256,passes=2;long seed=42;String motion="orbit";
 void validate(){if(color==null||accent==null||color.length!=3||accent.length!=3)throw new IllegalArgumentException("Colors require three components");for(float x:color)finite(x,0,16);for(float x:accent)finite(x,0,16);finite(radius,.1f,128);finite(intensity,0,16);finite(width,.1f,16);finite(rate,0,4096);finite(lifetime,.05f,60);finite(size,.001f,4);finite(speed,0,64);finite(drag,0,64);finite(gravity,-64,64);finite(orbit,-64,64);finite(amplitude,0,3.14f);finite(frequency,0,16);if(capacity<1||capacity>16384||passes<1||passes>8||!java.util.Set.of("orbit","burst","fountain","attract").contains(motion))throw new IllegalArgumentException("Invalid budget or motion");}
 static void finite(float v,float min,float max){if(!Float.isFinite(v)||v<min||v>max)throw new IllegalArgumentException("Out-of-range value: "+v);}
}
