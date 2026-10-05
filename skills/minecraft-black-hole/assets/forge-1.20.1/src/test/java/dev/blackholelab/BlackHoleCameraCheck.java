package dev.blackholelab;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Independent affine-camera oracle, including Minecraft's bob inside projection. */
public final class BlackHoleCameraCheck {
    private BlackHoleCameraCheck() {}

    public static void run() {
        int cases=0;double maxEyeError=0,maxRayError=0,maxOldPixelDrift=0;
        Vec3 anchor=new Vec3(101.25,65.8,-22.5);
        for(float far:new float[]{32,256,1024})for(float fov:new float[]{45,70,110})
            for(float phase:new float[]{0,.17f,.43f,.71f,.93f})for(int mode=0;mode<4;mode++) {
                Matrix4f view=new Matrix4f().rotateZ(.04f).rotateX(-.09f).rotateY(.18f);
                if(mode==3)view.translate(.04f,-.03f,.02f);
                Matrix4f cameraEffect=new Matrix4f();
                if(mode!=0) {
                    // Same order as GameRenderer: hurt, walking translation,
                    // walking rotations, then the affine portal distortion.
                    cameraEffect.rotateY(-.6f).rotateZ(.12f).rotateY(.6f);
                    float s=(float)Math.sin(phase*Math.PI*2),c=(float)Math.cos(phase*Math.PI*2);
                    cameraEffect.translate(s*.07f,-Math.abs(c)*.14f,0).rotateZ(s*.008f).rotateX(Math.abs(c)*.012f);
                    if(mode==2)cameraEffect.rotateZ(.3f).scale(1.08f,1,1).rotateZ(-.3f);
                }
                Matrix4f base=new Matrix4f().perspective((float)Math.toRadians(fov),1600f/900,.05f,far);
                if(mode==2)base.m20(.015f).m21(-.01f); // Off-axis projection retains its perspective eye.
                Matrix4f vp=new Matrix4f(base).mul(cameraEffect).mul(view),inverse=new Matrix4f(vp).invert();
                Vec3 eye=BlackHoleCamera.origin(inverse);
                Vector3f oracleEye=new Matrix4f(cameraEffect).mul(view).invert().transformPosition(new Vector3f());
                double eyeError=eye.distanceTo(new Vec3(oracleEye.x,oracleEye.y,oracleEye.z));
                maxEyeError=Math.max(maxEyeError,eyeError);
                if(eyeError>2e-6)throw new AssertionError("projection bob eye was lost: "+eyeError);

                Vec3 camera=new Vec3(100+phase*.7,65+phase*.2,-1-phase*1.5),relative=anchor.subtract(camera);
                Vector4f clip=new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1).mul(vp);
                float nx=clip.x/clip.w,ny=clip.y/clip.w;
                Vector4f point=new Vector4f(nx,ny,1,1).mul(inverse);point.div(point.w);
                Vec3 rd=new Vec3(point.x,point.y,point.z).subtract(eye).normalize();
                Vec3 toward=relative.subtract(eye).normalize();
                double rayError=rd.distanceTo(toward);maxRayError=Math.max(maxRayError,rayError);
                if(rayError>2e-6)throw new AssertionError("fixed world anchor does not lie on reconstructed eye ray: "+rayError);
                Vec3 hit=eye.add(rd.scale(relative.subtract(eye).length()));
                if(hit.distanceTo(relative)>.0001)throw new AssertionError("ray origin moved a fixed world anchor");
                if(camera.add(relative).distanceTo(anchor)>1e-12)throw new AssertionError("camera-relative coordinates changed world center");

                // The old shader normalized an unprojected FAR POINT as if it
                // were a direction from zero. Its center is where that far
                // point lies on the zero-to-anchor line, not the real eye ray.
                Vec3 oldAxis=relative.normalize();
                double denominator=(vp.m02()-vp.m03())*oldAxis.x+(vp.m12()-vp.m13())*oldAxis.y+(vp.m22()-vp.m23())*oldAxis.z;
                double t=-(vp.m32()-vp.m33())/denominator;
                Vector4f oldClip=new Vector4f((float)(oldAxis.x*t),(float)(oldAxis.y*t),(float)(oldAxis.z*t),1).mul(vp);
                double oldPixels=Math.hypot((oldClip.x/oldClip.w-nx)*800,(oldClip.y/oldClip.w-ny)*450);
                maxOldPixelDrift=Math.max(maxOldPixelDrift,oldPixels);cases++;
            }
        if(maxOldPixelDrift<2)throw new AssertionError("regression does not reproduce visible bob drift");
        Matrix4f zeroView=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.05f,256).rotateXYZ(.1f,.3f,-.2f);
        if(BlackHoleCamera.origin(zeroView.invert()).length()>1e-7)throw new AssertionError("rotation-only camera eye changed");
        System.out.println("PASS: "+cases+" fixed-anchor bob/hurt/off-axis/portal/view-translation cases; max eye error="+maxEyeError+", ray error="+maxRayError+", reproduced old drift="+maxOldPixelDrift+" px");
    }
}
