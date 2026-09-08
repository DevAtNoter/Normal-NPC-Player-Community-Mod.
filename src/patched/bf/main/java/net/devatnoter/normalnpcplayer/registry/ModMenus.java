package net.devatnoter.normalnpcplayer.registry;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.menu.BabyInventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, NormalNPCPlayer.MOD_ID);

    public static final RegistryObject<MenuType<BabyInventoryMenu>> BABY_INVENTORY =
            MENUS.register("baby_inventory", () ->
                    IForgeMenuType.create((windowId, inventory, data) -> {
                        int entityId = data.readVarInt();
                        var entity = inventory.player.level().getEntity(entityId);
                        if (entity instanceof net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity baby) {
                            return new BabyInventoryMenu(windowId, inventory, baby);
                        }
                        return new BabyInventoryMenu(windowId, inventory, (net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity) null);
                    }));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
