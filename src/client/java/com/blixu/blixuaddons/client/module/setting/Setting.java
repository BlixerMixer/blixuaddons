package com.blixu.blixuaddons.client.module.setting;

import com.blixu.blixuaddons.client.config.ConfigManager;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A value a module exposes in its settings panel. Right-click a row in the menu to reset it. */
public abstract class Setting<T> {
	private final String id;
	private final String name;
	private final T defaultValue;
	protected T value;
	private BooleanSupplier visible = () -> true;
	private final List<Consumer<T>> listeners = new ArrayList<>(1);

	protected Setting(String id, String name, T defaultValue) {
		this.id = id;
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public T get() { return value; }

	public void set(T value) {
		this.value = value;
		for (Consumer<T> l : listeners) l.accept(value);
		ConfigManager.save();
	}

	/** Set without listeners or saving (presets use this, then save once). */
	public void setSilently(T value) {
		this.value = value;
	}

	public void reset() { set(defaultValue); }

	public T defaultValue() { return defaultValue; }

	/** Only show this row in the menu while {@code condition} holds. The value is still saved. */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
		this.visible = condition;
		return (S) this;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S onChange(Consumer<T> listener) {
		listeners.add(listener);
		return (S) this;
	}

	public boolean isVisible() { return visible.getAsBoolean(); }

	public String id() { return id; }
	public String name() { return name; }

	public abstract JsonElement toJson();

	/** Load from config. Bad/missing values are ignored and the default stays. */
	public abstract void fromJson(JsonElement json);
}
