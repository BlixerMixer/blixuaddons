package com.blixu.blixuaddons.client.feature.slayer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Reads the scoreboard sidebar as plain text lines (Hypixel puts its text in team prefixes/suffixes). */
public final class Sidebar {
	private Sidebar() {}

	private static final java.util.regex.Pattern CODES = java.util.regex.Pattern.compile("§.");
	private static long cachedTick = -1;
	private static String cached = "";

	/** All sidebar lines joined with newlines, colour codes stripped. Cached per game tick. */
	public static String text() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return "";
		long t = mc.level.getGameTime();
		if (t == cachedTick) return cached;
		cachedTick = t;
		Scoreboard sb = mc.level.getScoreboard();
		Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return cached = "";
		StringBuilder out = new StringBuilder();
		for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
			PlayerTeam team = sb.getPlayersTeam(e.owner());
			Component line = e.display() != null ? e.display() : PlayerTeam.formatNameForTeam(team, Component.literal(e.owner()));
			out.append(strip(line.getString())).append('\n');
		}
		return cached = out.toString();
	}

	public static boolean contains(String needle) {
		return text().contains(needle);
	}

	/** Removes legacy section-sign colour codes (Hypixel still sends some inside literal text). */
	public static String strip(String s) {
		return s.indexOf('§') < 0 ? s : CODES.matcher(s).replaceAll("");
	}
}
