package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.world.Atmosphere;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Atmosphere: adjust the fog the client is about to draw. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void blixuaddons$fog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level,
								 CallbackInfoReturnable<FogData> cir) {
		FogData fog = cir.getReturnValue();
		Atmosphere atmosphere = ModuleManager.active(Atmosphere.class);
		if (atmosphere != null) atmosphere.apply(fog, camera, renderDistanceInChunks * 16f);
		var cosmic = ModuleManager.active(com.blixu.blixuaddons.client.module.impl.world.CosmicSky.class);
		if (cosmic != null && (atmosphere == null || !atmosphere.overrideColor.get())) {
			float k = cosmic.opacity();
			int c = cosmic.horizonColor();
			fog.color.set(net.minecraft.util.Mth.lerp(k, fog.color.x, (c >> 16 & 255) / 255f), net.minecraft.util.Mth.lerp(k, fog.color.y, (c >> 8 & 255) / 255f),
				net.minecraft.util.Mth.lerp(k, fog.color.z, (c & 255) / 255f), 1f);
		}
	}
}
