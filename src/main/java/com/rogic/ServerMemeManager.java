package com.rogic;

import com.rogic.network.FlatS2CPacket;
import com.rogic.network.MemeStopS2CPacket;
import com.rogic.network.MemeTriggerS2CPacket;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntityEntity;
import net.minecraft.util.Hand;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.util.math.Vec3dd;
import net.minecraft.world.InteractionResult;

import java.util.*;

/**
 * 服务端权威的猫对头状态机。
 * - 扫描：命名"老吴"的猫 + 6 格内任意猫 → 建配对
 * - APPROACHING：两只猫被禁用 AI，平滑走向贴脸点（≈1.3 中心距，头对头、身体不重叠）
 * - LOCKED：冻结在贴脸点、脸对脸，广播 trigger 包（客户端播放歪头+音乐+放大）
 * - 右键其中一只：解除锁定（恢复 AI + 给一点向外速度自然走开），双方进入 3 分钟冷却
 *
 * 所有移动/朝向由服务端驱动，客户端只负责渲染，保证多人一致、无瞬移。
 */
public final class ServerMemeManager {
	public static final String LAOWU_NAME = "老吴";
	public static final double TRIGGER_DISTANCE = 6.0;          // 触发扫描距离
	public static final double LOCK_DISTANCE = 2.0;             // 锁定时两猫中心距（头对头、身体明显分开）
	public static final double SPLIT = LOCK_DISTANCE / 2.0;     // 各自离中点
	public static final double APPROACH_SPEED = 0.14;           // 每 tick 前进距离（≈走路）
	public static final long COOLDOWN_TICKS = 3L * 60 * 20;     // 3 分钟
	public static final int SOUND_LAOWU2 = 0;
	public static final int SOUND_QILIANG = 1;
	public static final int SOUND_ZHANHOU = 2;
	public static final float ROLL_ANGLE = 0.5f;               // 歪头角度（弧度，≈28°）
	/** 铲子拍扁：持续 tick 数（8 秒 = 8*20），到时自动恢复 */
	public static final long FLAT_TICKS = 8L * 20;

	private static final List<MemePair> activePairs = new ArrayList<>();
	private static final Map<UUID, Long> cooldownExpire = new HashMap<>();
	/** 铲子拍扁的猫：entityId -> 开始拍扁时的 tick */
	private static final Map<Integer, Long> flattened = new HashMap<>();
	private static int scanCounter = 0;

	private ServerMemeManager() {}

	/** 每个服务端 tick 推进一次（END_SERVER_TICK） */
	public static void serverTick(MinecraftServer server) {
		scanCounter++;
		if (scanCounter % 10 == 0) scan(server);

		Iterator<MemePair> it = activePairs.iterator();
		while (it.hasNext()) {
			MemePair p = it.next();
			if (!p.alive()) {
				silentStop(p);          // 猫没了，静默停音乐
				it.remove();
				continue;
			}
			p.tick();
		}

		long now = server.getTickCount();
		cooldownExpire.entrySet().removeIf(e -> e.getValue() <= now);

		// 铲子拍扁：到时自动恢复（发 flat=false 包）
		flattened.entrySet().removeIf(e -> {
			if (e.getValue() + FLAT_TICKS <= now) {
				restoreFlat(server, e.getKey());
				return true;
			}
			return false;
		});
	}

	/** 右键猫（带玩家与手）→ 手持铲子则拍扁；否则若在某配对中则释放 */
	public static InteractionResult onRightClick(Cat cat, Player player, Hand hand) {
		if (cat == null) return InteractionResult.PASS;
		LaowuMemeMod.LOGGER.info("[laowu meme] onRightClick: cat={} player={} hand={} item={}",
				cat.getId(), player != null ? player.getName().getString() : "null", hand,
				player != null && player.getItemInHand(hand) != null ? player.getItemInHand(hand).getItem().toString() : "null");
		if (player != null && player.getItemInHand(hand).getItem() instanceof ShovelItem) {
			LaowuMemeMod.LOGGER.info("[laowu meme] 命中铲子，拍扁 catId={}", cat.getId());
			flattenCat(cat);
			return InteractionResult.SUCCESS;
		}
		LaowuMemeMod.LOGGER.info("[laowu meme] 非铲子，走对头释放逻辑 catId={}", cat.getId());
		return onRightClick(cat);
	}

	/** 右键猫 → 若在某配对中则释放 */
	public static InteractionResult onRightClick(Cat cat) {
		if (cat == null) return InteractionResult.PASS;
		MemePair p = findPair(cat.getUUID());
		if (p == null) return InteractionResult.PASS;
		release(p, true);
		activePairs.remove(p);
		return InteractionResult.SUCCESS;
	}

	/** 铲子拍扁：解除对头/耄耋状态，进入扁平态（客户端渲染压扁），8 秒后自动恢复 */
	public static void flattenCat(Cat cat) {
		// 服务端守卫：单机/集成服务器下 UseEntityCallback 在客户端线程也会触发
		if (!(cat.getWorld() instanceof ServerWorld)) return;
		// 解除对头配对（若有）
		MemePair p = findPair(cat.getUUID());
		if (p != null) {
			release(p, false);
			activePairs.remove(p);
		}
		// 解除耄耋绑定（若有）——由 MaodieStructureManager 处理，这里只通知客户端恢复
		int id = cat.getId();
		if (!flattened.containsKey(id)) {
			flattened.put(id, (long) (cat.getWorld() instanceof ServerWorld sl ? sl.getServer().getTickCount() : 0));
			MinecraftServer server = cat.getWorld() instanceof ServerWorld sl2 ? sl2.getServer() : null;
			if (server != null) {
				for (ServerPlayerEntity sp : server.getPlayerList().getPlayers()) {
					sendFlatPacket(sp, id, true);
				}
			}
			LaowuMemeMod.LOGGER.info("[laowu meme] 铲子拍扁：catId={}", id);
		}
	}

	/** 扁平态到期恢复 */
	private static void restoreFlat(MinecraftServer server, int catId) {
		for (ServerPlayerEntity sp : server.getPlayerList().getPlayers()) {
			sendFlatPacket(sp, catId, false);
		}
		LaowuMemeMod.LOGGER.info("[laowu meme] 拍扁恢复：catId={}", catId);
	}

	// ---- 内部 ----

	private static void scan(MinecraftServer server) {
		for (ServerWorld level : server.getAllLevels()) {
			List<? extends Cat> cats = level.getEntities(EntityTypeTest.forClass(Cat.class), c -> true);
			Set<UUID> used = new HashSet<>();
			for (Cat laowu : cats) {
				if (!isLaowu(laowu)) continue;
				UUID id = laowu.getUUID();
				if (used.contains(id) || isActive(id) || onCooldown(id)) continue;

				Cat partner = null;
				double best = TRIGGER_DISTANCE * TRIGGER_DISTANCE;
				for (Cat c : cats) {
					if (c == laowu) continue;
					UUID cid = c.getUUID();
					if (used.contains(cid) || isActive(cid) || onCooldown(cid)) continue;
					double d = laowu.squaredDistanceTo(c);
					if (d <= best) { best = d; partner = c; }
				}
				if (partner != null) {
					startPair(laowu, partner);
					used.add(id);
					used.add(partner.getUUID());
				}
			}
		}
	}

	private static void startPair(Cat a, Cat b) {
		int rollSign = a.getRandom().nextBoolean() ? 1 : -1;
		int soundId = a.getRandom().nextInt(3); // 0=laowu2, 1=qiliang, 2=zhanhou
		activePairs.add(new MemePair(a, b, rollSign, soundId));
		LaowuMemeMod.LOGGER.info("[laowu meme] 配对锁定：{} <-> {}", a.getUUID(), b.getUUID());
	}

	private static void release(MemePair p, boolean giveKnockback) {
		long expire = p.server().getTickCount() + COOLDOWN_TICKS;
		for (Cat c : new Cat[]{p.catA, p.catB}) {
			if (c == null || c.isRemoved()) continue;
			c.setNoAi(false);
			if (giveKnockback) {
				Vec3d away = new Vec3d(c.getX() - p.other(c).getX(), 0, c.getZ() - p.other(c).getZ());
				if (away.lengthSqr() < 1e-4) away = new Vec3d(c.getRandom().nextDouble() - 0.5, 0, c.getRandom().nextDouble() - 0.5);
				away = away.normalize().scale(0.35);
				c.setDeltaMovement(away);
			}
			cooldownExpire.put(c.getUUID(), expire);
		}
		broadcastStop(p);
	}

	private static void silentStop(MemePair p) {
		broadcastStop(p);
	}

	private static void broadcastStop(MemePair p) {
		MemeStopS2CPacket pkt = new MemeStopS2CPacket(p.catAId, p.catBId);
		for (ServerPlayerEntity sp : p.server().getPlayerList().getPlayers()) {
			sendStopPacket(sp, pkt);
		}
	}

	private static void broadcastTrigger(MemePair p) {
		MemeTriggerS2CPacket pkt = new MemeTriggerS2CPacket(p.catAId, p.catBId, p.soundId, p.rollSign);
		for (ServerPlayerEntity sp : p.server().getPlayerList().getPlayers()) {
			sendTriggerPacket(sp, pkt);
		}
	}

	// ---- 网络包发送辅助 ----

	private static void sendTriggerPacket(ServerPlayerEntity player, MemeTriggerS2CPacket pkt) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		MemeTriggerS2CPacket.write(pkt, buf);
		ServerPlayNetworking.send(player, MemeTriggerS2CPacket.ID, buf);
	}

	private static void sendStopPacket(ServerPlayerEntity player, MemeStopS2CPacket pkt) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		MemeStopS2CPacket.write(pkt, buf);
		ServerPlayNetworking.send(player, MemeStopS2CPacket.ID, buf);
	}

	private static void sendFlatPacket(ServerPlayerEntity player, int catId, boolean flat) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		FlatS2CPacket.write(new FlatS2CPacket(catId, flat), buf);
		ServerPlayNetworking.send(player, FlatS2CPacket.ID, buf);
	}

	private static boolean isLaowu(Cat c) {
		return c.getCustomName() != null && LAOWU_NAME.equals(c.getCustomName().getString());
	}
	private static boolean isActive(UUID id) {
		for (MemePair p : activePairs) if (p.has(id)) return true;
		return false;
	}
	private static boolean onCooldown(UUID id) {
		return cooldownExpire.containsKey(id);
	}
	private static MemePair findPair(UUID id) {
		for (MemePair p : activePairs) if (p.has(id)) return p;
		return null;
	}

	// ---- 配对 ----

	static final class MemePair {
		final Cat catA, catB;
		final int catAId, catBId;
		final int rollSign, soundId;
		boolean locked = false;

		MemePair(Cat a, Cat b, int rollSign, int soundId) {
			this.catA = a; this.catB = b;
			this.catAId = a.getId(); this.catBId = b.getId();
			this.rollSign = rollSign; this.soundId = soundId;
		}

		MinecraftServer server() { return catA.getWorld().getServer(); }
		boolean has(UUID id) { return catA.getUUID().equals(id) || catB.getUUID().equals(id); }
		boolean alive() { return !catA.isRemoved() && !catB.isRemoved() && catA.isAlive() && catB.isAlive(); }
		Cat other(Cat c) { return c == catA ? catB : catA; }

		void tick() {
			if (!locked) approachTick();
			else lockTick();
		}

		private void approachTick() {
			catA.setNoAi(true); catB.setNoAi(true);
			catA.setOnGround(true); catB.setOnGround(true);

			Vec3d pa = catA.position(), pb = catB.position();
			Vec3d mid = pa.add(pb).scale(0.5);
			Vec3d dirAB = new Vec3d(pb.x - pa.x, 0, pb.z - pa.z);
			if (dirAB.lengthSqr() < 1e-4) dirAB = new Vec3d(1, 0, 0);
			else dirAB = dirAB.normalize();

			Vec3d targetA = mid.add(dirAB.scale(-SPLIT));
			Vec3d targetB = mid.add(dirAB.scale(SPLIT));

			moveToward(catA, targetA);
			moveToward(catB, targetB);
			faceEachOther();

			if (catA.distanceTo(catB) <= LOCK_DISTANCE + 0.05) {
				locked = true;
				broadcastTrigger(this);
				LaowuMemeMod.LOGGER.info("[laowu meme] 进入锁定：{} <-> {}", catAId, catBId);
			}
		}

		private void lockTick() {
			catA.setNoAi(true); catB.setNoAi(true);
			catA.setOnGround(true); catB.setOnGround(true);

			Vec3d pa = catA.position(), pb = catB.position();
			Vec3d mid = pa.add(pb).scale(0.5);
			Vec3d dirAB = new Vec3d(pb.x - pa.x, 0, pb.z - pa.z);
			if (dirAB.lengthSqr() < 1e-4) dirAB = new Vec3d(1, 0, 0);
			else dirAB = dirAB.normalize();

			Vec3d targetA = mid.add(dirAB.scale(-SPLIT));
			Vec3d targetB = mid.add(dirAB.scale(SPLIT));

			// 轻微吸附，避免漂移
			if (catA.position().squaredDistanceTo(targetA) > 0.0025) catA.setPos(targetA.x, catA.getY(), targetA.z);
			if (catB.position().squaredDistanceTo(targetB) > 0.0025) catB.setPos(targetB.x, catB.getY(), targetB.z);
			faceEachOther();
		}

		private void moveToward(Cat c, Vec3d target) {
			Vec3d cur = c.position();
			double dx = target.x - cur.x, dz = target.z - cur.z;
			double dist = Math.hypot(dx, dz);
			if (dist <= APPROACH_SPEED) {
				c.setPos(target.x, cur.y, target.z);
			} else {
				c.setPos(cur.x + dx / dist * APPROACH_SPEED, cur.y, cur.z + dz / dist * APPROACH_SPEED);
			}
		}

		private void faceEachOther() {
			float yawA = facingYaw(catA.position(), catB.position());
			float yawB = facingYaw(catB.position(), catA.position());
			catA.setYRot(yawA); catA.setYHeadRot(yawA);
			catB.setYRot(yawB); catB.setYHeadRot(yawB);
		}
	}

	private static float facingYaw(Vec3d from, Vec3d to) {
		double dx = to.x - from.x, dz = to.z - from.z;
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}
}
