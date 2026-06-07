package net.pwindows.anti_anti_xray.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import java.util.*;

public class OreCache {

    private static final Long2ObjectOpenHashMap<List<CachedOre>> chunkCache = new Long2ObjectOpenHashMap<>();
    private static long worldSeed = 0;
    private static boolean hasSeed = false;
    private static List<DatapackParser.OreRule> activeRules = new ArrayList<>();

    public record CachedOre(BlockPos pos, BlockState state, boolean exposed) {
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static void setSeed(String seedString) {
        try {
            worldSeed = Long.parseLong(seedString);
        } catch (NumberFormatException e) {
            worldSeed = (long) seedString.hashCode();
        }
        hasSeed = true;
        clearCache();
    }

    public static void setRules(List<DatapackParser.OreRule> rules) {
        activeRules = rules != null ? rules : new ArrayList<>();
        clearCache();
    }

    public static List<CachedOre> getOresForChunk(ChunkPos pos) {
        return chunkCache.get(chunkKey(pos.x(), pos.z()));
    }

    public static void calculateForChunk(LevelChunk chunk) {
        if (!hasSeed || activeRules.isEmpty()) return;

        ChunkPos chunkPos = chunk.getPos();
        long key = chunkKey(chunkPos.x(), chunkPos.z());
        if (chunkCache.containsKey(key)) return;

        List<CachedOre> ores = new ArrayList<>();

        // Step 1: Derive the decoration seed for this chunk
        WorldgenRandom baseRandom = new WorldgenRandom(WorldgenRandom.Algorithm.XOROSHIRO.newInstance(worldSeed));
        long decorationSeed = baseRandom.setDecorationSeed(worldSeed, chunkPos.x(), chunkPos.z());

        for (DatapackParser.OreRule rule : activeRules) {
            if (rule.count <= 0 || rule.oreBlock == null || rule.featureIndex < 0) continue;

            // Step 2: Create the feature-specific random using the exact Minecraft seeding
            WorldgenRandom featureRandom = new WorldgenRandom(WorldgenRandom.Algorithm.XOROSHIRO.newInstance(0));
            featureRandom.setFeatureSeed(decorationSeed, rule.featureIndex, rule.generationStep);

            int count = rule.count;
            for (int i = 0; i < count; i++) {
                // InSquarePlacement: random X/Z within chunk
                int x = (chunkPos.x() << 4) + featureRandom.nextInt(16);
                int z = (chunkPos.z() << 4) + featureRandom.nextInt(16);
                // HeightProvider
                int y = sampleHeight(featureRandom, rule);
                BlockPos center = new BlockPos(x, y, z);
                // Use the real OreFeature placement logic
                placeOreVein(featureRandom, center, rule, ores, chunk);
            }
        }
        chunkCache.put(key, ores);
    }

    private static int sampleHeight(WorldgenRandom random, DatapackParser.OreRule rule) {
        int min = rule.minY, max = rule.maxY;
        if (rule.heightProviderType.equals("minecraft:trapezoid")) {
            int range = max - min;
            int plateau = rule.plateau;
            if (plateau >= range) return Mth.randomBetweenInclusive(random, min, max);
            int plateauStart = (range - plateau) / 2;
            int plateauEnd = range - plateauStart;
            return min + Mth.randomBetweenInclusive(random, 0, plateauEnd) + Mth.randomBetweenInclusive(random, 0, plateauStart);
        } else {
            return Mth.randomBetweenInclusive(random, min, max);
        }
    }

    // Replicates OreFeature.place() vein shape
    private static void placeOreVein(WorldgenRandom random, BlockPos origin, DatapackParser.OreRule rule,
                                     List<CachedOre> ores, LevelChunk chunk) {
        float dir = random.nextFloat() * (float) Math.PI;
        float spreadXY = rule.veinSize / 8.0F;
        int maxRadius = Mth.ceil((rule.veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double x0 = origin.getX() + Math.sin(dir) * spreadXY;
        double x1 = origin.getX() - Math.sin(dir) * spreadXY;
        double z0 = origin.getZ() + Math.cos(dir) * spreadXY;
        double z1 = origin.getZ() - Math.cos(dir) * spreadXY;
        double y0 = origin.getY() + random.nextInt(3) - 2;
        double y1 = origin.getY() + random.nextInt(3) - 2;

        int size = rule.veinSize;
        double[] data = new double[size * 4];
        for (int i = 0; i < size; i++) {
            float step = (float) i / size;
            double xx = Mth.lerp(step, x0, x1);
            double yy = Mth.lerp(step, y0, y1);
            double zz = Mth.lerp(step, z0, z1);
            double ss = random.nextDouble() * size / 16.0;
            double r = ((Math.sin(Math.PI * step) + 1.0F) * ss + 1.0) / 2.0;
            data[i * 4] = xx;
            data[i * 4 + 1] = yy;
            data[i * 4 + 2] = zz;
            data[i * 4 + 3] = r;
        }

        for (int i = 0; i < size; i++) {
            double r = data[i * 4 + 3];
            if (r < 0.0) continue;
            double xx = data[i * 4], yy = data[i * 4 + 1], zz = data[i * 4 + 2];
            int xMin = Math.max(Mth.floor(xx - r), origin.getX() - maxRadius);
            int yMin = Math.max(Mth.floor(yy - r), origin.getY() - 2 - maxRadius);
            int zMin = Math.max(Mth.floor(zz - r), origin.getZ() - maxRadius);
            int xMax = Mth.floor(xx + r);
            int yMax = Mth.floor(yy + r);
            int zMax = Mth.floor(zz + r);

            for (int x = xMin; x <= xMax; x++) {
                double xd = (x + 0.5 - xx) / r;
                if (xd * xd >= 1.0) continue;
                for (int y = yMin; y <= yMax; y++) {
                    double yd = (y + 0.5 - yy) / r;
                    if (xd * xd + yd * yd >= 1.0) continue;
                    for (int z = zMin; z <= zMax; z++) {
                        double zd = (z + 0.5 - zz) / r;
                        if (xd * xd + yd * yd + zd * zd >= 1.0) continue;
                        BlockPos pos = new BlockPos(x, y, z);
                        if (pos.getX() >> 4 != origin.getX() >> 4 || pos.getZ() >> 4 != origin.getZ() >> 4) continue;
                        BlockState existing = chunk.getBlockState(pos);
                        boolean canPlace = false;
                        for (Block target : rule.replaceTargets) {
                            if (existing.is(target)) {
                                canPlace = true;
                                break;
                            }
                        }
                        if (!canPlace) continue;
                        boolean exposed = isExposedToAir(pos, chunk);
                        if (exposed && random.nextFloat() < rule.discardChance) continue;
                        ores.add(new CachedOre(pos.immutable(), rule.oreBlock.defaultBlockState(), exposed));
                    }
                }
            }
        }
    }

    private static boolean isExposedToAir(BlockPos pos, LevelChunk chunk) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (chunk.getBlockState(pos.offset(dx, dy, dz)).isAir()) return true;
                }
        return false;
    }

    public static void clearCache() {
        chunkCache.clear();
    }

    public static void onChunkUnload(LevelChunk chunk) {
        chunkCache.remove(chunkKey(chunk.getPos().x(), chunk.getPos().z()));
    }
}