package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.FirstPersonFx;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person hooks: hand pose, custom swing, inspection, hand aura and trails.
 * Only the first-person hand pass goes through here, so third person and inventory
 * rendering are unaffected.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void blixuaddons$begin(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		FirstPersonFx.begin(poseStack);
	}

	@Inject(method = "submitHandsWithItems", at = @At("RETURN"))
	private void blixuaddons$end(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		FirstPersonFx.end(collector);
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
	private void blixuaddons$pose(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack,
								  float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		FirstPersonFx.afterPush(poseStack, itemStack, arm(player, hand));
	}

	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"))
	private void blixuaddons$swing(ItemInHandRenderer self, float attack, PoseStack pose, int invert, HumanoidArm arm, Operation<Void> original,
								   @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) ItemStack stack) {
		boolean main = hand == InteractionHand.MAIN_HAND;
		if (!FirstPersonFx.swing(pose, attack, invert, stack, main)) {
			original.call(self, FirstPersonFx.classicAttack(attack, main), pose, invert, arm);
		}
	}

	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	private void blixuaddons$item(ItemInHandRenderer self, LivingEntity mob, ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
								  SubmitNodeCollector out, int light, Operation<Void> original,
								  @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) AbstractClientPlayer player) {
		boolean main = hand == InteractionHand.MAIN_HAND;
		HumanoidArm arm = arm(player, hand);
		int lit = FirstPersonFx.light(light);
		int echoes = FirstPersonFx.afterimageCount(main);
		for (int i = echoes; i >= 1; i--) {
			pose.pushPose();
			// Dimmer light makes the copies read as fading echoes behind the real item.
			if (FirstPersonFx.prepareAfterimage(pose, i, stack, arm)) original.call(self, mob, stack, ctx, pose, out, dim(lit, i));
			pose.popPose();
		}
		pose.pushPose();
		FirstPersonFx.beforeItem(pose, stack, arm, main);
		original.call(self, mob, stack, ctx, pose, out, lit);
		FirstPersonFx.afterItem(pose, stack, arm, main);
		pose.popPose();
	}

	private static HumanoidArm arm(AbstractClientPlayer player, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
	}

	private static int dim(int packedLight, int step) {
		int block = Math.max(0, (packedLight & 0xFFFF) - step * 0x40);
		int sky = Math.max(0, (packedLight >>> 16) - step * 0x40);
		return block | (sky << 16);
	}

	@WrapOperation(method = "renderPlayerArm", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
	private void blixuaddons$rightArm(AvatarRenderer<?> renderer, PoseStack pose, SubmitNodeCollector out, int light, Identifier skin, boolean sleeve,
									  Operation<Void> original) {
		blixuaddons$arm(renderer, pose, out, light, skin, sleeve, original, HumanoidArm.RIGHT);
	}

	@WrapOperation(method = "renderPlayerArm", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V"))
	private void blixuaddons$leftArm(AvatarRenderer<?> renderer, PoseStack pose, SubmitNodeCollector out, int light, Identifier skin, boolean sleeve,
									 Operation<Void> original) {
		blixuaddons$arm(renderer, pose, out, light, skin, sleeve, original, HumanoidArm.LEFT);
	}

	private static void blixuaddons$arm(AvatarRenderer<?> renderer, PoseStack pose, SubmitNodeCollector out, int light, Identifier skin, boolean sleeve,
										Operation<Void> original, HumanoidArm arm) {
		pose.pushPose();
		FirstPersonFx.beforeItem(pose, ItemStack.EMPTY, arm, true);
		original.call(renderer, pose, out, FirstPersonFx.light(light), skin, sleeve);
		FirstPersonFx.afterArm(pose, arm);
		pose.popPose();
	}
}
