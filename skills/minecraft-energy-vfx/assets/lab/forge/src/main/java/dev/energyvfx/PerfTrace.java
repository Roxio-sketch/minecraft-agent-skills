package dev.energyvfx;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.*;

/** Opt-in asynchronous GPU timestamps. Never waits for an unavailable query. */
@Mod.EventBusSubscriber(modid=EnergyVfx.ID,value=Dist.CLIENT)
public final class PerfTrace {
 static final boolean ENABLED=Boolean.getBoolean("energyvfx.perfQa");
 private static final int RING=24,MARKS=9;
 private static final int[][] queries=new int[RING][MARKS];
 private static final boolean[] pending=new boolean[RING];
 private static final String[] labels=new String[RING];
 private static final long[] frameIds=new long[RING];
 private static final JsonArray rawFrames=new JsonArray();
 static long frameId;
 private static final Map<String,Map<String,List<Double>>> samples=new LinkedHashMap<>();
 private static boolean initialized,supported;private static int slot=-1,next,dropped;
 private static long previousFrame,cpuBegan;
 private static boolean passMarked;private static final boolean[] marked=new boolean[MARKS];
 static void cpu(String metric,long nanos){if(ENABLED&&PerformanceQa.recording)add(PerformanceQa.label,metric,nanos/1e6);}
 static void value(String metric,double value){if(ENABLED&&PerformanceQa.recording)add(PerformanceQa.label,metric,value);}
 private static void add(String label,String metric,double ms){if(label==null||!Double.isFinite(ms)||ms<0)return;samples.computeIfAbsent(label,k->new LinkedHashMap<>()).computeIfAbsent(metric,k->new ArrayList<>()).add(ms);}
 private static void init(){if(initialized)return;initialized=true;supported=GL.getCapabilities().OpenGL33||GL.getCapabilities().GL_ARB_timer_query;if(supported)for(int i=0;i<RING;i++)for(int j=0;j<MARKS;j++)queries[i][j]=GL15.glGenQueries();}
 static void collect(){if(!initialized||!supported)return;for(int i=0;i<RING;i++)if(pending[i]&&GL15.glGetQueryObjecti(queries[i][7],GL15.GL_QUERY_RESULT_AVAILABLE)!=0){long[] t=new long[MARKS];for(int j=0;j<MARKS;j++)t[j]=GL33.glGetQueryObjectui64(queries[i][j],GL15.GL_QUERY_RESULT);String label=labels[i];add(label,"frameGpuMs",(t[7]-t[0])/1e6);add(label,"vfxGpuMs",(t[6]-t[1])/1e6);add(label,"copyGpuMs",(t[2]-t[1])/1e6);add(label,"flowCacheGpuMs",(t[8]-t[2])/1e6);add(label,"volumeWithCacheGpuMs",(t[3]-t[2])/1e6);add(label,"volumeGpuMs",(t[3]-t[8])/1e6);add(label,"bloomGpuMs",(t[5]-t[3])/1e6);add(label,"compositeGpuMs",(t[6]-t[5])/1e6);if((PerformanceQa.BUILDUP || ZoomMotionQa.ENABLED)){JsonObject row=new JsonObject();row.addProperty("frameId",frameIds[i]);row.addProperty("case",label);row.addProperty("frameGpuMs",(t[7]-t[0])/1e6);row.addProperty("vfxGpuMs",(t[6]-t[1])/1e6);row.addProperty("copyGpuMs",(t[2]-t[1])/1e6);row.addProperty("flowCacheGpuMs",(t[8]-t[2])/1e6);row.addProperty("volumeGpuMs",(t[3]-t[8])/1e6);row.addProperty("bloomGpuMs",(t[5]-t[3])/1e6);row.addProperty("compositeGpuMs",(t[6]-t[5])/1e6);rawFrames.add(row);}pending[i]=false;}}
 static JsonArray rawFrames(){collect();return rawFrames;}
 static void mark(int index){if(slot<0)return;GL33.glQueryCounter(queries[slot][index],GL33.GL_TIMESTAMP);marked[index]=true;passMarked=true;}
 @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){if(!ENABLED||!PerformanceQa.ready||PerformanceQa.done)return;
  if(e.phase==TickEvent.Phase.START){frameId++;init();collect();long now=System.nanoTime();cpuBegan=now;slot=-1;passMarked=false;Arrays.fill(marked,false);if(PerformanceQa.recording){if(previousFrame!=0)add(PerformanceQa.label,"frameIntervalMs",(now-previousFrame)/1e6);if(supported){for(int n=0;n<RING;n++){int i=(next+n)%RING;if(!pending[i]){slot=i;next=(i+1)%RING;break;}}if(slot>=0){labels[slot]=PerformanceQa.label;frameIds[slot]=frameId;GL33.glQueryCounter(queries[slot][0],GL33.GL_TIMESTAMP);}else dropped++;}}previousFrame=now;
  }else{if(PerformanceQa.recording)add(PerformanceQa.label,"frameSubmitCpuMs",(System.nanoTime()-cpuBegan)/1e6);if(slot>=0){if(!marked[8])GL33.glQueryCounter(queries[slot][8],GL33.GL_TIMESTAMP);for(int j=1;j<=6;j++)if(!marked[j])GL33.glQueryCounter(queries[slot][j],GL33.GL_TIMESTAMP);GL33.glQueryCounter(queries[slot][7],GL33.GL_TIMESTAMP);pending[slot]=true;slot=-1;}}
 }
 static void resetFrameClock(){previousFrame=0;}
 static int count(String label){var row=samples.get(label);return row==null||!row.containsKey("frameGpuMs")?0:row.get("frameGpuMs").size();}
 static JsonObject report(){collect();JsonObject result=new JsonObject();result.addProperty("gpuTimerAvailable",supported);result.addProperty("droppedQueryFrames",dropped);result.addProperty("gpuReadback","Only available timestamp results are read; no glFinish or blocking query wait");JsonObject cases=new JsonObject();for(var row:samples.entrySet()){JsonObject metrics=new JsonObject();for(var metric:row.getValue().entrySet()){double[] values=metric.getValue().stream().mapToDouble(Double::doubleValue).sorted().toArray();if(values.length==0)continue;JsonObject stats=new JsonObject();stats.addProperty("samples",values.length);stats.addProperty("mean",Arrays.stream(values).average().orElse(0));stats.addProperty("median",values[values.length/2]);stats.addProperty("p95",values[(int)Math.floor((values.length-1)*.95)]);stats.addProperty("p99",values[(int)Math.floor((values.length-1)*.99)]);stats.addProperty("max",values[values.length-1]);metrics.add(metric.getKey(),stats);}cases.add(row.getKey(),metrics);}result.add("cases",cases);return result;}
}
