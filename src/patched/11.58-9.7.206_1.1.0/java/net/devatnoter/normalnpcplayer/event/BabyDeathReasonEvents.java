package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.common.Mod;

/** Explains a Baby's actual death cause to its owner. */
@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BabyDeathReasonEvents {
    private BabyDeathReasonEvents() {}

    public static void notifyOwner(net.minecraft.server.level.ServerPlayer owner,
                                   BabyNPCPlayerEntity baby,
                                   DamageSource source) {
        if (owner == null || source == null || baby == null) return;

        Component reason = reason(source, baby);
        Component message = Component.translatable(
                "message.normalnpcplayer.baby_death_reason",
                baby.getDisplayName(),
                reason
        );
        owner.sendSystemMessage(message);
    }

    private static Component reason(DamageSource source, BabyNPCPlayerEntity baby) {
        // Environmental / world damage (Minecraft 1.20.1 DamageTypes).
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.void");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.LAVA))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.lava");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.DROWN))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.drowning");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.cactus");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.SWEET_BERRY_BUSH))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.sweet_berry_bush");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.STALAGMITE))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.stalagmite");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FALL))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.fall");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.wall");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.in_wall");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.STARVE))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.starvation");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FREEZE))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.freeze");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.fire");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.hot_floor");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.lightning");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.magic");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.indirect_magic");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.WITHER))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.wither");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.DRAGON_BREATH))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.dragon_breath");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.DRY_OUT))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.dry_out");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.cramming");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.STING))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.sting");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.falling_block");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_ANVIL))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.anvil");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_STALACTITE))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.stalactite");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.THORNS))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.thorns");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.SONIC_BOOM))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.sonic_boom");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.explosion");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.player_explosion");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FIREWORKS))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.fireworks");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.BAD_RESPAWN_POINT))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.bad_respawn_point");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.OUTSIDE_BORDER))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.outside_border");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL))
            return Component.translatable("message.normalnpcplayer.baby_death_reason.generic_kill");

        // Entity / projectile damage. The causing entity is preferred because
        // it is the actual attacker/shooter in Minecraft 1.20.1 DamageSource.
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();

        if (source.is(net.minecraft.world.damagesource.DamageTypes.ARROW))
            return attacker != null
                    ? Component.translatable("message.normalnpcplayer.baby_death_reason.arrow", attacker.getDisplayName())
                    : Component.translatable("message.normalnpcplayer.baby_death_reason.arrow_unknown");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.TRIDENT))
            return attacker != null
                    ? Component.translatable("message.normalnpcplayer.baby_death_reason.trident", attacker.getDisplayName())
                    : Component.translatable("message.normalnpcplayer.baby_death_reason.trident_unknown");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.MOB_PROJECTILE))
            return attacker != null
                    ? Component.translatable("message.normalnpcplayer.baby_death_reason.projectile", attacker.getDisplayName())
                    : Component.translatable("message.normalnpcplayer.baby_death_reason.projectile_unknown");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FIREBALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.UNATTRIBUTED_FIREBALL))
            return attacker != null
                    ? Component.translatable("message.normalnpcplayer.baby_death_reason.fireball", attacker.getDisplayName())
                    : Component.translatable("message.normalnpcplayer.baby_death_reason.fireball_unknown");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.WITHER_SKULL))
            return attacker != null
                    ? Component.translatable("message.normalnpcplayer.baby_death_reason.wither_skull", attacker.getDisplayName())
                    : Component.translatable("message.normalnpcplayer.baby_death_reason.wither_skull_unknown");
        if (source.is(net.minecraft.world.damagesource.DamageTypes.THROWN))
            return attacker != null
                    ? Component.translatable("message.normalnpcplayer.baby_death_reason.thrown", attacker.getDisplayName())
                    : Component.translatable("message.normalnpcplayer.baby_death_reason.thrown_unknown");

        if (source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)
                || source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                || source.is(net.minecraft.world.damagesource.DamageTypes.STING)) {
            if (attacker != null && attacker != baby)
                return Component.translatable("message.normalnpcplayer.baby_death_reason.attacked", attacker.getDisplayName());
        }

        if (attacker != null && attacker != baby && direct != null && direct != attacker)
            return Component.translatable("message.normalnpcplayer.baby_death_reason.attacked", attacker.getDisplayName());
        if (attacker != null && attacker != baby)
            return Component.translatable("message.normalnpcplayer.baby_death_reason.attacked", attacker.getDisplayName());

        // Includes generic/unknown damage and future/custom damage types.
        return Component.translatable("message.normalnpcplayer.baby_death_reason.generic");
    }
}
