package com.sealedwordorb;

import com.sealedwordorb.item.SealedWordOrbItem;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Handle entities before their own UI or interaction consumes the right click. */
@Mod.EventBusSubscriber(modid = SealedWordOrb.MOD_ID)
public final class OrbInteractions {
    private OrbInteractions() {}

    @SubscribeEvent
    public static void onItemRightClick(PlayerInteractEvent.RightClickItem event) {
        // Run before vanilla's creative-mode count restoration and returned-stack assignment.
        // This also preserves any new hand item created by the orb's command.
        useOrb(event);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        useOrb(event);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        useOrb(event);
    }

    private static void useOrb(PlayerInteractEvent event) {
        if (event.getItemStack().is(SealedWordOrb.ORB.get())) {
            event.setCancellationResult(SealedWordOrbItem.activate(event.getEntity(), event.getHand()).getResult());
            event.setCanceled(true);
        }
    }
}
