package com.blixu.blixuaddons.client.feature.slayer;

import com.blixu.blixuaddons.BlixuAddons;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows YOUR Voidgloom Seraph (ported from the EmanTracker Polinex script): health, hits shield, laser
 * phase on server ticks, beacons (placed, thrown + predicted landing), Nukekubi heads, laser-wall
 * guardians, and the Voidlings / mini bosses around it. Detection only; drawing is in VoidgloomVisuals.
 */
public final class VoidgloomTracker {
	private VoidgloomTracker() {}

	// full health / shield hits per tier (hits from SkyHanni's damage indicator)
	public static final Map<String, Double> TIER_HP = Map.of("I", 300e3, "II", 12e6, "III", 50e6, "IV", 210e6);
	public static final Map<String, Integer> TIER_HITS = Map.of("I", 15, "II", 30, "III", 60, "IV", 100);
	/** The laser lasts 8.2 s of server time from when the boss mounts its armor stand. */
	public static final int LASER_TICKS = 164;
	/** From 9 logged T4 fights: the hits shield goes up at 2/3 and 1/3 of max health; lasers start at 5/6, 1/2, 1/6. */
	public static final double[] SHIELD_FRACS = {2 / 3.0, 1 / 3.0};
	public static final double[] LASER_FRACS_IV = {5 / 6.0, 1 / 2.0, 1 / 6.0};

	private static final Pattern TIER = Pattern.compile("Seraph\\s+(IV|III|II|I)\\b");
	private static final Pattern HITS = Pattern.compile("(\\d+)\\s*Hits?\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern HP = Pattern.compile("([\\d.,]+)\\s*([kKmMbB]?)(?:\\s*/\\s*([\\d.,]+)\\s*([kKmMbB]?))?\\s*❤");
	private static final Pattern TIMER = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
	public static final Pattern FANATIC = Pattern.compile("Voidling (Fanatic|Extremist)", Pattern.CASE_INSENSITIVE);
	// shortcut: mini boss names from the wiki (Devotee T2+, Radical T3+, Voidcrazed Maniac T4); add missing ones here
	public static final Pattern MINI = Pattern.compile("Voidling (Devotee|Radical)|Voidcrazed Maniac", Pattern.CASE_INSENSITIVE);

	public enum Phase { DAMAGE, SHIELD, LASER }

	public static final class Boss {
		public String tier = "";
		public double hp, maxHp;
		public @Nullable Integer hits;
		public boolean held;
		public double x, y, z;
	}

	public static final class Laser {
		int vehicleId;
		public double startTick;
		public long startMs;
		int missing;
		boolean shieldSeen, immune, alerted;
	}

	public static final class Flying {
		final List<double[]> hist = new ArrayList<>();
		public List<double[]> path = List.of();
		public int @Nullable [] land;
		public double landTick;
	}

	/** One laser beam this tick: from the guardian to its target (or along its head facing). */
	public record Beam(int guardianId, int targetId, boolean elder) {}

	// ---- state
	public static int bodyId = -1;
	public static @Nullable Boss boss;
	public static @Nullable Laser laser;
	public static final Map<Long, Long> beacons = new HashMap<>();           // packed pos -> ms placed
	public static final Map<Integer, Flying> flying = new HashMap<>();        // stand id -> throw
	public static final List<Integer> heads = new ArrayList<>();             // Nukekubi stand ids
	public static final List<Beam> beams = new ArrayList<>();
	public static final List<Integer> fanatics = new ArrayList<>(), minis = new ArrayList<>();
	private static final Map<Integer, Double> standSeen = new HashMap<>();   // stand id -> server tick first seen (-inf = before lock)
	private static long questSince, hpChangedMs;
	private static @Nullable Integer lastHitsSeen;
	private static int hitsGoneTicks;

	/** Screen alert text, its colour and until when it shows (ms). */
	public static String alertText = "TERM NOW";
	public static int alertColor = 0xFFFFFFFF;
	public static long alertUntil;
	/** Set by the module each tick: which beacon alerts are wanted. */
	public static boolean beaconSoonAlert, beaconDownAlert;
	private static boolean wasHeld;

	public static long alertStart;
	/** When TERM NOW will fire during the current laser (ms, wall clock); 0 = no countdown. */
	public static long termAt;

	public static void alert(String text, int color, long ms) {
		long now = System.currentTimeMillis();
		if (!text.equals(alertText) || now >= alertUntil) alertStart = now; // a refreshed TERM NOW doesn't re-pop
		alertText = text;
		alertColor = color;
		alertUntil = System.currentTimeMillis() + ms;
	}
	public static final long ALERT_MS = 1500;

	public static boolean debug;
	private static String lastTagText = "";

	public static void reset() {
		if (boss != null && debug) BlixuAddons.LOGGER.info("Voidgloom: boss gone");
		bodyId = -1;
		boss = null;
		laser = null;
		beacons.clear();
		flying.clear();
		heads.clear();
		beams.clear();
		standSeen.clear();
		lastHitsSeen = null;
		lastTagText = "";
		wasHeld = false;
	}

	public static @Nullable Entity body() {
		Minecraft mc = Minecraft.getInstance();
		return bodyId < 0 || mc.level == null ? null : mc.level.getEntity(bodyId);
	}

	// ------------------------------------------------------------------ parsing

	public record Tag(String tier, @Nullable Integer hits, @Nullable Double hp, @Nullable Double maxHp) {}

	/** "☠ Voidgloom Seraph IV 189.4M/210M❤", "... IV 12.3M❤" or "... IV 45 Hits". */
	public static @Nullable Tag parseTag(String line) {
		if (!line.contains("Voidgloom Seraph")) return null;
		Matcher t = TIER.matcher(line), h = HITS.matcher(line), hp = HP.matcher(line);
		boolean hasHp = hp.find();
		return new Tag(t.find() ? t.group(1) : "", h.find() ? Integer.parseInt(h.group(1)) : null,
			hasHp ? num(hp.group(1), hp.group(2)) : null, hasHp && hp.group(3) != null ? num(hp.group(3), hp.group(4)) : null);
	}

	private static double num(String n, String suffix) {
		double v = Double.parseDouble(n.replace(",", ""));
		return switch (suffix.toLowerCase()) {
			case "k" -> v * 1e3;
			case "m" -> v * 1e6;
			case "b" -> v * 1e9;
			default -> v;
		};
	}

	public static String fmt(double n) {
		if (n >= 1e6) return String.format(n >= 1e8 ? "%.0fM" : "%.1fM", n / 1e6);
		if (n >= 1e3) return String.format(n >= 1e5 ? "%.0fk" : "%.1fk", n / 1e3);
		return Long.toString(Math.round(n));
	}

	private static boolean isTagOf(Entity body, Entity s) {
		return Math.abs(s.getX() - body.getX()) < 1.5 && Math.abs(s.getZ() - body.getZ()) < 1.5
			&& s.getY() - body.getY() > 0.5 && s.getY() - body.getY() < 5;
	}

	private static String label(Entity e) {
		return e.hasCustomName() ? Sidebar.strip(e.getCustomName().getString()) : "";
	}

	private static int timerOf(Entity body, List<ArmorStand> stands) {
		int best = -1;
		for (ArmorStand s : stands) {
			if (!isTagOf(body, s)) continue;
			Matcher m = TIMER.matcher(label(s).trim());
			if (m.matches()) best = Math.max(best, Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)));
		}
		return best;
	}

	/** Next hits shield below the current health: {health it goes up at, damage left} or null. */
	public static double @Nullable [] nextPhase(Boss b) {
		if (b.maxHp <= 0) return null;
		for (double f : SHIELD_FRACS) {
			double at = f * b.maxHp;
			if (at < b.hp - 1) return new double[] {at, b.hp - at};
		}
		return null;
	}

	public static Phase phase() {
		if (boss == null) return Phase.DAMAGE;
		if (laser != null && (laser.shieldSeen || laser.immune)) return Phase.LASER;
		if (boss.hits != null) return Phase.SHIELD;
		return Phase.DAMAGE;
	}

	public static double laserTicksLeft(long now) {
		return laser == null ? 0 : Math.max(0, LASER_TICKS - (ServerClock.tick(now) - laser.startTick));
	}

	// ------------------------------------------------------------------ per tick

	public static void tick(Minecraft mc, boolean termAlert, double termLeadSec, boolean hitsOverAlert, boolean scanBeacons, boolean scanHeads, boolean scanWalls) {
		if (mc.level == null || mc.player == null) {
			reset();
			return;
		}
		long now = System.currentTimeMillis();
		if (!Sidebar.contains("Slay the boss!")) {
			questSince = 0;
			if (bodyId >= 0) reset();
			fanatics.clear();
			minis.clear();
			scanMobs(mc, null);
			return;
		}
		if (questSince == 0) questSince = now;

		List<ArmorStand> stands = new ArrayList<>();
		List<EnderMan> endermen = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.distanceToSqr(mc.player) > 32 * 32) continue;
			if (e instanceof ArmorStand s) stands.add(s);
			else if (e instanceof EnderMan m) endermen.add(m);
		}

		// Lock: when "Slay the boss!" shows yours has just spawned, so take the Voidgloom with the most time
		// left (nearest wins ties); for a few seconds a fresher one that loads late can still take over.
		// shortcut: someone else spawning theirs the same second can fool it.
		Entity body = body();
		if (body == null || now - questSince < 5000) {
			EnderMan best = null;
			int bestT = -2;
			double bestD = Double.MAX_VALUE;
			for (EnderMan m : endermen) {
				boolean tagged = false;
				for (ArmorStand s : stands) if (isTagOf(m, s) && parseTag(label(s)) != null) { tagged = true; break; }
				if (!tagged) continue;
				int t = timerOf(m, stands);
				double d = m.distanceToSqr(mc.player);
				if (t > bestT || (t == bestT && d < bestD)) { best = m; bestT = t; bestD = d; }
			}
			if (body == null || (best != null && best.getId() != bodyId && bestT > timerOf(body, stands) + 2)) {
				if (bodyId >= 0) reset();
				if (best == null) return;
				body = best;
				bodyId = best.getId();
				if (debug) BlixuAddons.LOGGER.info("Voidgloom: locked onto enderman {} (timer {}s)", bodyId, bestT);
			}
		}
		if (boss == null) for (ArmorStand s : stands) standSeen.put(s.getId(), Double.NEGATIVE_INFINITY);

		// re-read every nametag line above the boss; health and hits can sit on different stands
		String tier = "";
		Double hp = null, maxHp = null;
		Integer hits = null;
		StringBuilder tagText = new StringBuilder();
		for (ArmorStand s : stands) {
			if (!isTagOf(body, s)) continue;
			String t = label(s);
			tagText.append(t).append(" | ");
			Tag p = parseTag(t);
			if (p != null) {
				if (!p.tier().isEmpty()) tier = p.tier();
				if (p.hp() != null) { hp = p.hp(); maxHp = p.maxHp(); }
			}
			Matcher h = HITS.matcher(t);
			if (h.find()) hits = Integer.parseInt(h.group(1));
		}
		if (debug && !tagText.toString().equals(lastTagText)) BlixuAddons.LOGGER.info("Voidgloom: nametags {}", tagText);
		lastTagText = tagText.toString();

		if (boss == null) boss = new Boss();
		Boss b = boss;
		if (!tier.isEmpty()) b.tier = tier;
		if (b.maxHp <= 0 && TIER_HP.containsKey(b.tier)) b.hp = b.maxHp = TIER_HP.get(b.tier); // opens in hits phase at full health
		if (hp != null) {
			if (hp != b.hp) hpChangedMs = now;
			b.hp = hp;
			b.maxHp = maxHp != null ? maxHp : TIER_HP.getOrDefault(b.tier, Math.max(b.maxHp, hp));
		}
		b.hits = hits;
		if (laser != null && hits != null) laser.shieldSeen = true;
		if (hits != null) {
			lastHitsSeen = hits;
			hitsGoneTicks = 0;
		} else if (lastHitsSeen != null && ++hitsGoneTicks >= 2) {
			if (lastHitsSeen <= 10 && hitsOverAlert) alert("HITS OVER!", 0xFFFFFFFF, ALERT_MS);
			lastHitsSeen = null;
		}
		b.x = body.getX();
		b.y = body.getY();
		b.z = body.getZ();
		b.held = body instanceof LivingEntity le && (le.getMainHandItem().is(Items.BEACON) || le.getOffhandItem().is(Items.BEACON));
		// it picks the beacon up a moment before throwing it: warn once per pickup
		if (b.held && !wasHeld && beaconSoonAlert) alert("BEACON SOON", 0xFFFFAA33, 1800);
		wasHeld = b.held;

		// Laser: the boss rides an armor stand for the phase (we can see the real vehicle, unlike the script).
		double tickNow = ServerClock.tick(now);
		for (ArmorStand s : stands) standSeen.putIfAbsent(s.getId(), tickNow);
		Entity mount = body.getVehicle();
		if (mount instanceof ArmorStand) {
			if (laser == null || laser.vehicleId != mount.getId()) {
				laser = new Laser();
				laser.vehicleId = mount.getId();
				double seen = standSeen.getOrDefault(mount.getId(), tickNow);
				laser.startTick = Double.isInfinite(seen) ? tickNow : seen;
				laser.startMs = now;
				if (debug) BlixuAddons.LOGGER.info("Voidgloom: laser started ({} TPS)", String.format("%.1f", ServerClock.tps()));
			}
			laser.missing = 0;
		} else if (laser != null && ++laser.missing >= 3) {
			laser = null;
		}
		// during a laser the boss stays damageable until its shield shows, or its health stalls just above the next threshold
		if (laser != null && !laser.immune && b.maxHp > 0 && now - hpChangedMs >= 800) {
			for (double f : SHIELD_FRACS) if (b.hp >= f * b.maxHp * 0.98 && b.hp <= f * b.maxHp * 1.1) { laser.immune = true; break; }
		}
		if (laser != null && termAlert) {
			double leftMs = laserTicksLeft(now) / ServerClock.tps() * 1000;
			if (!laser.alerted && leftMs <= termLeadSec * 1000) laser.alerted = true;
			if (laser.alerted) alert("TERM NOW", 0xFFFFFFFF, (long) leftMs + ALERT_MS);
			termAt = laser.alerted ? 0 : now + (long) (leftMs - termLeadSec * 1000);
		} else {
			termAt = 0;
		}

		if (scanBeacons) scanFlying(mc, stands);
		else flying.clear();
		if (scanHeads) scanHeads(mc, stands);
		else heads.clear();
		if (scanWalls) scanWalls(mc);
		else beams.clear();
		scanMobs(mc, stands);
	}

	/** Voidling Fanatics/Extremists and mini bosses: endermen whose name tag stack matches. */
	private static void scanMobs(Minecraft mc, @Nullable List<ArmorStand> stands) {
		fanatics.clear();
		minis.clear();
		if (mc.level == null || mc.player == null) return;
		if (stands == null) {
			stands = new ArrayList<>();
			for (Entity e : mc.level.entitiesForRendering()) if (e instanceof ArmorStand s && e.distanceToSqr(mc.player) < 32 * 32) stands.add(s);
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof EnderMan) || e.getId() == bodyId || e.distanceToSqr(mc.player) > 32 * 32) continue;
			StringBuilder tag = new StringBuilder();
			for (ArmorStand s : stands) if (isTagOf(e, s)) tag.append(label(s)).append(' ');
			if (FANATIC.matcher(tag).find()) fanatics.add(e.getId());
			else if (MINI.matcher(tag).find()) minis.add(e.getId());
		}
	}

	// ------------------------------------------------------------------ beacons

	public static void onBlock(BlockPos pos, BlockState state) {
		if (boss == null) return;
		long key = pos.asLong();
		if (state.is(Blocks.BEACON)) {
			if (Math.sqrt(pos.distToCenterSqr(boss.x, boss.y, boss.z)) > 20) return;
			if (!beacons.containsKey(key) && beaconDownAlert) alert("BEACON DOWN!", 0xFFFF4040, 2200);
			beacons.put(key, System.currentTimeMillis());
			if (debug) BlixuAddons.LOGGER.info("Voidgloom: beacon placed at {}", pos);
		} else {
			beacons.remove(key);
		}
	}

	private static final double BEACON_HEAD = 1.6;

	private static void scanFlying(Minecraft mc, List<ArmorStand> stands) {
		Map<Integer, Boolean> seen = new HashMap<>();
		for (ArmorStand s : stands) {
			if (!s.getItemBySlot(EquipmentSlot.HEAD).is(Items.BEACON)) continue;
			if (Math.hypot(s.getX() - boss.x, s.getZ() - boss.z) > 24) continue; // someone else's boss
			seen.put(s.getId(), true);
			Flying f = flying.computeIfAbsent(s.getId(), k -> new Flying());
			f.hist.add(new double[] {s.getX(), s.getY() + BEACON_HEAD, s.getZ()});
			if (f.hist.size() > 3) f.hist.remove(0);
			if (f.hist.size() == 3) {
				Landing l = predictLanding(f.hist, (x, y, z) -> mc.level.getBlockState(new BlockPos(x, y, z)).blocksMotion());
				f.path = l == null ? List.of() : l.path;
				f.land = l == null ? null : l.land;
				f.landTick = l == null ? 0 : ServerClock.tick(System.currentTimeMillis()) + l.ticks;
			}
		}
		flying.keySet().removeIf(id -> !seen.containsKey(id));
	}

	public interface Solid {
		boolean at(int x, int y, int z);
	}

	public record Landing(List<double[]> path, int[] land, int ticks) {}

	/** From the last three tick positions, step the arc (velocity + its change per tick) until it hits a solid block. */
	public static @Nullable Landing predictLanding(List<double[]> h, Solid solid) {
		double[] p0 = h.get(h.size() - 3), p1 = h.get(h.size() - 2), p2 = h.get(h.size() - 1);
		double[] v = new double[3], a = new double[3], p = p2.clone();
		for (int i = 0; i < 3; i++) {
			v[i] = p2[i] - p1[i];
			a[i] = (p2[i] - p1[i]) - (p1[i] - p0[i]);
		}
		List<double[]> path = new ArrayList<>();
		path.add(p.clone());
		for (int t = 1; t <= 80; t++) {
			for (int i = 0; i < 3; i++) v[i] += a[i];
			double[] next = {p[0] + v[0], p[1] + v[1], p[2] + v[2]};
			int bx = (int) Math.floor(next[0]), by = (int) Math.floor(next[1]), bz = (int) Math.floor(next[2]);
			if (solid.at(bx, by, bz)) return new Landing(path, new int[] {bx, by, bz}, t);
			p = next;
			if (t % 3 == 0) path.add(p.clone());
		}
		return null;
	}

	// ------------------------------------------------------------------ heads, walls

	private static void scanHeads(Minecraft mc, List<ArmorStand> stands) {
		heads.clear();
		List<Player> players = new ArrayList<>();
		for (Player p : mc.level.players()) if (p != mc.player && p.distanceToSqr(mc.player) < 48 * 48) players.add(p);
		for (ArmorStand s : stands) {
			ItemStack head = s.getItemBySlot(EquipmentSlot.HEAD);
			// a Nukekubi head is a plain player head with no SkyBlock id (power orbs etc. carry ids)
			if (!head.is(Items.PLAYER_HEAD)) continue;
			var data = head.get(DataComponents.CUSTOM_DATA);
			if (data != null && data.copyTag().contains("id")) continue;
			Double seen = standSeen.get(s.getId());
			if (seen == null || Double.isInfinite(seen)) continue; // was there before the lock
			if (Math.hypot(s.getX() - boss.x, s.getZ() - boss.z) >= 20) continue;
			boolean pet = false;
			for (Player p : players) if (Math.hypot(p.getX() - s.getX(), p.getZ() - s.getZ()) < 2.5) { pet = true; break; }
			if (!pet) heads.add(s.getId());
		}
	}

	private static void scanWalls(Minecraft mc) {
		beams.clear();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Guardian g) || Math.hypot(e.getX() - boss.x, e.getZ() - boss.z) >= 24) continue;
			LivingEntity t = g.getActiveAttackTarget();
			beams.add(new Beam(g.getId(), t != null ? t.getId() : -1, g instanceof net.minecraft.world.entity.monster.ElderGuardian));
		}
	}

	// ------------------------------------------------------------------ self-check

	public static void selfCheck() {
		Tag a = parseTag("☠ Voidgloom Seraph IV 189.4M/210M❤");
		Tag b = parseTag("[Lv655]  Voidgloom Seraph IV 45 Hits");
		Tag c = parseTag("☠ Voidgloom Seraph I 1,234❤");
		Tag d = parseTag("☠ Voidgloom Seraph III 1 Hit");
		if (a == null || !a.tier().equals("IV") || Math.abs(a.hp() - 189.4e6) > 1 || a.maxHp() != 210e6 || a.hits() != null) throw new IllegalStateException("Voidgloom parse A");
		if (b == null || !b.tier().equals("IV") || b.hits() != 45 || b.hp() != null) throw new IllegalStateException("Voidgloom parse B");
		if (c == null || !c.tier().equals("I") || c.hp() != 1234 || c.maxHp() != null) throw new IllegalStateException("Voidgloom parse C");
		if (d == null || d.hits() != 1) throw new IllegalStateException("Voidgloom parse D");
		if (parseTag("Spawned by: Steve") != null) throw new IllegalStateException("Voidgloom parse E");
		if (!fmt(12.3e6).equals("12.3M") || !fmt(210e6).equals("210M") || !fmt(450e3).equals("450k")) throw new IllegalStateException("Voidgloom fmt");
		Boss x = new Boss();
		x.hp = 141e6;
		x.maxHp = 210e6;
		double[] n = nextPhase(x);
		if (n == null || Math.abs(n[0] - 140e6) > 1 || Math.abs(n[1] - 1e6) > 1) throw new IllegalStateException("Voidgloom next phase A");
		x.hp = 60e6;
		if (nextPhase(x) != null) throw new IllegalStateException("Voidgloom next phase C");
		Landing l = predictLanding(List.of(new double[] {0.5, 4.2, 0.5}, new double[] {0.5, 4.1, 0.5}, new double[] {0.5, 3.96, 0.5}), (bx, by, bz) -> by < 1);
		if (l == null || l.land()[1] != 0 || l.land()[0] != 0 || l.land()[2] != 0) throw new IllegalStateException("Voidgloom landing");
		ServerClock.selfCheck();
	}
}
