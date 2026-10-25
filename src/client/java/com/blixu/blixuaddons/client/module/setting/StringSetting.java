package com.blixu.blixuaddons.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Free text (names, block/item lists). Click the field in the menu and type. */
public class StringSetting extends Setting<String> {
	private final int maxLength;

	public StringSetting(String id, String name, String defaultValue, int maxLength) {
		super(id, name, defaultValue);
		this.maxLength = maxLength;
	}

	public int maxLength() { return maxLength; }

	@Override
	public void set(String v) {
		super.set(v.length() > maxLength ? v.substring(0, maxLength) : v);
	}

	@Override public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement json) {
		try {
			String s = json.getAsString();
			value = s.length() > maxLength ? s.substring(0, maxLength) : s;
		} catch (RuntimeException ignored) {
		}
	}
}
