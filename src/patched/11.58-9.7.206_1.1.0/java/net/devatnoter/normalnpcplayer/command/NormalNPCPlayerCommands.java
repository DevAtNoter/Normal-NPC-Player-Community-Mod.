package net.devatnoter.normalnpcplayer.command;

import com.mojang.brigadier.CommandDispatcher;
import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.capability.PlayerFamilyCapability;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class NormalNPCPlayerCommands {

    private NormalNPCPlayerCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(buildRoot("normalnpcplayer"));
        dispatcher.register(buildRoot("nnp"));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildRoot(
            String root
    ) {
        return Commands.literal(root)
                .then(Commands.literal("ride")
                        .executes(context -> rideOwnBaby(
                                context.getSource().getPlayerOrException()
                        )))
                .then(Commands.literal("get_own_baby")
                        .executes(context -> getOwnBaby(
                                context.getSource().getPlayerOrException()
                        )))
                .then(Commands.literal("baby_gamemode")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .executes(context -> setBabyGameMode(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "mode")
                                ))))
                .then(Commands.literal("baby_birth")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("parent", EntityArgument.player())
                                .executes(context -> directBabyBirth(
                                        EntityArgument.getPlayer(context, "parent")
                                ))
                                .then(Commands.argument("second_parent", EntityArgument.player())
                                        .executes(context -> directBabyBirth(
                                                EntityArgument.getPlayer(context, "parent"),
                                                EntityArgument.getPlayer(context, "second_parent")
                                        )))))
                .then(Commands.literal("baby_grow")
                        .then(Commands.literal("accelerate")
                                .executes(context -> accelerateBabyGrowth(
                                        context.getSource().getPlayerOrException(),
                                        1
                                ))
                                .then(Commands.argument("days", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                        .executes(context -> accelerateBabyGrowth(
                                                context.getSource().getPlayerOrException(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "days")
                                        ))))
                        .then(Commands.literal("instant")
                                .executes(context -> growBabyInstant(
                                        context.getSource().getPlayerOrException()
                                ))));
    }

    private static int rideOwnBaby(ServerPlayer player) {
        BabyNPCPlayerEntity baby = findOwnBaby(player);

        if (baby == null) {
            player.displayClientMessage(
                    Component.literal("No loaded Baby belonging to you was found."),
                    true
            );
            return 0;
        }

        baby.beginNeckRide(player);

        if (!player.hasPassenger(baby)) {
            player.displayClientMessage(
                    Component.literal("Failed to mount Baby as a real passenger."),
                    true
            );
            return 0;
        }

        player.displayClientMessage(
                Component.literal("Baby is now a real head passenger."),
                true
        );
        return 1;
    }

    private static BabyNPCPlayerEntity findOwnBaby(ServerPlayer player) {
        UUID childUUID = player.getCapability(PlayerFamilyCapability.PLAYER_FAMILY)
                .map(data -> data.getChildUUID())
                .orElse(null);

        if (childUUID != null) {
            net.minecraft.world.entity.Entity entity = player.serverLevel().getEntity(childUUID);
            if (entity instanceof BabyNPCPlayerEntity baby
                    && player.getUUID().equals(baby.getOwnerUUID())
                    && !baby.isRemoved()) {
                return baby;
            }
        }

        return player.serverLevel()
                .getEntitiesOfClass(
                        BabyNPCPlayerEntity.class,
                        player.getBoundingBox().inflate(32.0D),
                        baby -> !baby.isRemoved()
                                && player.getUUID().equals(baby.getOwnerUUID())
                )
                .stream()
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    private static int directBabyBirth(ServerPlayer parent) {
        BabyNPCPlayerEntity baby =
                net.devatnoter.normalnpcplayer.breeding.PlayerBabyBirthManager.birth(parent);

        if (baby == null) {
            parent.displayClientMessage(
                    Component.literal("Failed to create single-parent Player Baby."), true);
            return 0;
        }

        parent.displayClientMessage(
                Component.literal("Player Baby was born directly with one biological parent."), true);
        return 1;
    }

    private static int directBabyBirth(ServerPlayer parentA, ServerPlayer parentB) {
        if (parentA == parentB) {
            parentA.displayClientMessage(
                    Component.literal("Two different players are required."), true);
            return 0;
        }

        if (parentA.serverLevel() != parentB.serverLevel()) {
            parentA.displayClientMessage(
                    Component.literal("Both parents must be in the same dimension."), true);
            return 0;
        }

        BabyNPCPlayerEntity baby =
                net.devatnoter.normalnpcplayer.breeding.PlayerBabyBirthManager.birth(parentA, parentB);

        if (baby == null) {
            parentA.displayClientMessage(
                    Component.literal("Failed to create Player Baby."), true);
            return 0;
        }

        parentA.displayClientMessage(
                Component.literal("Player Baby was born directly from the command."), true);
        parentB.displayClientMessage(
                Component.literal("Player Baby was born directly from the command."), true);
        return 1;
    }

    private static int accelerateBabyGrowth(ServerPlayer player, int days) {
        BabyNPCPlayerEntity baby = findOwnBaby(player);

        if (baby == null
                || baby.getBabyType() != net.devatnoter.normalnpcplayer.breeding.BabyType.PLAYER) {
            player.displayClientMessage(
                    Component.literal("No Player Baby from breeding was found nearby."),
                    true
            );
            return 0;
        }

        if (!net.devatnoter.normalnpcplayer.growth.BabyGrowthManager
                .acceleratePlayerBaby(player, days)) {
            player.displayClientMessage(
                    Component.literal("Failed to accelerate Player Baby growth."),
                    true
            );
            return 0;
        }

        player.displayClientMessage(
                Component.literal("Player Baby growth accelerated by " + days + " Minecraft day(s)."),
                true
        );
        return 1;
    }

    private static int growBabyInstant(ServerPlayer player) {
        BabyNPCPlayerEntity baby = findOwnBaby(player);

        if (baby == null
                || baby.getBabyType() != net.devatnoter.normalnpcplayer.breeding.BabyType.PLAYER) {
            player.displayClientMessage(
                    Component.literal("No Player Baby from breeding was found nearby."),
                    true
            );
            return 0;
        }

        if (!net.devatnoter.normalnpcplayer.growth.BabyGrowthManager
                .growPlayerBabyInstant(player)) {
            player.displayClientMessage(
                    Component.literal("Player Baby could not grow right now (check space around it)."),
                    true
            );
            return 0;
        }

        player.displayClientMessage(
                Component.literal("Player Baby grew up instantly."),
                true
        );
        return 1;
    }

    private static int setBabyGameMode(ServerPlayer player, String mode) {
        BabyNPCPlayerEntity baby = findOwnBaby(player);
        if (baby == null) {
            player.displayClientMessage(Component.literal("No loaded Baby belonging to you was found."), true);
            return 0;
        }
        if (!baby.setBabyGameMode(mode)) {
            player.displayClientMessage(Component.literal("Use survival, adventure, creative, or spectator."), true);
            return 0;
        }
        player.displayClientMessage(Component.literal("Baby game mode: " + baby.getBabyGameMode()), true);
        return 1;
    }

    private static int getOwnBaby(ServerPlayer player) {
        if (!player.getAbilities().instabuild) {
            player.displayClientMessage(
                    Component.literal("This test command is only available in Creative."),
                    true
            );
            return 0;
        }

        BabyNPCPlayerEntity baby =
                ModEntities.BABY_NPC_PLAYER.get().create(player.serverLevel());

        if (baby == null) {
            player.displayClientMessage(
                    Component.literal("Failed to create Baby NPC Player."),
                    true
            );
            return 0;
        }

        baby.setOwnerUUID(player.getUUID());
        baby.setOwnerName(player.getGameProfile().getName());
        baby.setProfileName(player.getGameProfile().getName());
        baby.setBiologicalParent(player);
        baby.initializeRandomAppearance();

        ItemStack stack = BabyNPCPlayerItem.createFromEntity(baby);
        baby.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        player.getInventory().setChanged();
        player.displayClientMessage(
                Component.literal("Received your own Baby Item for testing."),
                true
        );
        return 1;
    }
}
