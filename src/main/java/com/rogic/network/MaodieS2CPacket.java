package com.rogic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：通知某只猫进入/退出耄耋绑定状态。
 * bound=true：猫被召到结构锚点，客户端开始"看玩家 + 过近哈气"渲染；
 * bound=false：结构破坏/猫消失，客户端停止渲染、猫恢复自由。
 */
public class MaodieS2CPacket {
	public static final ResourceLocation ID = new ResourceLocation("laowu_meme", "maodie");

	public final int catId;
	public final boolean bound;

	public MaodieS2CPacket(int catId, boolean bound) {
		this.catId = catId;
		this.bound = bound;
	}

	public static void write(MaodieS2CPacket pkt, FriendlyByteBuf buf) {
		buf.writeInt(pkt.catId);
		buf.writeBoolean(pkt.bound);
	}

	public static MaodieS2CPacket read(FriendlyByteBuf buf) {
		int catId = buf.readInt();
		boolean bound = buf.readBoolean();
		return new MaodieS2CPacket(catId, bound);
	}
}
