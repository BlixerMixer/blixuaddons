package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.perf.FrameStats;
import com.blixu.blixuaddons.client.feature.perf.OcclusionCuller;
import com.blixu.blixuaddons.client.module.impl.PerformanceEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Always-on saving: Hypixel fills areas with invisible armor stands (hologram anchors, hitboxes). One
 * that is invisible, shows no name, wears/holds nothing and isn't glowing draws nothing at all, but would
 * still go through render-state extraction every frame; skip it. Anything visible is untouched.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderSkipMixin {
	private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void blixuaddons$skipEmptyStands(E entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		// Damage Tags draws its own numbers in place of Hypixel's damage stands
		if (com.blixu.blixuaddons.client.module.impl.combat.DamageTags.replacing && entity instanceof ArmorStand ds
			&& com.blixu.blixuaddons.client.module.impl.combat.DamageTags.isDamageStand(ds)) {
			cir.setReturnValue(false);
			return;
		}
		if (!(entity instanceof ArmorStand stand) || !stand.isInvisible() || stand.isCustomNameVisible() || stand.isCurrentlyGlowing()) return;
		for (EquipmentSlot slot : SLOTS) if (!stand.getItemBySlot(slot).isEmpty()) return;
		cir.setReturnValue(false);
	}

	/** Occlusion culling fallback (only when EntityCulling isn't installed), after vanilla's frustum test said yes. */
	@Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
	private <E extends Entity> void blixuaddons$occlusion(E entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (!PerformanceEngine.cullingOn || !cir.getReturnValueZ()) return;
		EntityRendererInvoker r = (EntityRendererInvoker) ((EntityRenderDispatcher) (Object) this).getRenderer(entity);
		if (!r.blixuaddons$affectedByCulling(entity) || !OcclusionCuller.eligible(entity, PerformanceEngine.conservativeOn)) {
			FrameStats.entity(false);
			return;
		}
		boolean hidden = OcclusionCuller.occluded(entity, r.blixuaddons$cullingBox(entity), camX, camY, camZ);
		FrameStats.entity(hidden);
		if (hidden) cir.setReturnValue(false);
	}
}
