package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.BlixuAddons;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;

/**
 * Our own GPU pipelines. A pipeline = which GLSL shaders run + how the result
 * blends onto the screen. Shaders live in assets/blixuaddons/shaders/core/.
 */
public final class UiPipelines {
	private UiPipelines() {}

	/** Rounded boxes, outlines and soft shadows (rounded_rect.vsh / .fsh). */
	public static final RenderPipeline ROUNDED_RECT = RenderPipeline.builder()
		.withBindGroupLayout(BindGroupLayouts.GLOBALS)
		.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
		.withLocation(BlixuAddons.id("pipeline/rounded_rect"))
		.withVertexShader(BlixuAddons.id("core/rounded_rect"))
		.withFragmentShader(BlixuAddons.id("core/rounded_rect"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		// ENTITY has the slots we need: Position, Color, UV0 (float2), UV1 + UV2 (int2).
		.withVertexBinding(0, DefaultVertexFormat.ENTITY)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.build();

	/** Menu icons: tinted texture with 4-tap supersampling (gui_icon.fsh), so small icons stay smooth. */
	public static final RenderPipeline GUI_ICON = RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED_SNIPPET)
		.withLocation(BlixuAddons.id("pipeline/gui_icon"))
		.withVertexShader(BlixuAddons.id("core/gui_icon"))
		.withFragmentShader(BlixuAddons.id("core/gui_icon"))
		.build();

	/** Frosted panels: rounded box filled with the shared HUD blur texture (rounded_blur.fsh). */
	public static final RenderPipeline ROUNDED_BLUR = RenderPipeline.builder()
		.withBindGroupLayout(BindGroupLayouts.GLOBALS)
		.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
		.withLocation(BlixuAddons.id("pipeline/rounded_blur"))
		.withVertexShader(BlixuAddons.id("core/rounded_rect"))
		.withFragmentShader(BlixuAddons.id("core/rounded_blur"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withVertexBinding(0, DefaultVertexFormat.ENTITY)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.build();
}
