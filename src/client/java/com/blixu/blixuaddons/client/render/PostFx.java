package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.BlixuAddons;
import com.blixu.blixuaddons.client.mixin.PostChainAccessor;
import com.blixu.blixuaddons.client.mixin.PostPassAccessor;
import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.hud.HudStyle;
import com.blixu.blixuaddons.client.module.impl.world.ColorGrading;
import com.blixu.blixuaddons.client.module.impl.world.Fullbright;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Screen-space effects: world colour grading (+ optional bloom) and the one shared blur
 * that every frosted HUD panel samples. If a shader fails to load the effect is skipped
 * (vanilla image stays), which is the fallback for unsupported setups.
 */
public final class PostFx {
	private PostFx() {}

	private static final Identifier GRADE = BlixuAddons.id("color_grade");
	private static final Identifier FXAA = BlixuAddons.id("fxaa");
	private static final Identifier HAND_GLOW = BlixuAddons.id("hand_glow");

	/** Hand Aura: shader glow around the first-person hand/item, right after the hand pass. */
	public static void handGlow(RenderTarget main, GraphicsResourceAllocator pool) {
		var aura = ModuleManager.active(com.blixu.blixuaddons.client.module.impl.player.HandAura.class);
		Minecraft mc = Minecraft.getInstance();
		if (aura == null || mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
		PostChain chain = mc.getShaderManager().getPostChain(HAND_GLOW, LevelTargetBundle.MAIN_TARGETS);
		if (chain == null) return;
		boolean low = com.blixu.blixuaddons.client.module.impl.LowEndMode.lightGlow();
		writeAll(chain, "HandConfig", aura.uniforms(low));
		writeAll(chain, "DistConfig", new float[] {aura.radius.getF(), 0f, 0.05f, 512f});
		writeAll(chain, "GlowConfig", com.blixu.blixuaddons.client.feature.GlowStyle.uniforms());
		chain.process(main, pool);
	}
	private static final Identifier GRADE_BLOOM = BlixuAddons.id("color_grade_bloom");
	private static final Identifier HUD_BLUR = BlixuAddons.id("hud_blur");

	private static ByteBuffer SCRATCH = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder());

	private static boolean worldRenderedThisFrame;

	// Shared HUD blur
	private static @Nullable TextureTarget blurTarget;
	private static @Nullable TextureTarget retiring;
	private static long lastBlurRequest = -100;
	private static long frame;
	private static boolean blurValid;

	public static void markWorldRendered() {
		worldRenderedThisFrame = true;
	}

	/** Runs after the world (incl. hand) is drawn, before the GUI. */
	public static void afterWorld(RenderTarget main, GraphicsResourceAllocator pool) {
		frame++;
		if (!worldRenderedThisFrame) return;
		worldRenderedThisFrame = false;
		// Anti-aliasing first, so grading/bloom work on the smoothed image.
		var aa = ModuleManager.active(com.blixu.blixuaddons.client.module.impl.world.AntiAliasing.class);
		if (aa != null && !com.blixu.blixuaddons.client.module.impl.LowEndMode.skipAa()) {
			PostChain fxaa = Minecraft.getInstance().getShaderManager().getPostChain(FXAA, LevelTargetBundle.MAIN_TARGETS);
			if (fxaa != null) {
				writeAll(fxaa, "FxaaConfig", aa.uniforms());
				fxaa.process(main, pool);
			}
		}
		ColorGrading grading = ModuleManager.active(ColorGrading.class);
		Fullbright fb = ModuleManager.active(Fullbright.class);
		boolean exposureOnly = grading == null && fb != null && fb.exposureEv() > 0.001f;
		if (grading == null && !exposureOnly) return;
		boolean bloom = grading != null && grading.bloom.get();
		PostChain chain = Minecraft.getInstance().getShaderManager().getPostChain(bloom ? GRADE_BLOOM : GRADE, LevelTargetBundle.MAIN_TARGETS);
		if (chain == null) return;
		float[] u = grading != null ? grading.uniforms() : ColorGrading.exposureOnly(fb);
		writeAll(chain, "GradeConfig", u);
		chain.process(main, pool);
	}

	/** Runs right before the GUI draws: copies the world image and blurs it once for all panels. */
	public static void beforeGui(RenderTarget main, GraphicsResourceAllocator pool) {
		if (retiring != null) {
			retiring.destroyBuffers();
			retiring = null;
		}
		HudStyle style = ModuleManager.find(HudStyle.class);
		boolean wanted = style != null && style.isEnabled() && style.blur.get() && frame - lastBlurRequest < 3;
		if (!wanted || main.getColorTexture() == null) {
			blurValid = false;
			return;
		}
		TextureTarget target = blurTarget;
		if (target == null || target.width != main.width || target.height != main.height) {
			// The GUI built this frame may still point at the old target, so free it next frame.
			retiring = target;
			blurTarget = new TextureTarget("blixuAddons HUD blur", main.width, main.height, false, GpuFormat.RGBA8_UNORM);
			blurValid = false;
			if (target == null) return;
		}
		if (target.width != main.width || target.height != main.height || target.getColorTexture() == null) return;
		RenderSystem.getDevice().createCommandEncoder()
			.copyTextureToTexture(main.getColorTexture(), target.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
		PostChain chain = Minecraft.getInstance().getShaderManager().getPostChain(HUD_BLUR, LevelTargetBundle.MAIN_TARGETS);
		if (chain == null) {
			blurValid = false;
			return;
		}
		float r = style.blurRadius.getF() * (float) Minecraft.getInstance().getWindow().getGuiScale() / 2f;
		List<PostPass> passes = ((PostChainAccessor) chain).blixuaddons$passes();
		if (passes.size() >= 2) {
			write(passes.get(0), "HudBlurConfig", new float[] {1, 0, r, 0});
			write(passes.get(1), "HudBlurConfig", new float[] {0, 1, r, 0});
		}
		chain.process(target, pool);
		blurValid = true;
	}

	/** The blurred world image for frosted panels, or null (then panels fall back to a plain tint). */
	public static @Nullable GpuTextureView requestBlur() {
		lastBlurRequest = frame;
		TextureTarget t = blurTarget;
		return blurValid && t != null ? t.getColorTextureView() : null;
	}

	public static GpuSampler linearSampler() {
		return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}

	/** Writes {@code block} in every pass of {@code chain} that has it (passes without it are skipped). */
	public static void writeUniforms(PostChain chain, String block, float[] values) {
		writeAll(chain, block, values);
	}

	private static void writeAll(PostChain chain, String block, float[] values) {
		for (PostPass pass : ((PostChainAccessor) chain).blixuaddons$passes()) write(pass, block, values);
	}

	private static void write(PostPass pass, String block, float[] values) {
		GpuBuffer buffer = ((PostPassAccessor) pass).blixuaddons$customUniforms().get(block);
		if (buffer == null || (buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0) return;
		int n = (int) Math.min(values.length, buffer.size() / 4);
		if (n * 4 > SCRATCH.capacity()) SCRATCH = ByteBuffer.allocateDirect(Integer.highestOneBit(n * 4) * 2).order(ByteOrder.nativeOrder());
		SCRATCH.clear();
		for (int i = 0; i < n; i++) SCRATCH.putFloat(values[i]);
		SCRATCH.flip();
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, n * 4L), SCRATCH);
	}

	public static void close() {
		if (blurTarget != null) blurTarget.destroyBuffers();
		if (retiring != null) retiring.destroyBuffers();
		blurTarget = null;
		retiring = null;
	}
}
