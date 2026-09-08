package net.devatnoter.normalnpcplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid="normalnpcplayer", value=net.minecraftforge.api.distmarker.Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GrowthChildKeybinds {
    public static final KeyMapping GUARD_MODIFIER = new KeyMapping("key.normalnpcplayer.guard_modifier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.normalnpcplayer");
    private GrowthChildKeybinds() {}
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(GUARD_MODIFIER);}
}
