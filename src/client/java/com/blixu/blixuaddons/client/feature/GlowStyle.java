package com.blixu.blixuaddons.client.feature;

import com.blixu.blixuaddons.BlixuAddons;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.module.setting.Setting;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Starfield Aura glow for everything drawn into the outline buffer. One post-effect chain draws it
 * for all objects, but each module has its OWN look (strength, outer colour, stars): every outline
 * colour carries its module's id in the low 3 bits of its alpha (the high 5 bits are the opacity),
 * and the shader looks the module's settings up by that id.
 */
public enum GlowStyle {
	STARFIELD("Aura glow", BlixuAddons.id("glow_starfield"));

	public final String label;
	public final Identifier postChain;

	GlowStyle(String label, Identifier postChain) {
		this.label = label;
		this.postChain = postChain;
	}

	public static GlowStyle current() {
		return STARFIELD;
	}

	// ------------------------------------------------------------------ module ids

	/** Look ids (low 4 bits of the outline alpha). 15 = anything we didn't colour (vanilla glowing). */
	public static final int MOB = 0, ESP = 1, OVERLAY = 2, SELF = 3, HIT = 4, SLAYER = 5, SLAYER_BOSS = 6,
		SLAYER_MINI = 8, SLAYER_VOIDLING = 9, DEFAULT = 15;

	/** Outline colour for {@code id}'s object: rgb, opacity in 16 steps (high 4 bits of alpha), look id in the low 4. */
	public static int encode(int rgb, float opacity, int id) {
		int level = Mth.clamp(Math.round(opacity * 15), 1, 15);
		return ((level << 4 | (id & 15)) << 24) | (rgb & 0xFFFFFF);
	}

	// ------------------------------------------------------------------ designs

	/** The aura designs shared with Hand Aura (glow_common.glsl paintDesign). Shader index 0 is the plain soft band, not selectable. */
	public enum Design {
		NEBULA("Nebula", 1), PLASMA("Plasma", 2), FLAME("Flame", 3), GALAXY("Galaxy", 4);
		public final String label;
		public final int shader;
		Design(String label, int shader) { this.label = label; this.shader = shader; }
	}

	// ------------------------------------------------------------------ per-module look

	/** One module's glow look. */
	public static final class Look {
		public final NumberSetting strength, bodyTint;
		public final BoolSetting ownOuter;
		public final ColorSetting outer;
		/** Optional per-module extras (null = defaults: radius 1, plain soft band and fill in the glow colours). */
		public @org.jspecify.annotations.Nullable NumberSetting radius, animSpeed;
		/** When set, replaces {@link #bodyTint}: how strongly the design shows on the object. */
		public @org.jspecify.annotations.Nullable NumberSetting designOpacity;
		public com.blixu.blixuaddons.client.module.setting.@org.jspecify.annotations.Nullable EnumSetting<Design> bandDesign, fillDesign;
		public @org.jspecify.annotations.Nullable ColorSetting fillA, fillB;

		/** A look that shares {@code band}'s glow settings (strength, outer colour, radius); only the design is its own. */
		private Look(Look band) {
			strength = band.strength;
			bodyTint = band.bodyTint;
			ownOuter = band.ownOuter;
			outer = band.outer;
			radius = band.radius;
		}

		/**
		 * Another object's look (e.g. one mob type) with this look's glow band but its own design rows,
		 * added where the caller is in its settings list. Setting ids are prefixed so they stay unique.
		 */
		public Look designLook(java.util.function.UnaryOperator<Setting<?>> add, int id, java.util.function.BooleanSupplier visible, String prefix) {
			Look l = new Look(this);
			l.designRows(add, visible, prefix);
			LOOKS[id] = l;
			return l;
		}

		Look(java.util.function.UnaryOperator<Setting<?>> add, java.util.function.BooleanSupplier visible, double tint, String tintLabel) {
			strength = (NumberSetting) add.apply(new NumberSetting("glow_strength", "Glow strength", 1, 0, 2, 0.05).visibleWhen(visible));
			bodyTint = (NumberSetting) add.apply(new NumberSetting("glow_body_tint", tintLabel, tint, 0, 1, 0.05).visibleWhen(visible));
			ownOuter = (BoolSetting) add.apply(new BoolSetting("glow_own_second", "Own outer glow colour", false).visibleWhen(visible));
			outer = (ColorSetting) add.apply(new ColorSetting("glow_second", "Outer glow colour", 0x22D3EE).visibleWhen(() -> visible.getAsBoolean() && ownOuter.get()));
		}

		/** The fill's own two colours and its animation speed (the designs paint only in these colours). */
		public Look withFillColors(java.util.function.UnaryOperator<Setting<?>> add, java.util.function.BooleanSupplier visible) {
			return withFillColors(add, visible, "");
		}

		private Look withFillColors(java.util.function.UnaryOperator<Setting<?>> add, java.util.function.BooleanSupplier visible, String prefix) {
			fillA = (ColorSetting) add.apply(new ColorSetting(prefix + "glow_fill_a", "Design colour", 0xD13BFF).visibleWhen(visible));
			fillB = (ColorSetting) add.apply(new ColorSetting(prefix + "glow_fill_b", "Design colour 2", 0x22D3EE).visibleWhen(visible));
			animSpeed = (NumberSetting) add.apply(new NumberSetting(prefix + "glow_anim_speed", "Animation speed", 1, 0, 4, 0.05).visibleWhen(visible));
			return this;
		}

		/** Design, its two colours, animation speed and opacity. */
		@SuppressWarnings("unchecked")
		private void designRows(java.util.function.UnaryOperator<Setting<?>> add, java.util.function.BooleanSupplier visible, String prefix) {
			fillDesign = (com.blixu.blixuaddons.client.module.setting.EnumSetting<Design>) add.apply(
				new com.blixu.blixuaddons.client.module.setting.EnumSetting<>(prefix + "glow_fill_design", "Design", Design.NEBULA, d -> d.label).visibleWhen(visible));
			withFillColors(add, visible, prefix);
			designOpacity = (NumberSetting) add.apply(new NumberSetting(prefix + "glow_design_opacity", "Design opacity", bodyTint.defaultValue(), 0, 1, 0.05).visibleWhen(visible));
		}

		/**
		 * Mob glows: the glow around the mob (strength, outer colour + its own radius) and, in a section
		 * of its own, the design on the mob: pattern, its two colours, animation speed and opacity.
		 */
		public Look withMobFill(java.util.function.UnaryOperator<Setting<?>> add, java.util.function.BooleanSupplier visible) {
			return withDesign(add, visible, 0.4);
		}

		/**
		 * Every glow's layout: the glow around the object (strength, outer colour, radius) and a Design
		 * section for the animated pattern on it. Design opacity starts at the glow's old tint default.
		 */
		public Look withDesign(java.util.function.UnaryOperator<Setting<?>> add, java.util.function.BooleanSupplier visible, double radiusDefault) {
			return withDesign(add, visible, radiusDefault, "glow_radius");
		}

		/** {@code radiusId}: for modules whose saved "glow_radius" meant something else before. */
		@SuppressWarnings("unchecked")
		public Look withDesign(java.util.function.UnaryOperator<Setting<?>> add, java.util.function.BooleanSupplier visible, double radiusDefault, String radiusId) {
			radius = (NumberSetting) add.apply(new NumberSetting(radiusId, "Glow radius", radiusDefault, 0.1, 2, 0.05).visibleWhen(visible));
			bodyTint.visibleWhen(() -> false); // replaced by Design opacity below
			add.apply(new com.blixu.blixuaddons.client.module.setting.SectionSetting("glow_design_header", "Design", "The animated pattern on top of it").visibleWhen(visible));
			designRows(add, visible, "");
			return this;
		}
	}

	private static final Look[] LOOKS = new Look[16];

	/** Adds a module's own glow rows and registers them under its id. {@code visible}: when the module uses the glow. */
	public static Look addLook(java.util.function.UnaryOperator<Setting<?>> add, int id, java.util.function.BooleanSupplier visible) {
		return addLook(add, id, visible, 0.5);
	}

	/** {@code tint}: default for how much of the glow lies over the object itself (0 = only around it). */
	public static Look addLook(java.util.function.UnaryOperator<Setting<?>> add, int id, java.util.function.BooleanSupplier visible, double tint) {
		return addLook(add, id, visible, tint, "Tint on the object");
	}

	public static Look addLook(java.util.function.UnaryOperator<Setting<?>> add, int id, java.util.function.BooleanSupplier visible, double tint, String tintLabel) {
		Look l = new Look(add, visible, tint, tintLabel);
		LOOKS[id] = l;
		return l;
	}

	public static @org.jspecify.annotations.Nullable Look look(int id) {
		return id >= 0 && id < LOOKS.length ? LOOKS[id] : null;
	}

	/**
	 * GlowConfig (std140): FillA[16] (rgb, w = animation speed), Outer[16] (rgb, w = use it),
	 * Look[16] (x = strength, y = fill on the object, z = radius scale, w = band design + fill design * 8),
	 * Global (y = Low-End Mode), FillB[16] (rgb, w = 1 when the module has its own fill colours).
	 */
	public static float[] uniforms() {
		float[] u = new float[65 * 4];
		for (int id = 0; id < 16; id++) {
			Look l = LOOKS[id];
			int o = l != null ? l.outer.argb() : 0;
			boolean ownFill = l != null && l.fillA != null && l.fillB != null;
			put(u, id, ownFill ? l.fillA.argb() : 0xFFFFFF, l != null && l.animSpeed != null ? l.animSpeed.getF() : 1f);
			put(u, 49 + id, ownFill ? l.fillB.argb() : 0xFFFFFF, ownFill ? 1 : 0);
			put(u, 16 + id, o, l != null && l.ownOuter.get() ? 1 : 0);
			u[(32 + id) * 4] = l != null ? l.strength.getF() : 1f;
			u[(32 + id) * 4 + 1] = l == null ? 0.5f : l.designOpacity != null ? l.designOpacity.getF() : l.bodyTint.getF();
			u[(32 + id) * 4 + 2] = l != null && l.radius != null ? l.radius.getF() : 1f;
			int band = l != null && l.bandDesign != null ? l.bandDesign.get().shader : 0;
			int fill = l != null && l.fillDesign != null ? l.fillDesign.get().shader : 0;
			u[(32 + id) * 4 + 3] = band + fill * 8;
		}
		u[48 * 4 + 1] = com.blixu.blixuaddons.client.module.impl.LowEndMode.lightGlow() ? 1f : 0f;
		return u;
	}

	private static void put(float[] u, int vec, int rgb, float w) {
		u[vec * 4] = ((rgb >> 16) & 255) / 255f;
		u[vec * 4 + 1] = ((rgb >> 8) & 255) / 255f;
		u[vec * 4 + 2] = (rgb & 255) / 255f;
		u[vec * 4 + 3] = w;
	}

	// ------------------------------------------------------------------ distance

	/** Aura size around glowing objects, in blocks (a world size, so it shrinks with distance like the object). */
	public static final float AURA_BLOCKS = 0.35f;

	/** DistConfig for the world glow: max radius (px), pixels per block at distance 1, near, far. */
	public static float[] distUniforms() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		float h = mc.getWindow().getHeight();
		float fov = mc.options.fov().get() * Mth.DEG_TO_RAD;
		float focal = h / (2f * (float) Math.tan(fov / 2f)); // pixels per block at 1 block away
		float far = Math.max(64f, mc.options.getEffectiveRenderDistance() * 16f * 4f);
		return new float[] {Math.min(64f, h / 18f), AURA_BLOCKS * focal, 0.05f, far};
	}
}
