package net.devatnoter.normalnpcplayer.event;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.devatnoter.normalnpcplayer.equipment.BabyCombatController;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.registry.ModItems;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;

/** Server-side hooks for Totem of Baby Combat. */
@Mod.EventBusSubscriber(modid = NormalNPCPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BabyCombatEvents {
    private BabyCombatEvents() {}

    /**
     * Combat Mode gets one last, server-side reaction window before a lethal
     * hit is applied. If the Baby has a vanilla Totem of Undying in its own
     * inventory, move it into the offhand so vanilla's normal death-protection
     * code can consume it. If the response is too late, vanilla simply lets
     * the Baby die.
     */
    @SubscribeEvent
    public static void onBabyLethalDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof BabyNPCPlayerEntity baby)) return;
        if (baby.level().isClientSide || !baby.isAlive()) return;
        if (!BabyCombatController.isCombatActive(baby)) return;
        BabyCombatController.prepareVanillaTotemForLethalDamage(
                baby, event.getSource(), event.getAmount());
    }

    /**
     * Every kill made by a Baby in combat damages its held combat totem.
     * The cost scales with the defeated mob's actual combat stats.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        BabyNPCPlayerEntity baby = null;
        if (event.getSource().getEntity() instanceof BabyNPCPlayerEntity directBaby) {
            baby = directBaby;
        } else if (event.getSource().getEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof BabyNPCPlayerEntity projectileBaby) {
            baby = projectileBaby;
        }
        if (baby == null || !baby.isAlive() || !BabyCombatController.isCombatActive(baby)) return;
        if (event.getEntity() == baby || !(event.getEntity() instanceof Mob)) return;
        BabyCombatController.damageTotemForKill(baby, event.getEntity());
    }

    /**
     * Mob drops which already contain a vanilla Totem of Undying are eligible
     * for the Baby Combat Totem roll. Outcomes are intentionally independent:
     * vanilla only, Baby only, or both.
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity().getType() == net.minecraft.world.entity.EntityType.PLAYER) return;

        ItemEntity vanillaTotem = null;
        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().is(Items.TOTEM_OF_UNDYING)) {
                vanillaTotem = drop;
                break;
            }
        }
        if (vanillaTotem == null) return;

        float roll = event.getEntity().getRandom().nextFloat();
        if (roll < 0.35F) {
            // Baby only.
            vanillaTotem.setItem(new ItemStack(ModItems.TOTEM_OF_BABY_COMBAT.get()));
        } else if (roll < 0.70F) {
            // Vanilla only: leave the original drop untouched.
        } else {
            // Both.
            event.getDrops().add(new ItemEntity(
                    event.getEntity().level(),
                    vanillaTotem.getX(), vanillaTotem.getY(), vanillaTotem.getZ(),
                    new ItemStack(ModItems.TOTEM_OF_BABY_COMBAT.get())
            ));
        }
    }
}
