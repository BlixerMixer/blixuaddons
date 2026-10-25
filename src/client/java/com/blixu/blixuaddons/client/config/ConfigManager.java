package com.blixu.blixuaddons.client.config;

import com.blixu.blixuaddons.BlixuAddons;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.ModuleManager;
import com.blixu.blixuaddons.client.module.setting.Setting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Saves which modules are on and their settings to config/blixuaddons.json.
 * Named profiles live in config/blixuaddons_profiles/NAME.json (same format).
 * Unknown modules/settings in the file are kept, so older/newer versions don't wipe each other.
 */
public final class ConfigManager {
	private ConfigManager() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("blixuaddons.json");
	private static final Path PROFILES = FabricLoader.getInstance().getConfigDir().resolve("blixuaddons_profiles");
	private static boolean loading;
	/** What was in the file, so entries we don't know about survive a save. */
	private static JsonObject lastRead = new JsonObject();

	public static void load() {
		if (!Files.exists(FILE)) {
			save();
			return;
		}
		lastRead = read(FILE);
		apply(lastRead);
	}

	private static JsonObject read(Path file) {
		try {
			JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
		} catch (IOException | RuntimeException e) {
			BlixuAddons.LOGGER.warn("Could not read {}, using defaults", file, e);
			return new JsonObject();
		}
	}

	private static void apply(JsonObject root) {
		loading = true;
		try {
			JsonObject modules = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : new JsonObject();
			for (Module module : ModuleManager.all()) {
				if (!modules.has(module.id()) || !modules.get(module.id()).isJsonObject()) continue;
				JsonObject m = modules.getAsJsonObject(module.id());
				try {
					if (m.has("enabled")) module.loadEnabled(m.get("enabled").getAsBoolean());
				} catch (RuntimeException ignored) {
				}
				JsonObject settings = m.has("settings") && m.get("settings").isJsonObject() ? m.getAsJsonObject("settings") : new JsonObject();
				for (Setting<?> s : module.settings()) {
					JsonElement v = settings.get(s.id());
					if (v != null && !v.isJsonNull()) s.fromJson(v);
				}
				JsonElement key = settings.get(module.toggleKey.id());
				if (key != null && !key.isJsonNull()) module.toggleKey.fromJson(key);
			}
		} finally {
			loading = false;
		}
	}

	private static JsonObject snapshot() {
		JsonObject root = lastRead.deepCopy();
		JsonObject modules = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : new JsonObject();
		for (Module module : ModuleManager.all()) {
			JsonObject m = modules.has(module.id()) && modules.get(module.id()).isJsonObject() ? modules.getAsJsonObject(module.id()) : new JsonObject();
			m.addProperty("enabled", module.isEnabled());
			JsonObject settings = m.has("settings") && m.get("settings").isJsonObject() ? m.getAsJsonObject("settings") : new JsonObject();
			for (Setting<?> s : module.settings()) {
				JsonElement v = s.toJson();
				if (!v.isJsonNull()) settings.add(s.id(), v);
			}
			settings.add(module.toggleKey.id(), module.toggleKey.toJson());
			m.add("settings", settings);
			modules.add(module.id(), m);
		}
		root.add("modules", modules);
		root.addProperty("version", 2);
		return root;
	}

	private static boolean dirty;

	/** Marks the config for saving. The write happens in {@link #flush()} (each second, menu close, shutdown). */
	public static void save() {
		if (loading || ModuleManager.all().isEmpty()) return;
		dirty = true;
	}

	public static void flush() {
		if (!dirty) return;
		dirty = false;
		JsonObject root = snapshot();
		lastRead = root;
		write(FILE, root);
	}

	private static void write(Path file, JsonObject root) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			BlixuAddons.LOGGER.warn("Could not save {}", file, e);
		}
	}

	// ------------------------------------------------------------------ profiles

	/** Letters, digits, space, dash and underscore only, so a name can't escape the folder. */
	public static String cleanName(String name) {
		String clean = name.trim().replaceAll("[^A-Za-z0-9 _-]", "");
		return clean.isEmpty() ? "default" : clean.substring(0, Math.min(clean.length(), 32));
	}

	public static void saveProfile(String name) {
		write(PROFILES.resolve(cleanName(name).toLowerCase(Locale.ROOT) + ".json"), snapshot());
	}

	public static boolean loadProfile(String name) {
		Path file = PROFILES.resolve(cleanName(name).toLowerCase(Locale.ROOT) + ".json");
		if (!Files.exists(file)) return false;
		apply(read(file));
		save();
		return true;
	}

	public static boolean deleteProfile(String name) {
		try {
			return Files.deleteIfExists(PROFILES.resolve(cleanName(name).toLowerCase(Locale.ROOT) + ".json"));
		} catch (IOException e) {
			return false;
		}
	}

	public static List<String> listProfiles() {
		List<String> names = new ArrayList<>();
		if (!Files.isDirectory(PROFILES)) return names;
		try (Stream<Path> files = Files.list(PROFILES)) {
			files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
				.map(n -> n.substring(0, n.length() - 5)).sorted().forEach(names::add);
		} catch (IOException ignored) {
		}
		return names;
	}

	/** Saves one module's settings under a preset name (per-module presets, e.g. hand poses). */
	public static void saveModulePreset(Module module, String name) {
		JsonObject settings = new JsonObject();
		for (Setting<?> s : module.settings()) {
			JsonElement v = s.toJson();
			if (!v.isJsonNull()) settings.add(s.id(), v);
		}
		write(PROFILES.resolve("module_" + module.id()).resolve(cleanName(name).toLowerCase(Locale.ROOT) + ".json"), settings);
	}

	public static boolean loadModulePreset(Module module, String name) {
		Path file = PROFILES.resolve("module_" + module.id()).resolve(cleanName(name).toLowerCase(Locale.ROOT) + ".json");
		if (!Files.exists(file)) return false;
		JsonObject settings = read(file);
		loading = true;
		try {
			for (Setting<?> s : module.settings()) {
				JsonElement v = settings.get(s.id());
				if (v != null && !v.isJsonNull()) s.fromJson(v);
			}
		} finally {
			loading = false;
		}
		save();
		return true;
	}
}
