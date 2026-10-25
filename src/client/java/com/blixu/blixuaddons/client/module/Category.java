package com.blixu.blixuaddons.client.module;

/** The tabs in the menu. {@code icon} is a file in textures/gui/icon/. */
public enum Category {
	HUD("HUD", "Panels, blur, glow, palettes and profiles", "hud"),
	VISUALS("Visuals", "Highlights, overlays and how things look", "visuals"),
	PLAYER("Player", "First-person hands, swings, inspections and your own glow", "player"),
	COMBAT("Combat Visuals", "Entity materials, hit feedback, tracers and damage numbers", "combat"),
	SLAYERS("Slayers", "Boss HUDs, phase glows, laser walls and alerts for each slayer", "damage"),
	PROJECTILES("Projectiles", "Trajectories, projectile looks, trails and impacts", "projectile"),
	COSMETICS("Cosmetics", "Hats, wings, relics and trails — only you see them", "cosmetics"),
	WORLD("World", "Sky, atmosphere, brightness, colour grading and highlights", "world"),
	CAMERA("Camera", "Freelook, zoom and camera feel", "camera"),
	OPTIMIZATIONS("Optimizations", "Squeeze out more FPS and less stutter", "optimizations"),
	QOL("Quality of Life", "Small things that save you time", "qol");

	public final String title;
	public final String subtitle;
	public final String icon;

	Category(String title, String subtitle, String icon) {
		this.title = title;
		this.subtitle = subtitle;
		this.icon = icon;
	}
}
