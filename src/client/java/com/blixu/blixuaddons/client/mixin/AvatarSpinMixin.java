package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.cosmetic.PlayerSpin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player Spin: rewrites only the local player's render state (body yaw, crouch pose). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarSpinMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void blixuaddons$spin(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		if (entity != Minecraft.getInstance().player) return;
		PlayerSpin spin = ModuleManager.active(PlayerSpin.class);
		if (spin == null) return;
		float head = state.bodyRot + state.yRot; // where the head really looks
		state.bodyRot = spin.bodyYaw();
		state.yRot = spin.spinHead.get() ? 0f : net.minecraft.util.Mth.wrapDegrees(head - state.bodyRot);
		if (spin.crouching()) state.isCrouching = true;
	}
}
