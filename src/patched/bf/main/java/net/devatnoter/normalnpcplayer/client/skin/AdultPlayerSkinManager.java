package net.devatnoter.normalnpcplayer.client.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side Player Profile texture pipeline.
 *
 * The important part is that this class NEVER performs a blocking profile
 * lookup. Minecraft's SkinManager handles texture loading asynchronously.
 *
 * This follows the architecture used by PlayerMobs: resolve the name to a
 * complete GameProfile first, then ask SkinManager to register the profile's
 * SKIN/CAPE textures and render the cached ResourceLocations when available.
 */
public final class AdultPlayerSkinManager {

    private static final Map<UUID, ProfileTextures> CACHE =
            new ConcurrentHashMap<>();

    private static final Map<UUID, Boolean> REQUESTED =
            new ConcurrentHashMap<>();

    private AdultPlayerSkinManager() {
    }

    public static ProfileTextures getTextures(GameProfile profile) {
        if (profile == null) {
            return ProfileTextures.EMPTY;
        }

        // A name-only profile has not been resolved yet. The entity's
        // getGameProfile() starts SkullBlockEntity.updateGameprofile().
        if (!profile.isComplete() || profile.getId() == null) {
            return ProfileTextures.forProfile(profile);
        }

        UUID uuid = profile.getId();

        ProfileTextures cached = CACHE.get(uuid);
        // A CAPE/ELYTRA callback can arrive before SKIN. Never return a
        // partially populated cache entry to the renderer.
        if (cached != null && cached.skin() != null) {
            return cached;
        }

        requestTextures(profile);
        return ProfileTextures.forProfile(profile);
    }

    private static void requestTextures(GameProfile profile) {
        UUID uuid = profile.getId();

        if (uuid == null || REQUESTED.putIfAbsent(uuid, Boolean.TRUE) != null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        try {
            minecraft.getSkinManager().registerSkins(
                    profile,
                    (type, location, texture) -> onTextureLoaded(
                            uuid,
                            type,
                            location,
                            texture
                    ),
                    false
            );
        } catch (Exception ignored) {
            // If the client texture pipeline fails, keep the default skin.
            REQUESTED.remove(uuid);
        }
    }

    private static void onTextureLoaded(
            UUID uuid,
            MinecraftProfileTexture.Type type,
            ResourceLocation location,
            MinecraftProfileTexture texture
    ) {
        CACHE.compute(
                uuid,
                (ignored, old) -> {
                    ProfileTextures current = old == null
                            ? ProfileTextures.EMPTY
                            : old;

                    ResourceLocation skin = current.skin();
                    ResourceLocation cape = current.cape();
                    ResourceLocation elytra = current.elytra();
                    boolean slim = current.slim();

                    if (type == MinecraftProfileTexture.Type.SKIN && location != null) {
                        skin = location;

                        String model = texture == null
                                ? null
                                : texture.getMetadata("model");

                        slim = "slim".equalsIgnoreCase(model);
                    } else if (type == MinecraftProfileTexture.Type.CAPE && location != null) {
                        cape = location;
                    } else if (type == MinecraftProfileTexture.Type.ELYTRA && location != null) {
                        elytra = location;
                    }

                    return new ProfileTextures(
                            skin,
                            cape,
                            elytra,
                            slim
                    );
                }
        );
    }

    public static boolean isSlim(GameProfile profile, boolean fallback) {
        if (profile == null || profile.getId() == null) {
            return fallback;
        }

        ProfileTextures textures = CACHE.get(profile.getId());
        return textures == null ? fallback : textures.slim();
    }

    public static void clear(UUID profileId) {
        if (profileId != null) {
            CACHE.remove(profileId);
            REQUESTED.remove(profileId);
        }
    }

    public record ProfileTextures(
            ResourceLocation skin,
            ResourceLocation cape,
            ResourceLocation elytra,
            boolean slim
    ) {
        public static final ProfileTextures EMPTY = new ProfileTextures(
                null,
                null,
                null,
                false
        );

        private static ProfileTextures forProfile(GameProfile profile) {
            if (profile == null || profile.getId() == null) {
                return EMPTY;
            }

            return new ProfileTextures(
                    DefaultPlayerSkin.getDefaultSkin(profile.getId()),
                    null,
                    null,
                    false
            );
        }
    }
}
