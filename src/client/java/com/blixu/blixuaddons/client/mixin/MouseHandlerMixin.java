package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.camera.CameraTweaks;
import com.blixu.blixuaddons.client.module.impl.camera.Freelook;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Freelook: mouse movement turns the camera offset instead of the player. Zoom: slower turning while zoomed. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void blixuaddons$turn(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		CameraTweaks t = ModuleManager.active(CameraTweaks.class);
		double k = t != null ? t.sensitivity() : 1.0;
		Freelook f = ModuleManager.find(Freelook.class);
		if (f != null && f.capturing()) {
			f.turn(dx * k, dy * k);
			return;
		}
		original.call(player, dx * k, dy * k);
	}
}
