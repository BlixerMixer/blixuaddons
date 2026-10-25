package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.BlixuAddons;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Render types for world visuals, all on our own GLSL (shaders/core/world_fx, world_line,
 * entity_material). They depth-test against the world (reverse-Z GREATER_OR_EQUAL like vanilla);
 * only the outline mask feeds the through-wall glow styles.
 */
public final class WorldPipelines {
	private WorldPipelines() {}

	private static RenderPipeline.Builder fx(String name) {
		return RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(BlixuAddons.id("pipeline/" + name))
			.withVertexShader(BlixuAddons.id("core/world_fx"))
			.withFragmentShader(BlixuAddons.id("core/world_fx"))
			.withCull(false);
	}

	/** Additive position+colour quads (glows, energy, sparks). No depth write, no culling. */
	public static final RenderPipeline GLOW_PIPELINE = fx("world_glow")
		.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
		.build();

	public static final RenderType GLOW = RenderType.create("blixuaddons_glow",
		RenderSetup.builder(GLOW_PIPELINE).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup());

	/** Position+colour quads into the entity-outline target: picked up by the glow post chain (shared glow style). */
	public static final RenderPipeline OUTLINE_PIPELINE = fx("world_outline")
		.withShaderDefine("NO_DITHER")
		.withColorTargetState(ColorTargetState.DEFAULT) // no blending: alpha carries opacity + module id
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)) // its own buffer's depth: the aura reads distance from it
		.build();

	public static final RenderType OUTLINE = RenderType.create("blixuaddons_outline",
		RenderSetup.builder(OUTLINE_PIPELINE)
			.setOutputTarget(OutputTarget.OUTLINE_TARGET)
			.setOutline(RenderSetup.OutlineProperty.IS_OUTLINE)
			.createRenderSetup());

	/**
	 * Fully opaque quads of the fill batch. These write depth, so solid cosmetics (hat, wings, relics)
	 * hide their own back faces and aren't painted over by later passes.
	 */
	public static final RenderPipeline SOLID_PIPELINE = fx("world_solid")
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
		.build();

	public static final RenderType SOLID = RenderType.create("blixuaddons_solid", RenderSetup.builder(SOLID_PIPELINE).createRenderSetup());

	/** Alpha-blended quads (fills, ribbons, translucent cosmetics). Sorted so overlapping translucency looks right. */
	public static final RenderPipeline FILL_PIPELINE = fx("world_fill").build();

	public static final RenderType FILL = RenderType.create("blixuaddons_fill", RenderSetup.builder(FILL_PIPELINE).sortOnUpload().createRenderSetup());

	/** Thick screen-space lines with anti-aliased edges. */
	public static final RenderPipeline LINE_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(BlixuAddons.id("pipeline/world_line"))
		.withVertexShader(BlixuAddons.id("core/world_line"))
		.withFragmentShader(BlixuAddons.id("core/world_line"))
		.build();

	public static final RenderType LINES = RenderType.create("blixuaddons_lines",
		RenderSetup.builder(LINE_PIPELINE)
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.createRenderSetup());

	/** Through-wall versions (tracers, slayer head lines and labels): never depth-tested. */
	private static final DepthStencilState NO_DEPTH = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

	public static final RenderPipeline XRAY_LINE_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(BlixuAddons.id("pipeline/world_line_xray"))
		.withVertexShader(BlixuAddons.id("core/world_line"))
		.withFragmentShader(BlixuAddons.id("core/world_line"))
		.withDepthStencilState(NO_DEPTH)
		.build();

	public static final RenderType XRAY_LINES = RenderType.create("blixuaddons_lines_xray",
		RenderSetup.builder(XRAY_LINE_PIPELINE).setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET).createRenderSetup());

	public static final RenderPipeline XRAY_GLOW_PIPELINE = fx("world_glow_xray")
		.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
		.withDepthStencilState(NO_DEPTH)
		.build();

	public static final RenderType XRAY_GLOW = RenderType.create("blixuaddons_glow_xray", RenderSetup.builder(XRAY_GLOW_PIPELINE).createRenderSetup());

	public static RenderType fill() {
		return FILL;
	}

	public static RenderType lines() {
		return LINES;
	}

	// ------------------------------------------------------------------ entity materials (Self Aura, Hit Color)

	private static RenderPipeline.Builder material(String name, boolean emissive) {
		return RenderPipeline.builder(emissive ? RenderPipelines.ENTITY_EMISSIVE_SNIPPET : RenderPipelines.ENTITY_SNIPPET)
			.withLocation(BlixuAddons.id("pipeline/" + name))
			.withVertexShader(BlixuAddons.id("core/entity_material"))
			.withFragmentShader(BlixuAddons.id("core/entity_material"))
			.withBindGroupLayout(BindGroupLayouts.SAMPLER1);
	}

	private static final RenderPipeline MATERIAL_SOLID = material("entity_material_solid", false).build();
	private static final RenderPipeline MATERIAL_TRANSLUCENT = material("entity_material_translucent", false)
		.withShaderDefine("ALPHA_CUTOUT", 0.1f)
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withCull(false)
		.build();
	private static final RenderPipeline MATERIAL_EMISSIVE = material("entity_material_emissive", true)
		.withShaderDefine("ALPHA_CUTOUT", 0.1f)
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withCull(false)
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.build();

	/** Our procedural enchant glint (entity_glint.fsh), added on top of the model. */
	private static final RenderPipeline GLINT_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
		.withLocation(BlixuAddons.id("pipeline/entity_glint"))
		.withVertexShader(BlixuAddons.id("core/entity_glint"))
		.withFragmentShader(BlixuAddons.id("core/entity_glint"))
		.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
		.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
		.withCull(false)
		.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
		.build();

	private static RenderType glintType;

	/** {@code texture}: any bound texture (the shader is procedural); the model's own is fine. */
	public static RenderType entityGlint(Identifier texture) {
		if (glintType == null) {
			glintType = RenderType.create("blixuaddons_glint", RenderSetup.builder(GLINT_PIPELINE)
				.withTexture("Sampler0", texture).useOverlay().createRenderSetup());
		}
		return glintType;
	}

	private static final Map<Identifier, RenderType> SOLID_TYPES = new HashMap<>(), TRANSLUCENT_TYPES = new HashMap<>(), EMISSIVE_TYPES = new HashMap<>();

	public static RenderType entitySolid(Identifier texture) {
		return SOLID_TYPES.computeIfAbsent(texture, t -> RenderType.create("blixuaddons_material_solid", RenderSetup.builder(MATERIAL_SOLID)
			.withTexture("Sampler0", t).useLightmap().useOverlay().affectsCrumbling()
			.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));
	}

	public static RenderType entityTranslucent(Identifier texture) {
		return TRANSLUCENT_TYPES.computeIfAbsent(texture, t -> RenderType.create("blixuaddons_material_translucent", RenderSetup.builder(MATERIAL_TRANSLUCENT)
			.withTexture("Sampler0", t).useLightmap().useOverlay().affectsCrumbling().sortOnUpload()
			.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));
	}

	public static RenderType entityTranslucentEmissive(Identifier texture) {
		return EMISSIVE_TYPES.computeIfAbsent(texture, t -> RenderType.create("blixuaddons_material_emissive", RenderSetup.builder(MATERIAL_EMISSIVE)
			.withTexture("Sampler0", t).useOverlay().affectsCrumbling().sortOnUpload()
			.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));
	}
}
