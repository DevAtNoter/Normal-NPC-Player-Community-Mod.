package net.devatnoter.normalnpcplayer.client.cutscene;

import net.devatnoter.normalnpcplayer.NormalNPCPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Client-side spectator-style camera for the server-wide Parental Accomplishment ceremony.
 * The camera is a client-only invisible spectator rig, never the player's own camera.
 */
@Mod.EventBusSubscriber(
        modid = NormalNPCPlayer.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ParentalAccomplishmentCutsceneClient {
    private static final int TOTAL_TICKS = 3000;      // 150s / 2:30
    private static final int FIRST_SHOT_TICKS = 100;  // 0-5s: achiever
    private static final int CHILD_SHOT_TICKS = 100;  // 5-10s: child
    private static final int STATIC_SHOT_COUNT = 9;
    private static final int CREDIT_TICKS = 80;       // final 4s
    private static final int CREDIT_START = TOTAL_TICKS - CREDIT_TICKS;
    private static final int CREDIT_SUBTITLE_DELAY_TICKS = 20; // 1s after title
    private static final int CREDIT_SUBTITLE_FADE_TICKS = 20;
    private static final int ROLLING_CREDITS_START = 240; // 12s
    private static final int ROLLING_CREDITS_END = CREDIT_START;
    private static final int STATIC_SECTION_START = 200; // 10s
    private static final int STATIC_SECTION_END = CREDIT_START;
    private static final int ORBIT_START = 1500; // 75s
    private static final int ORBIT_END = 2000;    // 100s
    private static final int STATIC_SHOT_TICKS = 300; // ~15s per locked shot outside orbit

    private static final String ROLLING_TEXT = """
Somewhere beyond the End, a new beginning was born.

The world remembers those who dared to continue.

Not every victory is written in stone.
Some victories are carried quietly in the hearts of others.

A child once followed footsteps.
Small footsteps.
Uncertain footsteps.

There was a time when every road had to be shown.
Every danger had to be faced together.
Every distant horizon seemed impossibly far away.

But time has a quiet way of changing everything.

The child watched.
The child learned.
The child remembered.

And one day,
those footsteps became a path of their own.

The End was never truly the end.

It was only a place where the world grew quiet.

A place beyond familiar skies.
Beyond the forests.
Beyond the oceans.
Beyond every road that once seemed to stretch forever.

Here, the stars appear closer.
The void appears endless.

And silence seems to remember
things that the world below has forgotten.

The stars above the End have witnessed many endings.

They have watched travelers arrive with nothing but courage.
They have watched kingdoms rise beneath distant skies.
They have watched worlds change.

But tonight,
they witnessed something different.

Life continued where everything should have disappeared.

Beyond the void, something remained.

A story.

A family.

A promise.

Perhaps this is what it means to win.

Not to conquer the End.
Not to stand above the world.
Not to claim a crown that will eventually disappear.

A victory can be loud.

But the victories that matter most
are often quiet.

They live in the memories of people.
They appear in the places we leave behind.
They continue through those who come after us.

The child grows.

The journey continues.

And the footsteps that once seemed so small
become the footsteps of someone
walking farther than before.

One day, they may walk beyond the path you showed them.

One day, they may find places you never reached.

One day, they may create a story
that no longer needs your name.

And perhaps that is the greatest victory of all.

To know that something you helped begin
can continue without you.

The world moves on.

The seasons change.

The stars continue their endless journey across the sky.

The places we once knew become memories.

The people we once walked beside
continue along roads of their own.

And somewhere beneath the endless sky...

Someone remembers.

Someone always remembers.

Even the End cannot erase what was loved.

Even the void cannot swallow everything.

Because there are things that remain
long after the journey has ended.

A memory.

A name.

A family.

A beginning.

Somewhere,
a new story has already begun.

The End was reached.

But the story was not.

The child who once followed footsteps
now has footsteps of their own.

The road ahead is still unknown.

The sky is still endless.

The world is still waiting.

And this time...

it does not belong to the past.

"From today, nothing can be changed, but your choice has chosen to be the creation that you would like to be in. I know the past as you know your own. I will not say you have done a pretty good job, but you are The GOAT here." — Unknown

It belongs to those who come next.
""".strip();
    private static final String[] ROLLING_LINES = ROLLING_TEXT.split("\\R", -1);
    // Legacy verse selector uses the same cinematic text source.
    private static final String[] VERSES = ROLLING_LINES;


    private static boolean active;
    private static boolean suppressNextPauseScreen;
    private static int tick;
    private static int achieverId = -1;
    private static int childId = -1;
    private static long startGameTime;
    private static long sceneSeed;
    // Local playback clock. Normal playback remains synchronized to the server start;
    // optional 2x playback is deliberately client-local so one viewer can speed up
    // without changing the scene timing for everyone else.
    private static double localElapsedTicks;
    private static long lastClientGameTime;
    private static double playbackSpeed = 1.0D;
    private static String verse = VERSES[0];
    private static int verseIndex;
    private static int staticShotIndex = -1;
    private static Vec3[] staticShotPositions = new Vec3[STATIC_SHOT_COUNT];
    private static float[] staticShotYaws = new float[STATIC_SHOT_COUNT];
    private static float[] staticShotPitches = new float[STATIC_SHOT_COUNT];

    private static Vec3 firstShotStartPosition = Vec3.ZERO;
    private static Vec3 firstShotTargetPosition = Vec3.ZERO;
    private static Vec3 shotStartPosition = Vec3.ZERO;
    private static Vec3 shotTargetPosition = Vec3.ZERO;
    private static float firstShotStartYaw;
    private static float firstShotStartPitch;
    private static float shotStartYaw;
    private static float shotStartPitch;

    // Freeze cinematic anchors at scene start. The real entities may move during the
    // 60-second ceremony; the camera must not chase their network/tick movement.
    private static Vec3 achieverScenePosition = Vec3.ZERO;
    private static Vec3 childScenePosition = Vec3.ZERO;
    private static float achieverSceneYaw;
    private static float childSceneYaw;
    private static Vec3 shotLookTarget = Vec3.ZERO;
    private static Vec3 lastRigPosition = Vec3.ZERO;
    private static float lastRigYaw;
    private static float lastRigPitch;
    private static boolean rigTransformInitialized;
    private static ArmorStand cameraRig;
    private static CameraType previousCameraType;

    // Ignore duplicate delivery of the same server cutscene packet. Replaying the
    // same packet while the scene is active resets the camera origin and can make
    // the final credit appear to pop/restart.
    private static long activeSceneStartGameTime = Long.MIN_VALUE;
    private static long activeSceneSeed = Long.MIN_VALUE;
    private static int activeSceneAchieverId = -1;
    private static int activeSceneChildId = -1;

    private ParentalAccomplishmentCutsceneClient() {}

    public static void start(int newAchieverId, int newChildId, long newStartGameTime, long newSceneSeed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        Entity achiever = minecraft.level.getEntity(newAchieverId);
        Entity child = minecraft.level.getEntity(newChildId);
        if (!isValidSubject(minecraft, achiever) || !isValidSubject(minecraft, child)) return;

        // A PLAY_TO_CLIENT packet can be delivered more than once. Never restart
        // an identical active scene: doing so resets the cinematic origin.
        if (active
                && activeSceneStartGameTime == newStartGameTime
                && activeSceneSeed == newSceneSeed
                && activeSceneAchieverId == newAchieverId
                && activeSceneChildId == newChildId) {
            return;
        }

        // Replace an actually different local scene cleanly.
        if (active) stop();

        active = true;
        activeSceneStartGameTime = newStartGameTime;
        activeSceneSeed = newSceneSeed;
        activeSceneAchieverId = newAchieverId;
        activeSceneChildId = newChildId;
        suppressNextPauseScreen = false;
        previousCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        achieverId = newAchieverId;
        childId = newChildId;
        startGameTime = newStartGameTime;
        sceneSeed = newSceneSeed;
        localElapsedTicks = 0.0D;
        lastClientGameTime = minecraft.level.getGameTime();
        playbackSpeed = 1.0D;
        staticShotIndex = -1;
        staticShotPositions = new Vec3[STATIC_SHOT_COUNT];
        staticShotYaws = new float[STATIC_SHOT_COUNT];
        staticShotPitches = new float[STATIC_SHOT_COUNT];
        verseIndex = deterministicVerseIndex(0);
        verse = VERSES[verseIndex];

        // Snapshot subjects once. Do not derive camera coordinates from moving
        // entities every tick; that creates visible vertical/yaw jitter.
        achieverScenePosition = achiever.position();
        childScenePosition = child.position();
        achieverSceneYaw = achiever.getYRot();
        childSceneYaw = child.getYRot();

        rigTransformInitialized = false;
        firstShotStartPosition = findSafeCameraPosition(minecraft, createEstablishingPosition(achieverScenePosition), achieverScenePosition);
        firstShotTargetPosition = findSafeCameraPosition(minecraft, frontOf(achieverScenePosition, achieverSceneYaw, 5.0D, 1.45D), achieverScenePosition);
        float[] firstStartAngles = lookAt(firstShotStartPosition, targetPoint(achieverScenePosition, achiever.getBbHeight(), 0.72D));
        float[] firstTargetAngles = lookAt(firstShotTargetPosition, targetPoint(achieverScenePosition, achiever.getBbHeight(), 0.72D));
        firstShotStartYaw = firstStartAngles[0];
        firstShotStartPitch = firstStartAngles[1];
        shotStartYaw = firstTargetAngles[0];
        shotStartPitch = firstTargetAngles[1];
        buildStaticShots(minecraft);

        createSpectatorCamera(minecraft);
        if (minecraft.screen != null) minecraft.setScreen(null);
    }

    private static boolean isValidSubject(Minecraft minecraft, Entity entity) {
        return entity != null && entity.level() == minecraft.level;
    }

    private static void createSpectatorCamera(Minecraft minecraft) {
        if (minecraft.level == null) return;

        cameraRig = EntityType.ARMOR_STAND.create(minecraft.level);
        if (cameraRig == null) return;

        cameraRig.setInvisible(true);
        cameraRig.setNoGravity(true);
        cameraRig.setInvulnerable(true);
        cameraRig.setSilent(true);
        cameraRig.noPhysics = true;

        // Client-only entity: it is never sent to or spawned on the server.
        minecraft.level.addFreshEntity(cameraRig);
        minecraft.setCameraEntity(cameraRig);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (suppressNextPauseScreen) suppressNextPauseScreen = false;
        if (!active) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || cameraRig == null) {
            stop();
            return;
        }

        Entity achiever = minecraft.level.getEntity(achieverId);
        Entity child = minecraft.level.getEntity(childId);
        if (!isValidSubject(minecraft, achiever) || !isValidSubject(minecraft, child)) {
            stop();
            return;
        }

        // The scene is on a server timeline. Every viewer receives the same
        // start tick and seed, so shots/text change at the same moments.
        long now = minecraft.level.getGameTime();
        long delta = Math.max(0L, now - lastClientGameTime);
        lastClientGameTime = now;
        localElapsedTicks = Math.min((double) TOTAL_TICKS, localElapsedTicks + delta * playbackSpeed);
        tick = (int) Math.max(0L, Math.min((long) TOTAL_TICKS, Math.floor(localElapsedTicks)));

        if (minecraft.screen != null) minecraft.setScreen(null);
        // Perspective is locked to first-person mode for the spectator rig;
        // the player is not the camera entity and cannot rotate this camera.
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(cameraRig);

        if (tick < FIRST_SHOT_TICKS) {
            updateAchieverShot(cameraRig, achiever);
        } else if (tick < FIRST_SHOT_TICKS + CHILD_SHOT_TICKS) {
            updateChildShot(cameraRig, child, achiever);
        } else if (tick < STATIC_SECTION_END) {
            updateStaticShot(cameraRig);
        }

        updateVerse();

        if (tick >= TOTAL_TICKS) stop();
    }

    private static void updateAchieverShot(ArmorStand rig, Entity achiever) {
        float phase = smootherStep(clamp01(tick / (float) FIRST_SHOT_TICKS));
        Vec3 desired = lerpVec(firstShotStartPosition, firstShotTargetPosition, phase);
        Vec3 target = targetPoint(achieverScenePosition, achiever.getBbHeight(), 0.72D);
        // Camera principle: interpolate only the position, then derive one exact
        // look-at rotation from that position. Do not independently interpolate
        // yaw/pitch; that can create a small angular kink while the path bends.
        float[] angles = lookAt(desired, target);
        placeRig(rig, desired, angles);
    }

    private static void updateChildShot(ArmorStand rig, Entity child, Entity achiever) {
        float phase = smootherStep(clamp01((tick - FIRST_SHOT_TICKS) / (float) CHILD_SHOT_TICKS));
        Vec3 start = firstShotTargetPosition;
        Vec3 target = findSafeCameraPosition(Minecraft.getInstance(), frontOf(childScenePosition, childSceneYaw, 4.0D, 1.45D), childScenePosition);
        Vec3 startLook = targetPoint(achieverScenePosition, achiever.getBbHeight(), 0.72D);
        Vec3 targetLook = targetPoint(childScenePosition, child.getBbHeight(), 0.72D);
        Vec3 position = lerpVec(start, target, phase);
        float[] angles = lookAt(position, targetLook);
        placeRig(rig, position, angles);
    }

    private static void updateStaticShot(ArmorStand rig) {
        if (staticShotIndex < 0) return;

        if (tick >= ORBIT_START && tick < ORBIT_END) {
            updateEndIslandOrbit(rig);
            return;
        }

        // Remove the orbit interval from the static-shot clock so the same locked
        // shot resumes after the 360-degree aerial reveal instead of jumping to a
        // different composition.
        int elapsed = tick - STATIC_SECTION_START;
        if (tick >= ORBIT_END) elapsed -= (ORBIT_END - ORBIT_START);
        int index = Math.min(STATIC_SHOT_COUNT - 1, Math.max(0, elapsed / STATIC_SHOT_TICKS));
        Vec3 position = staticShotPositions[index];
        float yaw = staticShotYaws[index];
        float pitch = staticShotPitches[index];
        placeRig(rig, position, new float[]{yaw, pitch});
    }

    private static void updateEndIslandOrbit(ArmorStand rig) {
        float phase = smootherStep(clamp01((tick - ORBIT_START) / (float) (ORBIT_END - ORBIT_START)));
        double angle = sceneSeed * 0.0000001D + phase * Math.PI * 2.0D;
        Vec3 center = new Vec3(
                (achieverScenePosition.x + childScenePosition.x) * 0.5D,
                Math.min(achieverScenePosition.y, childScenePosition.y),
                (achieverScenePosition.z + childScenePosition.z) * 0.5D
        );

        // High but deliberately inside ordinary render distance: a wide aerial
        // reveal of The End without abandoning the loaded player/world area.
        double radius = 27.0D;
        double height = 28.0D + (16.0D - 28.0D) * phase;
        Vec3 desired = new Vec3(
                center.x + Math.cos(angle) * radius,
                center.y + height,
                center.z + Math.sin(angle) * radius
        );
        Vec3 safe = findSafeCameraPosition(Minecraft.getInstance(), desired, center);
        Vec3 target = new Vec3(center.x, center.y + 1.0D, center.z);
        float[] angles = lookAt(safe, target);
        placeRig(rig, safe, angles);
    }

    private static void buildStaticShots(Minecraft minecraft) {
        if (minecraft.level == null) return;

        // Nine deliberately different, locked-off compositions. Every candidate is
        // collision-tested before it is accepted; if a random point is bad, search
        // deterministic alternatives rather than allowing a wall/floor penetration.
        for (int i = 0; i < STATIC_SHOT_COUNT; i++) {
            Random random = new Random(sceneSeed ^ (0x9E3779B97F4A7C15L * (i + 17L)));
            boolean childShot = (i % 2 == 0);
            Vec3 subject = childShot ? childScenePosition : achieverScenePosition;
            double baseAngle = random.nextDouble() * Math.PI * 2.0D;
            Vec3 safe = null;

            for (int attempt = 0; attempt < 32 && safe == null; attempt++) {
                double angle = baseAngle + attempt * (Math.PI / 8.0D);
                double radius = 6.0D + random.nextDouble() * 5.0D;
                double height = 2.0D + random.nextDouble() * 3.0D;
                Vec3 desired = new Vec3(
                        subject.x + Math.cos(angle) * radius,
                        subject.y + height,
                        subject.z + Math.sin(angle) * radius
                );
                if (isCameraSpaceClear(minecraft, desired) && hasLineOfSight(minecraft, desired, targetPoint(subject, 1.4D, 0.72D))) {
                    safe = desired;
                }
            }

            if (safe == null) {
                safe = findSafeCameraPosition(minecraft, frontOf(subject, (float) random.nextDouble() * 360.0F, 6.0D, 2.5D), subject);
            }

            staticShotPositions[i] = safe;
            Vec3 lookTarget = targetPoint(subject, 1.8D, 0.72D);
            float[] angles = lookAt(safe, lookTarget);
            staticShotYaws[i] = angles[0];
            staticShotPitches[i] = angles[1];
        }
        staticShotIndex = 0;
    }

    private static boolean hasLineOfSight(Minecraft minecraft, Vec3 from, Vec3 to) {
        if (minecraft.level == null) return true;
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        return minecraft.level.clip(context).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static Vec3 findSafeCameraPosition(Minecraft minecraft, Vec3 desired, Vec3 subject) {
        if (minecraft.level == null) return desired;
        if (isCameraSpaceClear(minecraft, desired)) return desired;

        // Resolve only the camera location. Never move the subject or alter world
        // collision. Try radial fallbacks, then vertical fallbacks.
        Vec3 direction = desired.subtract(subject);
        double length = Math.max(0.001D, direction.length());
        Vec3 unit = direction.scale(1.0D / length);
        for (int i = 1; i <= 8; i++) {
            Vec3 candidate = subject.add(unit.scale(Math.max(2.0D, length - i * 0.65D)));
            if (isCameraSpaceClear(minecraft, candidate)) return candidate;
        }
        for (int i = 1; i <= 6; i++) {
            Vec3 candidate = desired.add(0.0D, i * 0.6D, 0.0D);
            if (isCameraSpaceClear(minecraft, candidate)) return candidate;
        }
        return subject.add(0.0D, 2.0D, Math.max(2.0D, length * 0.5D));
    }

    private static boolean isCameraSpaceClear(Minecraft minecraft, Vec3 position) {
        if (minecraft.level == null) return true;
        AABB cameraBox = new AABB(position.x - 0.12D, position.y - 0.12D, position.z - 0.12D,
                position.x + 0.12D, position.y + 0.12D, position.z + 0.12D);
        if (!minecraft.level.noCollision(null, cameraBox)) return false;
        BlockPos center = BlockPos.containing(position);
        BlockState state = minecraft.level.getBlockState(center);
        return state.getCollisionShape(minecraft.level, center).isEmpty();
    }

    private static void placeRig(ArmorStand rig, Vec3 cameraPosition, float[] angles) {
        // Let Minecraft's normal render interpolation do the smoothing between
        // the previous and current cinematic transform. The previous version
        // forced old == current every tick, which removed interpolation and
        // made the camera visibly step at 20 Hz.
        double eyeHeight = rig.getEyeHeight();
        double x = cameraPosition.x;
        double y = cameraPosition.y - eyeHeight;
        double z = cameraPosition.z;

        if (!rigTransformInitialized) {
            lastRigPosition = new Vec3(x, y, z);
            lastRigYaw = angles[0];
            lastRigPitch = angles[1];
            rigTransformInitialized = true;
        }

        rig.xo = lastRigPosition.x;
        rig.yo = lastRigPosition.y;
        rig.zo = lastRigPosition.z;
        rig.yRotO = lastRigYaw;
        rig.xRotO = lastRigPitch;
        rig.yHeadRotO = lastRigYaw;

        // Keep yaw continuous across the -180/180 boundary. Without this, a
        // perfectly smooth look-at path can suddenly choose the long rotational
        // route for one rendered frame, which is the small "snap" visible while
        // the camera turns through a diagonal composition.
        float continuousYaw = normalizeAngleToReference(angles[0], lastRigYaw);

        rig.setPosRaw(x, y, z);
        rig.setYRot(continuousYaw);
        rig.setXRot(angles[1]);
        rig.setYHeadRot(continuousYaw);

        lastRigPosition = new Vec3(x, y, z);
        lastRigYaw = continuousYaw;
        lastRigPitch = angles[1];
    }

    private static void updateVerse() {
        // Rolling credits use the fixed narrative below. Keep the legacy verse
        // state untouched so no other subsystem depends on it.
        verse = VERSES[deterministicVerseIndex(tick / 45)];
    }

    private static int deterministicVerseIndex(int slot) {
        long mixed = sceneSeed ^ (0xD1B54A32D192ED03L * (slot + 1L));
        mixed ^= (mixed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= (mixed >>> 33);
        return (int) Math.floorMod(mixed, VERSES.length);
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!active || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) return;

        // Left click is a deliberate 2x toggle for viewers who want to finish
        // faster. It does not affect the shared server trigger or other viewers.
        playbackSpeed = playbackSpeed == 1.0D ? 2.0D : 1.0D;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEscape(InputEvent.Key event) {
        if (!active || event.getKey() != GLFW.GLFW_KEY_ESCAPE || event.getAction() != GLFW.GLFW_PRESS) return;
        suppressNextPauseScreen = true;
        stop();
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (active || suppressNextPauseScreen) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (active) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        // The camera is a separate cinematic entity, so the player's first-person
        // hand must never be rendered into the shot.
        if (active) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGuiPost(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        if (!active) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();

        if (tick >= CREDIT_START) {
            // Final ten seconds: large title credit.
            float t = (tick - STATIC_SECTION_END) / (float) CREDIT_TICKS;
            float fadeIn = smoothStep(clamp01(t / 0.20F));
            float fadeOut = smoothStep(clamp01((1.0F - t) / 0.18F));
            int alpha = (int) (255.0F * Math.min(fadeIn, fadeOut));

            graphics.pose().pushPose();
            float scale = 2.75F;
            // Scale around the actual screen centre rather than scaling the GUI
            // origin. This keeps the title locked to the true cinematic centre
            // and prevents the final credit from appearing offset or duplicated
            // by a second origin calculation.
            graphics.pose().translate(width * 0.5F, height * 0.5F - 5.0F, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawCenteredString(
                    minecraft.font,
                    Component.literal("Normal NPC Player"),
                    0,
                    0,
                    (alpha << 24) | 0xFFFFFF
            );
            graphics.pose().popPose();

            // One second after the main title appears, fade in the subtitle.
            // It is deliberately rendered from the same screen-centre origin so
            // the credit remains one coherent title card rather than a second
            // independently positioned overlay.
            int subtitleElapsed = tick - STATIC_SECTION_END - CREDIT_SUBTITLE_DELAY_TICKS;
            if (subtitleElapsed >= 0) {
                float subtitleIn = smoothStep(clamp01(subtitleElapsed / (float) CREDIT_SUBTITLE_FADE_TICKS));
                int subtitleAlpha = (int) (255.0F * subtitleIn * fadeOut);

                graphics.pose().pushPose();
                graphics.pose().translate(width * 0.5F, height * 0.5F + 32.0F, 0.0F);
                graphics.pose().scale(1.15F, 1.15F, 1.0F);
                graphics.drawCenteredString(
                        minecraft.font,
                        Component.literal("Community Mod!"),
                        0,
                        0,
                        (subtitleAlpha << 24) | 0xFFFFFF
                );
                graphics.pose().popPose();
            }
            return;
        }

        if (tick >= STATIC_SECTION_START && tick < STATIC_SECTION_END) {
            renderStaticShotTransition(graphics, width, height);
        }

        if (tick >= ROLLING_CREDITS_START && tick < ROLLING_CREDITS_END) {
            renderRollingVerses(graphics, minecraft, width, height);
        }
    }

    private static void renderStaticShotTransition(GuiGraphics graphics, int width, int height) {
        // Never flash during the uninterrupted aerial orbit. The transition is
        // allowed again only after the orbit has completely finished.
        if (tick >= ORBIT_START && tick < ORBIT_END) return;
        int local = tick - STATIC_SECTION_START;
        if (tick >= ORBIT_END) local -= (ORBIT_END - ORBIT_START);
        local = Math.floorMod(local, STATIC_SHOT_TICKS);
        final int fadeTicks = 7;
        int alpha;
        if (local < fadeTicks) {
            alpha = (int) (255.0F * smoothStep(1.0F - local / (float) fadeTicks));
        } else if (local >= STATIC_SHOT_TICKS - fadeTicks) {
            alpha = (int) (255.0F * smoothStep((local - (STATIC_SHOT_TICKS - fadeTicks)) / (float) fadeTicks));
        } else {
            return;
        }
        graphics.fill(0, 0, width, height, (alpha << 24));
    }

    private static void renderRollingVerses(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
        final int lineHeight = 18;
        final float elapsed = (tick - ROLLING_CREDITS_START) / 20.0F;
        final float duration = (ROLLING_CREDITS_END - ROLLING_CREDITS_START) / 20.0F;
        final float totalHeight = ROLLING_LINES.length * lineHeight;
        final float travel = height + totalHeight + 80.0F;
        final float progress = clamp01(elapsed / duration);
        final float yBase = height + 30.0F - travel * progress;

        for (int i = 0; i < ROLLING_LINES.length; i++) {
            String line = ROLLING_LINES[i];
            float y = yBase + i * lineHeight;
            if (y < -lineHeight || y > height + lineHeight) continue;
            if (line.isBlank()) continue;

            float edge = Math.min(1.0F, Math.min((y + lineHeight) / 60.0F, (height - y + lineHeight) / 60.0F));
            int alpha = (int) (255.0F * smoothStep(clamp01(edge)));
            graphics.drawCenteredString(
                    minecraft.font,
                    Component.literal(line),
                    width / 2,
                    (int) y,
                    (alpha << 24) | 0xFFFFFF
            );
        }
    }

    private static Vec3 createEstablishingPosition(Vec3 subject) {
        Random random = new Random(sceneSeed ^ 0xA24BAED4963EE407L);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 9.0D + random.nextDouble() * 3.0D;
        double y = subject.y + 4.5D + random.nextDouble() * 1.5D;
        return new Vec3(
                subject.x + Math.cos(angle) * radius,
                y,
                subject.z + Math.sin(angle) * radius
        );
    }

    private static Vec3 frontOf(Vec3 entityPosition, float entityYaw, double distance, double height) {
        double radians = Math.toRadians(entityYaw);
        return new Vec3(
                entityPosition.x - Math.sin(radians) * distance,
                entityPosition.y + height,
                entityPosition.z - Math.cos(radians) * distance
        );
    }

    private static Vec3 targetPoint(Vec3 entityPosition, double bbHeight, double heightFactor) {
        return new Vec3(
                entityPosition.x,
                entityPosition.y + bbHeight * heightFactor,
                entityPosition.z
        );
    }

    private static float[] lookAt(Vec3 camera, Vec3 target) {
        double dx = target.x - camera.x;
        double dy = target.y - camera.y;
        double dz = target.z - camera.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-5D) horizontal = 1.0E-5D;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new float[]{yaw, pitch};
    }

    private static Vec3 lerpVec(Vec3 a, Vec3 b, float t) {
        return new Vec3(
                lerpDouble(a.x, b.x, t),
                lerpDouble(a.y, b.y, t),
                lerpDouble(a.z, b.z, t)
        );
    }

    private static double lerpDouble(double a, double b, double t) { return a + (b - a) * t; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float lerpAngle(float a, float b, float t) {
        return a + wrapDegrees(b - a) * t;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    private static float smoothStep(float t) { return t * t * (3.0F - 2.0F * t); }

    // Quintic easing: zero velocity and zero acceleration at both ends. It removes
    // the tiny perceptual "kick" that can remain when a camera path changes
    // direction, while preserving the slow, deliberate cinematic motion.
    private static float smootherStep(float t) {
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    private static float normalizeAngleToReference(float angle, float reference) {
        float result = angle;
        while (result - reference > 180.0F) result -= 360.0F;
        while (result - reference < -180.0F) result += 360.0F;
        return result;
    }

    private static float clamp01(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }

    private static float cinematicOpacity() {
        float t = tick / (float) TOTAL_TICKS;
        if (t < 0.06F) return smoothStep(t / 0.06F);
        if (t > 0.96F) return smoothStep((1.0F - t) / 0.04F);
        return 1.0F;
    }

    public static boolean isActive() { return active; }

    private static void stop() {
        Minecraft minecraft = Minecraft.getInstance();
        active = false;
        tick = 0;
        localElapsedTicks = 0.0D;
        lastClientGameTime = 0L;
        playbackSpeed = 1.0D;
        achieverId = -1;
        childId = -1;
        staticShotIndex = -1;
        staticShotPositions = new Vec3[STATIC_SHOT_COUNT];
        staticShotYaws = new float[STATIC_SHOT_COUNT];
        staticShotPitches = new float[STATIC_SHOT_COUNT];

        if (cameraRig != null) {
            cameraRig.remove(Entity.RemovalReason.DISCARDED);
            cameraRig = null;
        }

        if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        }
        if (previousCameraType != null && minecraft.options != null) {
            minecraft.options.setCameraType(previousCameraType);
        }
        previousCameraType = null;
        if (minecraft.screen != null) minecraft.setScreen(null);
    }
}
