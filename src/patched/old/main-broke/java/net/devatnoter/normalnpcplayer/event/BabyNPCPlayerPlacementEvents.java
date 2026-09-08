package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyNPCPlayerPlacementEvents {

    private BabyNPCPlayerPlacementEvents() {
    }

    /**
     * Functional blocks keep their normal right-click behavior.
     * Only the Baby item's own use is denied.
     */
    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event
    ) {
        if (!BabyNPCPlayerItem.isBabyStack(
                event.getItemStack()
        )) {
            return;
        }

        if (isFunctionalBlock(
                event.getLevel().getBlockState(event.getPos())
        )) {
            event.setUseItem(
                    net.minecraftforge.eventbus.api.Event.Result.DENY
            );
        }
    }

    private static boolean isFunctionalBlock(
            BlockState state
    ) {
        Block block = state.getBlock();

        return block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof FenceGateBlock
                || block instanceof ChestBlock
                || block instanceof EnderChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof HopperBlock
                || block instanceof FurnaceBlock
                || block instanceof BrewingStandBlock
                || block instanceof DispenserBlock
                || block instanceof DropperBlock
                || block instanceof CraftingTableBlock
                || block instanceof StonecutterBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof SmithingTableBlock
                || block instanceof GrindstoneBlock
                || block instanceof AnvilBlock
                || block instanceof EnchantmentTableBlock
                || block instanceof LecternBlock
                || block instanceof BedBlock
                || block instanceof BellBlock
                || block instanceof NoteBlock
                || block instanceof JukeboxBlock
                || block instanceof BaseEntityBlock
                || block instanceof SignBlock;
    }
}
