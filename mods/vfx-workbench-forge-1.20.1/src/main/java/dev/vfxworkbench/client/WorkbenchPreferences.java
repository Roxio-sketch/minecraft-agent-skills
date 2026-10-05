package dev.vfxworkbench.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.nio.file.Path;

/** UI selection only; effect parameter defaults and presets remain independent. */
final class WorkbenchPreferences {
    private static boolean loaded;
    private static String selected;
    private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("config/vfxworkbench/ui.json");}
    static String selectedSkill(){
        if(!loaded){
            loaded=true;
            try{
                if(Files.isRegularFile(file())){
                    var json=JsonParser.parseString(Files.readString(file())).getAsJsonObject();
                    if(json.has("selectedSkill")) selected=json.get("selectedSkill").getAsString();
                }
            }catch(Exception ex){System.err.println("[VFXWORKBENCH] Cannot read UI selection: "+ex.getMessage());}
        }
        return selected;
    }
    static void remember(String id){
        if(java.util.Objects.equals(id,selected))return;
        selected=id;loaded=true;
        try{
            Path path=file();Files.createDirectories(path.getParent());
            var json=new JsonObject();json.addProperty("selectedSkill",id);
            Files.writeString(path,json.toString());
        }catch(Exception ex){System.err.println("[VFXWORKBENCH] Cannot save UI selection: "+ex.getMessage());}
    }
}
