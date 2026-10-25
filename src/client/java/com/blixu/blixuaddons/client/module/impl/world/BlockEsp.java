package com.blixu.blixuaddons.client.module.impl.world;

import com.blixu.blixuaddons.client.module.Category;
import com.blixu.blixuaddons.client.module.Module;
import com.blixu.blixuaddons.client.module.setting.BoolSetting;
import com.blixu.blixuaddons.client.module.setting.ColorSetting;
import com.blixu.blixuaddons.client.module.setting.EnumSetting;
import com.blixu.blixuaddons.client.module.setting.NumberSetting;
import com.blixu.blixuaddons.client.module.setting.StringSetting;
import com.blixu.blixuaddons.client.render.Shape;
import com.blixu.blixuaddons.client.render.WorldCtx;
import com.blixu.blixuaddons.client.style.Colors;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Highlights chosen blocks in loaded chunks only, and only blocks with at least one open
 * face, using the Mob Highlight glow style (shared outline buffer, which draws through walls). Scanning is spread over ticks (a few sections per tick).
 */
public class BlockEsp extends Module {
	public final StringSetting blocks = add(new StringSetting("blocks", "Blocks (names, comma separated)", "Diamond Ore, Emerald Ore, Chest", 512));
	public final ColorSetting color = add(new ColorSetting("color", "Glow colour", 0x22D3EE));
	public final com.blixu.blixuaddons.client.feature.GlowStyle.Look glowLook = com.blixu.blixuaddons.client.feature.GlowStyle.addLook(this::add, com.blixu.blixuaddons.client.feature.GlowStyle.ESP, () -> true).withDesign(this::add, () -> true, 1.0);
	public final NumberSetting range = add(new NumberSetting("range", "Max distance", 48, 8, 128, 1));
	public final NumberSetting maxBlocks = add(new NumberSetting("max_blocks", "Max highlighted blocks", 200, 10, 1000, 10));
	public final BoolSetting exposedOnly = add(new BoolSetting("exposed_only", "Only blocks with an open face", false));


	/** Section key → packed (pos long, category) pairs. */
	private final Long2ObjectOpenHashMap<long[]> found = new Long2ObjectOpenHashMap<>();
	private final Map<BlockState, Integer> cache = new IdentityHashMap<>();
	private final List<TagKey<Block>> customTags = new ArrayList<>();
	private final List<Block> customBlocks = new ArrayList<>();
	private String parsedList = null;
	private int scanIndex;

	public BlockEsp() {
		super("block_esp", "Block ESP",
			"Glow the blocks you list by name, in the Mob Highlight style (through walls).",
			Category.VISUALS, "cube", false);
		blocks.onChange(v -> resetScan());
	}

	private void resetScan() {
		cache.clear();
		found.clear();
		scanIndex = 0;
	}

	private final List<String> names = new ArrayList<>();

	/** 0 = on the whitelist, -1 = not. Matches the block's display name or id, ignoring case. */
	private int categoryOf(BlockState s) {
		Integer c = cache.get(s);
		if (c != null) return c;
		if (!blocks.get().equals(parsedList)) parseList();
		Block block = s.getBlock();
		String name = block.getName().getString().toLowerCase(java.util.Locale.ROOT);
		String id = BuiltInRegistries.BLOCK.getKey(block).getPath().replace('_', ' ');
		int cat = -1;
		for (String n : names) {
			if (name.equals(n) || id.equals(n) || name.contains(n) && n.length() >= 4) {
				cat = 0;
				break;
			}
		}
		cache.put(s, cat);
		return cat;
	}

	private void parseList() {
		parsedList = blocks.get();
		names.clear();
		for (String raw : parsedList.split(",")) {
			String n = raw.trim().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
			if (n.startsWith("minecraft:")) n = n.substring(10);
			if (!n.isEmpty()) names.add(n);
		}
	}

	/** Blocks from the whitelist, for the preview icons. */
	public List<Block> listedBlocks() {
		if (!blocks.get().equals(parsedList)) parseList();
		List<Block> out = new ArrayList<>();
		for (String n : names) {
			for (Block b : BuiltInRegistries.BLOCK) {
				String name = b.getName().getString().toLowerCase(java.util.Locale.ROOT);
				if (name.equals(n) || BuiltInRegistries.BLOCK.getKey(b).getPath().replace('_', ' ').equals(n)) {
					out.add(b);
					break;
				}
			}
			if (out.size() >= 4) break;
		}
		return out;
	}

	@Override
	public void onTick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) return;
		int r = Mth.ceil(range.get() / 16.0) + 1;
		int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
		int side = r * 2 + 1;
		int total = side * side;
		Predicate<BlockState> any = s -> !s.isAir() && categoryOf(s) >= 0;
		// Scan a few chunk columns per tick, cycling through the area around the player.
		for (int n = 0; n < 3; n++) {
			int i = scanIndex++ % total;
			int cx = pcx - r + i % side, cz = pcz - r + i / side;
			LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
			if (chunk == null) continue;
			LevelChunkSection[] sections = chunk.getSections();
			for (int si = 0; si < sections.length; si++) {
				int sy = level.getSectionYFromSectionIndex(si);
				long key = SectionPos.asLong(cx, sy, cz);
				LevelChunkSection sec = sections[si];
				if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(any)) {
					found.remove(key);
					continue;
				}
				found.put(key, scanSection(level, sec, cx, sy, cz));
			}
		}
		if (scanIndex % total == 0) {
			// drop far sections once per full cycle
			found.long2ObjectEntrySet().removeIf(e -> Math.abs(SectionPos.x(e.getLongKey()) - pcx) > r + 1 || Math.abs(SectionPos.z(e.getLongKey()) - pcz) > r + 1);
		}
	}

	private long[] scanSection(ClientLevel level, LevelChunkSection sec, int cx, int sy, int cz) {
		long[] out = new long[16];
		int n = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
			BlockState s = sec.getBlockState(x, y, z);
			if (s.isAir()) continue;
			int cat = categoryOf(s);
			if (cat < 0) continue;
			pos.set((cx << 4) + x, (sy << 4) + y, (cz << 4) + z);
			if (exposedOnly.get() && !exposed(level, pos)) continue;
			if (n + 2 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
			out[n++] = pos.asLong();
			out[n++] = cat;
		}
		return java.util.Arrays.copyOf(out, n);
	}

	private static boolean exposed(ClientLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (Direction d : Direction.values()) {
			p.setWithOffset(pos, d);
			if (!level.getBlockState(p).isSolidRender()) return true;
		}
		return false;
	}

	private int colorFor(int cat) {
		return color.argb();
	}

	// Reused per-frame buffers (no per-block allocation while drawing).
	private long[] dPos = new long[1024];
	private int[] dCat = new int[1024];
	private double[] dDist = new double[1024];

	@Override
	public void onWorldRender(WorldCtx ctx) {
		double max2 = range.get() * range.get();
		int count = 0;
		for (long[] arr : found.values()) {
			for (int i = 0; i + 1 < arr.length; i += 2) {
				long p = arr[i];
				double dx = BlockPos.getX(p) + 0.5 - ctx.cam.x, dy = BlockPos.getY(p) + 0.5 - ctx.cam.y, dz = BlockPos.getZ(p) + 0.5 - ctx.cam.z;
				double d2 = dx * dx + dy * dy + dz * dz;
				if (d2 > max2) continue;
				if (count == dPos.length) {
					dPos = java.util.Arrays.copyOf(dPos, count * 2);
					dCat = java.util.Arrays.copyOf(dCat, count * 2);
					dDist = java.util.Arrays.copyOf(dDist, count * 2);
				}
				dPos[count] = p;
				dCat[count] = (int) arr[i + 1];
				dDist[count] = d2;
				count++;
			}
		}
		int limit = Math.min(maxBlocks.getI(), count);
		if (count > limit) selectNearest(count, limit);
		for (int i = 0; i < limit; i++) {
			long p = dPos[i];
			int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
			int oc = com.blixu.blixuaddons.client.feature.GlowStyle.encode(colorFor(dCat[i]), 1f, com.blixu.blixuaddons.client.feature.GlowStyle.ESP);
			ctx.boxFill(ctx.outline, x, y, z, x + 1, y + 1, z + 1, oc, oc);
		}
	}


	/** Quickselect: moves the {@code k} nearest entries to the front (order among them not sorted). */
	private void selectNearest(int n, int k) {
		int lo = 0, hi = n - 1;
		java.util.Random rnd = java.util.concurrent.ThreadLocalRandom.current();
		while (lo < hi) {
			int pi = lo + rnd.nextInt(hi - lo + 1);
			double pivot = dDist[pi];
			swap(pi, hi);
			int store = lo;
			for (int i = lo; i < hi; i++) if (dDist[i] < pivot) swap(i, store++);
			swap(store, hi);
			if (store == k) return;
			if (store < k) lo = store + 1; else hi = store - 1;
		}
	}

	private void swap(int a, int b) {
		long p = dPos[a]; dPos[a] = dPos[b]; dPos[b] = p;
		int c = dCat[a]; dCat[a] = dCat[b]; dCat[b] = c;
		double d = dDist[a]; dDist[a] = dDist[b]; dDist[b] = d;
	}

	@Override
	protected void onDisable() {
		found.clear();
	}

	@Override
	public void onWorldChange() {
		resetScan();
	}

	private List<Block> previewBlocks = List.of();
	private String previewList;

	@Override
	public Preview preview() {
		return Preview.FLAT;
	}

	@Override
	public void drawPreview(net.minecraft.client.gui.GuiGraphicsExtractor g, float x, float y, float w, float h, float time) {
		if (!blocks.get().equals(previewList)) {
			previewList = blocks.get();
			previewBlocks = listedBlocks();
		}
		var mh = com.blixu.blixuaddons.client.module.ModuleManager.get(com.blixu.blixuaddons.client.module.impl.MobHighlight.class);
		int n = previewBlocks.size();
		if (n == 0) {
			com.blixu.blixuaddons.client.render.Ui.textCentered(g, "No block matches your list", x + w / 2, y + h / 2 - 4, 0xFF9097AB, com.blixu.blixuaddons.client.render.UiFont.SMALL);
			return;
		}
		float size = 26, gap = 10;
		float total = n * size + (n - 1) * gap;
		float sx = x + (w - total) / 2, sy = y + h / 2 - size / 2;
		float[][] parts = new float[n][];
		for (int i = 0; i < n; i++) parts[i] = new float[] {sx + i * (size + gap), sy, size, size, 2};
		com.blixu.blixuaddons.client.render.GlowPreview.paint(g, glowLook, color.argb(), time, parts, 0);
		for (int i = 0; i < n; i++) {
			g.pose().pushMatrix();
			g.pose().translate(parts[i][0] + size / 2, parts[i][1] + size / 2);
			g.pose().scale(1.4f, 1.4f);
			g.item(new net.minecraft.world.item.ItemStack(previewBlocks.get(i).asItem()), -8, -8);
			g.pose().popMatrix()
;
		}
		com.blixu.blixuaddons.client.render.Ui.text(g, "Aura glow", x + 6, y + h - 11, 0xFF9097AB, com.blixu.blixuaddons.client.render.UiFont.SMALL);
	}
}
