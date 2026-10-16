package dev.spacerift;

/** Bit-for-bit LCG traversal matches geometry.js, within float coordinate precision. */
final class RiftGeometry {
    final float[] spine=new float[132],a=new float[32],b=new float[32],x=new float[33];
    long seed;
    float random(){seed=(1664525L*seed+1013904223L)&0xffffffffL;return (float)(seed/4294967296.0);}
    void split(int l,int r,float amp){if(r-l<2)return;int m=(l+r)/2;x[m]=(x[l]+x[r])*.5f+(random()-.5f)*amp;split(l,m,amp*.53f);split(m,r,amp*.53f);}
    RiftGeometry(RiftParameters p){
        seed=(long)p.get("u_seed")+1;split(0,32,p.get("u_bend")*1.65f);
        float len=p.get("u_length");
        for(int i=0;i<33;i++){spine[i*4]=x[i];spine[i*4+1]=(i/32f-.5f)*len;}
        for(int i=0;i<8;i++){
            float y=((i+.4f+random()*.2f)/8*1.64f-.82f)*len*.5f,s=(y/len+.5f)*32;int j=Math.min(31,(int)s);
            float cx=x[j]+(x[j+1]-x[j])*(s-j),side=i%2==0?-1:1,reach=p.get("u_branchLength")*(.65f+random()*.65f),dy=(random()-.5f)*reach*1.9f;
            float tip=cx+side*(p.get("u_width")*(p.get("u_mode")>.5?2:1)+reach);
            a[i*4]=cx;a[i*4+1]=y;a[i*4+2]=cx+(tip-cx)*.55f;a[i*4+3]=y+dy*.45f;
            b[i*4]=tip;b[i*4+1]=y+dy;b[i*4+2]=.22f+random()*.3f;b[i*4+3]=random();
        }
    }
    static String key(RiftParameters p){return p.get("u_seed")+":"+p.get("u_length")+":"+p.get("u_width")+":"+p.get("u_bend")+":"+p.get("u_branchLength")+":"+p.get("u_mode");}
}
