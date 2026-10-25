package com.blixu.blixuaddons.client.feature.perf;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Which performance mods are installed, so we never run a second copy of what they already do
 * (two entity cullers, two batchers...), and which graphics backend Blaze3D is on.
 */
public final class PerfCompat {
	private PerfCompat() {}

	public static final boolean SODIUM = loaded("sodium");
	public static final boolean ENTITY_CULLING = loaded("entityculling");
	public static final boolean IMMEDIATELY_FAST = loaded("immediatelyfast");
	public static final boolean FERRITE_CORE = loaded("ferritecore");
	public static final boolean LITHIUM = loaded("lithium");
	public static final boolean BAD_OPTIMIZATIONS = loaded("badoptimizations");
	public static final boolean MORE_CULLING = loaded("moreculling");
	public static final boolean SODIUM_EXTRA = loaded("sodium-extra");
	public static final boolean SCALABLE_LUX = loaded("scalablelux");

	private static String backend;

	private static boolean loaded(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}

	/** "OpenGL · <GPU>" / "Vulkan · <GPU>", straight from the active GPU device. */
	public static String backend() {
		if (backend == null) {
			try {
				var info = RenderSystem.getDevice().getDeviceInfo();
				backend = info.backendName() + " · " + info.name();
			} catch (RuntimeException e) {
				return "starting";
			}
		}
		return backend;
	}

	public static List<String> installed() {
		List<String> out = new ArrayList<>();
		if (SODIUM) out.add("Sodium");
		if (ENTITY_CULLING) out.add("EntityCulling");
		if (IMMEDIATELY_FAST) out.add("ImmediatelyFast");
		if (FERRITE_CORE) out.add("FerriteCore");
		if (LITHIUM) out.add("Lithium");
		if (BAD_OPTIMIZATIONS) out.add("BadOptimizations");
		if (MORE_CULLING) out.add("MoreCulling");
		if (SODIUM_EXTRA) out.add("Sodium Extra");
		if (SCALABLE_LUX) out.add("ScalableLux");
		return out;
	}
}
