package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.world.AntiDebuff;
import com.blixu.blixuaddons.client.style.Colors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Anti-Debuff: first-person fire / water / in-block overlays (lower, fade, recolour or hide). */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@WrapOperation(method = "submit", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
	private void blixuaddons$fire(PoseStack pose, SubmitNodeCollector out, TextureAtlasSprite sprite, Operation<Void> original) {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		if (a == null) {
			original.call(pose, out, sprite);
			return;
		}
		if (AntiDebuff.keep(a.fire) <= 0.01f) return;
		pose.pushPose();
		pose.translate(0f, -a.fireLower.getF(), 0f);
		original.call(pose, out, sprite);
		pose.popPose();
	}

	@ModifyArg(method = "buildFireQuad", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"),
		index = 8)
	private static int blixuaddons$fireColor(int color) {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		if (a == null) return color;
		int c = a.fireTint.get() ? (color & 0xFF000000) | (a.fireColor.argb() & 0xFFFFFF) : color;
		return Colors.withAlpha(c, AntiDebuff.keep(a.fire));
	}

	@WrapOperation(method = "submit", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitWater(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
	private void blixuaddons$water(Minecraft mc, PoseStack pose, SubmitNodeCollector out, Operation<Void> original) {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		if (a == null || AntiDebuff.keep(a.water) > 0.5f) original.call(mc, pose, out);
	}

	@ModifyArg(method = "submit", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitBlockSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"),
		index = 3)
	private int blixuaddons$inWall(int color) {
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		return a == null ? color : Colors.withAlpha(color, AntiDebuff.keep(a.inWall));
	}
}
