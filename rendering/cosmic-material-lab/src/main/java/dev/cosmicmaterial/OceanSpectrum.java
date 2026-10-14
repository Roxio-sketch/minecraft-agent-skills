package dev.cosmicmaterial;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;
import java.util.*;

/** Original dual-domain Gaussian spectrum + inverse FFT, shared per material
 * state. Uses no compute-shader extension and never changes carrier geometry. */
final class OceanSpectrum {
    private static final int N=128,COUNT=N*N;
    private static final double TAU=2*Math.PI;
    private static final int[] REVERSE=new int[N];
    private static final double[] COS=new double[N/2],SIN=new double[N/2];
    private record Key(int seed,float length,float wind,float depth,float spread) {}
    private static final LinkedHashMap<Key,State> CACHE=new LinkedHashMap<>(8,.75f,true);
    static {
        for(int i=0;i<N;i++){int v=i,r=0;for(int b=0;b<7;b++){r=r*2+(v&1);v>>>=1;}REVERSE[i]=r;}
        for(int i=0;i<N/2;i++){COS[i]=Math.cos(TAU*i/N);SIN[i]=Math.sin(TAU*i/N);}
    }
    static void clear(){for(State s:CACHE.values())s.close();CACHE.clear();}
    static void bind(FantasyPresets.Preset p,ShaderInstance shader,float seconds){
        RenderSystem.assertOnRenderThread();
        Key key=new Key((int)p.seed(),p.waveLength(),p.windAngle()%360,p.depth(),p.warp());
        State state=CACHE.get(key);
        if(state==null){state=new State(key);CACHE.put(key,state);if(CACHE.size()>8){var it=CACHE.entrySet().iterator();var old=it.next();old.getValue().close();it.remove();}}
        float time=p.speed()==0?0:seconds*p.speed();
        if(Float.floatToIntBits(time)!=Float.floatToIntBits(state.time)){
            for(Cascade c:state.cascades){c.update(time);c.upload();}state.time=time;
        }
        shader.setSampler("fmOcean0",state.cascades[0].texture);
        shader.setSampler("fmOcean1",state.cascades[1].texture);
        shader.safeGetUniform("fmOceanLengths").set((float)state.cascades[0].length,(float)state.cascades[1].length);
    }
    private static final class State {
        final Cascade[] cascades;float time=Float.NaN;
        State(Key key){cascades=new Cascade[]{new Cascade(key,0),new Cascade(key,1)};}
        void close(){for(Cascade c:cascades)GlStateManager._deleteTexture(c.texture);}
    }
    private static int hash(int x){x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;return x^(x>>>16);}
    private static double random(int x){return((hash(x)>>>8)+.5)/16777216.0;}
    private static void transform1(double[] r,double[] im,int offset,int stride){
        for(int i=0;i<N;i++){int j=REVERSE[i];if(j>i){int a=offset+i*stride,b=offset+j*stride;double t=r[a];r[a]=r[b];r[b]=t;t=im[a];im[a]=im[b];im[b]=t;}}
        for(int size=2;size<=N;size*=2){int half=size/2,jump=N/size;
            for(int start=0;start<N;start+=size)for(int j=0;j<half;j++){
                int a=offset+(start+j)*stride,b=a+half*stride,k=j*jump;
                double tr=r[b]*COS[k]-im[b]*SIN[k],ti=r[b]*SIN[k]+im[b]*COS[k];
                r[b]=r[a]-tr;im[b]=im[a]-ti;r[a]+=tr;im[a]+=ti;
            }
        }
    }
    private static void inverse2(double[] r,double[] im){
        for(int y=0;y<N;y++)transform1(r,im,y*N,1);for(int x=0;x<N;x++)transform1(r,im,x,N);
        for(int i=0;i<COUNT;i++){r[i]/=COUNT;im[i]/=COUNT;}
    }
    private static final class Cascade {
        final double length;
        final double[] h0r=new double[COUNT],h0i=new double[COUNT],omega=new double[COUNT],kx=new double[COUNT],ky=new double[COUNT];
        final double[][] r=new double[3][COUNT],im=new double[3][COUNT];
        final FloatBuffer pixels=BufferUtils.createFloatBuffer(COUNT*4);
        final int texture=GlStateManager._genTexture();boolean allocated;
        Cascade(Key p,int layer){
            length=p.length*(layer==1?3.7:32);
            double peak=p.length*(layer==1?.45:4),cut=p.length*(layer==1?.025:.10),wind=p.wind*Math.PI/180,sum=0;
            for(int y=0;y<N;y++)for(int x=0;x<N;x++){
                int i=y*N+x;double vx=(x<=N/2?x:x-N)*TAU/length,vy=(y<=N/2?y:y-N)*TAU/length,k2=vx*vx+vy*vy,k=Math.sqrt(k2);
                if(k==0||x==N/2||y==N/2)continue;
                double alignment=(vx*Math.cos(wind)+vy*Math.sin(wind))/k;
                double direction=(.035+.965*Math.pow(Math.abs(alignment),(layer==1?2:4)*(1.3-.3*p.spread)))*(alignment<0?.09:1);
                double power=Math.exp(-1/(k2*Math.pow(peak/TAU,2)))/Math.pow(k2,2)*direction*Math.exp(-k2*cut*cut);
                int seed=p.seed^(layer==1?0x243f6a88:0x13198a2e);
                double u=random((i+1)*1597334677^seed),v=random((i+1)*(int)3812015801L^seed^0x6a09e667),g=Math.sqrt(-2*Math.log(u));
                h0r[i]=g*Math.cos(TAU*v)*Math.sqrt(power);h0i[i]=g*Math.sin(TAU*v)*Math.sqrt(power);sum+=power;
                omega[i]=Math.sqrt(9.81*k*Math.tanh(k*p.depth));kx[i]=vx/k;ky[i]=vy/k;
            }
            double scale=(layer==1?.10:.90)*COUNT/Math.sqrt(Math.max(4*sum,1e-20));
            for(int i=0;i<COUNT;i++){h0r[i]*=scale;h0i[i]*=scale;}
        }
        void update(double t){
            for(int y=0;y<N;y++)for(int x=0;x<N;x++){
                int i=y*N+x,j=((N-y)%N)*N+(N-x)%N;double c=Math.cos(omega[i]*t),s=Math.sin(omega[i]*t);
                double hr=(h0r[i]+h0r[j])*c-(h0i[i]+h0i[j])*s,hi=(h0r[i]-h0r[j])*s+(h0i[i]-h0i[j])*c;
                r[0][i]=hr;im[0][i]=hi;r[1][i]=-hi*kx[i];im[1][i]=hr*kx[i];r[2][i]=-hi*ky[i];im[2][i]=hr*ky[i];
            }
            for(int i=0;i<3;i++)inverse2(r[i],im[i]);
            pixels.clear();for(int i=0;i<COUNT;i++)pixels.put((float)r[0][i]).put((float)r[1][i]).put((float)r[2][i]).put(0);pixels.flip();
        }
        void upload(){
            int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0+3);
            int oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            if(!allocated){GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,N,N,0,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);allocated=true;}
            else GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,N,N,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._bindTexture(oldTexture);RenderSystem.activeTexture(oldActive);
        }
    }
}
