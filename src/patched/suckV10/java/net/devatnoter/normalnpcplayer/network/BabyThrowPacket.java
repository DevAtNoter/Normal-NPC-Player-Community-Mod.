package net.devatnoter.normalnpcplayer.network;

import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.devatnoter.normalnpcplayer.item.BabyNPCPlayerItem;
import net.devatnoter.normalnpcplayer.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-authoritative Baby throw action.
 *
 * Client only supplies the charge amount. The server clamps it and derives
 * sprint momentum from the real Player velocity, so the client cannot directly
 * choose an arbitrary throw speed.
 */
public final class BabyThrowPacket {
    private static final int START = 0;
    private static final int THROW = 1;
    private static final int CANCEL = 2;

    private final int action;
    private final float charge;

    public BabyThrowPacket(int action, float charge) {
        this.action = action;
        this.charge = charge;
    }

    public static BabyThrowPacket start() {
        return new BabyThrowPacket(START, 0.0F);
    }

    public static BabyThrowPacket throwBaby(float charge) {
        return new BabyThrowPacket(THROW, charge);
    }

    public static BabyThrowPacket cancel() {
        return new BabyThrowPacket(CANCEL, 0.0F);
    }

    public static void encode(BabyThrowPacket packet, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeByte(packet.action);
        buf.writeFloat(packet.charge);
    }

    public static BabyThrowPacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new BabyThrowPacket(buf.readByte(), buf.readFloat());
    }

    public static void handle(BabyThrowPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || packet.action != THROW) {
                return;
            }

            throwBaby(player, packet.charge);
        });

        context.setPacketHandled(true);
    }

    private static void throwBaby(ServerPlayer player, float rawCharge) {
        ItemStack stack = findCarriedBaby(player);
        if (!BabyNPCPlayerItem.isBabyStack(stack)) {
            return;
        }

        // The throw interaction is only valid when the opposite hand is empty.
        // This mirrors the client-side rule and keeps the server authoritative.
        if (stack == player.getItemInHand(InteractionHand.MAIN_HAND)
                && !player.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
            return;
        }
        if (stack == player.getItemInHand(InteractionHand.OFF_HAND)
                && !player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return;
        }

        float charge = Math.max(0.0F, Math.min(1.0F, rawCharge));

        CompoundTag data = BabyNPCPlayerItem.copyEntityData(stack);
        if (data.isEmpty()) {
            return;
        }

        // The carried Baby remains subject to the same ownership/life-bond
        // rules as normal placement. This is an item -> entity transition,
        // not a second Baby.
        if (!data.getBoolean("Orphaned")) {
            boolean allowed = data.hasUUID("OwnerUUID")
                    && player.getUUID().equals(data.getUUID("OwnerUUID"));

            // Player Babies have two equal parents. Either parent may throw
            // the carried Baby; the legacy OwnerUUID remains only for
            // backward compatibility with older Baby data.
            if ("PLAYER".equalsIgnoreCase(data.getString("BabyType"))) {
                allowed = allowed
                        || (data.hasUUID("OwnerAUUID")
                            && player.getUUID().equals(data.getUUID("OwnerAUUID")))
                        || (data.hasUUID("OwnerBUUID")
                            && player.getUUID().equals(data.getUUID("OwnerBUUID")))
                        || (data.hasUUID("BiologicalParentAUUID")
                            && player.getUUID().equals(data.getUUID("BiologicalParentAUUID")))
                        || (data.hasUUID("BiologicalParentBUUID")
                            && player.getUUID().equals(data.getUUID("BiologicalParentBUUID")));
            }

            if (!allowed) {
                return;
            }
        }

        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        Entity restored = EntityType.create(data, serverLevel).orElse(null);
        if (!(restored instanceof BabyNPCPlayerEntity baby)) {
            return;
        }

        baby.applyPersistedAppearance(
                BabyNPCPlayerItem.getSpecialVariantId(stack),
                BabyNPCPlayerItem.getTextureId(stack),
                BabyNPCPlayerItem.getTextureIndex(stack)
        );

        /*
         * Throw direction is the exact direction the player is looking,
         * just like an Egg/Snowball. Do not replace it with horizontal-only
         * facing and do not add the player's movement vector: when sprinting
         * and jumping, the Baby must never get pushed backward by the
         * carrier's momentum.
         */
        Vec3 look = player.getLookAngle().normalize();

        // Spawn just ahead of the player's body so the Baby is not created
        // inside the carrier's collision box.
        Vec3 spawn = player.getEyePosition(1.0F)
                .add(look.scale(0.65D))
                .add(0.0D, -0.45D, 0.0D);

        baby.moveTo(
                spawn.x,
                spawn.y,
                spawn.z,
                player.getYRot(),
                0.0F
        );

        baby.setYRot(player.getYRot());
        baby.setXRot(player.getXRot());
        baby.setYHeadRot(player.getYRot());
        baby.setYBodyRot(player.getYRot());

        // Real sprint momentum, measured on the server.
        double horizontalSpeed = new Vec3(
                player.getDeltaMovement().x,
                0.0D,
                player.getDeltaMovement().z
        ).length();

        double sprintFactor = Math.max(
                0.0D,
                Math.min(1.0D, horizontalSpeed / 0.30D)
        );

        // Charge controls the throw. Sprint momentum increases both range and
        // height, while preserving a useful throw even from a standing start.
        double mass = 1.0D;
        double chargePower = 0.60D + (1.80D * charge);
        double sprintMultiplier = 1.0D + (0.75D * sprintFactor);

        double throwSpeed =
                (chargePower / mass) * sprintMultiplier;

        /*
         * Full 3D look vector, matching projectile-style throwing. Sprint only
         * increases the magnitude; it never changes the direction.
         */
        Vec3 throwVelocity = look.scale(throwSpeed);

        baby.setDeltaMovement(throwVelocity);
        baby.setNoGravity(false);
        baby.noPhysics = false;
        baby.fallDistance = 0.0F;
        baby.beginThrownPhysics(
                player.getUUID(),
                throwVelocity,
                charge,
                sprintFactor
        );

        serverLevel.addFreshEntity(baby);

        // Consume the ItemStack only after the replacement Entity is ready.
        stack.shrink(1);
        if (stack.isEmpty()) {
            clearBabyFromHand(player, stack);
        }

        // The sound event itself contains multiple scream variants, so
        // Minecraft selects a random entry from sounds.json.
        baby.playSound(
                ModSounds.BABY_SCREAM.get(),
                1.0F,
                0.92F + baby.getRandom().nextFloat() * 0.16F
        );
    }

    private static ItemStack findCarriedBaby(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);

        if (BabyNPCPlayerItem.isBabyStack(main) && off.isEmpty()) {
            return main;
        }
        if (BabyNPCPlayerItem.isBabyStack(off) && main.isEmpty()) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static void clearBabyFromHand(ServerPlayer player, ItemStack stack) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND) == stack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } else if (player.getItemInHand(InteractionHand.OFF_HAND) == stack) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }
}
