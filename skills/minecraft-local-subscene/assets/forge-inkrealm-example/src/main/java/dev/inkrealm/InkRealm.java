package dev.inkrealm;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

@Mod(InkRealm.ID)
public final class InkRealm {
    public static final String ID="inkrealm";
    public InkRealm(){DistExecutor.safeRunWhenOn(Dist.CLIENT,()->InkClient::initialize);}
}
