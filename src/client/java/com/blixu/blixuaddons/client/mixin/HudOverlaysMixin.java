package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.world.AntiDebuff;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Anti-Debuff: fades the portal, nausea, pumpkin/helmet and frost screen overlays. */
@Mixin(Hud.class)
public abstract class HudOverlaysMixin {
	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractPortalOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"))
	private void blixuaddons$portal(Hud self, GuiGraphicsExtractor g, float alpha, Operation<Void> original) {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		float k = a == null ? 1f : AntiDebuff.keep(a.portal);
		if (k > 0.01f) original.call(self, g, alpha * k);
	}

	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractConfusionOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;F)V"))
	private void blixuaddons$nausea(Hud self, GuiGraphicsExtractor g, float strength, Operation<Void> original) {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		float k = a == null ? 1f : AntiDebuff.keep(a.nausea);
		if (k > 0.01f) original.call(self, g, strength * k);
	}

	@WrapOperation(method = "extractCameraOverlays", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V"))
	private void blixuaddons$texture(Hud self, GuiGraphicsExtractor g, Identifier texture, float alpha, Operation<Void> original) {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		float k = 1f;
		if (a != null) k = AntiDebuff.keep(texture.getPath().contains("powder_snow") ? a.freeze : a.pumpkin);
		if (k > 0.01f) original.call(self, g, texture, alpha * k);
	}
}
