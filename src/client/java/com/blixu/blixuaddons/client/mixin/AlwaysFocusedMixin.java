package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.AlwaysFocused;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Always Focused: losing window focus doesn't pause the game or open the pause menu. */
@Mixin(Minecraft.class)
public abstract class AlwaysFocusedMixin {
	@Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
	private void blixuaddons$alwaysFocused(CallbackInfo ci) {
		if (ModuleManager.active(AlwaysFocused.class) != null) ci.cancel();
	}
}
