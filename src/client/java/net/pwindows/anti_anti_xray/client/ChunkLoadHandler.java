package net.pwindows.anti_anti_xray.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public class ChunkLoadHandler {

    public static void register() {
        ClientChunkEvents.CHUNK_LOAD.register((ClientLevel world, LevelChunk chunk) -> {
            OreCache.calculateForChunk(chunk);

            // Place predicted ores directly into the world (renders immediately)
            ChunkPos chunkPos = chunk.getPos();
            List<OreCache.CachedOre> ores = OreCache.getOresForChunk(chunkPos);
            if (ores != null) {
                for (OreCache.CachedOre ore : ores) {
                    BlockState existing = world.getBlockState(ore.pos());
                    if (existing.is(Blocks.STONE) || existing.is(Blocks.DEEPSLATE) || existing.is(Blocks.NETHERRACK)) {
                        world.setBlock(ore.pos(), ore.state(), 3);
                    }
                }
            }
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((ClientLevel world, LevelChunk chunk) -> {
            OreCache.onChunkUnload(chunk);
        });
    }
}