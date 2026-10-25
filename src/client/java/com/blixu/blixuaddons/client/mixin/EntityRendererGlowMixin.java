package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.EntityVisuals;
import com.blixu.blixuaddons.client.feature.MobGlow;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the outline colour (normally the team colour) for ours, and records per-entity material data. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererGlowMixin<T extends Entity, S extends EntityRenderState> {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("TAIL")
	)
	private void blixuaddons$neonColor(T entity, S state, float partialTick, CallbackInfo ci) {
		if (MobGlow.shouldGlow(entity)) {
			state.outlineColor = withAlpha(MobGlow.colorFor(entity));
		} else {
			int c = EntityVisuals.outlineColor(entity);
			if (c != 0) state.outlineColor = withAlpha(c);
		}
		EntityVisuals.extract(entity, state);
	}

	/** Keep a given alpha (glow opacity, read by our rendertype_outline shader); a bare RGB means opaque. */
	private static int withAlpha(int c) {
		return (c >>> 24) == 0 ? ARGB.opaque(c) : c;
	}
}
