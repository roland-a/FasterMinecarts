package gpa.faster_minecarts.carts.util;

import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class VanillaCartReplacer {
    private VanillaCartReplacer(){}

    private static final Map<Class<? extends AbstractMinecartEntity>, Function<AbstractMinecartEntity, AbstractMinecartEntity>> minecartReplacementMap = new HashMap<>();

    @SubscribeEvent
    public static void replaceVanillaCart(EntityJoinWorldEvent e){
        if (!(e.getEntity() instanceof AbstractMinecartEntity)) return;

        Function<AbstractMinecartEntity, AbstractMinecartEntity> func = minecartReplacementMap.get(e.getEntity().getClass());

        if (func == null) return;

        //adds the replacement cart
        e.getWorld().addFreshEntity(
            func.apply((AbstractMinecartEntity)e.getEntity())
        );

        //prevents the original cart from spawning
        e.setCanceled(true);
    }

    public static void add(Class<? extends AbstractMinecartEntity> clazz, Function<AbstractMinecartEntity, AbstractMinecartEntity> maker){
        minecartReplacementMap.put(
            (Class<? extends AbstractMinecartEntity>)clazz.getSuperclass(),
            maker
        );
    }

}
