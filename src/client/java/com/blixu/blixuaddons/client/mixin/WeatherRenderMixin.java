package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.impl.PerformanceEngine;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide rain and snow: skips only the drawing; rain sounds, splashes and the weather itself are untouched. */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherRenderMixin {
	@Inject(method = "render(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V", at = @At("HEAD"), cancellable = true)
	private void blixuaddons$hideWeather(Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
		if (PerformanceEngine.weatherHidden) ci.cancel();
	}
}
