package dev.energyvfx;
import java.util.*;
import org.lwjgl.opengl.GL20;
final class Uniforms {
 private static final Map<Integer,Map<String,Integer>> cache=new HashMap<>();
 static int location(int program,String name){return cache.computeIfAbsent(program,k->new HashMap<>()).computeIfAbsent(name,k->GL20.glGetUniformLocation(program,k));}
 static void clear(){cache.clear();}
}
