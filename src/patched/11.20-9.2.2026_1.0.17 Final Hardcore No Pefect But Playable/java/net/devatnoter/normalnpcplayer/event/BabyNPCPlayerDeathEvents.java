package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class BabyNPCPlayerDeathEvents {

    private BabyNPCPlayerDeathEvents() {
    }

    /**
     * A Baby carried as a Life Bond item must never become a dropped item
     * when its biological owner dies.
     *
     * Instead the saved Baby is restored at the death position as a real
     * entity and immediately becomes orphaned, exactly like an entity Baby
     * whose biological parent died while it was in the world.
     *
     * Foster Babies are deliberately excluded: Foster relationships are not
     * Life Bond relationships.
     */
    @SubscribeEvent
    public static void onPlayerDeathDrops(
            LivingDropsEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID ownerUUID = player.getUUID();
        ServerLevel level = player.serverLevel();

        for (var iterator = event.getDrops().iterator(); iterator.hasNext();) {
            Entity drop = iterator.next();

            if (!(drop instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) {
                continue;
            }

            ItemStack stack = itemEntity.getItem();

            if (!BabyNPCPlayerItem.isBabyStack(stack)) {
                continue;
            }

            var data = BabyNPCPlayerItem.copyEntityData(stack);

            // A biological Baby that already reached 0 HP while carried is
            // genuinely dead. Do not resurrect that zero-HP snapshot as an
            // orphan during the owner's Life Bond death conversion. This also
            // closes the placement/death timing loophole for carried Babies.
            if (data.contains("Health") && data.getFloat("Health") <= 0.0F) {
                if (data.hasUUID("OwnerUUID")
                        && ownerUUID.equals(data.getUUID("OwnerUUID"))
                        && !data.getBoolean("Foster")
                        && !data.getBoolean("Orphaned")) {
                    iterator.remove();
                    BabyNPCPlayerItem.forgetRuntimeState(stack);
                    continue;
                }
            }

            // Only the biological owner's Life Bond Baby is converted.
            if (!data.hasUUID("OwnerUUID")
                    || !ownerUUID.equals(data.getUUID("OwnerUUID"))
                    || (data.contains("Foster") && data.getBoolean("Foster"))) {
                continue;
            }

            Entity restored = EntityType.create(data.copy(), level).orElse(null);

            if (!(restored instanceof BabyNPCPlayerEntity baby)) {
                // If reconstruction fails, still prevent the Life Bond Baby
                // from becoming an item drop.
                iterator.remove();
                continue;
            }

            baby.applyPersistedAppearance(
                        BabyNPCPlayerItem.getSpecialVariantId(stack),
                        BabyNPCPlayerItem.getTextureId(stack),
                        BabyNPCPlayerItem.getTextureIndex(stack)
                );
            baby.moveTo(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot()
            );
            baby.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            baby.setNoGravity(false);
            baby.noPhysics = false;
            baby.fallDistance = 0.0F;
            baby.clearInitialRideState();

            // Parent death -> orphan. Keep the original biological lineage.
            baby.setOrphaned(true);
            baby.setFoster(false);

            level.addFreshEntity(baby);
            BabyNPCPlayerItem.forgetRuntimeState(stack);
            BabyNPCPlayerEntity.playOrphanParticles(level, baby);

            // Remove the Baby ItemEntity so it cannot also fall as an item.
            iterator.remove();
        }
    }
}
