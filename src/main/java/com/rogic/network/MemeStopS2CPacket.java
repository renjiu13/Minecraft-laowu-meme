package com.rogic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：通知某两只猫结束锁定状态（右键释放 / 猫消失）。
 * 客户端据此停止歪头渲染与音乐。
 */
public class MemeStopS2CPacket {
	public static final ResourceLocation ID = new ResourceLocation("laowu_meme", "stop");

	public final int catAId;
	public final int catBId;

	public MemeStopS2CPacket(int catAId, int catBId) {
		this.catAId = catAId;
		this.catBId = catBId;
	}

	public static void write(MemeStopS2CPacket pkt, FriendlyByteBuf buf) {
		buf.writeInt(pkt.catAId);
		buf.writeInt(pkt.catBId);
	}

	public static MemeStopS2CPacket read(FriendlyByteBuf buf) {
		int catAId = buf.readInt();
		int catBId = buf.readInt();
		return new MemeStopS2CPacket(catAId, catBId);
	}
}
