package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.EntityVisuals;
import com.blixu.blixuaddons.client.feature.MobGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes our chosen mobs count as "glowing" on this client only. */
@Mixin(Minecraft.class)
public class MinecraftGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void blixuaddons$forceGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (MobGlow.shouldGlow(entity) || EntityVisuals.wantsOutline(entity)) {
			cir.setReturnValue(true);
		}
	}
}
