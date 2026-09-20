package com.rogic.client.sound;

import com.rogic.LaowuMemeMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Registry;

/**
 * 注册两条音频的 SoundEvent：老吴2、凄凉。
 * 对应资源在 assets/laowu_meme/sounds/laowu2.ogg、qiliang.ogg，sounds.json 里定义。
 *
 * 1.20.1 版本：使用 SoundEvent.of() 和 BuiltInRegistries.SOUND_EVENT 注册。
 */
public class ModSounds {
	public static SoundEvent LAOWU2;
	public static SoundEvent QILIANG;
	public static SoundEvent ZHANHOU;
	public static SoundEvent MAODIE;

	public static void init() {
		LAOWU2 = register("laowu2");
		QILIANG = register("qiliang");
		ZHANHOU = register("zhanhou");
		MAODIE = register("maodie");
	}

	private static SoundEvent register(String name) {
		ResourceLocation id = new ResourceLocation(LaowuMemeMod.MOD_ID, name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, new SoundEvent(id));
	}
}
