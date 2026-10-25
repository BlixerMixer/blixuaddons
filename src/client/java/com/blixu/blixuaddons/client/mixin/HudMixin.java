package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.hud.HudOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws our HUD on top of the vanilla one. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void blixuaddons$drawHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!((Hud) (Object) this).isHidden()) {
			HudOverlay.render(graphics, deltaTracker);
		}
	}
}
