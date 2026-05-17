package net.pwindows.anti_anti_xray.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

public class OreCache {

    private static final Long2ObjectOpenHashMap<List<CachedOre>> chunkCache = new Long2ObjectOpenHashMap<>();
    private static long worldSeed = 0;
    private static boolean hasSeed = false;
    private static List<DatapackParser.OreRule> activeRules = new ArrayList<>();

    public record CachedOre(BlockPos pos, BlockState state, boolean exposed) {}

    // Helper to pack chunk coordinates into a long key (standard Minecraft encoding)
    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static void setSeed(String seedString) {
        try {
            worldSeed = Long.parseLong(seedString);
        } catch (NumberFormatException e) {
            worldSeed = (long) seedString.hashCode();
        }
        hasSeed = true;
        clearCache();
        System.out.println("OreCache seed set to: " + worldSeed);
    }

    public static void setRules(List<DatapackParser.OreRule> rules) {
        activeRules = rules != null ? rules : new ArrayList<>();
        clearCache();
        System.out.println("OreCache rules updated: " + activeRules.size() + " rules");
    }

    public static BlockState getTrueBlockState(BlockPos pos) {
        if (!hasSeed || activeRules.isEmpty()) return null;

        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        List<CachedOre> ores = chunkCache.get(key);

        if (ores == null) return null;

        for (CachedOre ore : ores) {
            if (ore.pos.equals(pos)) {
                return ore.state;
            }
        }
        return null;
    }

    public static void calculateForChunk(LevelChunk chunk) {
        if (!hasSeed || activeRules.isEmpty()) return;

        ChunkPos chunkPos = chunk.getPos();
        long key = chunkKey(chunkPos.x(), chunkPos.z());
        if (chunkCache.containsKey(key)) return;

        List<CachedOre> ores = new ArrayList<>();

        for (DatapackParser.OreRule rule : activeRules) {
            if (rule.count <= 0 || rule.oreBlock == null) continue;

            Random random = createChunkRandom(chunkPos, rule);

            for (int i = 0; i < rule.count; i++) {
                int minX = chunkPos.x() << 4;
                int minZ = chunkPos.z() << 4;
                int x = minX + random.nextInt(16);
                int z = minZ + random.nextInt(16);
                int y = getRandomHeight(random, rule);

                BlockPos center = new BlockPos(x, y, z);
                generateVein(random, center, rule, ores, chunk);
            }
        }

        chunkCache.put(key, ores);
    }

    private static Random createChunkRandom(ChunkPos chunkPos, DatapackParser.OreRule rule) {
        long seed = worldSeed;
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        seed += chunkPos.x() * 0x4f9939f508L;
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        seed += chunkPos.z() * 0x1ef1565bd5L;
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        seed += rule.oreBlock.hashCode();
        return new Random(seed);
    }

    private static int getRandomHeight(Random random, DatapackParser.OreRule rule) {
        if ("minecraft:uniform".equals(rule.heightProviderType)) {
            return rule.minY + random.nextInt(rule.maxY - rule.minY + 1);
        } else {
            int range = rule.maxY - rule.minY;
            return rule.minY + random.nextInt(range + 1);
        }
    }

    private static void generateVein(Random random, BlockPos center,
                                     DatapackParser.OreRule rule,
                                     List<CachedOre> ores, LevelChunk chunk) {
        int size = rule.veinSize;
        int placed = 0;
        int attempts = size * 3;

        for (int attempt = 0; attempt < attempts && placed < size; attempt++) {
            int dx = random.nextInt(size + 1) - size / 2;
            int dy = random.nextInt(size + 1) - size / 2;
            int dz = random.nextInt(size + 1) - size / 2;

            if (dx * dx + dy * dy + dz * dz > (size / 2.0) * (size / 2.0)) continue;

            BlockPos pos = center.offset(dx, dy, dz);

            // Ensure we stay inside the chunk horizontally
            if (pos.getX() >> 4 != center.getX() >> 4 ||
                    pos.getZ() >> 4 != center.getZ() >> 4) continue;

            BlockState existing = chunk.getBlockState(pos);

            boolean canReplace = false;
            for (Block target : rule.replaceTargets) {
                if (existing.is(target)) {
                    canReplace = true;
                    break;
                }
            }
            if (!canReplace) continue;

            boolean exposed = isExposedToAir(pos, chunk);
            if (exposed && random.nextFloat() < rule.discardChance) continue;

            ores.add(new CachedOre(pos.immutable(), rule.oreBlock.defaultBlockState(), exposed));
            placed++;
        }
    }

    private static boolean isExposedToAir(BlockPos pos, LevelChunk chunk) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos neighbor = pos.offset(dx, dy, dz);
                    if (chunk.getBlockState(neighbor).isAir()) return true;
                }
            }
        }
        return false;
    }

    public static void clearCache() {
        chunkCache.clear();
    }

    public static void onChunkUnload(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        chunkCache.remove(chunkKey(chunkPos.x(), chunkPos.z()));
    }
}