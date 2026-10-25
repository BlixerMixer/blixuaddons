package com.blixu.blixuaddons.client.feature;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;

/** Groups held items for per-category first-person profiles. */
public enum ItemCategory {
	SWORD("Swords"),
	TOOL("Tools"),
	BOW("Bows"),
	FOOD("Food"),
	SHIELD("Shields"),
	HAND("Empty hand"),
	OTHER("Other");

	public final String label;

	ItemCategory(String label) {
		this.label = label;
	}

	public static ItemCategory of(ItemStack stack) {
		if (stack.isEmpty()) return HAND;
		if (stack.is(ItemTags.SWORDS)) return SWORD;
		if (stack.getItem() instanceof ShieldItem) return SHIELD;
		if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT)) return BOW;
		if (stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) return TOOL;
		ItemUseAnimation use = stack.getUseAnimation();
		if (stack.has(DataComponents.FOOD) || use == ItemUseAnimation.EAT || use == ItemUseAnimation.DRINK) return FOOD;
		return OTHER;
	}
}
