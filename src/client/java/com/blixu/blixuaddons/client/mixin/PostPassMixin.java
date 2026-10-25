package com.blixu.blixuaddons.client.mixin;

import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Our post passes get uniform buffers we can rewrite each frame (live colour grading / blur radius). */
@Mixin(PostPass.class)
public abstract class PostPassMixin {
	@Shadow @Final private String name;

	@ModifyArg(
		method = "<init>",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;"),
		index = 1
	)
	private int blixuaddons$writableUniforms(int usage) {
		return name.startsWith("blixuaddons:") ? usage | 8 /* USAGE_COPY_DST */ : usage;
	}
}
