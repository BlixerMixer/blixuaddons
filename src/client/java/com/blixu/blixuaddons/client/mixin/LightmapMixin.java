package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.LightmapHooks;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright and anti-darkness adjust the lightmap inputs; world light levels are untouched. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void blixuaddons$adjust(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		LightmapHooks.apply(state);
	}
}
