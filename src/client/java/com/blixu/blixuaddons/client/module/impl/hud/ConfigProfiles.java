package com.blixu.blixuaddons.client.module.impl.hud;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.ActionSetting;
import com.blixu.blixuaddons.client.module.setting.StringSetting;

import java.util.List;

/** Save the whole setup under a name and switch between setups. Files: config/blixuaddons_profiles/. */
public class ConfigProfiles extends Module {
	public final StringSetting profile = add(new StringSetting("profile", "Profile name", "default", 32));
	private String status = "";

	public ConfigProfiles() {
		super("profiles", "Config Profiles",
			"Save, load and delete complete setups by name.",
			Category.HUD, "gear", true);
		add(new ActionSetting("save", "Save current setup", () -> label("Save"), () -> {
			ConfigManager.saveProfile(profile.get());
			status = "Saved " + ConfigManager.cleanName(profile.get());
		}));
		add(new ActionSetting("load", "Load profile", () -> label("Load"), () -> {
			String name = ConfigManager.cleanName(profile.get());
			status = ConfigManager.loadProfile(name) ? "Loaded " + name : "No profile " + name;
		}));
		add(new ActionSetting("delete", "Delete profile", () -> label("Delete"), () -> {
			String name = ConfigManager.cleanName(profile.get());
			status = ConfigManager.deleteProfile(name) ? "Deleted " + name : "No profile " + name;
		}));
		add(new ActionSetting("list", "Saved profiles", this::listLabel, () -> {
			List<String> all = ConfigManager.listProfiles();
			if (all.isEmpty()) return;
			// Click cycles the name field through saved profiles.
			int i = all.indexOf(ConfigManager.cleanName(profile.get()).toLowerCase(java.util.Locale.ROOT));
			profile.set(all.get((i + 1) % all.size()));
		}));
	}

	private String label(String verb) {
		return status.startsWith(verb.substring(0, 3)) || status.startsWith("No") ? status : verb;
	}

	private String listLabel() {
		List<String> all = ConfigManager.listProfiles();
		return all.isEmpty() ? "none yet" : all.size() + " saved — next ›";
	}
}
