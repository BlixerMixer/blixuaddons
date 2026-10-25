package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.world.AntiDebuff;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadLocalRandom;

/** Anti-Debuff: drops a share of potion-effect swirl particles (purely visual particles). */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void blixuaddons$potion(ParticleOptions options, double x, double y, double z, double xa, double ya, double za,
									CallbackInfoReturnable<Particle> cir) {
		var type = options.getType();
		if (type != ParticleTypes.ENTITY_EFFECT && type != ParticleTypes.EFFECT && type != ParticleTypes.INSTANT_EFFECT) return;
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		if (a != null && ThreadLocalRandom.current().nextFloat() < a.potionParticles.getF()) cir.setReturnValue(null);
	}
}
