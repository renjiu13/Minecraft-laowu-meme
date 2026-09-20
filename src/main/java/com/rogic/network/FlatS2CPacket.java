package com.rogic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端：通知某只猫进入/退出「铲子拍扁」扁平态。
 * flat=true：猫被铲子拍扁（渲染 scale.y 压缩）；flat=false：8 秒到自动恢复原状。
 */
public class FlatS2CPacket {
	public static final Identifier ID = new Identifier("laowu_meme", "flat");

	public final int catId;
	public final boolean flat;

	public FlatS2CPacket(int catId, boolean flat) {
		this.catId = catId;
		this.flat = flat;
	}

	public static void write(FlatS2CPacket pkt, FriendlyByteBuf buf) {
		buf.writeInt(pkt.catId);
		buf.writeBoolean(pkt.flat);
	}

	public static FlatS2CPacket read(FriendlyByteBuf buf) {
		int catId = buf.readInt();
		boolean flat = buf.readBoolean();
		return new FlatS2CPacket(catId, flat);
	}
}
