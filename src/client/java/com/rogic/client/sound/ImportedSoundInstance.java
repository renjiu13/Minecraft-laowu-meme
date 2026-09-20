package com.rogic.client.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractTickableSoundInstance;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;
import net.minecraft.entity.Entity;

/**
 * 导入音频的循环播放实例：绕过资源系统，直接从磁盘 config/laowu_meme/sounds/<名>.ogg 读取字节流，
 * 由 SoundLibraryMixin 在 getStream 拦截 laowu_meme:sounds/imported/<hex名>.ogg 时提供 JOrbis 解码流。
 * 文件名经 SoundIdCodec hex 编码进 Identifier，规避 [a-z0-9/._-] 限制。
 *
 * 1.20.1 版本：使用 Identifier 而非 Identifier。
 */
public class ImportedSoundInstance extends AbstractTickableSoundInstance {
	private final WeightedSoundSet events;
	private final int catAId, catBId;

	public ImportedSoundInstance(String baseName, int catAId, int catBId) {
		// 用 LAOWU2 仅作构造载体；真正播放的声音由下方 disk Sound 提供（location=imported/<hex>，
		// 经 Sound.getPath() 后变为 sounds/imported/<hex>.ogg，被 mixin 拦截读盘）。
		super(ModSounds.LAOWU2, SoundCategory.NEUTRAL, Random.create());
		Sound sound = new Sound(
				"laowu_meme:imported/" + SoundIdCodec.encode(baseName),
				1.0f,   // volume
				1.0f,   // pitch
				1,
				Sound.Type.SOUND_EVENT,
				true,   // stream：走 SoundLibrary.getStream（被 mixin 拦截）
				false,  // preload
				16);    // 衰减距离
		this.events = new WeightedSoundSet(getLocation(), null);
		this.events.addSound(sound);
		this.catAId = catAId;
		this.catBId = catBId;
		this.looping = true;
		this.delay = 0;
		this.volume = 1.0f;
		// 关闭 MC 自带衰减，改由下方 getVolume() 手动平滑计算，统一所有音频的 16 格衰退
		this.attenuation = SoundInstance.AttenuationType.NONE;
		updatePos();
	}

	@Override
	public WeightedSoundSet resolve(SoundManager manager) {
		// 必须填充超类 this.sound 字段（仿默认实现），否则 getVolume() 在 SoundEngine.play 里 NPE
		this.sound = this.events.getSound(this.random);
		return this.events;
	}

	@Override
	public float getVolume() {
		// 手动平滑距离衰减：0~16 格线性从 1 降到 0，超过 16 格保持 0（静音但不突然停）
		Minecraft mc = MinecraftClient.getInstance();
		if (mc.player == null) return this.volume;
		double dist = Math.sqrt(mc.player.squaredDistanceTo(this.x, this.y, this.z));
		float f = (float) (1.0 - dist / 16.0);
		if (f < 0.0f) f = 0.0f;
		if (f > 1.0f) f = 1.0f;
		return this.volume * f;
	}

	@Override
	public void tick() {
		if (!updatePos()) {
			stop();
		}
	}

	private boolean updatePos() {
		Minecraft mc = MinecraftClient.getInstance();
		if (mc.world == null) return false;
		Entity a = mc.world.getEntity(catAId);
		Entity b = mc.world.getEntity(catBId);
		if (a == null || b == null) return false;
		this.x = (a.getX() + b.getX()) / 2.0;
		this.y = (a.getY() + b.getY()) / 2.0;
		this.z = (a.getZ() + b.getZ()) / 2.0;
		// 玩家离中点超过 32 格时直接停止，避免极远距离仍占声音通道。
		if (mc.player != null && mc.player.squaredDistanceTo(this.x, this.y, this.z) > 32 * 32) {
			return false;
		}
		return true;
	}
}
