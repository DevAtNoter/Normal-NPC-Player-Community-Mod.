package net.devatnoter.normalnpcplayer.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerFamilyProvider implements ICapabilitySerializable<CompoundTag> {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(
                    NormalNPCPlayer.MOD_ID,
                    "your_first_child"
            );

    private final PlayerFamilyData data = new PlayerFamilyData();

    private final LazyOptional<PlayerFamilyData> optional =
            LazyOptional.of(() -> this.data);
    public void invalidate() {
        optional.invalidate();
    }
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> cap,
            @Nullable Direction side) {

        if (cap == PlayerFamilyCapability.PLAYER_FAMILY) {
            return optional.cast();
        }

        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {

        CompoundTag tag = new CompoundTag();

        tag.putBoolean("FirstChild", data.hasFirstChild());

        if (data.getChildUUID() != null) {
            tag.putUUID("ChildUUID", data.getChildUUID());
        }

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {

        data.setFirstChild(tag.getBoolean("FirstChild"));

        if (tag.hasUUID("ChildUUID")) {
            data.setChildUUID(tag.getUUID("ChildUUID"));
        } else {
            data.clearChild();
        }
    }
}