package com.blixu.blixuaddons.client.mixin;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Entity outline pipelines draw with no depth at all, so the glow couldn't tell how far a glowing mob is
 * (it read the wall in front of it instead: wrong size, cracks at block edges). Give them a depth test and
 * write into the outline buffer's own depth; nearer outlines also cover farther ones correctly now.
 */
@Mixin(RenderPipeline.Builder.class)
public abstract class OutlineDepthMixin {
	@Shadow private Optional<Identifier> location;
	@Shadow private Optional<DepthStencilState> depthStencilState;

	@Inject(method = "build", at = @At("HEAD"))
	private void blixuaddons$outlineDepth(CallbackInfoReturnable<RenderPipeline> cir) {
		if (location.isEmpty() || depthStencilState.isPresent()) return;
		String path = location.get().getPath();
		if (path.equals("pipeline/outline_cull") || path.equals("pipeline/outline_no_cull")) {
			depthStencilState = Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true));
		}
	}
}
