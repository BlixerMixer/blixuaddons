package com.blixu.blixuaddons.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String id, String name, boolean defaultValue) {
		super(id, name, defaultValue);
	}

	public void toggle() { set(!value); }

	@Override public JsonElement toJson() { return new JsonPrimitive(value); }

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) value = json.getAsBoolean();
	}
}
