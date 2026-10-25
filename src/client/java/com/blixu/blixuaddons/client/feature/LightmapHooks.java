package com.blixu.blixuaddons.client.feature;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.world.AntiDebuff;
import com.blixu.blixuaddons.client.module.impl.world.Fullbright;
import net.minecraft.client.renderer.state.LightmapRenderState;

public final class LightmapHooks {
	private LightmapHooks() {}

	public static void apply(LightmapRenderState state) {
		if (!state.needsUpdate) return;
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		if (a != null && a.darkness.getF() > 0) {
			float s = a.darkness.getF();
			state.darknessEffectScale *= 1f - s;
			float gamma = net.minecraft.client.Minecraft.getInstance().options.gamma().get().floatValue();
			state.brightness = net.minecraft.util.Mth.lerp(s, state.brightness, Math.max(state.brightness, gamma));
		}
		Fullbright fb = ModuleManager.active(Fullbright.class);
		if (fb != null) fb.applyLightmap(state);
	}
}
