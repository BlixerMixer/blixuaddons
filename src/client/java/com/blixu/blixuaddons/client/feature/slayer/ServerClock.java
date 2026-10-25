package com.blixu.blixuaddons.client.feature.slayer;

/**
 * Real server time from the time packets: the server sends one every 10 or 20 ticks, so counting them
 * gives ticks that slow down when the server lags (and so does the Voidgloom laser). Between packets
 * we extrapolate at the measured TPS, capped at the next packet so a frozen server freezes the count.
 */
public final class ServerClock {
	private ServerClock() {}

	private static double baseTick;
	private static long lastMs;
	private static double tps = 20;
	private static int perPacket = 20;
	private static final long[] GAPS = new long[10];
	private static int gapCount, gapNext;

	/** Called on every time packet (main thread). */
	public static void onTimePacket(long now) {
		if (lastMs == 0) {
			baseTick = now / 50.0; // continue from the wall-clock fallback so earlier timestamps still line up
		} else {
			long gap = now - lastMs;
			if (gap < 5000) { // skip world-switch pauses
				GAPS[gapNext] = gap;
				gapNext = (gapNext + 1) % GAPS.length;
				gapCount = Math.min(GAPS.length, gapCount + 1);
			}
			if (gapCount > 0) {
				double avg = 0;
				for (int i = 0; i < gapCount; i++) avg += GAPS[i];
				avg /= gapCount;
				// shortcut: a 10-tick server below ~13 TPS reads as a 20-tick one; fine for normal lag
				perPacket = avg < 750 ? 10 : 20;
				tps = Math.max(1, Math.min(20, perPacket * 1000.0 / avg));
			}
			baseTick += perPacket;
		}
		lastMs = now;
	}

	public static double tick(long now) {
		if (lastMs == 0) return now / 50.0; // no time packet yet: wall clock at 20 TPS
		return baseTick + Math.min(perPacket, (now - lastMs) * tps / 1000.0);
	}

	public static double tps() {
		return tps;
	}

	public static void reset() {
		baseTick = 0;
		lastMs = 0;
		tps = 20;
		perPacket = 20;
		gapCount = gapNext = 0;
	}

	/** Self-check with the same cases as the original script. */
	public static void selfCheck() {
		reset();
		for (int i = 0; i < 5; i++) onTimePacket(1000 + i * 2000L); // half speed, first packet at tick 20
		if (Math.abs(tps - 10) > 0.01 || tick(10000) != 110 || tick(18999) != 120)
			throw new IllegalStateException("ServerClock check 1 failed: tps " + tps + " ticks " + tick(10000) + " " + tick(18999));
		reset();
		for (int i = 0; i < 6; i++) onTimePacket(1000 + i * 520L); // Hypixel style: every 10 ticks at ~19.2 TPS
		if (perPacket != 10 || Math.abs(tps - 1000 / 52.0) > 0.01 || Math.abs(tick(3860) - 75) > 0.01)
			throw new IllegalStateException("ServerClock check 2 failed");
		reset();
	}
}
