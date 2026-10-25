package com.blixu.blixuaddons.client.mixin;

import com.blixu.blixuaddons.client.feature.slayer.ServerClock;
import com.blixu.blixuaddons.client.feature.slayer.VoidgloomTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slayer tracking hooks. The handlers first hop to the main thread (they throw on the network thread),
 * so TAIL only runs on the main thread, after the world has the update.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleSetTime", at = @At("TAIL"))
	private void blixuaddons$serverClock(ClientboundSetTimePacket packet, CallbackInfo ci) {
		ServerClock.onTimePacket(System.currentTimeMillis());
	}

	@Inject(method = "handleBlockUpdate", at = @At("TAIL"))
	private void blixuaddons$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		VoidgloomTracker.onBlock(packet.getPos(), packet.getBlockState());
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
	private void blixuaddons$blocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		if (VoidgloomTracker.boss != null) packet.runUpdates(VoidgloomTracker::onBlock);
	}
}
