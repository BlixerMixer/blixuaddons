package com.blixu.blixuaddons.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Function;

/** Pick one option from a fixed list (shown as  ‹ Option ›  in the menu). */
public class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] options;
	private final Function<E, String> label;

	public EnumSetting(String id, String name, E defaultValue, Function<E, String> label) {
		super(id, name, defaultValue);
		this.options = defaultValue.getDeclaringClass().getEnumConstants();
		this.label = label;
	}

	public void cycle(int direction) {
		int i = Math.floorMod(value.ordinal() + direction, options.length);
		set(options[i]);
	}

	public String label() { return label.apply(value); }
	public int index() { return value.ordinal(); }
	public int count() { return options.length; }

	@Override public JsonElement toJson() { return new JsonPrimitive(value.name()); }

	@Override
	public void fromJson(JsonElement json) {
		try {
			for (E e : options) {
				if (e.name().equals(json.getAsString())) value = e;
			}
		} catch (RuntimeException ignored) {
		}
	}
}
