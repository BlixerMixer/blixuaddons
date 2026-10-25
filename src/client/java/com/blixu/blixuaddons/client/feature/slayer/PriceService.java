package com.blixu.blixuaddons.client.feature.slayer;

import com.blixu.blixuaddons.BlixuAddons;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SkyBlock item prices for the rare-drop screen from the SkyCofl API (sky.coflnet.com, item price
 * "current": auction house and bazaar). One small request per item when a drop needs it, cached for
 * 10 minutes; never on the render thread, never sends anything about the player.
 */
public final class PriceService {
	private PriceService() {}

	private static final String API = "https://sky.coflnet.com/api/item/price/%s/current";
	private static final long FRESH_MS = 10 * 60_000L;

	private record Price(double value, long at) {}

	private static final Map<String, Price> PRICES = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	/** Price in coins (what it sells for), or null while unknown. Starts a fetch when missing or stale. */
	public static @Nullable Double price(String id) {
		String tag = id.toUpperCase(Locale.ROOT);
		Price p = PRICES.get(tag);
		if (p == null || System.currentTimeMillis() - p.at > FRESH_MS) fetch(tag);
		return p == null ? null : p.value;
	}

	public static void prefetch(String id) {
		price(id);
	}

	private static void fetch(String tag) {
		if (PENDING.putIfAbsent(tag, true) != null) return;
		Thread t = new Thread(() -> {
			try {
				HttpRequest req = HttpRequest.newBuilder(URI.create(String.format(API, tag.replace(";", "%3B"))))
					.timeout(Duration.ofSeconds(15)).header("User-Agent", "blixuAddons").GET().build();
				HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() == 200) {
					JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
					double sell = o.has("sell") ? o.get("sell").getAsDouble() : 0, buy = o.has("buy") ? o.get("buy").getAsDouble() : 0;
					double v = sell > 0 ? sell : buy;
					if (v > 0) PRICES.put(tag, new Price(v, System.currentTimeMillis()));
				}
			} catch (Exception e) {
				BlixuAddons.LOGGER.warn("SkyCofl price for {} failed: {}", tag, e.toString());
			} finally {
				PENDING.remove(tag);
			}
		}, "blixuaddons-price");
		t.setDaemon(true);
		t.start();
	}

	/** "12.4M", "850k", "1,234" coins. */
	public static String coins(double v) {
		if (v >= 1e9) return String.format(Locale.ROOT, "%.2fB", v / 1e9);
		if (v >= 1e6) return String.format(Locale.ROOT, "%.1fM", v / 1e6);
		if (v >= 1e4) return String.format(Locale.ROOT, "%.0fk", v / 1e3);
		return String.format(Locale.ROOT, "%,d", Math.round(v));
	}
}
