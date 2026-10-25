package com.blixu.blixuaddons.client.feature.slayer;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rare slayer drops (under 5%) with their per-tier base chances, from the community wiki
 * (hypixelskyblock.minecraft.wiki drop tables, October 2026), and the chat-line parser.
 *
 * Magic Find: drops under 5% get their weight multiplied by (1 + MF/100) (wiki "Slayer § Boss Loot").
 * shortcut: we scale the chance itself instead of re-normalising the whole loot pool, which reads a few
 * percent high at very high MF; fine for a "1 in N" display.
 */
public final class SlayerDrops {
	private SlayerDrops() {}

	public enum Rarity {
		RARE("Rare", 0xFF5B8CFF), EXTRAORDINARY("Extraordinary", 0xFFC77DFF), PRAY_RNGESUS("Pray RNGesus", 0xFFFF55FF),
		RNGESUS_INCARNATE("RNGesus Incarnate", 0xFFFF5555);
		public final String label;
		public final int color;
		Rarity(String l, int c) { label = l; color = c; }
	}

	/** chances[tier - 1] in percent, or -1 when it doesn't drop from that tier. */
	public record Drop(String name, String slayer, Rarity rarity, double[] chances, String id, Item icon) {
		public int topTier() {
			for (int t = chances.length; t >= 1; t--) if (chances[t - 1] >= 0) return t;
			return 1;
		}

		/** Base chance (%) at {@code tier}, or at its highest tier when that tier doesn't have it. */
		public double baseChance(int tier) {
			if (tier >= 1 && tier <= chances.length && chances[tier - 1] >= 0) return chances[tier - 1];
			return chances[topTier() - 1];
		}
	}

	public static final List<Drop> DROPS = new ArrayList<>();

	private static void d(String name, String slayer, Rarity r, String chances, String id, Item icon) {
		String[] p = chances.split(",");
		double[] c = new double[p.length];
		for (int i = 0; i < p.length; i++) c[i] = p[i].trim().equals("-") ? -1 : Double.parseDouble(p[i].trim());
		DROPS.add(new Drop(name, slayer, r, c, id, icon));
	}

	static {
		Rarity R = Rarity.RARE, E = Rarity.EXTRAORDINARY, P = Rarity.PRAY_RNGESUS, I = Rarity.RNGESUS_INCARNATE;
		// ---- Voidgloom Seraph (tiers I-IV)
		String V = "Voidgloom Seraph";
		d("Summoning Eye", V, P, "-,0.67,0.60,0.56", "SUMMONING_EYE", Items.ENDER_EYE);
		d("Null Atom", V, R, "-,-,3.73,4.94", "NULL_ATOM", Items.PLAYER_HEAD);
		d("Mana Steal I", V, R, "-,-,4.47,4.23", "ENCHANTMENT_MANA_STEAL_1", Items.ENCHANTED_BOOK);
		d("Endersnake Rune", V, E, "-,-,2.42,5.30", "ENDERSNAKE_RUNE;1", Items.PLAYER_HEAD);
		d("Transmission Tuner", V, E, "-,-,2.24,2.12", "TRANSMISSION_TUNER", Items.PLAYER_HEAD);
		d("Hazmat Enderman", V, E, "-,-,1.04,1.55", "HAZMAT_ENDERMAN", Items.PLAYER_HEAD);
		d("Smarty Pants I", V, E, "-,-,-,1.76", "ENCHANTMENT_SMARTY_PANTS_1", Items.ENCHANTED_BOOK);
		d("End Rune", V, E, "-,-,-,0.66", "END_RUNE;1", Items.PLAYER_HEAD);
		d("Sinful Dice", V, P, "-,-,-,0.46", "SINFUL_DICE", Items.PLAYER_HEAD);
		d("Etherwarp Merger", V, P, "-,-,-,0.42", "ETHERWARP_MERGER", Items.PLAYER_HEAD);
		d("Pocket Espresso Machine", V, P, "-,-,-,0.39", "POCKET_ESPRESSO_MACHINE", Items.PLAYER_HEAD);
		d("Handy Blood Chalice", V, P, "-,-,-,0.18", "HANDY_BLOOD_CHALICE", Items.PLAYER_HEAD);
		d("Void Conqueror Enderman Skin", V, P, "-,-,-,0.17", "PET_SKIN_ENDERMAN_SLAYER", Items.PLAYER_HEAD);
		d("Judgement Core", V, P, "-,-,-,0.056", "JUDGEMENT_CORE", Items.PLAYER_HEAD);
		d("Enchant Rune", V, P, "-,-,-,0.046", "ENCHANT_RUNE;1", Items.PLAYER_HEAD);
		d("Exceedingly Rare Ender Artifact Upgrader", V, I, "-,-,-,0.028", "EXCEEDINGLY_RARE_ENDER_ARTIFACT_UPGRADER", Items.PLAYER_HEAD);
		d("End Stone Idol", V, I, "-,-,-,0.014", "ENDSTONE_IDOL", Items.PLAYER_HEAD);
		d("Byzantium Dye", V, I, "0.00001,0.00004,0.0001,0.0002", "DYE_BYZANTIUM", Items.DYE.purple());
		// ---- Revenant Horror (tiers I-V)
		String Z = "Revenant Horror";
		d("Undead Catalyst", Z, E, "-,2.5,2.5,0.75,2.5", "UNDEAD_CATALYST", Items.PLAYER_HEAD);
		d("Revenant Catalyst", Z, E, "-,1.25,1.25,1.25,1.25", "REVENANT_CATALYST", Items.PLAYER_HEAD);
		d("Pestilence Rune", Z, E, "-,0.83,3.33,-,-", "PESTILENCE_RUNE;1", Items.PLAYER_HEAD);
		d("Revenant Shard", Z, E, "-,-,1.0,1.5,2.0", "REVENANT_SHARD", Items.PRISMARINE_SHARD);
		d("Smite VI", Z, P, "-,-,0.5,0.5,0.5", "ENCHANTMENT_SMITE_6", Items.ENCHANTED_BOOK);
		d("Beheaded Horror", Z, P, "-,-,0.1,0.2,0.2", "BEHEADED_HORROR", Items.PLAYER_HEAD);
		d("Snake Rune", Z, P, "-,-,-,0.2,0.2", "SNAKE_RUNE;1", Items.PLAYER_HEAD);
		d("Festering Maggot", Z, P, "-,-,-,0.1,0.2", "FESTERING_MAGGOT", Items.PLAYER_HEAD);
		d("Scythe Blade", Z, P, "-,-,-,0.07,0.15", "SCYTHE_BLADE", Items.IRON_SWORD);
		d("Shredded Sinew", Z, P, "-,-,-,-,0.08", "SHREDDED_SINEW", Items.PLAYER_HEAD);
		d("Severed Hand", Z, P, "-,-,-,-,0.07", "SEVERED_HAND", Items.PLAYER_HEAD);
		d("Warden Heart", Z, I, "-,-,-,-,0.02", "WARDEN_HEART", Items.PLAYER_HEAD);
		d("Matcha Dye", Z, I, "0.000000001,0.000000004,0.00000001,0.00000002,0.00000004", "DYE_MATCHA", Items.DYE.green());
		// ---- Tarantula Broodfather (tiers I-V)
		String T = "Tarantula Broodfather";
		d("Spider Catalyst", T, E, "-,1.05,2.06,0.62,0.18", "SPIDER_CATALYST", Items.PLAYER_HEAD);
		d("Bite Rune", T, E, "-,0.69,2.67,-,-", "BITE_RUNE;1", Items.PLAYER_HEAD);
		d("Tarantula Catalyst", T, P, "-,-,0.25,0.37,0.42", "TARANTULA_CATALYST", Items.PLAYER_HEAD);
		d("Bane of Arthropods VI", T, P, "-,-,0.21,0.42,0.35", "ENCHANTMENT_BANE_OF_ARTHROPODS_6", Items.ENCHANTED_BOOK);
		d("Fly Swatter", T, P, "-,-,0.082,0.17,0.21", "FLY_SWATTER", Items.GOLDEN_SHOVEL);
		d("Tarantula Talisman", T, P, "-,-,0.082,0.17,0.21", "TARANTULA_TALISMAN", Items.PLAYER_HEAD);
		d("Darkness Within Rune", T, P, "-,-,-,0.39,0.66", "DARKNESS_WITHIN_RUNE;1", Items.PLAYER_HEAD);
		d("Vial of Venom", T, P, "-,-,-,0.083,0.14", "VIAL_OF_VENOM", Items.POTION);
		d("Digested Mosquito", T, P, "-,-,-,0.058,0.071", "DIGESTED_MOSQUITO", Items.PLAYER_HEAD);
		d("Primordial Shard", T, E, "-,-,-,-,0.71", "PRIMORDIAL_SHARD", Items.PRISMARINE_SHARD);
		d("Shriveled Wasp", T, P, "-,-,-,-,0.14", "SHRIVELED_WASP", Items.PLAYER_HEAD);
		d("Ensnared Snail", T, P, "-,-,-,-,0.042", "ENSNARED_SNAIL", Items.PLAYER_HEAD);
		d("Primordial Eye", T, I, "-,-,-,-,0.014", "PRIMORDIAL_EYE", Items.ENDER_EYE);
		d("Brick Red Dye", T, I, "0.00001,0.00004,0.0001,0.0002,0.0004", "DYE_BRICK_RED", Items.DYE.red());
		// ---- Sven Packmaster (top-tier chances; the wiki table doesn't map every row to a tier)
		String S = "Sven Packmaster";
		d("Spirit Rune", S, R, "-,-,0.68,2.66", "SPIRIT_RUNE;1", Items.PLAYER_HEAD);
		d("Critical VI", S, E, "-,-,0.41,0.81", "ENCHANTMENT_CRITICAL_6", Items.ENCHANTED_BOOK);
		d("Furball", S, E, "-,-,0.82,1.62", "FURBALL", Items.PLAYER_HEAD);
		d("Red Claw Egg", S, P, "-,-,0.04,0.12", "RED_CLAW_EGG", Items.WOLF_SPAWN_EGG);
		d("Couture Rune", S, P, "-,-,-,0.24", "COUTURE_RUNE;1", Items.PLAYER_HEAD);
		d("Grizzly Salmon", S, P, "-,-,-,0.06", "GRIZZLY_BAIT", Items.SALMON);
		d("Overflux Capacitor", S, P, "-,-,-,0.04", "OVERFLUX_CAPACITOR", Items.QUARTZ);
		d("Celeste Dye", S, I, "0.00001,0.00004,0.0001,0.0002", "DYE_CELESTE", Items.DYE.lightBlue());
		// ---- Inferno Demonlord (top-tier chances)
		String B = "Inferno Demonlord";
		d("Lavatears Rune", B, R, "-,-,1.15,2.31", "LAVATEARS_RUNE;1", Items.PLAYER_HEAD);
		d("Archfiend Dice", B, E, "-,-,1.16,1.05", "ARCHFIEND_DICE", Items.PLAYER_HEAD);
		d("Smoldering VI", B, E, "-,-,-,1.32", "ENCHANTMENT_SMOLDERING_6", Items.ENCHANTED_BOOK);
		d("Duplex I", B, R, "-,-,-,1.84", "ENCHANTMENT_ULTIMATE_REITERATE_1", Items.ENCHANTED_BOOK);
		d("Fiery Burst Rune", B, P, "-,-,-,0.21", "FIERY_BURST_RUNE;1", Items.PLAYER_HEAD);
		d("High Class Archfiend Dice", B, P, "-,-,-,0.26", "HIGH_CLASS_ARCHFIEND_DICE", Items.PLAYER_HEAD);
		d("Wilson's Engineering Plans", B, P, "-,-,-,0.09", "WILSON_ENGINEERING_PLANS", Items.PAPER);
		d("Subzero Inverter", B, P, "-,-,-,0.09", "SUBZERO_INVERTER", Items.PLAYER_HEAD);
		d("Flame Dye", B, I, "0.00001,0.00004,0.0001,0.0002", "DYE_FLAME", Items.DYE.orange());
	}

	// ------------------------------------------------------------------ chat

	/** e.g. "CRAZY RARE DROP!  (Pocket Espresso Machine) (+251% ✯ Magic Find)", also "(2x Null Atom)". */
	private static final Pattern LINE = Pattern.compile("^\\s*([A-Z !]*?)\\s*DROP!\\s*\\(?(.+?)\\)?\\s*(?:\\(\\+(\\d+(?:[.,]\\d+)?)%\\s*✯?\\s*Magic Find\\))?\\s*$");
	private static final Pattern QTY = Pattern.compile("^\\d+x\\s+");
	private static final Pattern BOOK = Pattern.compile("^Enchanted Book\\s*\\((.+)\\)$");

	public record Parsed(String label, String name, double magicFind) {}

	public static @Nullable Parsed parse(String raw) {
		Matcher m = LINE.matcher(Sidebar.strip(raw));
		if (!m.matches()) return null;
		String name = QTY.matcher(m.group(2).trim()).replaceFirst("");
		Matcher b = BOOK.matcher(name);
		if (b.matches()) name = b.group(1);
		double mf = m.group(3) == null ? 0 : Double.parseDouble(m.group(3).replace(',', '.'));
		String label = m.group(1).trim();
		return new Parsed(label.isEmpty() ? "RARE" : label, name.trim(), mf);
	}

	public static @Nullable Drop find(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		for (Drop d : DROPS) if (d.name().toLowerCase(Locale.ROOT).equals(n)) return d;
		for (Drop d : DROPS) if (n.contains(d.name().toLowerCase(Locale.ROOT)) || d.name().toLowerCase(Locale.ROOT).contains(n)) return d;
		return null;
	}

	/** Chance (%) with Magic Find: drops under 5% get (1 + MF/100), capped at 100. */
	public static double withMagicFind(double base, double mf) {
		return base < 5 ? Math.min(100, base * (1 + mf / 100)) : base;
	}

	public static String oneIn(double percent) {
		if (percent <= 0) return "?";
		double n = 100 / percent;
		if (n >= 1e6) return String.format(Locale.ROOT, "1 in %.1fM", n / 1e6);
		return "1 in " + String.format(Locale.ROOT, "%,d", Math.round(n));
	}

	public static void selfCheck() {
		Parsed p = parse("CRAZY RARE DROP!  (Pocket Espresso Machine) (+251% ✯ Magic Find)");
		if (p == null || !p.name().equals("Pocket Espresso Machine") || p.magicFind() != 251 || !p.label().equals("CRAZY RARE"))
			throw new IllegalStateException("SlayerDrops parse A");
		Parsed q = parse("RARE DROP! (2x Null Atom) (+120% ✯ Magic Find)");
		if (q == null || !q.name().equals("Null Atom")) throw new IllegalStateException("SlayerDrops parse B");
		Parsed r = parse("RARE DROP! (Enchanted Book (Smite VI))");
		if (r == null || !r.name().equals("Smite VI") || r.magicFind() != 0) throw new IllegalStateException("SlayerDrops parse C");
		if (find("Pocket Espresso Machine") == null || Math.abs(withMagicFind(0.39, 100) - 0.78) > 1e-9 || withMagicFind(12, 300) != 12)
			throw new IllegalStateException("SlayerDrops chance");
	}
}
