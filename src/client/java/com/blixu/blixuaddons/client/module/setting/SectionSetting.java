package com.blixu.blixuaddons.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A heading that groups the settings after it (the full-screen Slayers page lays sections out in columns). Not saved. */
public class SectionSetting extends Setting<String> {
	private final String subtitle;

	public SectionSetting(String id, String name, String subtitle) {
		super(id, name, name);
		this.subtitle = subtitle;
	}

	public String subtitle() {
		return subtitle;
	}

	@Override public void reset() {}

	@Override public JsonElement toJson() { return JsonNull.INSTANCE; }

	@Override public void fromJson(JsonElement json) {}
}
