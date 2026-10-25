package com.blixu.blixuaddons.client.feature;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.player.HandPose;
import com.blixu.blixuaddons.client.module.impl.player.ItemInspect;
import com.blixu.blixuaddons.client.module.impl.player.SwingAnimations;
import com.blixu.blixuaddons.client.render.GeoBatch;
import com.blixu.blixuaddons.client.render.Shape;
import com.blixu.blixuaddons.client.render.Shapes;
import com.blixu.blixuaddons.client.render.WorldPipelines;
import com.blixu.blixuaddons.client.style.Colors;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Random;

/**
 * Coordinates everything drawn in the first-person hand pass: hand pose, custom swings,
 * inspections, hand aura and swing trails. Geometry is collected in "base" space (the pose
 * at the start of the hand pass) and submitted once at the end of the pass.
 */
public final class FirstPersonFx {
	private FirstPersonFx() {}

	private static final Matrix4f BASE = new Matrix4f();
	private static final Matrix4f BASE_INV = new Matrix4f();
	private static final Matrix4f TMP = new Matrix4f();
	private static final Vector3f V = new Vector3f();
	private static final PoseStack SUBMIT = new PoseStack();
	private static GeoBatch GLOW = new GeoBatch(false, 40_000);
	private static GeoBatch FILL = new GeoBatch(false, 20_000);
	private static GeoBatch LINES = new GeoBatch(true, 8_000);
	private static final Vector3f RIGHT = new Vector3f(1, 0, 0);
	private static final Vector3f UP = new Vector3f(0, 1, 0);
	private static final Random RANDOM = new Random();

	// Item capture: set while our renderItem call runs; LayerRenderState.submit records the model matrix + quads.
	private static boolean capturing;
	private static boolean captured;
	private static final Matrix4f ITEM_POSE = new Matrix4f();
	private static List<BakedQuad> itemQuads = List.of();
	/** Pose before the swing transform, for afterimage copies. */
	private static final PoseStack.Pose PRE_SWING = new PoseStack().last();
	private static boolean preSwingValid;

	// Trail (main hand tip positions in base space)
	private static final int TRAIL_MAX = 48;
	private static final float[] TRAIL = new float[TRAIL_MAX * 3];
	private static final float[] TRAIL_AGE = new float[TRAIL_MAX];
	private static int trailCount;

	private static long lastNanos = System.nanoTime();
	private static float dt = 1 / 60f;
	private static float time;

	// ------------------------------------------------------------------ pass lifecycle

	public static void begin(PoseStack pose) {
		long now = System.nanoTime();
		dt = Math.min((now - lastNanos) / 1e9f, 0.1f);
		lastNanos = now;
		time += dt;
		BASE.set(pose.last().pose());
		BASE.invert(BASE_INV);
		GLOW.clear();
		FILL.clear();
		LINES.clear();
		SwingAnimations swing = ModuleManager.active(SwingAnimations.class);
		if (swing != null) swing.update(dt);
		ItemInspect inspect = ModuleManager.active(ItemInspect.class);
		if (inspect != null) inspect.update(dt);
		ageTrail();
	}

	public static void end(SubmitNodeCollector out) {
		drawTrail();
		SUBMIT.setIdentity();
		SUBMIT.last().pose().set(BASE);
		FILL.submit(out, SUBMIT, WorldPipelines.fill());
		GLOW.submit(out, SUBMIT, WorldPipelines.GLOW);
		LINES.submit(out, SUBMIT, WorldPipelines.lines());
	}

	// ------------------------------------------------------------------ pose hooks

	public static void afterPush(PoseStack pose, ItemStack stack, HumanoidArm arm) {
		HandPose hp = ModuleManager.active(HandPose.class);
		if (hp != null) hp.applyOffset(pose, ItemCategory.of(stack), arm);
		preSwingValid = false;
	}

	/** Replaces vanilla's swingArm when a custom style is active. Returns true if handled. */
	public static boolean swing(PoseStack pose, float vanillaAttack, int invert, ItemStack stack, boolean mainHand) {
		PRE_SWING.set(pose.last());
		preSwingValid = true;
		SwingAnimations s = ModuleManager.active(SwingAnimations.class);
		if (s == null || !mainHand) return false;
		return s.apply(pose, s.styleFor(ItemCategory.of(stack)), s.progress(), invert);
	}

	/** CLASSIC style keeps vanilla's motion but on our clock/easing. */
	public static float classicAttack(float vanillaAttack, boolean mainHand) {
		SwingAnimations s = ModuleManager.active(SwingAnimations.class);
		if (s == null || !mainHand) return vanillaAttack;
		return s.classicAttack(s.progress());
	}

	public static void beforeItem(PoseStack pose, ItemStack stack, HumanoidArm arm, boolean mainHand) {
		ItemCategory cat = ItemCategory.of(stack);
		int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
		HandPose hp = ModuleManager.active(HandPose.class);
		if (hp != null) hp.applyRotationScale(pose, cat, arm);
		ItemInspect inspect = ModuleManager.active(ItemInspect.class);
		if (mainHand && inspect != null && inspect.active()) inspect.apply(pose, cat, invert);
		capturing = true;
		captured = false;
	}

	public static boolean inspecting() {
		ItemInspect inspect = ModuleManager.active(ItemInspect.class);
		return inspect != null && inspect.active() && inspect.metallic.get();
	}

	public static int light(int vanilla) {
		HandPose hp = ModuleManager.active(HandPose.class);
		return hp != null && hp.emissive.get() ? 0xF000F0 : vanilla;
	}

	/** Called from the item layer submit while capturing: remembers where the model is drawn. */
	public static void captureItem(PoseStack.Pose pose, List<BakedQuad> quads) {
		if (!capturing || captured) return;
		ITEM_POSE.set(pose.pose());
		itemQuads = quads;
		captured = true;
	}

	public static boolean capturingItem() {
		return capturing;
	}

	public static void afterItem(PoseStack pose, ItemStack stack, HumanoidArm arm, boolean mainHand) {
		capturing = false;
		boolean haveModel = captured;
		captured = false;
		// Model matrix (model space 0..1 cube) in base space, or the hand anchor if no model was captured.
		Matrix4f m = TMP.set(BASE_INV).mul(haveModel ? ITEM_POSE : pose.last().pose());

		SwingAnimations s = ModuleManager.active(SwingAnimations.class);
		ItemInspect inspect = ModuleManager.active(ItemInspect.class);
		if (mainHand && s != null) {
			boolean inspectTrail = inspect != null && inspect.active() && inspect.trail.get();
			if ((s.trail.get() && s.swinging()) || inspectTrail) {
				point(m, haveModel ? 0.92f : 0f, haveModel ? 0.92f : 0.1f, haveModel ? 0.5f : -0.2f);
				pushTrail(V.x, V.y, V.z);
			}
			if (s.glow.getF() > 0 && s.swinging()) {
				float b = Mth.sin(s.progress() * Mth.PI) * s.glow.getF();
				point(m, 0.5f, 0.5f, 0.5f);
				Shapes.billboard(GLOW, V.x, V.y, V.z, RIGHT, UP, 0.35f * b + 0.05f, 0, Colors.withAlpha(s.glowColor.argb(), 0.55f * b), Shape.SOFT, 0, 1, 0);
			}
		}
	}

	/** Afterimage: extra copies of the item at earlier swing phases. */
	public static int afterimageCount(boolean mainHand) {
		SwingAnimations s = ModuleManager.active(SwingAnimations.class);
		if (s == null || !mainHand || !s.afterimage.get() || !s.swinging() || !preSwingValid) return 0;
		return s.afterimageCount.getI();
	}

	/** Puts {@code pose} back to before the swing and applies the swing at a lagged phase. */
	public static boolean prepareAfterimage(PoseStack pose, int index, ItemStack stack, HumanoidArm arm) {
		SwingAnimations s = ModuleManager.active(SwingAnimations.class);
		if (s == null) return false;
		float p = s.progress() - index * s.afterimageLag.getF();
		if (p <= 0) return false;
		pose.last().set(PRE_SWING);
		int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
		if (!s.apply(pose, s.styleFor(ItemCategory.of(stack)), p, invert)) return false;
		HandPose hp = ModuleManager.active(HandPose.class);
		if (hp != null) hp.applyRotationScale(pose, ItemCategory.of(stack), arm);
		return true;
	}

	/** Empty hand: aura along the arm. {@code pose} is where the arm model is about to be drawn. */
	public static void afterArm(PoseStack pose, HumanoidArm arm) {
		capturing = false;
		captured = false;

	}

	// ------------------------------------------------------------------ aura drawing

	private static void point(Matrix4f m, float x, float y, float z) {
		m.transformPosition(x, y, z, V);
	}

	// ------------------------------------------------------------------ trail

	private static void pushTrail(float x, float y, float z) {
		if (trailCount == TRAIL_MAX) {
			System.arraycopy(TRAIL, 3, TRAIL, 0, (TRAIL_MAX - 1) * 3);
			System.arraycopy(TRAIL_AGE, 1, TRAIL_AGE, 0, TRAIL_MAX - 1);
			trailCount--;
		}
		TRAIL[trailCount * 3] = x;
		TRAIL[trailCount * 3 + 1] = y;
		TRAIL[trailCount * 3 + 2] = z;
		TRAIL_AGE[trailCount] = 0f;
		trailCount++;
	}

	private static void ageTrail() {
		int keep = 0;
		for (int i = 0; i < trailCount; i++) {
			TRAIL_AGE[i] += dt;
			if (TRAIL_AGE[i] < 0.22f) {
				if (keep != i) {
					TRAIL[keep * 3] = TRAIL[i * 3];
					TRAIL[keep * 3 + 1] = TRAIL[i * 3 + 1];
					TRAIL[keep * 3 + 2] = TRAIL[i * 3 + 2];
					TRAIL_AGE[keep] = TRAIL_AGE[i];
				}
				keep++;
			}
		}
		trailCount = keep;
	}

	private static void drawTrail() {
		SwingAnimations s = ModuleManager.active(SwingAnimations.class);
		if (s == null || trailCount < 2) return;
		int len = Math.min(trailCount, s.trailLength.getI());
		int start = trailCount - len;
		float w = s.trailWidth.getF();
		for (int i = start + 1; i < trailCount; i++) {
			float t0 = (i - 1 - start) / (float) len, t1 = (i - start) / (float) len;
			float fade0 = t0 * (1 - TRAIL_AGE[i - 1] / 0.22f), fade1 = t1 * (1 - TRAIL_AGE[i] / 0.22f);
			int c0 = Colors.withAlpha(Colors.lerp(s.trailColor2.argb(), s.trailColor.argb(), t0), fade0 * 0.9f);
			int c1 = Colors.withAlpha(Colors.lerp(s.trailColor2.argb(), s.trailColor.argb(), t1), fade1 * 0.9f);
			ribbon(TRAIL[(i - 1) * 3], TRAIL[(i - 1) * 3 + 1], TRAIL[(i - 1) * 3 + 2], TRAIL[i * 3], TRAIL[i * 3 + 1], TRAIL[i * 3 + 2],
				w * t0, w * t1, c0, c1);
		}
	}

	private static void ribbon(float ax, float ay, float az, float bx, float by, float bz, float w1, float w2, int c1, int c2) {
		float dx = bx - ax, dy = by - ay;
		float len = Mth.sqrt(dx * dx + dy * dy);
		if (len < 1e-5f) return;
		// Perpendicular in the screen plane (base space ≈ view space).
		float px = -dy / len, py = dx / len;
		GLOW.quad(ax - px * w1, ay - py * w1, az, ax + px * w1, ay + py * w1, az, bx + px * w2, by + py * w2, bz, bx - px * w2, by - py * w2, bz, c1, c1, c2, c2);
	}

	// ---------------------------------------------------------------- menu previews

	private static @org.jspecify.annotations.Nullable Object previewing;
	private static float previewClock;

	/** While set, the given module animates on the preview clock instead of real swings/keys. */
	public static void setPreview(@org.jspecify.annotations.Nullable Object module, float time) {
		previewing = module;
		previewClock = time;
	}

	public static boolean previewing(Object module) {
		return previewing == module;
	}

	public static float previewTime() {
		return previewClock;
	}

	public static void reset() {
		trailCount = 0;
		capturing = false;
		captured = false;
	}
}
