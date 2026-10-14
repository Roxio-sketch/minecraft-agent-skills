package cn.blockforge.generated.generatedmod.client;

/** QA-only bytecode helper mirroring the reviewable production hook patch. */
public final class AlchemyVolumeExternalHook {
    private static java.lang.reflect.Method claim;
    private static boolean checked;
    public static boolean claims(Object furnace) {
        if(!checked){checked=true;try{claim=Class.forName("dev.alchemyvfx.HostHook").getMethod("claims",Object.class);}catch(ReflectiveOperationException absentRuntime){}}
        if(claim==null)return false;
        try{return Boolean.TRUE.equals(claim.invoke(null,furnace));}catch(ReflectiveOperationException failedRuntime){return false;}
    }
    private AlchemyVolumeExternalHook() {}
}
