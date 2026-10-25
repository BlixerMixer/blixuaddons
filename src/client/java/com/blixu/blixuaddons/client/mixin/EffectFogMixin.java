package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.world.AntiDebuff;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Anti-Debuff: pushes blindness/darkness fog back out (partially or fully). The effect itself stays. */
@Mixin({BlindnessFogEnvironment.class, DarknessFogEnvironment.class})
public abstract class EffectFogMixin {
	@Unique
	private float blixuaddons$suppression() {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		if (a == null) return 0f;
		return ((Object) this) instanceof BlindnessFogEnvironment ? a.blindness.getF() : a.darkness.getF();
	}

	@Inject(method = "setupFog", at = @At("TAIL"))
	private void blixuaddons$clear(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
		float s = blixuaddons$suppression();
		if (s <= 0f) return;
		fog.environmentalStart = Mth.lerp(s, fog.environmentalStart, renderDistance * 0.8f);
		fog.environmentalEnd = Mth.lerp(s, fog.environmentalEnd, renderDistance);
		fog.skyEnd = Mth.lerp(s, fog.skyEnd, renderDistance);
		fog.cloudEnd = Mth.lerp(s, fog.cloudEnd, renderDistance);
	}

	@Inject(method = "getModifiedDarkness", at = @At("RETURN"), cancellable = true)
	private void blixuaddons$lighter(LivingEntity entity, float darkness, float partialTickTime, CallbackInfoReturnable<Float> cir) {
		float s = blixuaddons$suppression();
		if (s > 0f) cir.setReturnValue(Mth.lerp(s, cir.getReturnValueF(), darkness));
	}
}
