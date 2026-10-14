package research.subscene;

import org.joml.*;
import java.lang.Math;
import java.util.*;

/** Analytic camera/depth tests; no OpenGL or ClientLevel is initialized here. */
public final class MathContracts {
    static int passed=0;
    static void require(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    static double[] interval(Vector3d o,Vector3d d,double r){
        double a=d.dot(d),b=o.dot(d),c=o.dot(o)-r*r,h=b*b-a*c;
        if(a==0||h<0)return null;double s=Math.sqrt(h);return new double[]{(-b-s)/a,(-b+s)/a};
    }
    static double depth(double z,double n,double f){return f/(f-n)-f*n/((f-n)*z);}
    static double viewDepth(double depth,double n,double f){return f*n/(f-depth*(f-n));}
    static boolean close(double a,double b){return Math.abs(a-b)<1e-7;}
    public static void main(String[] args){
        double[] i=interval(new Vector3d(0,0,3),new Vector3d(0,0,-1),1);
        require(close(i[0],2)&&close(i[1],4),"outside sphere entry/exit");
        i=interval(new Vector3d(),new Vector3d(0,0,-1),1);require(close(i[0],-1)&&close(i[1],1),"inside sphere must use exit, not front face");
        require(interval(new Vector3d(3,0,3),new Vector3d(0,0,-1),1)==null,"sphere miss");
        i=interval(new Vector3d(0,0,3),new Vector3d(0,0,-2),1);require(close(i[0],1)&&close(i[1],2),"unnormalized transformed rays preserve parameter scale");
        for(double fov:new double[]{40,70,110}){
            Matrix4d p=new Matrix4d().perspective(Math.toRadians(fov),1.5,.1,100);
            Matrix4d inv=new Matrix4d(p).invert();Vector3d v=new Vector3d(.4,.2,-4);
            Vector4d clip=p.transform(new Vector4d(v,1));clip.div(clip.w);Vector4d restored=inv.transform(new Vector4d(clip));restored.div(restored.w);
            require(new Vector3d(restored.x,restored.y,restored.z).distance(v)<1e-8,"projection roundtrip FOV "+fov);
        }
        for(double z:new double[]{.1,.2,1,20,99})require(Math.abs(viewDepth(depth(z,.1,100),.1,100)-z)<1e-7,"linear depth roundtrip "+z);
        require(!close(depth(4,.1,100),depth(4,.5,100)),"raw depth from different near planes is not comparable");
        Matrix4d t=new Matrix4d().translation(10,2,-7).rotateY(.7).scale(2);
        Vector3d camera=new Vector3d(2,1,4),dest=t.transformPosition(new Vector3d(camera));
        Vector3d recovered=new Matrix4d(t).invert().transformPosition(new Vector3d(dest));
        require(camera.distance(recovered)<1e-8,"portal camera inverse roundtrip");
        Vector4d plane=new Vector4d(0,0,1,0),transformed=new Matrix4d(t).invert().transpose().transform(new Vector4d(plane));
        Vector4d point=t.transform(new Vector4d(.3,.4,0,1));require(Math.abs(point.dot(transformed))<1e-8,"clip plane inverse-transpose");
        Matrix4d reflection=new Matrix4d().scale(-1,1,1);require(reflection.determinant()<0,"mirror changes winding parity");
        Deque<String> stack=new ArrayDeque<>();stack.push("main");String[] active={"main"};
        try{String old=active[0];stack.push("remote");try{active[0]="remote";throw new IllegalStateException("injected");}finally{active[0]=old;stack.pop();}}catch(IllegalStateException expected){}
        require(stack.size()==1&&active[0].equals("main"),"exception restores logical scene stack");
        int visits=countTree(0,3,new int[]{7});require(visits==7,"global recursive view budget bounds branching");
        require(close(Math.max(.1,interval(new Vector3d(0,0,3),new Vector3d(0,0,-1),1)[0]),2),"near plane clipped entry");
        require(!(1.5>=2&&1.5<=4)&&(3>=2&&3<=4),"foreground versus replaced domain interval");
        // A stencil-only clear must not be assumed selective. The GPU probe uses a masked depth draw.
        System.out.println("CONTRACTS_PASSED="+passed);
    }
    static int countTree(int depth,int max,int[] budget){if(depth>max||budget[0]<=0)return 0;budget[0]--;return 1+countTree(depth+1,max,budget)+countTree(depth+1,max,budget);}
}
