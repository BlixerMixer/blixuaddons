package com.blixu.blixuaddons.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets our Ui toolkit queue custom shapes into Minecraft's GUI frame. */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
	@Accessor("guiRenderState")
	GuiRenderState blixuaddons$guiRenderState();
}
