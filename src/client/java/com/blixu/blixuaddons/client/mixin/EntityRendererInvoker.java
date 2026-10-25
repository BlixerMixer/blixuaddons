package com.blixu.blixuaddons.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The renderer's own culling box and "never cull me" flag, so occlusion culling respects special renderers. */
@Mixin(EntityRenderer.class)
public interface EntityRendererInvoker {
	@Invoker("affectedByCulling")
	boolean blixuaddons$affectedByCulling(Entity entity);

	@Invoker("getBoundingBoxForCulling")
	AABB blixuaddons$cullingBox(Entity entity);
}
