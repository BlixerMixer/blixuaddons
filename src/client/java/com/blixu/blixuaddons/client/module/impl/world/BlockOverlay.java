package com.blixu.blixuaddons.client.module.impl.world;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.render.WorldCtx;
import com.blixu.blixuaddons.client.style.Colors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Restyles the outline of the block you are looking at (the game's own target, so it is
 * always a visible block). Interaction and selection are unchanged; Vanilla style keeps
 * the original outline.
 */
public class BlockOverlay extends Module {
	public enum Style {
		VANILLA("Vanilla"), THIN("Thin outline"), CORNERS("Corner brackets"), WIREFRAME("Full wireframe"), TRANSPARENT("Transparent fill"),
		SOLID("Solid fill"), GRADIENT("Gradient outline"), PULSE("Pulsing highlight"), HIGHLIGHT("Highlight glow");
		public final String label;
		Style(String l) { label = l; }
	}

	public final EnumSetting<Style> style = add(new EnumSetting<>("style", "Style", Style.CORNERS, s -> s.label));
	public final ColorSetting outline = add(new ColorSetting("outline", "Outline colour", 0xD13BFF));
	public final com.blixu.blixuaddons.client.feature.GlowStyle.Look glowLook = com.blixu.blixuaddons.client.feature.GlowStyle.addLook(this::add, com.blixu.blixuaddons.client.feature.GlowStyle.OVERLAY, () -> style.get() == Style.HIGHLIGHT, 0.08)
		.withDesign(this::add, () -> style.get() == Style.HIGHLIGHT, 1.0);
	public final ColorSetting fillColor = add(new ColorSetting("fill_color", "Fill colour", 0x8B5CF6));
	public final ColorSetting secondary = add(new ColorSetting("secondary", "Secondary gradient colour", 0x22D3EE));
	public final NumberSetting thickness = add(new NumberSetting("thickness", "Line thickness", 3, 1, 10, 0.25));
	public final NumberSetting fillOpacity = add(new NumberSetting("fill_opacity", "Fill opacity", 0.18, 0, 1, 0.02));
	public final NumberSetting glow = add(new NumberSetting("glow", "Glow intensity", 0.3, 0, 1, 0.05));
	public final NumberSetting cornerSize = add(new NumberSetting("corner_size", "Corner marker size", 0.25, 0.05, 0.5, 0.01));
	public final NumberSetting speed = add(new NumberSetting("speed", "Animation speed", 1, 0.1, 4, 0.05));
	public final BoolSetting faceHighlight = add(new BoolSetting("face", "Highlight the targeted face", true));
	public final BoolSetting perCategory = add(new BoolSetting("per_category", "Different colour for containers", false));
	public final ColorSetting containerColor = add(new ColorSetting("container_color", "Container colour", 0xFF7A1A).visibleWhen(perCategory::get));

	public BlockOverlay() {
		super("block_overlay", "Block Overlay",
			"Corner brackets, wireframes, fills, gradients or a pulse on the block you look at.",
			Category.VISUALS, "cursor", false);
	}

	/** False = we draw it, so vanilla's outline is skipped. */
	public boolean allowVanilla() {
		return style.get() == Style.VANILLA;
	}

	@Override
	public void onWorldRender(WorldCtx ctx) {
		if (style.get() == Style.VANILLA) return;
		HitResult hit = ctx.mc.hitResult;
		if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK || ctx.mc.level == null) return;
		if (ctx.mc.gui.hud.isHidden()) return;
		BlockPos pos = bhr.getBlockPos();
		BlockState state = ctx.mc.level.getBlockState(pos);
		if (state.isAir() || !ctx.mc.level.getWorldBorder().isWithinBounds(pos)) return;
		VoxelShape shape = state.getShape(ctx.mc.level, pos, CollisionContext.of(ctx.mc.player));
		if (shape.isEmpty()) return;

		int lineC = perCategory.get() && state.getBlock() instanceof BaseEntityBlock ? containerColor.argb() : outline.argb();
		int fillC = perCategory.get() && state.getBlock() instanceof BaseEntityBlock ? containerColor.argb() : fillColor.argb();
		float pulse = 0.55f + 0.45f * Mth.sin(ctx.time * speed.getF() * 5f);
		float w = thickness.getF();
		float fo = fillOpacity.getF();
		double e = 0.003;
		for (AABB local : shape.toAabbs()) {
			double x0 = pos.getX() + local.minX - e, y0 = pos.getY() + local.minY - e, z0 = pos.getZ() + local.minZ - e;
			double x1 = pos.getX() + local.maxX + e, y1 = pos.getY() + local.maxY + e, z1 = pos.getZ() + local.maxZ + e;
			drawBox(ctx, x0, y0, z0, x1, y1, z1, lineC, fillC, pulse, w, fo);
		}
		if (faceHighlight.get()) face(ctx, pos, bhr.getDirection(), shape.bounds(), Colors.withAlpha(fillC, Math.max(0.12f, fo) * (0.7f + 0.3f * pulse)));
	}

	private void drawBox(WorldCtx ctx, double x0, double y0, double z0, double x1, double y1, double z1, int lineC, int fillC, float pulse, float w, float fo) {
			switch (style.get()) {
				case THIN -> ctx.boxLines(x0, y0, z0, x1, y1, z1, lineC, lineC, Math.max(1f, w * 0.5f));
				case CORNERS -> ctx.boxCorners(x0, y0, z0, x1, y1, z1, lineC, w, cornerSize.getF());
				case WIREFRAME -> {
					ctx.boxLines(x0, y0, z0, x1, y1, z1, lineC, lineC, w);
					ctx.line(x0, y0, z0, x1, y1, z1, lineC, lineC, w * 0.6f);
					ctx.line(x1, y0, z0, x0, y1, z1, lineC, lineC, w * 0.6f);
					ctx.line(x0, y0, z1, x1, y1, z0, lineC, lineC, w * 0.6f);
					ctx.line(x1, y0, z1, x0, y1, z0, lineC, lineC, w * 0.6f);
				}
				case TRANSPARENT -> {
					ctx.boxFill(ctx.fill, x0, y0, z0, x1, y1, z1, Colors.withAlpha(fillC, fo), Colors.withAlpha(fillC, fo * 0.7f));
					ctx.boxLines(x0, y0, z0, x1, y1, z1, lineC, lineC, w * 0.6f);
				}
				case SOLID -> {
					ctx.boxFill(ctx.fill, x0, y0, z0, x1, y1, z1, Colors.setAlpha(fillC, Math.max(fo, 0.7f)), Colors.setAlpha(fillC, Math.max(fo, 0.7f)));
					ctx.boxLines(x0, y0, z0, x1, y1, z1, lineC, lineC, w * 0.6f);
				}
				case GRADIENT -> ctx.boxLines(x0, y0, z0, x1, y1, z1, lineC, secondary.argb(), w);
				case PULSE -> {
					ctx.boxLines(x0, y0, z0, x1, y1, z1, Colors.withAlpha(lineC, pulse), Colors.withAlpha(lineC, pulse), w * (0.6f + 0.6f * pulse));
					ctx.boxFill(ctx.glow, x0, y0, z0, x1, y1, z1, Colors.withAlpha(fillC, fo * pulse), Colors.withAlpha(fillC, fo * pulse * 0.4f));
				}
				// the targeted block glows in the shared highlight style (outline buffer), in the outline colour
				case HIGHLIGHT -> {
					int oc = com.blixu.blixuaddons.client.feature.GlowStyle.encode(lineC, 1f, com.blixu.blixuaddons.client.feature.GlowStyle.OVERLAY);
					ctx.boxFill(ctx.outline, x0, y0, z0, x1, y1, z1, oc, oc);
				}
				case VANILLA -> { }
			}
			if (glow.getF() > 0.01f) {
				ctx.boxFill(ctx.glow, x0, y0, z0, x1, y1, z1, Colors.withAlpha(lineC, glow.getF() * 0.12f), Colors.withAlpha(lineC, 0f));
			}
	}

	@Override
	public Preview preview() {
		return Preview.SCENE;
	}

	@Override
	public void renderPreview(WorldCtx ctx, float time) {
		for (int i = -1; i <= 1; i++) ctx.boxFill(ctx.fill, i - 0.5, -0.5, -0.5, i + 0.5, 0.5, 0.5, 0xFF5E6E52, 0xFF7A9A62);
		float pulse = 0.55f + 0.45f * Mth.sin(time * speed.getF() * 5f);
		double e = 0.003;
		if (style.get() != Style.VANILLA) {
			drawBox(ctx, -0.5 - e, -0.5 - e, -0.5 - e, 0.5 + e, 0.5 + e, 0.5 + e, outline.argb(), fillColor.argb(), pulse, thickness.getF(), fillOpacity.getF());
		} else {
			ctx.boxLines(-0.5 - e, -0.5 - e, -0.5 - e, 0.5 + e, 0.5 + e, 0.5 + e, 0xFF000000, 0xFF000000, 2f);
		}
		if (faceHighlight.get()) ctx.boxFill(ctx.fill, -0.5, 0.505, -0.5, 0.5, 0.505, 0.5, Colors.withAlpha(fillColor.argb(), Math.max(0.12f, fillOpacity.getF())), Colors.withAlpha(fillColor.argb(), Math.max(0.12f, fillOpacity.getF())));
	}

	/** Fills just the face you are aiming at, nudged out so it doesn't z-fight. */
	private void face(WorldCtx ctx, BlockPos pos, Direction d, AABB b, int c) {
		double e = 0.004;
		double x0 = pos.getX() + b.minX, y0 = pos.getY() + b.minY, z0 = pos.getZ() + b.minZ;
		double x1 = pos.getX() + b.maxX, y1 = pos.getY() + b.maxY, z1 = pos.getZ() + b.maxZ;
		switch (d) {
			case UP -> ctx.boxFill(ctx.fill, x0, y1 + e, z0, x1, y1 + e, z1, c, c);
			case DOWN -> ctx.boxFill(ctx.fill, x0, y0 - e, z0, x1, y0 - e, z1, c, c);
			case NORTH -> ctx.boxFill(ctx.fill, x0, y0, z0 - e, x1, y1, z0 - e, c, c);
			case SOUTH -> ctx.boxFill(ctx.fill, x0, y0, z1 + e, x1, y1, z1 + e, c, c);
			case WEST -> ctx.boxFill(ctx.fill, x0 - e, y0, z0, x0 - e, y1, z1, c, c);
			case EAST -> ctx.boxFill(ctx.fill, x1 + e, y0, z0, x1 + e, y1, z1, c, c);
		}
	}
}
