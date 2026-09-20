package com.rogic.client;

import com.rogic.LaowuMemeMod;
import com.rogic.client.sound.AudioPool;
import com.rogic.client.sound.ImportedSoundInstance;
import com.rogic.client.sound.MaodieSoundInstance;
import com.rogic.client.sound.MemeSoundInstance;
import com.rogic.client.sound.ModSounds;
import com.rogic.maodie.MaodieBlueprint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3dd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * 客户端状态：收包驱动。记录哪些猫在对头效果中（携带音频 id 与歪头方向），
 * 并管理循环音频的播放/停止。渲染 mixin 通过 isActive / getRollSign 读取。
 */
public class ClientMemeState {
	public static final int SOUND_LAOWU2 = 0;
	public static final int SOUND_QILIANG = 1;

	private static final ClientMemeState INSTANCE = new ClientMemeState();
	public static ClientMemeState get() { return INSTANCE; }

	public static final class ActiveCat {
		public int partnerId;
		public int soundId;
		public int rollSign;
	}

	private final Map<Integer, ActiveCat> active = new HashMap<>();
	private final Map<String, SoundInstance> sounds = new HashMap<>();
	private final Map<Integer, Boolean> maodieBound = new HashMap<>();
	/** 正在循环播放哈气音效的猫：key=catId，value=循环音效实例。进入半径起播、离开半径停止。 */
	private final Map<Integer, MaodieSoundInstance> maodieSounds = new HashMap<>();
	/** 铲子拍扁的猫：渲染时 scale.y 压扁 */
	private final java.util.Set<Integer> flattened = new java.util.HashSet<>();

	public boolean isFlattened(int id) { return flattened.contains(id); }
	public void onFlat(int catId, boolean flat) {
		if (flat) {
			flattened.add(catId);
		} else {
			flattened.remove(catId);
		}
		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端拍扁状态：catId={} flat={}", catId, flat);
	}

	public boolean isMaodieBound(int id) { return maodieBound.containsKey(id); }
	public void onMaodieBind(int catId) {
		maodieBound.put(catId, true);
		LaowuMemeMod.LOGGER.info("[maodie] 客户端记录绑定 catId={}", catId);
	}
	public void onMaodieUnbind(int catId) {
		maodieBound.remove(catId);
		stopMaodieSound(catId);
		LaowuMemeMod.LOGGER.info("[maodie] 客户端解除绑定 catId={}", catId);
	}

	/**
	 * 玩家靠近耄耋猫时由客户端每 tick 调用：在 MAODIE_PROXIMITY_RADIUS（3 格）内则持续循环播放哈气，
	 * 离开范围即停止，直到再次进入才重新起播。
	 */
	public void tickMaodieAudio() {
		Minecraft mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		Player player = mc.player;
		double r = MaodieBlueprint.MAODIE_PROXIMITY_RADIUS;
		// 复制 keySet 避免迭代中改动 map
		for (int catId : new HashSet<>(maodieBound.keySet())) {
			Entity e = mc.world.getEntity(catId);
			if (e == null) { stopMaodieSound(catId); continue; }
			boolean near = player.squaredDistanceTo(e.position()) <= r * r;
			MaodieSoundInstance inst = maodieSounds.get(catId);
			if (near && inst == null) {
				startMaodieSound(catId);
			} else if (!near && inst != null) {
				stopMaodieSound(catId);
			}
		}
	}

	/** 起一个跟随猫的循环哈气音效。 */
	private void startMaodieSound(int catId) {
		Minecraft mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;
		MaodieSoundInstance inst = new MaodieSoundInstance(catId);
		maodieSounds.put(catId, inst);
		mc.getSoundManager().play(inst);
		LaowuMemeMod.LOGGER.info("[maodie] 起循环哈气音效 catId={}", catId);
	}

	/** 停止并移除某猫的循环哈气音效。 */
	private void stopMaodieSound(int catId) {
		MaodieSoundInstance inst = maodieSounds.remove(catId);
		if (inst != null) {
			MinecraftClient.getInstance().getSoundManager().stop(inst);
			LaowuMemeMod.LOGGER.info("[maodie] 停循环哈气音效 catId={}", catId);
		}
	}

	public boolean isActive(int entityId) { return active.containsKey(entityId); }
	public int getRollSign(int entityId) {
		ActiveCat a = active.get(entityId);
		return a == null ? 0 : a.rollSign;
	}

	/** 收到服务端 trigger 包：记录两只猫并起音乐 */
	public void onTrigger(int catAId, int catBId, int soundId, int rollSign) {
		ActiveCat sa = new ActiveCat(); sa.partnerId = catBId; sa.soundId = soundId; sa.rollSign = rollSign;
		ActiveCat sb = new ActiveCat(); sb.partnerId = catAId; sb.soundId = soundId; sb.rollSign = rollSign;
		active.put(catAId, sa);
		active.put(catBId, sb);
		startSound(catAId, catBId, soundId);
	}

	/** 收到服务端 stop 包：清状态 + 停音乐 */
	public void onStop(int catAId, int catBId) {
		active.remove(catAId);
		active.remove(catBId);
		stopSound(catAId, catBId);
	}

	private String key(int a, int b) { return Math.min(a, b) + "-" + Math.max(a, b); }

	private void startSound(int catAId, int catBId, int soundId) {
		Minecraft mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) return;
		Vec3d mid = midOf(catAId, catBId);
		if (mid == null) return;
		// 不再用 >16 格硬限制：只要猫在配对就起播，音量交给 MC 自身衰减
		// （导入音频衰减 16 格 / 固有音频默认衰减）。之前 >16 不播放是「整活却没声」的主因。
		// 同一对猫重复触发时，先停掉旧实例再起新的，避免两实例叠加 / 旧实例泄漏。
		SoundInstance old = sounds.get(key(catAId, catBId));
		if (old != null) mc.getSoundManager().stop(old);
		AudioPool.refreshImported();
		AudioPool.PlayTarget target = AudioPool.random();
		if (target == null) return;
		SoundInstance inst;
		if (target.isImported()) inst = new ImportedSoundInstance(target.importedName, catAId, catBId);
		else inst = new MemeSoundInstance(target.event, catAId, catBId);
		sounds.put(key(catAId, catBId), inst);
		mc.getSoundManager().play(inst);
	}

	private void stopSound(int catAId, int catBId) {
		SoundInstance inst = sounds.remove(key(catAId, catBId));
		if (inst != null) MinecraftClient.getInstance().getSoundManager().stop(inst);
	}

	private Vec3d midOf(int a, int b) {
		Minecraft mc = MinecraftClient.getInstance();
		if (mc.world == null) return null;
		Entity ea = mc.world.getEntity(a), eb = mc.world.getEntity(b);
		if (ea == null || eb == null) return null;
		return ea.position().add(eb.position()).scale(0.5);
	}
}
