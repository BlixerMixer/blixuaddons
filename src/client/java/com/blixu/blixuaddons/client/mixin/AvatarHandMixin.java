package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.player.HandPose;
import com.blixu.blixuaddons.client.style.Colors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** First-person arm tint (renderHand is only used for the first-person hand). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarHandMixin {
	@WrapOperation(method = "renderHand", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
	private void blixuaddons$tint(SubmitNodeCollector out, ModelPart part, PoseStack pose, RenderType type, int light, int overlay,
								  TextureAtlasSprite sprite, Operation<Void> original) {
		HandPose hp = ModuleManager.active(HandPose.class);
		if (hp == null || hp.armTintStrength.getF() <= 0f) {
			original.call(out, part, pose, type, light, overlay, sprite);
			return;
		}
		int tint = Colors.lerp(0xFFFFFFFF, hp.armTint.argb() | 0xFF000000, hp.armTintStrength.getF());
		out.submitModelPart(part, pose, type, light, overlay, sprite, tint, null);
	}
}
