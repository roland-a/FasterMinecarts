package gpa.faster_minecarts;

import gpa.faster_minecarts.carts.util.FloorType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod.EventBusSubscriber(modid = FasterMinecarts.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ServerConfig {
    private ServerConfig(){}

    public static Double maxSpeed = null;
    private static final ForgeConfigSpec.IntValue maxSpeedSpec;

    public static Integer slowDownDist = null;
    private static final ForgeConfigSpec.IntValue slowDownDistSpec;

    public static Boolean useGravity = null;
    private static final ForgeConfigSpec.BooleanValue useGravitySpec;

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        maxSpeedSpec = builder
            .comment(
                    "\nThe maximum speed a cart can go"
            )
            .defineInRange(
                "maxSpeed",
                30,
                10, 100
            );

        slowDownDistSpec = builder
            .comment(
                "\nThe maximum number of normal rails to put between powered rails." +
                "\nIt's recommended to use (2^n)-1 values (3,7,15,31,63...) as these allow the easiest railway upgrades."
            )
            .defineInRange(
                "slowDownDistance",
                    15,
                    1, 100
            );

        useGravitySpec = builder
            .comment(
                "\nWhether gravity adds extra acceleration or deceleration on carts moving on sloped rails."
            )
            .define(
                "useGravityOnSlope",
                true
            );

        SPEC = builder.build();
    }

    @SubscribeEvent
    public static void onModConfigEvent(ModConfig.ModConfigEvent configEvent) {
        if (configEvent.getConfig().getSpec() == SPEC) {
            bake();
        }

        //System.out.printf("%s %s %s\n", FloorType.POWERED.getAcc(), FloorType.UNPOWERED.getAcc(), FloorType.RAIL.getAcc());
    }

    static void bake(){
        maxSpeed = maxSpeedSpec.get()/20d;
        slowDownDist = slowDownDistSpec.get();
        useGravity = useGravitySpec.get();
    }

}
