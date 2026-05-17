package net.pwindows.anti_anti_xray.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.pwindows.anti_anti_xray.client.OreCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void overrideBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState original = cir.getReturnValue();
        BlockState override = OreCache.getTrueBlockState(pos);

        if (override == null) return;

        // Engine Mode 1: server sent stone, but ore exists here -> show ore
        if (original.is(Blocks.STONE) || original.is(Blocks.DEEPSLATE) ||
                original.is(Blocks.NETHERRACK)) {
            cir.setReturnValue(override);
        }
        // Engine Mode 2: server sent fake ore, but real block is stone -> show stone
        // (Optional - you can add the reverse check here later)
    }
}