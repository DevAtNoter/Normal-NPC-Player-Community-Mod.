package net.devatnoter.normalnpcplayer.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class BabyItemSyncPacket {
    private final UUID babyUuid;
    private final float health;
    private final float maxHealth;
    private final float hunger;
    private final float saturation;
    private final float exhaustion;
    private final int air;
    private final float absorption;
    private final int totalXp;
    private final int experienceLevel;
    private final ListTag effects;

    public BabyItemSyncPacket(UUID babyUuid, float health, float maxHealth,
                              float hunger, float saturation, float exhaustion,
                              int air, float absorption, int totalXp, int experienceLevel, ListTag effects) {
        this.babyUuid = babyUuid;
        this.health = health;
        this.maxHealth = maxHealth;
        this.hunger = hunger;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.air = air;
        this.absorption = absorption;
        this.totalXp = Math.max(0, totalXp);
        this.experienceLevel = Math.max(0, experienceLevel);
        this.effects = effects == null ? new ListTag() : (ListTag) effects.copy();
    }

    public static void encode(BabyItemSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.babyUuid);
        buf.writeFloat(packet.health);
        buf.writeFloat(packet.maxHealth);
        buf.writeFloat(packet.hunger);
        buf.writeFloat(packet.saturation);
        buf.writeFloat(packet.exhaustion);
        buf.writeInt(packet.air);
        buf.writeFloat(packet.absorption);
        buf.writeInt(packet.totalXp);
        buf.writeInt(packet.experienceLevel);
        net.minecraft.nbt.CompoundTag wrapper = new net.minecraft.nbt.CompoundTag();
        wrapper.put("Effects", packet.effects.copy());
        buf.writeNbt(wrapper);
    }

    public static BabyItemSyncPacket decode(FriendlyByteBuf buf) {
        return new BabyItemSyncPacket(
                buf.readUUID(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readInt(),
                buf.readFloat(),
                buf.readInt(),
                buf.readInt(),
                readEffects(buf)
        );
    }

    private static ListTag readEffects(FriendlyByteBuf buf) {
        net.minecraft.nbt.CompoundTag wrapper = buf.readNbt();
        if (wrapper == null || !wrapper.contains("Effects", 9)) {
            return new ListTag();
        }
        return wrapper.getList("Effects", 10).copy();
    }

    public static void handle(BabyItemSyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> BabyItemClientState.update(
                packet.babyUuid,
                packet.health,
                packet.maxHealth,
                packet.hunger,
                packet.saturation,
                packet.exhaustion,
                packet.air,
                packet.absorption,
                packet.totalXp,
                packet.experienceLevel,
                packet.effects
        ));
        context.setPacketHandled(true);
    }
}
