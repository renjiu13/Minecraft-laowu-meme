package com.rogic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端：通知某两只猫进入锁定（对头）状态。
 * 携带：两只猫的 entity id、选中的音频 id、歪头方向(±1)。
 *
 * 1.20.1 版本：使用旧版 FriendlyByteBuf 手动编码/解码，不再用 CustomPacketPayload + StreamCodec。
 */
public class MemeTriggerS2CPacket {
	public static final Identifier ID = new Identifier("laowu_meme", "trigger");

	public final int catAId;
	public final int catBId;
	public final int soundId;
	public final int rollSign;

	public MemeTriggerS2CPacket(int catAId, int catBId, int soundId, int rollSign) {
		this.catAId = catAId;
		this.catBId = catBId;
		this.soundId = soundId;
		this.rollSign = rollSign;
	}

	/** 服务端编码：写入 FriendlyByteBuf */
	public static void write(MemeTriggerS2CPacket pkt, FriendlyByteBuf buf) {
		buf.writeInt(pkt.catAId);
		buf.writeInt(pkt.catBId);
		buf.writeInt(pkt.soundId);
		buf.writeInt(pkt.rollSign);
	}

	/** 客户端解码：从 FriendlyByteBuf 读取 */
	public static MemeTriggerS2CPacket read(FriendlyByteBuf buf) {
		int catAId = buf.readInt();
		int catBId = buf.readInt();
		int soundId = buf.readInt();
		int rollSign = buf.readInt();
		return new MemeTriggerS2CPacket(catAId, catBId, soundId, rollSign);
	}
}
