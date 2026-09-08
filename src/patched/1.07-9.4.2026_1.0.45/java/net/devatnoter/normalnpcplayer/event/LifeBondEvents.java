package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class LifeBondEvents {

    private static final ResourceKey<DamageType> BABY_LIFE_BOND =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(
                            NormalNPCPlayer.MOD_ID,
                            "baby_life_bond"
                    )
            );

    private LifeBondEvents() {
    }

    /**
     * Player -> Baby
     *
     * When the owner dies, kill the bonded baby as well.
     *
     * We scan loaded entities on every server level so the baby does not
     * need to be close to the player and it may be in another dimension.
     *
     * Unloaded chunks do not contain active entity instances, so nothing
     * is forced to load merely because of Life Bond.
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID ownerUUID = player.getUUID();

        for (var level : player.server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (!(entity instanceof BabyNPCPlayerEntity baby)) {
                    continue;
                }

                if (!ownerUUID.equals(baby.getOwnerUUID()) || !baby.isAlive() || !baby.isLifeBondEnabled()) {
                    continue;
                }

                /*
                 * Foster parents do not participate in Life Bond damage, but
                 * their death still ends the current foster-care relationship.
                 * The Baby becomes orphaned and can be adopted by another
                 * player, exactly like a Baby whose biological parent died.
                 */
                if (baby.isFoster()) {
                    baby.setOrphaned(true);
                    baby.setFoster(false);
                    BabyNPCPlayerEntity.playOrphanParticles((net.minecraft.server.level.ServerLevel) level, baby);
                    continue;
                }

                /*
                 * A biological parent's death creates an orphan state rather
                 * than destroying the child.
                 */
                baby.setOrphaned(true);
                BabyNPCPlayerEntity.playOrphanParticles((net.minecraft.server.level.ServerLevel) level, baby);
            }
        }
    }

    /**
     * Baby -> Player
     *
     * Called by BabyNPCPlayerEntity.die().
     *
     * Kept here so the Life Bond implementation is centralized instead
     * of putting player-death event logic inside the entity itself.
     */
    public static void killOwner(ServerPlayer owner) {
        killOwner(owner, null);
    }

    private static final ResourceKey<DamageType> BABY_LIFE_BOND_STARVATION =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(
                            NormalNPCPlayer.MOD_ID,
                            "baby_life_bond_starvation"
                    )
            );

    /**
     * Kill the biological owner with the normal Baby Life Bond cause.
     */
    public static void killOwner(
            ServerPlayer owner,
            BabyNPCPlayerEntity baby
    ) {
        killOwner(owner, baby, false);
    }

    /**
     * Kill the biological owner with the Life Bond cause that matches why the
     * Baby died. Starvation gets its own DamageType so the vanilla death
     * message explicitly reports that the Baby starved to death.
     */
    public static void killOwner(
            ServerPlayer owner,
            BabyNPCPlayerEntity baby,
            boolean starvation
    ) {
        if (owner == null || !owner.isAlive()) {
            return;
        }

        ResourceKey<DamageType> key = starvation
                ? BABY_LIFE_BOND_STARVATION
                : BABY_LIFE_BOND;

        var damageTypeHolder = owner.serverLevel()
                .registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key);

        DamageSource source = new LifeBondDamageSource(
                damageTypeHolder,
                baby,
                starvation
        );

        owner.hurt(source, Float.MAX_VALUE);
    }

    /**
     * Explicit Life Bond death source.
     *
     * The custom localized message is provided directly here instead of
     * relying only on the damage_type JSON translation lookup. This keeps
     * the Life Bond wording deterministic in the death screen.
     */
    private static final class LifeBondDamageSource extends DamageSource {
        private final boolean starvation;

        private LifeBondDamageSource(
                net.minecraft.core.Holder<DamageType> type,
                Entity baby,
                boolean starvation
        ) {
            super(type, baby);
            this.starvation = starvation;
        }

        @Override
        public Component getLocalizedDeathMessage(LivingEntity killedEntity) {
            if (starvation) {
                return Component.translatable(
                        "death.attack.baby_life_bond_starvation",
                        killedEntity.getDisplayName()
                );
            }

            return Component.translatable(
                    "death.attack.baby_life_bond",
                    killedEntity.getDisplayName()
            );
        }
    }

    /**
     * Enforces the biological Life Bond for a Baby that is currently carried
     * as an ItemStack. The tooltip is only a client display; the server must
     * make the actual death decision from the authoritative carried state.
     *
     * Returns true when the Baby state is at or below zero HP and this player
     * is its biological Life Bond owner. Orphaned/Foster Babies are excluded.
     */
    public static boolean enforceCarriedBabyHealth(
            ServerPlayer holder,
            net.minecraft.nbt.CompoundTag babyData
    ) {
        return enforceCarriedBabyHealth(holder, babyData, false);
    }

    public static boolean enforceCarriedBabyHealth(
            ServerPlayer holder,
            net.minecraft.nbt.CompoundTag babyData,
            boolean starvation
    ) {
        if (holder == null || babyData == null) {
            return false;
        }

        float health = babyData.contains("Health")
                ? babyData.getFloat("Health")
                : 20.0F;

        if (health > 0.0F
                || babyData.getBoolean("Orphaned")
                || babyData.getBoolean("Foster")
                || !babyData.hasUUID("OwnerUUID")
                || !holder.getUUID().equals(babyData.getUUID("OwnerUUID"))) {
            return false;
        }

        killOwner(holder, null, starvation);
        return true;
    }
}
