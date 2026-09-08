package net.devatnoter.normalnpcplayer;

import com.mojang.logging.LogUtils;
import net.devatnoter.normalnpcplayer.capability.PlayerFamilyData;
import net.devatnoter.normalnpcplayer.client.model.AdultPlayerModelLayers;
import net.devatnoter.normalnpcplayer.client.renderer.AdultPlayerMobRenderer;
import net.devatnoter.normalnpcplayer.client.renderer.BabyNPCPlayerRenderer;
import net.devatnoter.normalnpcplayer.registry.ModCreativeTabs;
import net.devatnoter.normalnpcplayer.registry.ModBlocks;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.devatnoter.normalnpcplayer.registry.ModEffects;
import net.devatnoter.normalnpcplayer.registry.ModFeatures;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.devatnoter.normalnpcplayer.registry.ModMenus;
import net.devatnoter.normalnpcplayer.registry.ModLootModifiers;
import net.devatnoter.normalnpcplayer.registry.ModRecipeSerializers;
import net.devatnoter.normalnpcplayer.registry.ModParticles;
import net.devatnoter.normalnpcplayer.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.client.ConfigScreenHandler;
import net.devatnoter.normalnpcplayer.config.NNPConfig;
import net.devatnoter.normalnpcplayer.client.screen.GuideBookConfigScreen;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTab;
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

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModEntities.register(modEventBus);
        ModEffects.register(modEventBus);
        ModFeatures.FEATURES.register(modEventBus);
        ModMenus.register(modEventBus);
        ModLootModifiers.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);
        ModParticles.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, NNPConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ModLoadingContext.get().registerExtensionPoint(
                        ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(
                                (minecraft, parent) -> new GuideBookConfigScreen(parent)
                        )
                )
        );

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::buildCreativeModeTabContents);
        modEventBus.addListener(this::registerCapabilities);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void buildCreativeModeTabContents(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            // Green Apple goes immediately before the vanilla Apple.
            event.getEntries().putBefore(
                    Items.APPLE.getDefaultInstance(),
                    ModItems.GREEN_APPLE.get().getDefaultInstance(),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
            );

            // Oak variants follow the vanilla Enchanted Golden Apple in the
            // requested order: Oak -> Golden Oak -> Soul Oak.
            event.getEntries().putAfter(
                    Items.ENCHANTED_GOLDEN_APPLE.getDefaultInstance(),
                    ModItems.OAK.get().getDefaultInstance(),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
            );
            event.getEntries().putAfter(
                    ModItems.OAK.get().getDefaultInstance(),
                    ModItems.GOLDEN_OAK.get().getDefaultInstance(),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
            );
            event.getEntries().putAfter(
                    ModItems.GOLDEN_OAK.get().getDefaultInstance(),
                    ModItems.SOUL_OAK.get().getDefaultInstance(),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
            );
            return;
        }

        if (event.getTabKey() != CreativeModeTabs.INGREDIENTS) {
            return;
        }

        // Keep the vanilla Ingredients ordering and continue directly after Gold Nugget:
        // Iron Nugget -> Gold Nugget -> Emerald Nugget -> Diamond Nugget.
        event.getEntries().putAfter(
                Items.GOLD_NUGGET.getDefaultInstance(),
                ModItems.EMERALD_NUGGET.get().getDefaultInstance(),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
        );
        event.getEntries().putAfter(
                ModItems.EMERALD_NUGGET.get().getDefaultInstance(),
                ModItems.DIAMOND_NUGGET.get().getDefaultInstance(),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
        );
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            net.devatnoter.normalnpcplayer.network.ModNetwork.register();
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
            event.enqueueWork(() -> {
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenus.BABY_INVENTORY.get(),
                        net.devatnoter.normalnpcplayer.client.screen.BabyInventoryScreen::new
                );

                // Vanilla flower rendering path: cutout instead of the default solid layer.
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                        ModBlocks.ROSE.get(),
                        net.minecraft.client.renderer.RenderType.cutout()
                );
            });
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
        public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(
                    ModParticles.TOTEM_OF_BABY_COMBAT.get(),
                    net.devatnoter.normalnpcplayer.client.particle.BabyCombatTotemParticle.Provider::new
            );
        }

        @SubscribeEvent
        public static void addPlayerRenderLayers(EntityRenderersEvent.AddLayers event) {
            addBabyInventoryLayer(event, "default");
            addBabyInventoryLayer(event, "slim");
        }

        private static void addBabyInventoryLayer(
                EntityRenderersEvent.AddLayers event,
                String skin
        ) {
            net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer =
                    event.getSkin(skin);

            if (playerRenderer != null) {
                playerRenderer.addLayer(
                        new net.devatnoter.normalnpcplayer.client.renderer.BabyHeadInventoryRenderLayer(
                                playerRenderer
                        )
                );
            }
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

            event.registerEntityRenderer(
                    ModEntities.GROWTH_CHILD_PLAYER_MOB.get(),
                    context -> new AdultPlayerMobRenderer(context)
            );

            event.registerEntityRenderer(
                    ModEntities.GROWTH_CHILD_HARDCORE_PLAYER_MOB.get(),
                    context -> new AdultPlayerMobRenderer(context)
            );


            // Unknown Player shares the Adult renderer/model pipeline.
            // The entity is a subclass, so the same renderer is safe here.
            event.registerEntityRenderer(
                    ModEntities.UNKNOWN_PLAYER.get(),
                    context -> new AdultPlayerMobRenderer(context)
            );
        }
    }
}
