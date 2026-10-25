package com.blixu.blixuaddons.client;

import com.blixu.blixuaddons.BlixuAddons;
import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.gui.ClickGuiScreen;
import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.impl.MobHighlight;
import com.blixu.blixuaddons.client.feature.CombatEvents;
import com.blixu.blixuaddons.client.render.VisualsHooks;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class BlixuAddonsClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(BlixuAddons.id("main"));

	private static KeyMapping openMenu;

	/** The menu key (a normal Minecraft key binding, so it also shows in Controls). */
	public static KeyMapping openMenuKey() {
		return openMenu;
	}
	private static KeyMapping toggleEndermanGlow;
	private static int saveTicks;
	private static boolean openMenuNextTick;

	/** Tab completion for /blixutestraredrop: every drop in the table, matched on what's typed so far. */
	private static final com.mojang.brigadier.suggestion.SuggestionProvider<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> DROP_NAMES =
		(ctx, builder) -> {
			String typed = builder.getRemainingLowerCase();
			for (var d : com.blixu.blixuaddons.client.feature.slayer.SlayerDrops.DROPS) {
				if (d.name().toLowerCase(java.util.Locale.ROOT).startsWith(typed)) builder.suggest(d.name());
			}
			return builder.buildFuture();
		};

	@Override
	public void onInitializeClient() {
		ModuleManager.init();
		ConfigManager.load();
		try {
			com.blixu.blixuaddons.client.feature.slayer.VoidgloomTracker.selfCheck();
			com.blixu.blixuaddons.client.render.PixelFont.selfCheck();
			com.blixu.blixuaddons.client.feature.slayer.SlayerDrops.selfCheck();
			com.blixu.blixuaddons.client.feature.perf.OcclusionCuller.selfCheck();
			com.blixu.blixuaddons.client.module.impl.combat.DamageTags.selfCheck();
			com.blixu.blixuaddons.client.feature.KnifeInspects.selfCheck();
		} catch (IllegalStateException e) {
			com.blixu.blixuaddons.BlixuAddons.LOGGER.error("blixuAddons self-check failed (the game keeps running)", e);
		}
		VisualsHooks.init();
		net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(ctx -> new com.blixu.blixuaddons.client.gui.preview.PreviewRenderer());
		net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(ctx -> new com.blixu.blixuaddons.client.gui.preview.ItemSpinRenderer());

		openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.blixuaddons.open_menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			CATEGORY
		));
		toggleEndermanGlow = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.blixuaddons.toggle_enderman_glow",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_G,
			CATEGORY
		));

		// rare slayer drops are announced in chat
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) ModuleManager.get(com.blixu.blixuaddons.client.module.impl.slayer.RareDropAlert.class).onChat(message.getString());
		});

		// /blixu opens the menu (next tick: the chat screen closes itself after a command)
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("blixu").executes(ctx -> {
				openMenuNextTick = true;
				return 1;
			}));
			// /blixutestraredrop [item name]: play the rare drop animation (random drop from the table without a name)
			var rare = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.slayer.RareDropAlert.class);
			// /blixutestraredrop [screen|hud] [item]: screen / hud force that look for the test
			var testCmd = net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("blixutestraredrop")
				.executes(ctx -> {
					rare.test(null);
					return 1;
				})
				.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).suggests(DROP_NAMES)
					.executes(ctx -> {
						rare.test(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item"));
						return 1;
					}));
			for (var look : new String[] {"screen", "hud"}) {
				var style = look.equals("screen") ? com.blixu.blixuaddons.client.module.impl.slayer.RareDropAlert.Style.FULL
					: com.blixu.blixuaddons.client.module.impl.slayer.RareDropAlert.Style.HOTBAR;
				testCmd.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal(look)
					.executes(ctx -> {
						rare.test(null, style);
						return 1;
					})
					.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("item", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).suggests(DROP_NAMES)
						.executes(ctx -> {
							rare.test(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item"), style);
							return 1;
						})));
			}
			dispatcher.register(testCmd);
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openMenuNextTick) {
				openMenuNextTick = false;
				client.gui.setScreen(new ClickGuiScreen());
			}
			while (openMenu.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(new ClickGuiScreen());
				}
			}
			while (toggleEndermanGlow.consumeClick()) {
				MobHighlight highlight = ModuleManager.get(MobHighlight.class);
				highlight.toggle();
				if (client.player != null) {
					client.player.sendOverlayMessage(Component.literal("Mob Highlight: ")
						.append(highlight.isEnabled()
							? Component.literal("ON").withStyle(ChatFormatting.LIGHT_PURPLE)
							: Component.literal("OFF").withStyle(ChatFormatting.GRAY)));
				}
			}
			ModuleManager.tick(client);
			CombatEvents.tick(client);
			DevHarness.tick(client);
			if (++saveTicks >= 20) {
				saveTicks = 0;
				ConfigManager.flush();
			}
		});
	}
}
