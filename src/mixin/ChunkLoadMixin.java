package net.pwindows.anti_anti_xray.mixin;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.pwindows.anti_anti_xray.client.OreCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public class ChunkLoadMixin {

    @Inject(method = "getChunk", at = @At("RETURN"))
    private void onChunkLoad(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        LevelChunk chunk = cir.getReturnValue();
        if (chunk != null) {
            OreCache.calculateForChunk(chunk);
        }
    }
}