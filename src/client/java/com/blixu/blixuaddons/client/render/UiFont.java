package com.blixu.blixuaddons.client.render;

import com.blixu.blixuaddons.BlixuAddons;
import net.minecraft.network.chat.FontDescription;

/** Smooth TrueType fonts (Inter) defined in assets/blixuaddons/font/*.json. */
public enum UiFont {
	/** Inter Medium 8 — normal UI text. */
	BODY("ui"),
	/** Inter Regular 7 — descriptions, captions. */
	SMALL("ui_small"),
	/** Inter SemiBold 8 — labels, titles of cards. */
	SEMIBOLD("ui_semibold"),
	/** Inter Bold 13 — big headings. */
	TITLE("ui_title");

	public final FontDescription description;

	UiFont(String name) {
		this.description = new FontDescription.Resource(BlixuAddons.id(name));
	}
}
