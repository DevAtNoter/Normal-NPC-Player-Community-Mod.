package net.devatnoter.normalnpcplayer;

import com.mojang.logging.LogUtils;
import net.devatnoter.normalnpcplayer.capability.PlayerFamilyData;
import net.devatnoter.normalnpcplayer.client.model.AdultPlayerModelLayers;
import net.devatnoter.normalnpcplayer.client.renderer.AdultPlayerMobRenderer;
import net.devatnoter.normalnpcplayer.client.renderer.BabyNPCPlayerRenderer;
import net.devatnoter.normalnpcplayer.registry.ModCreativeTabs;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.devatnoter.normalnpcplayer.registry.ModMenus;
import net.devatnoter.normalnpcplayer.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(NormalNPCPlayer.MOD_ID)
public class NormalNPCPlayer {

    public static final String MOD_ID = "normalnpcplayer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @SuppressWarnings("removal")
    public NormalNPCPlayer() {
        IEventBus modEventBus =
                FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Packet / datapack / commands
        });
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerFamilyData.class);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Normal NPC Player server starting.");
    }

    @Mod.EventBusSubscriber(
            modid = MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.MOD,
            value = Dist.CLIENT
    )
    public static final class ClientModEvents {

        private ClientModEvents() {
        }

        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() ->
                    net.minecraft.client.gui.screens.MenuScreens.register(
                            ModMenus.BABY_INVENTORY.get(),
                            net.devatnoter.normalnpcplayer.client.screen.BabyInventoryScreen::new
                    )
            );
        }

        @SubscribeEvent
        public static void registerLayerDefinitions(
                EntityRenderersEvent.RegisterLayerDefinitions event
        ) {
            event.registerLayerDefinition(
                    AdultPlayerModelLayers.PLAYER,
                    AdultPlayerModelLayers::createWideLayer
            );

            event.registerLayerDefinition(
                    AdultPlayerModelLayers.PLAYER_SLIM,
                    AdultPlayerModelLayers::createSlimLayer
            );
        }

        @SubscribeEvent
        public static void registerRenderers(
                EntityRenderersEvent.RegisterRenderers event
        ) {
            event.registerEntityRenderer(
                    ModEntities.BABY_NPC_PLAYER.get(),
                    BabyNPCPlayerRenderer::new
            );

            event.registerEntityRenderer(
                    ModEntities.ADULT_PLAYER_MOB.get(),
                    AdultPlayerMobRenderer::new
            );
        }
    }
}
