package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.camera.CameraTweaks;
import com.blixu.blixuaddons.client.module.impl.camera.Freelook;
import com.blixu.blixuaddons.client.module.impl.world.AntiDebuff;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camera-only changes: freelook offset, roll/lean, FOV + zoom, third-person distance and
 * bobbing intensity. The entity rotation the server knows is never touched here.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow @Final private Quaternionf rotation;
	@Shadow @Final private Vector3f forwards;
	@Shadow @Final private Vector3f up;
	@Shadow @Final private Vector3f left;
	@Shadow private int matrixPropertiesDirty;
	@Shadow @Final private static Vector3fc FORWARDS;
	@Shadow @Final private static Vector3fc UP;
	@Shadow @Final private static Vector3fc LEFT;

	@Inject(method = "update", at = @At("HEAD"))
	private void blixuaddons$tick(DeltaTracker deltaTracker, CallbackInfo ci) {
		Freelook f = ModuleManager.find(Freelook.class);
		if (f != null && f.isEnabled()) f.update();
	}

	@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0))
	private void blixuaddons$freelookCart(Camera self, float yRot, float xRot, Operation<Void> original) {
		original.call(self, yRot + blixuaddons$yaw(), Mth.clamp(xRot + blixuaddons$pitch(), -90f, 90f));
	}

	@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
	private void blixuaddons$freelook(Camera self, float yRot, float xRot, Operation<Void> original) {
		original.call(self, yRot + blixuaddons$yaw(), Mth.clamp(xRot + blixuaddons$pitch(), -90f, 90f));
	}

	@Unique
	private static float blixuaddons$yaw() {
		Freelook f = ModuleManager.find(Freelook.class);
		return f != null && f.showing() ? f.yawOffset() : 0f;
	}

	@Unique
	private static float blixuaddons$pitch() {
		Freelook f = ModuleManager.find(Freelook.class);
		return f != null && f.showing() ? f.pitchOffset() : 0f;
	}

	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
	private float blixuaddons$distance(float cameraDist) {
		CameraTweaks t = ModuleManager.active(CameraTweaks.class);
		return t == null ? cameraDist : cameraDist * t.distance.getF() / 4f;
	}

	@Shadow protected abstract void setRotation(float yRot, float xRot);
	@Shadow protected abstract void setPosition(double x, double y, double z);
	@Shadow protected abstract void move(float forwards, float up, float right);
	@Shadow private float getMaxZoom(float cameraDist) { throw new AssertionError(); }

	/** Player Visual Editor: orbit around the player (wall-aware via the vanilla zoom check). */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void blixuaddons$editorOrbit(float partialTicks, CallbackInfo ci) {
		if (!com.blixu.blixuaddons.client.feature.PlayerEditorCamera.active()) return;
		var p = net.minecraft.client.Minecraft.getInstance().player;
		double x = Mth.lerp(partialTicks, p.xo, p.getX());
		double y = Mth.lerp(partialTicks, p.yo, p.getY()) + p.getBbHeight() * 0.62;
		double z = Mth.lerp(partialTicks, p.zo, p.getZ());
		setPosition(x, y, z);
		setRotation(com.blixu.blixuaddons.client.feature.PlayerEditorCamera.yaw(), 12f);
		move(-getMaxZoom(3.2f), 0f, 0f);
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void blixuaddons$roll(float partialTicks, CallbackInfo ci) {
		float roll = 0f;
		Freelook f = ModuleManager.find(Freelook.class);
		if (f != null && f.showing()) roll += f.rollDegrees();
		CameraTweaks t = ModuleManager.active(CameraTweaks.class);
		if (t != null) roll += t.leanDegrees();
		if (Math.abs(roll) < 0.01f) return;
		rotation.rotateZ(roll * Mth.DEG_TO_RAD);
		FORWARDS.rotate(rotation, forwards);
		UP.rotate(rotation, up);
		LEFT.rotate(rotation, left);
		matrixPropertiesDirty |= 3;
	}

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void blixuaddons$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		CameraTweaks t = ModuleManager.active(CameraTweaks.class);
		if (t != null) cir.setReturnValue(t.fov(cir.getReturnValueF()));
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void blixuaddons$bobAndSky(CameraRenderState state, float partialTicks, CallbackInfo ci) {
		CameraTweaks t = ModuleManager.active(CameraTweaks.class);
		AntiDebuff a = ModuleManager.active(AntiDebuff.class);
		float bob = (t != null ? t.bob() : 1f) * (a != null ? AntiDebuff.keep(a.bobbing) : 1f);
		state.entityRenderState.bob *= bob;
		if (a != null && state.entityRenderState.doesMobEffectBlockSky && Math.max(a.blindness.getF(), a.darkness.getF()) > 0.5f) {
			state.entityRenderState.doesMobEffectBlockSky = false;
		}
	}
}
