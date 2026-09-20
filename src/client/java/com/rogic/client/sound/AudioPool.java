package com.rogic.client.sound;

import net.minecraft.sound.SoundEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.MinecraftClient;

/**
 * 客户端本地音频池：管理「可随机播放」的音频集合 + 每条音频的启用/禁用状态。
 * - 固有音频：三段注册表 SoundEvent（[那个那个]/[老吴凄凉]/[战吼]），开箱即用、零操作。
 * - 用户导入：扫描 config/laowu_meme/sounds/*.ogg，触发整活时与固有音频一起随机播放，
 *   直接从磁盘读取（无需 F3+T、不进资源包，由 SoundLibraryMixin 提供字节流）。
 * - 启用/禁用：每条音频可单独启用或禁用，状态持久化到 enabled.properties；
 *   random() 只从启用条目里抽，确保禁用条目永不会被随机出来。
 *
 * 随机播放不依赖服务端指定的 soundId —— 实现「客户端各自随机」——
 * 多人下每只猫的动作仍服务端同步，但各自听到的音频可能不同（各玩各的梗，符合整活定位）。
 */
public class AudioPool {
	/** 固有音频：sound 注册名 -> SoundEvent（保持顺序：laowu2 / qiliang / zhanhou） */
	private static final Map<String, SoundEvent> BUILTINS = new LinkedHashMap<>();
	/** 固有音频：sound 注册名 -> GUI 显示名 */
	public static final Map<String, String> BUILTIN_DISPLAY = new LinkedHashMap<>();
	static {
		BUILTINS.put("laowu2", ModSounds.LAOWU2);
		BUILTINS.put("qiliang", ModSounds.QILIANG);
		BUILTINS.put("zhanhou", ModSounds.ZHANHOU);
		BUILTIN_DISPLAY.put("laowu2", "[那个那个]");
		BUILTIN_DISPLAY.put("qiliang", "[老吴凄凉]");
		BUILTIN_DISPLAY.put("zhanhou", "[战吼]");
	}

	private static final List<String> IMPORTED = new ArrayList<>();
	private static final Set<String> disabledKeys = new LinkedHashSet<>();

	public static void init() {
		disabledKeys.clear();
		refreshImported();  // 先扫磁盘，确保 toBoolMap 能覆盖所有已知 key
		// 从磁盘读回禁用状态：load 只 put 文件里有的 key（即上次禁用的）
		Map<String, Boolean> loaded = new LinkedHashMap<>();
		EnabledConfig.load(loaded);
		for (var e : loaded.entrySet()) {
			if (Boolean.FALSE.equals(e.getValue())) disabledKeys.add(e.getKey());
		}
	}

	/** 重新扫描 config/laowu_meme/sounds/*.ogg（去掉 .ogg 后缀作为显示/匹配名），排序后存入 IMPORTED */
	public static void refreshImported() {
		IMPORTED.clear();
		File dir = getSoundsDir();
		if (!dir.exists()) return;
		File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".ogg"));
		if (files == null) return;
		for (File f : files) {
			IMPORTED.add(stripExt(f.getName()));
		}
		Collections.sort(IMPORTED);
	}

	public static List<String> importedNames() {
		return new ArrayList<>(IMPORTED);
	}

	public static int importedCount() {
		return IMPORTED.size();
	}

	public static int builtinCount() {
		return BUILTINS.size();
	}

	/** 固有 key 列表（顺序与 BUILTINS 一致），用于 UI 列出所有条目 */
	public static List<String> builtinKeys() {
		return new ArrayList<>(BUILTINS.keySet());
	}

	/** 导入 key 列表（顺序与 IMPORTED 一致） */
	public static List<String> importedKeys() {
		List<String> keys = new ArrayList<>(IMPORTED.size());
		for (String n : IMPORTED) keys.add("imported:" + n);
		return keys;
	}

	public static boolean isEnabled(String key) {
		return !disabledKeys.contains(key);
	}

	public static void setEnabled(String key, boolean enabled) {
		if (enabled) disabledKeys.remove(key);
		else disabledKeys.add(key);
		persist();
	}

	/** 翻转 enabled 状态，返回新值 */
	public static boolean toggleEnabled(String key) {
		boolean now = !isEnabled(key);
		setEnabled(key, now);
		return now;
	}

	private static void persist() {
		Map<String, Boolean> map = new LinkedHashMap<>();
		// 把所有 known key 都写一遍（true/false 都写），保证磁盘文件反映完整状态
		for (String k : BUILTINS.keySet()) map.put("builtin:" + k, isEnabled("builtin:" + k));
		for (String n : IMPORTED) map.put("imported:" + n, isEnabled("imported:" + n));
		EnabledConfig.save(map);
	}

	private static Map<String, Boolean> toBoolMap() {
		Map<String, Boolean> m = new LinkedHashMap<>();
		for (String k : BUILTINS.keySet()) m.put("builtin:" + k, !disabledKeys.contains("builtin:" + k));
		for (String n : IMPORTED) m.put("imported:" + n, !disabledKeys.contains("imported:" + n));
		return m;
	}

	/** 从 enabled + imported 合并池随机挑一段（只抽启用的）；全空返回 null */
	public static PlayTarget random() {
		List<PlayTarget> pool = new ArrayList<>();
		for (var e : BUILTINS.entrySet()) {
			if (isEnabled("builtin:" + e.getKey())) {
				pool.add(PlayTarget.builtin(e.getValue()));
			}
		}
		for (String n : IMPORTED) {
			if (isEnabled("imported:" + n)) {
				pool.add(PlayTarget.imported(n));
			}
		}
		if (pool.isEmpty()) return null;
		return pool.get((int) (Math.random() * pool.size()));
	}

	/** 一次随机结果：要么一段固有 SoundEvent，要么一个导入音频名（base name，无 .ogg） */
	public static final class PlayTarget {
		public final SoundEvent event;
		public final String importedName;
		private PlayTarget(SoundEvent event, String importedName) {
			this.event = event;
			this.importedName = importedName;
		}
		public static PlayTarget builtin(SoundEvent event) { return new PlayTarget(event, null); }
		public static PlayTarget imported(String name) { return new PlayTarget(null, name); }
		public boolean isImported() { return importedName != null; }
	}

	public static File getSoundsDir() {
		return new File(getConfigDir(), "sounds");
	}

	public static File getConfigDir() {
		return new File(MinecraftClient.getInstance().runDirectory, "config/laowu_meme");
	}

	private static String stripExt(String name) {
		if (name.toLowerCase().endsWith(".ogg")) return name.substring(0, name.length() - 4);
		return name;
	}
}