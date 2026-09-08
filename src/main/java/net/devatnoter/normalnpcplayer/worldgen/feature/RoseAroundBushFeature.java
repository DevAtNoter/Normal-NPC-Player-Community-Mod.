package net.devatnoter.normalnpcplayer.worldgen.feature;

import net.devatnoter.normalnpcplayer.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class RoseAroundBushFeature extends Feature<NoneFeatureConfiguration> {
    public RoseAroundBushFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        boolean placedAny = false;
        int minX = origin.getX();
        int minZ = origin.getZ();
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int topY = Math.min(maxY, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
                int fromY = Math.max(minY, topY - 3);

                for (int y = fromY; y <= topY; y++) {
                    BlockPos roseBushPos = new BlockPos(x, y, z);
                    if (!level.getBlockState(roseBushPos).is(Blocks.ROSE_BUSH)) continue;

                    // Reliably try several nearby positions whenever a Rose Bush exists.
                    int placedForBush = 0;
                    int attempts = 16;
                    for (int i = 0; i < attempts && placedForBush < 2; i++) {
                        int dx = random.nextInt(7) - 3;
                        int dz = random.nextInt(7) - 3;
                        if (dx == 0 && dz == 0) continue;

                        BlockPos target = roseBushPos.offset(dx, 0, dz);
                        // Never write outside the chunk currently being generated.
                        // This keeps world generation self-contained and avoids triggering neighbor chunk loads.
                        if (target.getX() < minX || target.getX() > maxX || target.getZ() < minZ || target.getZ() > maxZ) continue;
                        if (!level.getBlockState(target).canBeReplaced()) continue;
                        BlockState groundState = level.getBlockState(target.below());
                        if (!groundState.is(net.minecraft.tags.BlockTags.DIRT)) continue;

                        BlockState rose = ModBlocks.ROSE.get().defaultBlockState();
                        if (!rose.canSurvive(level, target)) continue;

                        level.setBlock(target, rose, 2);
                        placedAny = true;
                        placedForBush++;
                    }
                    break;
                }
            }
        }

        return placedAny;
    }
}
