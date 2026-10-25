package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.world.AntiDebuff;
import com.blixu.blixuaddons.client.render.PostFx;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen-space passes: world colour grading after the level, the shared HUD blur before the GUI. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow @Final private RenderTarget mainRenderTarget;
	@Shadow @Final private CrossFrameResourcePool resourcePool;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void blixuaddons$afterWorld(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		PostFx.afterWorld(mainRenderTarget, resourcePool);
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
	private void blixuaddons$beforeGui(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		PostFx.beforeGui(mainRenderTarget, resourcePool);
	}

	// Anti-Debuff: scale the nausea / portal world warp (the effects themselves are untouched).
	@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
	private float blixuaddons$nausea(float value) {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		return a == null ? value : value * AntiDebuff.keep(a.nausea);
	}

	@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 0))
	private float blixuaddons$portal(float value) {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		return a == null ? value : value * AntiDebuff.keep(a.portal);
	}

	@ModifyExpressionValue(method = "bobHurt", at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;damageTiltStrength:D"))
	private double blixuaddons$hurtTilt(double value) {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		return a == null ? value : value * AntiDebuff.keep(a.hurtShake);
	}
}
