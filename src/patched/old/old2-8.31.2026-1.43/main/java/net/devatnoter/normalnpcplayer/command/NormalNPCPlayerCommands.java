package net.devatnoter.normalnpcplayer.command;

import com.mojang.brigadier.CommandDispatcher;
import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class NormalNPCPlayerCommands {

    private NormalNPCPlayerCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                event.getDispatcher();

        dispatcher.register(
                Commands.literal("normalnpcplayer")
                        .then(Commands.literal("get_own_baby")
                                .executes(context -> {
                                    ServerPlayer player =
                                            context.getSource().getPlayerOrException();

                                    if (!player.getAbilities().instabuild) {
                                        context.getSource().sendFailure(
                                                Component.literal(
                                                        "This test command is only available in Creative."
                                                )
                                        );
                                        return 0;
                                    }

                                    BabyNPCPlayerEntity baby =
                                            ModEntities.BABY_NPC_PLAYER.get()
                                                    .create(player.serverLevel());

                                    if (baby == null) {
                                        context.getSource().sendFailure(
                                                Component.literal(
                                                        "Failed to create Baby NPC Player."
                                                )
                                        );
                                        return 0;
                                    }

                                    baby.setOwnerUUID(player.getUUID());
                                    baby.setOwnerName(
                                            player.getGameProfile().getName()
                                    );
                                    baby.initializeRandomAppearance();

                                    ItemStack stack =
                                            BabyNPCPlayerItem.createFromEntity(baby);

                                    baby.remove(
                                            net.minecraft.world.entity.Entity.RemovalReason.DISCARDED
                                    );

                                    if (!player.getInventory().add(stack)) {
                                        player.drop(stack, false);
                                    }

                                    player.getInventory().setChanged();

                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "Received your own Baby Item for testing."
                                            ),
                                            false
                                    );

                                    return 1;
                                }))
        );
    }
}
