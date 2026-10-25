package com.blixu.blixuaddons.client.feature.slayer;

import com.blixu.blixuaddons.BlixuAddons;
import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SkyBlock item looks from the NEU repo (NotEnoughUpdates-REPO items/ID.json): the base item, the skull
 * texture for head items and the enchant glint. Fetched once per id, in the background, only when a
 * rare drop needs it; the ItemStack is built on the render thread from the cached data.
 */
public final class NeuItems {
	private NeuItems() {}

	private static final String REPO = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Pattern TEXTURE = Pattern.compile("Value:\\\\?\"([A-Za-z0-9+/=]+)\\\\?\"");
	private static final Pattern SKULL_ID = Pattern.compile("SkullOwner:\\{Id:\\\\?\"([0-9a-fA-F-]{36})\\\\?\"");

	private record Look(String itemId, @Nullable String texture, @Nullable String skullId, boolean glint) {}

	private static final Map<String, Look> LOOKS = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();
	private static final Map<String, ItemStack> STACKS = new ConcurrentHashMap<>();

	/** The item as it looks in SkyBlock, or null while it's still being fetched (or the repo doesn't have it). */
	public static @Nullable ItemStack stack(String skyblockId) {
		String id = repoId(skyblockId);
		ItemStack cached = STACKS.get(id);
		if (cached != null) return cached;
		Look look = LOOKS.get(id);
		if (look == null) {
			fetch(id);
			return null;
		}
		ItemStack s = build(look);
		STACKS.put(id, s);
		return s;
	}

	/** NEU names runes / enchanted books like ENDERSNAKE_RUNE;1 → ENDERSNAKE_RUNE-1. */
	private static String repoId(String id) {
		return id.replace(';', '-').replace(':', '-');
	}

	private static void fetch(String id) {
		if (PENDING.putIfAbsent(id, true) != null) return;
		Thread t = new Thread(() -> {
			try {
				HttpRequest req = HttpRequest.newBuilder(URI.create(REPO + id + ".json")).timeout(Duration.ofSeconds(15))
					.header("User-Agent", "blixuAddons").GET().build();
				HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() != 200) return;
				JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
				String nbt = o.has("nbttag") ? o.get("nbttag").getAsString() : "";
				Matcher tex = TEXTURE.matcher(nbt), uid = SKULL_ID.matcher(nbt);
				LOOKS.put(id, new Look(o.has("itemid") ? o.get("itemid").getAsString() : "minecraft:paper",
					tex.find() ? tex.group(1) : null, uid.find() ? uid.group(1) : null, nbt.contains("ench:")));
			} catch (Exception e) {
				BlixuAddons.LOGGER.warn("NEU repo lookup for {} failed: {}", id, e.toString());
			}
		}, "blixuaddons-neu");
		t.setDaemon(true);
		t.start();
	}

	private static ItemStack build(Look look) {
		Item item;
		if (look.itemId.equals("minecraft:skull") || look.texture != null) item = Items.PLAYER_HEAD;
		else {
			Identifier key = Identifier.tryParse(look.itemId);
			item = key == null ? Items.PAPER : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.PAPER);
		}
		ItemStack s = new ItemStack(item);
		if (item == Items.PLAYER_HEAD && look.texture != null) {
			UUID uuid = look.skullId != null ? UUID.fromString(look.skullId) : UUID.nameUUIDFromBytes(look.texture.getBytes());
			PropertyMap props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", look.texture)));
			s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(uuid, "blixu", props)));
		}
		if (look.glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return s;
	}
}
