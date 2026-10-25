package com.blixu.blixuaddons.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.util.function.Supplier;

/** A button row (reset, save preset, …). Not saved. The label can change (e.g. "Saved ✓"). */
public class ActionSetting extends Setting<Runnable> {
	private final Supplier<String> buttonLabel;

	public ActionSetting(String id, String name, Supplier<String> buttonLabel, Runnable action) {
		super(id, name, action);
		this.buttonLabel = buttonLabel;
	}

	public ActionSetting(String id, String name, String buttonLabel, Runnable action) {
		this(id, name, () -> buttonLabel, action);
	}

	public String buttonLabel() { return buttonLabel.get(); }

	public void run() { value.run(); }

	@Override public void reset() {}

	@Override public JsonElement toJson() { return JsonNull.INSTANCE; }

	@Override public void fromJson(JsonElement json) {}
}
