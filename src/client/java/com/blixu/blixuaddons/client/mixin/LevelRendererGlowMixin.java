package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.MobGlow;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Swaps Minecraft's outline effect for the glow style picked in the menu. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererGlowMixin {
	@ModifyArg(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"
		),
		index = 0
	)
	private Identifier blixuaddons$glowStyle(Identifier id) {
		return MobGlow.postChainFor(id);
	}

	/** Feed the shared glow settings (strength, second colour, star colour) into the chain about to run. */
	@com.llamalad7.mixinextras.injector.ModifyExpressionValue(
		method = "render",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"
		)
	)
	private net.minecraft.client.renderer.PostChain blixuaddons$glowUniforms(net.minecraft.client.renderer.PostChain chain) {
		if (chain != null) {
			com.blixu.blixuaddons.client.render.PostFx.writeUniforms(chain, "GlowConfig", com.blixu.blixuaddons.client.feature.GlowStyle.uniforms());
			com.blixu.blixuaddons.client.render.PostFx.writeUniforms(chain, "DistConfig", com.blixu.blixuaddons.client.feature.GlowStyle.distUniforms());
		}
		return chain;
	}
}
