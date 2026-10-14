package dev.scenewindow;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.*;
import java.util.*;
import java.lang.Math;

/** Original scene graph, independent of Minecraft world simulation. */
public final class Scenes {
    public record Node(String type, Vector3f position, Vector3f scale, Vector3f rotation,
                       Vector3f spin, float[] color, float[] vertices, List<Node> children) {}
    public record Scene(float[] background, float[] frame, Vector3f eye, String camera,
                        int resolution, int shape, ResourceLocation mask, List<Node> nodes) {}
    private static final Map<ResourceLocation, Scene> CACHE = new HashMap<>();
    public static int generation;
    public static String lastError = "";
    public static void clear() { CACHE.clear(); lastError=""; generation++; }
    public static Scene get(ResourceManager manager, ResourceLocation id) {
        return CACHE.computeIfAbsent(id, key -> {
            ResourceLocation path = new ResourceLocation(key.getNamespace(), "scenes/"+key.getPath()+".json");
            try (var reader = manager.openAsReader(path)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.get("version").getAsInt()!=1) throw new IllegalArgumentException("Expected version 1");
                String camera = string(root,"camera","offaxis");
                if (!Set.of("offaxis","fixed","orbit").contains(camera)) throw new IllegalArgumentException("Unknown camera");
                String aperture = string(root,"aperture","rectangle");
                int shape = List.of("rectangle","circle","diamond","mask").indexOf(aperture);
                if(shape<0)throw new IllegalArgumentException("Unknown aperture");
                ResourceLocation mask=shape==3?new ResourceLocation(root.get("mask").getAsString()):null;
                int resolution=root.has("resolution")?root.get("resolution").getAsInt():256;
                if(resolution<64||resolution>1024)throw new IllegalArgumentException("Resolution must be 64..1024");
                Vector3f eye=vec(root,"eye",new Vector3f(0,0,2.5f));
                if(eye.z<0.1f)throw new IllegalArgumentException("Eye must be in front of window");
                return new Scene(color(root,"background",new float[]{.025f,.035f,.07f}),
                    color(root,"frame",new float[]{.7f,.5f,.18f}),eye,camera,resolution,shape,mask,parseNodes(root.getAsJsonArray("nodes"),0));
            } catch(Exception ex) {
                lastError=path+": "+ex.getMessage();
                throw new IllegalArgumentException(lastError,ex);
            }
        });
    }
    private static String string(JsonObject j,String key,String fallback){return j.has(key)?j.get(key).getAsString():fallback;}
    private static float[] numbers(JsonArray a,int expected) {
        if(expected>=0&&a.size()!=expected)throw new IllegalArgumentException("Wrong vector size");
        float[] v=new float[a.size()];
        for(int i=0;i<v.length;i++){v[i]=a.get(i).getAsFloat();if(!Float.isFinite(v[i]))throw new IllegalArgumentException("Non-finite value");}
        return v;
    }
    private static Vector3f vec(JsonObject o,String k,Vector3f d){if(!o.has(k))return new Vector3f(d);return new Vector3f(numbers(o.getAsJsonArray(k),3));}
    private static float[] color(JsonObject o,String k,float[] d){return o.has(k)?numbers(o.getAsJsonArray(k),3):d.clone();}
    private static List<Node> parseNodes(JsonArray array,int depth){
        if(depth>32||array==null||array.size()>4096)throw new IllegalArgumentException("Scene graph bounds");
        List<Node> nodes=new ArrayList<>();
        for(JsonElement e:array){
            JsonObject o=e.getAsJsonObject();String type=string(o,"type","box");
            if(!Set.of("box","mesh","group").contains(type))throw new IllegalArgumentException("Unknown node type: "+type);
            float[] vertices=type.equals("mesh")?numbers(o.getAsJsonArray("vertices"),-1):new float[0];
            if(vertices.length%9!=0||vertices.length>900000)throw new IllegalArgumentException("Mesh triangle list required");
            nodes.add(new Node(type,vec(o,"position",new Vector3f()),vec(o,"scale",new Vector3f(1)),
                vec(o,"rotation",new Vector3f()),vec(o,"spin",new Vector3f()),color(o,"color",new float[]{1,1,1}),vertices,
                o.has("children")?parseNodes(o.getAsJsonArray("children"),depth+1):List.of()));
        }return List.copyOf(nodes);
    }
    public interface Backend { void draw(Scene scene, float seconds); }
    public static final Backend MESH_BACKEND = (scene,time) -> {
        BufferBuilder b=Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
        for(Node node:scene.nodes())emit(b,node,new Matrix4f(),time);
        BufferUploader.drawWithShader(b.end());
    };
    private static void emit(BufferBuilder b,Node n,Matrix4f parent,float seconds){
        Vector3f angles=new Vector3f(n.spin()).mul(seconds).add(n.rotation()).mul((float)(Math.PI/180));
        Matrix4f m=new Matrix4f(parent).translate(n.position()).rotateXYZ(angles.x,angles.y,angles.z).scale(n.scale());
        if(n.type().equals("box")){
            float[][] v={{-.5f,-.5f,-.5f},{.5f,-.5f,-.5f},{.5f,.5f,-.5f},{-.5f,.5f,-.5f},{-.5f,-.5f,.5f},{.5f,-.5f,.5f},{.5f,.5f,.5f},{-.5f,.5f,.5f}};
            int[][] faces={{4,5,6,7},{1,0,3,2},{0,4,7,3},{5,1,2,6},{3,7,6,2},{0,1,5,4}};
            for(int f=0;f<6;f++)for(int i:new int[]{0,1,2,0,2,3}){
                float[] p=v[faces[f][i]];vertex(b,m,p[0],p[1],p[2],n.color(),new float[]{1,.45f,.64f,.78f,.95f,.4f}[f]);
            }
        }else if(n.type().equals("mesh")){
            float[] v=n.vertices();for(int i=0;i<v.length;i+=3)vertex(b,m,v[i],v[i+1],v[i+2],n.color(),1);
        }
        for(Node child:n.children())emit(b,child,m,seconds);
    }
    private static void vertex(BufferBuilder b,Matrix4f m,float x,float y,float z,float[] c,float shade){
        b.vertex(m,x,y,z).color(c[0]*shade,c[1]*shade,c[2]*shade,1).endVertex();
    }
}
