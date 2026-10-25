package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.player.SwingAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Starts the cosmetic swing clock when the local player swings. Vanilla swing logic runs unchanged. */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
	private void blixuaddons$onSwing(InteractionHand hand, boolean sendToSwingingEntity, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player || hand != InteractionHand.MAIN_HAND) return;
		SwingAnimations s = ModuleManager.active(SwingAnimations.class);
		if (s != null) s.onSwing();
	}
}
