package com.rogic.client.sound;

import com.rogic.LaowuMemeMod;
import com.rogic.client.sound.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractTickableSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;
import net.minecraft.entity.Entity;

/**
 * 耄耋猫靠近时循环播放的「哈气」音效实例：跟随猫的位置，循环播放。
 * 玩家离开 3 格范围由 ClientMemeState 调用 stop() 停止（而非进范围只播一次）。
 *
 * 与 MemeSoundInstance 同一范式（循环 + 跟随实体），但此处声音是打包进 jar 的普通 ogg
 * （maodie.ogg），MC 自带 16 格距离衰减已验证可用（用户实测能听到），故不关闭衰减、
 * 也不手动改音量——行为与原先「进范围播一次」的用户体感一致，只是改成持续循环。
 */
public class MaodieSoundInstance extends AbstractTickableSoundInstance {
	private final int catId;

	public MaodieSoundInstance(int catId) {
		super(ModSounds.MAODIE, SoundCategory.NEUTRAL, Random.create());
		this.catId = catId;
		this.looping = true;
		this.delay = 0;
		this.volume = 1.0f;
		// 不覆盖 attenuation：依赖 MC 对打包 ogg 的自带衰减（16 格），玩家在 3 格内近乎满音量。
		updatePos();
	}

	@Override
	public void tick() {
		if (!updatePos()) {
			stop();
		}
	}

	/** 跟随目标猫；猫消失/无效则停。 */
	private boolean updatePos() {
		Minecraft mc = MinecraftClient.getInstance();
		if (mc.world == null) return false;
		Entity e = mc.world.getEntity(catId);
		if (e == null) return false;
		this.x = e.getX();
		this.y = e.getY();
		this.z = e.getZ();
		return true;
	}
}
