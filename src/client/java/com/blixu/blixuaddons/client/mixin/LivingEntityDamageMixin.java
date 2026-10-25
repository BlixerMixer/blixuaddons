package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.CombatEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the damage event the server sends for an entity (client side only). Damage itself is untouched. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void blixuaddons$onDamage(DamageSource source, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) CombatEvents.onHurt(self);
	}
}
