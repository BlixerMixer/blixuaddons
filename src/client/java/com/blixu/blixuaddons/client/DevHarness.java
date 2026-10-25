package com.blixu.blixuaddons.client;

import com.blixu.blixuaddons.BlixuAddons;
import com.blixu.blixuaddons.client.gui.ClickGuiScreen;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Development-only screenshot runner, inactive unless started with -Dblixu.shot=id1,id2
 * (or "editor"). Opens the menu on each module and saves run/screenshots/preview_ID.png,
 * so menu previews can be checked without clicking through by hand.
 */
public final class DevHarness {
	private DevHarness() {}

	private static int ticks;
	private static int index;

	public static void tick(Minecraft mc) {
		String spec = System.getProperty("blixu.shot");
		if (spec == null || mc.level == null || mc.player == null) return;
		String[] ids = spec.split(",");
		if (index >= ids.length) return;
		ticks++;
		String id = ids[index];
		if (id.startsWith("glow:")) {
			// glow:<STYLE> — Block Overlay "Highlight glow" in orange on the block below, in that shared glow style
			if (ticks == 1) {
				var bo = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.world.BlockOverlay.class);
				bo.setEnabled(true);
				bo.style.set(com.blixu.blixuaddons.client.module.impl.world.BlockOverlay.Style.HIGHLIGHT);
				bo.outline.set(0xFFFF7A1A);
				mc.gui.setScreen(null);
			}
			mc.player.setXRot(55f);
			mc.player.xRotO = 55f;
		} else if (id.startsWith("cosmic:")) {
			// cosmic:<time>:<pitch> - Cosmic Sky on, camera held level-ish facing north
			String[] q = id.split(":");
			if (ticks == 1) {
				ModuleManager.get(com.blixu.blixuaddons.client.module.impl.world.CosmicSky.class).setEnabled(true);
				mc.player.connection.sendCommand("time set " + q[1]);
				mc.gui.setScreen(null);
			}
			mc.player.setYRot(180f);
			mc.player.yRotO = 180f;
			mc.player.setXRot(Float.parseFloat(q[2]));
			mc.player.xRotO = mc.player.getXRot();
		} else if (id.startsWith("sky:")) {
			// sky:<STYLE>:<pitch> - Cosmic Sky in that style at noon, diagnostics panel on, facing north
			String[] q = id.split(":");
			if (ticks == 1) {
				var cs = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.world.CosmicSky.class);
				cs.setEnabled(true);
				cs.style.set(com.blixu.blixuaddons.client.module.impl.world.CosmicSky.Style.valueOf(q[1]));
				ModuleManager.get(com.blixu.blixuaddons.client.module.impl.PerformanceEngine.class).diagnostics.set(true);
				mc.player.connection.sendCommand("time set 6000");
				mc.gui.setScreen(null);
			}
			mc.player.setYRot(180f);
			mc.player.yRotO = 180f;
			mc.player.setXRot(Float.parseFloat(q[2]));
			mc.player.xRotO = mc.player.getXRot();
		} else if (id.startsWith("hand:")) {
			// hand:<STYLE> - Hand Aura in that style, looking slightly down
			if (ticks == 1) {
				var ha = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.player.HandAura.class);
				ha.setEnabled(true);
				ha.style.set(com.blixu.blixuaddons.client.feature.GlowStyle.Design.valueOf(id.substring(5)));
				mc.gui.setScreen(null);
			}
			mc.player.setXRot(20f);
			mc.player.xRotO = 20f;
		} else if (id.startsWith("self:")) {
			// self:<DESIGN> - Self Aura in that design, third person from behind
			if (ticks == 1) {
				var sa = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.player.SelfAura.class);
				sa.setEnabled(true);
				sa.glowLook.fillDesign.set(com.blixu.blixuaddons.client.feature.GlowStyle.Design.valueOf(id.substring(5)));
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
				mc.gui.setScreen(null);
			}
			if (ticks == 115) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			mc.player.setXRot(15f);
			mc.player.xRotO = 15f;
		} else if (id.startsWith("mob:")) {
			// mob:<DESIGN> - an enderman 3 blocks ahead in Mob Highlight with that fill design
			if (ticks == 1) {
				var mh = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.MobHighlight.class);
				mh.setEnabled(true);
				mh.names.set("Enderman");
				mh.look.fillDesign.set(com.blixu.blixuaddons.client.feature.GlowStyle.Design.valueOf(id.substring(4)));
				mc.player.connection.sendCommand("weather clear"); // rain makes endermen teleport away
				mc.player.connection.sendCommand("kill @e[type=enderman]");
				mc.player.connection.sendCommand("execute rotated ~ 0 run summon enderman ^ ^ ^3 {NoAI:1b}");
				mc.gui.setScreen(null);
			}
			mc.player.setXRot(10f);
			mc.player.xRotO = 10f;
		} else if (id.equals("dmg")) {
			// fake Hypixel damage stands: 14 in one spot (thinning) plus a crit
			if (ticks == 1) {
				ModuleManager.get(com.blixu.blixuaddons.client.module.impl.combat.DamageTags.class).setEnabled(true);
				mc.gui.setScreen(null);
			}
			if (ticks >= 95 && ticks < 109) {
				// chat commands can't carry the section sign, so the names are plain (real stands are coloured)
				String name = ticks == 95 ? "\u2727123,456\u2727" : String.format(java.util.Locale.ROOT, "%,d", 1000 + ticks * 731);
				mc.player.connection.sendCommand("summon armor_stand ^ ^ ^5 {Invisible:1b,NoGravity:1b,Marker:1b,CustomNameVisible:1b,CustomName:\"" + name + "\"}");
			}
			if (ticks == 118) mc.player.connection.sendCommand("kill @e[type=armor_stand]");
			mc.player.setXRot(-35f); // up at open sky, so nothing blocks the line of sight
			mc.player.xRotO = -35f;
		} else if (id.startsWith("knife:")) {
			// knife:<STYLE>:<progress> - diamond sword, that CS inspect, screenshot at that point of it
			String[] q = id.split(":");
			var ii = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.player.ItemInspect.class);
			if (ticks == 1) {
				ii.setEnabled(true);
				ii.perItem.set(false);
				ii.cancelAction.set(false);
				ii.style.set(com.blixu.blixuaddons.client.module.impl.player.ItemInspect.Style.valueOf(q[1]));
				mc.player.connection.sendCommand("item replace entity @s weapon.mainhand with diamond_sword");
				mc.gui.setScreen(null);
			}
			int lead = Math.round(Float.parseFloat(q[2]) * ii.style.get().knife.seconds / ii.knifeSpeed.getF() * 20f);
			if (ticks == 110 - lead) ii.start(mc.player);
			mc.player.setXRot(10f);
			mc.player.xRotO = 10f;
		} else if (id.startsWith("look:")) {
			// look:<RELIC>:<HAT> - Floating Relics model + Cosmetics hat, third person from the front
			String[] q = id.split(":");
			if (ticks == 1) {
				var fr = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.cosmetic.FloatingRelics.class);
				fr.setEnabled(true);
				fr.model.set(com.blixu.blixuaddons.client.module.impl.cosmetic.FloatingRelics.Model.valueOf(q[1]));
				var co = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.cosmetic.Cosmetics.class);
				co.setEnabled(true);
				co.hat.set(com.blixu.blixuaddons.client.module.impl.cosmetic.Cosmetics.Hat.valueOf(q[2]));
				ModuleManager.get(com.blixu.blixuaddons.client.module.impl.player.SelfAura.class).setEnabled(false); // keep the head readable
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
				mc.gui.setScreen(null);
			}
			if (ticks == 115) mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			mc.player.setXRot(20f);
			mc.player.xRotO = 20f;
		} else if (id.startsWith("traj:")) {
			// traj:term / traj:bow - Trajectories with a bow named Terminator (3 arrows) or a plain bow
			if (ticks == 1) {
				ModuleManager.get(com.blixu.blixuaddons.client.module.impl.projectile.Trajectories.class).setEnabled(true);
				ModuleManager.get(com.blixu.blixuaddons.client.module.impl.player.HandAura.class).setEnabled(false);
				mc.player.connection.sendCommand(id.endsWith("term")
					? "item replace entity @s weapon.mainhand with bow[custom_name=\"Terminator\"]"
					: "item replace entity @s weapon.mainhand with bow");
				mc.gui.setScreen(null);
			}
			mc.player.setXRot(-30f); // over the tall grass
			mc.player.xRotO = -30f;
		} else if (id.startsWith("aa:")) {
			// aa:on / aa:off - same view, anti-aliasing toggled, for a pixel comparison
			if (ticks == 1) {
				ModuleManager.get(com.blixu.blixuaddons.client.module.impl.world.AntiAliasing.class).setEnabled(id.endsWith("on"));
				mc.gui.setScreen(null);
			}
			mc.player.setYRot(180f);
			mc.player.yRotO = 180f;
			mc.player.setXRot(10f);
			mc.player.xRotO = 10f;
		} else if (id.equals("aapair")) {
			// AA on at tick 110 (normal grab below), off right after, second grab two ticks later
			var aa = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.world.AntiAliasing.class);
			if (ticks == 1) {
				aa.setEnabled(true);
				mc.gui.setScreen(null);
			}
			if (ticks == 111) aa.setEnabled(false);
			if (ticks == 113) Screenshot.grab(mc.gameDirectory, "preview_aapair_off.png", mc.gameRenderer.mainRenderTarget(), 1, c -> {});
			mc.player.setXRot(10f);
			mc.player.xRotO = 10f;
		} else if (id.startsWith("cosmic:")) {
			// cosmic:<time>:<pitch> - Cosmic Sky on, camera held level-ish facing north
			String[] q = id.split(":");
			if (ticks == 1) {
				ModuleManager.get(com.blixu.blixuaddons.client.module.impl.world.CosmicSky.class).setEnabled(true);
				mc.player.connection.sendCommand("time set " + q[1]);
				mc.gui.setScreen(null);
			}
			mc.player.setYRot(180f);
			mc.player.yRotO = 180f;
			mc.player.setXRot(Float.parseFloat(q[2]));
			mc.player.xRotO = mc.player.getXRot();
		} else if (id.startsWith("aa:")) {
			// aa:on / aa:off - same view, anti-aliasing toggled, for a pixel comparison
			if (ticks == 1) {
				ModuleManager.get(com.blixu.blixuaddons.client.module.impl.world.AntiAliasing.class).setEnabled(id.endsWith("on"));
				mc.gui.setScreen(null);
			}
			mc.player.setYRot(180f);
			mc.player.yRotO = 180f;
			mc.player.setXRot(10f);
			mc.player.xRotO = 10f;
		} else if (id.equals("spin")) {
			if (ticks == 1) {
				var sp = ModuleManager.get(com.blixu.blixuaddons.client.module.impl.cosmetic.PlayerSpin.class);
				sp.setEnabled(true);
				sp.crouchSpam.set(true);
			}
			if (ticks == 60) mc.gui.setScreen(new ClickGuiScreen(true));
		} else if (id.equals("slayers")) {
			if (ticks == 60) {
				try {
					ClickGuiScreen gui = new ClickGuiScreen();
					var f = ClickGuiScreen.class.getDeclaredField("category");
					f.setAccessible(true);
					f.set(gui, com.blixu.blixuaddons.client.module.Category.SLAYERS);
					mc.gui.setScreen(gui);
				} catch (ReflectiveOperationException ex) {
					BlixuAddons.LOGGER.warn("harness", ex);
				}
			}
		} else if (id.equals("menu")) {
			if (ticks == 60) mc.gui.setScreen(new ClickGuiScreen());
		} else if (ticks == 60) open(mc, id);
		if (ticks == 110) {
			Screenshot.grab(mc.gameDirectory, "preview_" + id.replace(':', '_') + ".png", mc.gameRenderer.mainRenderTarget(), 1, c -> BlixuAddons.LOGGER.info("shot {}", id));
		}
		if (ticks == 120 + (id.equals("aapair") ? 10 : 0)) {
			mc.gui.setScreen(null);
			ticks = 0;
			index++;
		}
	}

	private static void open(Minecraft mc, String id) {
		if (id.equals("editor")) {
			mc.gui.setScreen(new ClickGuiScreen(true));
			return;
		}
		if (id.equals("world_editor")) {
			mc.gui.setScreen(new ClickGuiScreen(false, true));
			return;
		}
		for (Module m : ModuleManager.all()) {
			if (!m.id().equals(id)) continue;
			try {
				ClickGuiScreen gui = new ClickGuiScreen();
				var f = ClickGuiScreen.class.getDeclaredField("category");
				f.setAccessible(true);
				f.set(gui, m.category());
				var q = ClickGuiScreen.class.getDeclaredField("search");
				q.setAccessible(true);
				q.set(gui, m.name());
				var e = ClickGuiScreen.class.getDeclaredField("expanded");
				e.setAccessible(true);
				e.set(gui, m);
				mc.gui.setScreen(gui);
			} catch (ReflectiveOperationException ex) {
				BlixuAddons.LOGGER.warn("harness", ex);
			}
		}
	}
}
