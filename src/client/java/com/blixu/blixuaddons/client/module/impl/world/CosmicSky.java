package com.blixu.blixuaddons.client.module.impl.world;

import com.blixu.blixuaddons.BlixuAddons;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.style.Colors;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Replaces the overworld sky with a GLSL-painted cosmos (shaders/core/cosmic_sky): deep space, a
 * drifting two-colour nebula with dust lanes along a galactic band, and twinkling stars. One draw call:
 * a sphere just inside the far plane, so blocks, entities and clouds stay in front of it.
 */
public class CosmicSky extends Module {
	/** Order = the shader's style index (cosmic_sky.fsh). */
	public enum Style {
		NEBULA("Nebula", 0xFF020206), AURORA("Aurora", 0xFF07101C), CLOUDS("Realistic clouds", 0xFFA9C4E4),
		SUNSET("Sunset", 0xFFC9805E), GALAXY("Galaxy core", 0xFF06050A);
		final String label;
		final int fog;
		Style(String label, int fog) { this.label = label; this.fog = fog; }
	}

	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Sky", Style.NEBULA, s -> s.label));
	public final ColorSetting colorA = add(new ColorSetting("color_a", "Main colour (nebula, aurora, galaxy)", 0x8B5CF6));
	public final ColorSetting colorB = add(new ColorSetting("color_b", "Second colour", 0xFF4FB8));
	public final NumberSetting intensity = add(new NumberSetting("intensity", "Intensity", 1, 0, 2, 0.05));
	public final NumberSetting starDensity = add(new NumberSetting("star_density", "Star density", 1, 0, 1.25, 0.05));
	public final NumberSetting starBrightness = add(new NumberSetting("star_brightness", "Star brightness", 1, 0, 1.25, 0.05));
	public final NumberSetting drift = add(new NumberSetting("drift", "Drift speed", 1, 0, 5, 0.1));
	public final BoolSetting nightOnly = add(new BoolSetting("night_only", "Only at night (fades with the sun)", false));

	private static final RenderPipeline PIPELINE = RenderPipeline.builder()
		.withBindGroupLayout(BindGroupLayouts.GLOBALS)
		.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
		.withLocation(BlixuAddons.id("pipeline/cosmic_sky"))
		.withVertexShader(BlixuAddons.id("core/cosmic_sky"))
		.withFragmentShader(BlixuAddons.id("core/cosmic_sky"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.withCull(false)
		.withVertexBinding(0, DefaultVertexFormat.ENTITY)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.build();
	private static final RenderType TYPE = RenderType.create("blixuaddons_cosmic_sky", RenderSetup.builder(PIPELINE).createRenderSetup());

	/** Cube faces (6), each a GRID x GRID patch pushed out onto the sphere so it sits inside the far plane. */
	private static final int GRID = 6;
	private static final Matrix4f[] FACE = {
		new Matrix4f(), new Matrix4f().rotateX(Mth.PI), new Matrix4f().rotateX(Mth.HALF_PI),
		new Matrix4f().rotateX(-Mth.HALF_PI), new Matrix4f().rotateZ(Mth.HALF_PI), new Matrix4f().rotateZ(-Mth.HALF_PI),
	};

	public CosmicSky() {
		super("cosmic_sky", "Cosmic Sky",
			"GLSL skies: nebula, aurora borealis, realistic clouds, sunset or the galaxy core.",
			Category.WORLD, "sky", false);
	}

	/** 0..1 how much of the cosmos shows right now (0 outside the overworld or under water). */
	public float opacity() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return 0f;
		if (mc.gameRenderer.mainCamera().getFluidInCamera() != FogType.NONE) return 0f;
		if (!nightOnly.get()) return 1f;
		long tod = Math.floorMod(mc.level.getOverworldClockTime(), 24000L); // 0 = sunrise, 6000 = noon
		float day = Mth.clamp(Mth.cos((tod - 6000) / 24000f * Mth.TWO_PI) * 2.2f + 0.5f, 0f, 1f);
		return 1f - day;
	}

	/** Fog colour that matches the sky's horizon, so distant terrain fades into space instead of a blue band. */
	public int horizonColor() {
		Style s = style.get();
		return s == Style.CLOUDS || s == Style.SUNSET ? s.fog : Colors.lerp(s.fog, Colors.scale(colorA.argb() | 0xFF000000, 0.08f), 0.5f);
	}

	/** One draw: submitted before everything else in the translucent pass. */
	public void submit(OrderedSubmitNodeCollector out, PoseStack pose, float farPlane) {
		float alpha = opacity();
		if (alpha <= 0.003f) return;
		float radius = Math.min(farPlane * 0.85f, 900f);
		int a = colorA.argb(), b = colorB.argb();
		int color = (Math.round(alpha * 255) << 24) | (a & 0xFFFFFF);
		float time = (Colors.seconds() * drift.getF()) % 20000f;
		float inten = intensity.getF() + style.index() * 4f; // style rides in UV0.y: index * 4 + intensity (0..2)
		int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		int packed = Math.round(starDensity.getF() * 100) + Math.round(starBrightness.getF() * 100) * 128;
		float low = com.blixu.blixuaddons.client.module.impl.LowEndMode.lightSky() ? 1f : 0f; // rides in Normal.x
		out.submitCustomGeometry(pose, TYPE, (p, vc) -> {
			Vector3f v = new Vector3f();
			for (Matrix4f f : FACE) {
				for (int i = 0; i < GRID; i++) {
					for (int j = 0; j < GRID; j++) {
						corner(vc, p, f, v, i, j, radius, color, time, inten, br, bg, bb, packed, low);
						corner(vc, p, f, v, i, j + 1, radius, color, time, inten, br, bg, bb, packed, low);
						corner(vc, p, f, v, i + 1, j + 1, radius, color, time, inten, br, bg, bb, packed, low);
						corner(vc, p, f, v, i + 1, j, radius, color, time, inten, br, bg, bb, packed, low);
					}
				}
			}
		});
	}

	private static void corner(VertexConsumer vc, PoseStack.Pose p, Matrix4f face, Vector3f v, int i, int j, float radius,
							   int color, float time, float inten, int br, int bg, int bb, int packed, float low) {
		face.transformPosition(-1 + 2f * i / GRID, 1, -1 + 2f * j / GRID, v).normalize(radius);
		vc.addVertex(p, v.x, v.y, v.z).setColor(color).setUv(time, inten).setUv1(br, bg).setUv2(bb, packed).setNormal(low, 1f - low, 0f);
	}
}
