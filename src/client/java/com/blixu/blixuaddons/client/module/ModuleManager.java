package com.blixu.blixuaddons.client.module;

import com.blixu.blixuaddons.BlixuAddons;
import com.blixu.blixuaddons.client.module.impl.ActiveModulesHud;
import com.blixu.blixuaddons.client.module.impl.MobHighlight;
import com.blixu.blixuaddons.client.module.impl.ToggleSprint;
import com.blixu.blixuaddons.client.module.impl.hud.ConfigProfiles;
import com.blixu.blixuaddons.client.module.impl.hud.HudStyle;
import com.blixu.blixuaddons.client.module.impl.hud.PaletteEditor;
import com.blixu.blixuaddons.client.module.impl.combat.DamageTags;
import com.blixu.blixuaddons.client.module.impl.combat.HitColor;
import com.blixu.blixuaddons.client.module.impl.combat.HitParticles;
import com.blixu.blixuaddons.client.module.impl.combat.Tracers;
import com.blixu.blixuaddons.client.module.impl.projectile.Trajectories;
import com.blixu.blixuaddons.client.module.impl.world.BlockEsp;
import com.blixu.blixuaddons.client.module.impl.world.BlockOverlay;
import com.blixu.blixuaddons.client.module.impl.cosmetic.Cosmetics;
import com.blixu.blixuaddons.client.module.impl.cosmetic.FloatingRelics;
import com.blixu.blixuaddons.client.module.impl.cosmetic.PlayerTrails;
import com.blixu.blixuaddons.client.module.impl.player.HandAura;
import com.blixu.blixuaddons.client.module.impl.player.SelfAura;
import com.blixu.blixuaddons.client.module.impl.player.HandPose;
import com.blixu.blixuaddons.client.module.impl.player.ItemInspect;
import com.blixu.blixuaddons.client.module.impl.player.SwingAnimations;
import com.blixu.blixuaddons.client.module.impl.camera.CameraTweaks;
import com.blixu.blixuaddons.client.module.impl.camera.Freelook;
import com.blixu.blixuaddons.client.module.impl.world.AntiDebuff;
import com.blixu.blixuaddons.client.module.impl.world.Atmosphere;
import com.blixu.blixuaddons.client.module.impl.world.ColorGrading;
import com.blixu.blixuaddons.client.module.impl.world.Fullbright;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Holds every module. Register new features in {@link #init()}. */
public final class ModuleManager {
	private ModuleManager() {}

	private static final List<Module> MODULES = new ArrayList<>();
	private static final Map<Class<?>, Module> BY_TYPE = new HashMap<>();
	/** Modules whose per-frame hook threw: logged once, then skipped so one bug can't spam or crash rendering. */
	private static final Set<Module> BROKEN = new HashSet<>();

	public static void init() {
		register(new MobHighlight());
		register(new ActiveModulesHud());
		register(new ToggleSprint());
		// HUD & shared style
		register(new HudStyle());
		register(new PaletteEditor());
		register(new ConfigProfiles());
		// Player (first person)
		register(new HandPose());
		register(new SwingAnimations());
		register(new ItemInspect());
		register(new HandAura());
		register(new SelfAura());
		// Combat visuals
		register(new HitColor());
		register(new HitParticles());
		register(new DamageTags());
		register(new Tracers());
		// Projectiles
		register(new Trajectories());
		// Cosmetics
		register(new PlayerTrails());
		register(new FloatingRelics());
		register(new Cosmetics());
		register(new com.blixu.blixuaddons.client.module.impl.world.CosmicSky());
		register(new com.blixu.blixuaddons.client.module.impl.PerformanceEngine());
		register(new com.blixu.blixuaddons.client.module.impl.world.AntiAliasing());
		register(new com.blixu.blixuaddons.client.module.impl.LowEndMode());
		register(new com.blixu.blixuaddons.client.module.impl.AlwaysFocused());
		register(new com.blixu.blixuaddons.client.module.impl.slayer.VoidgloomVisuals());
		register(new com.blixu.blixuaddons.client.module.impl.slayer.RareDropAlert());
		register(new com.blixu.blixuaddons.client.module.impl.cosmetic.PlayerSpin());
		// World
		register(new ColorGrading());
		register(new Fullbright());
		register(new Atmosphere());
		register(new AntiDebuff());
		register(new BlockEsp());
		register(new BlockOverlay());
		// Camera
		register(new Freelook());
		register(new CameraTweaks());
	}

	private static void register(Module module) {
		module.finishSettings();
		MODULES.add(module);
		BY_TYPE.put(module.getClass(), module);
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(MODULES);
	}

	public static List<Module> in(Category category) {
		return MODULES.stream().filter(m -> m.category() == category).toList();
	}

	public static <T extends Module> T get(Class<T> type) {
		T m = find(type);
		if (m == null) throw new IllegalStateException("Module not registered: " + type.getSimpleName());
		return m;
	}

	/** Like {@link #get} but null before init (safe from mixins that can run early). */
	public static <T extends Module> @Nullable T find(Class<T> type) {
		Module m = BY_TYPE.get(type);
		return m != null ? type.cast(m) : null;
	}

	/** Shortcut for mixins: the module if registered and switched on, else null. */
	public static <T extends Module> @Nullable T active(Class<T> type) {
		T m = find(type);
		return m != null && m.isEnabled() ? m : null;
	}

	public static void tick(Minecraft client) {
		for (Module m : MODULES) {
			if (m.toggleKey.consumePress()) m.toggle();
			if (m.isEnabled()) run(m, mod -> mod.onTick(client));
		}
	}

	public static void worldChange() {
		for (Module m : MODULES) run(m, Module::onWorldChange);
		com.blixu.blixuaddons.client.feature.CombatEvents.clear();
		com.blixu.blixuaddons.client.feature.EntityVisuals.clearCaches();
		com.blixu.blixuaddons.client.feature.FirstPersonFx.reset();
		BROKEN.clear();
	}

	/** Calls {@code action} on every enabled module, isolating failures per module. */
	public static void forEnabled(Consumer<Module> action) {
		for (Module m : MODULES) {
			if (m.isEnabled() && !BROKEN.contains(m)) run(m, action);
		}
	}

	private static void run(Module m, Consumer<Module> action) {
		try {
			action.accept(m);
		} catch (RuntimeException e) {
			if (BROKEN.add(m)) BlixuAddons.LOGGER.error("Module {} failed; its effects are paused until the world changes", m.name(), e);
		}
	}
}
