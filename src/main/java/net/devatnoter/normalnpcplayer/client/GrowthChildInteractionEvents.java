package net.devatnoter.normalnpcplayer.client;

import net.devatnoter.normalnpcplayer.entity.GrowthChildPlayerMobEntity;
import net.devatnoter.normalnpcplayer.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="normalnpcplayer", value=net.minecraftforge.api.distmarker.Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GrowthChildInteractionEvents {
    private GrowthChildInteractionEvents() {}
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event){
        if(!event.isUseItem() || event.getHand()!=InteractionHand.MAIN_HAND || !GrowthChildKeybinds.GUARD_MODIFIER.isDown())return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null)return;
        if(mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof GrowthChildPlayerMobEntity child && child.isAlive()){
            ModNetwork.sendGrowthChildGuard(child.getId());
            event.setCanceled(true);
            event.setSwingHand(true);
        }
    }
}
