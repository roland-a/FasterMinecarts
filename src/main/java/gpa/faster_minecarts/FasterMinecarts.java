package gpa.faster_minecarts;

import gpa.faster_minecarts.carts.*;
import gpa.faster_minecarts.carts.util.VanillaCartReplacer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(FasterMinecarts.MOD_ID)
public final class FasterMinecarts {
    public static final String MOD_ID = "faster_minecarts";

    public FasterMinecarts() {
        MinecraftForge.EVENT_BUS.register(VanillaCartReplacer.class);
        MinecraftForge.EVENT_BUS.register(ServerConfig.class);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        MinecartEntityExt.init();
    }
}
