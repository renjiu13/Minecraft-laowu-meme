package com.rogic.client;

import com.rogic.LaowuMemeMod;
import com.rogic.client.sound.AudioPool;
import com.rogic.client.sound.ModSounds;
import com.rogic.network.FlatS2CPacket;
import com.rogic.network.MaodieS2CPacket;
import com.rogic.network.MemeStopS2CPacket;
import com.rogic.network.MemeTriggerS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端入口：只做收包 + 音频。
 * 锁定/移动/释放全部由服务端驱动，客户端不跑猫 AI、不挂 tick、不处理右键。
 *
 * 1.20.1 版本：使用旧版 ClientPlayNetworking.registerGlobalReceiver，
 * 回调中手动从 FriendlyByteBuf 解码包数据。
 */
public class LaowuMemeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化中...");
		ModSounds.init();
		AudioPool.init();

		// 注册客户端网络包接收器
		ClientPlayNetworking.registerGlobalReceiver(MemeTriggerS2CPacket.ID, (client, handler, buf, responseSender) -> {
			MemeTriggerS2CPacket pkt = MemeTriggerS2CPacket.read(buf);
			client.execute(() ->
					ClientMemeState.get().onTrigger(pkt.catAId, pkt.catBId, pkt.soundId, pkt.rollSign)
			);
		});

		ClientPlayNetworking.registerGlobalReceiver(MemeStopS2CPacket.ID, (client, handler, buf, responseSender) -> {
			MemeStopS2CPacket pkt = MemeStopS2CPacket.read(buf);
			client.execute(() ->
					ClientMemeState.get().onStop(pkt.catAId, pkt.catBId)
			);
		});

		ClientPlayNetworking.registerGlobalReceiver(MaodieS2CPacket.ID, (client, handler, buf, responseSender) -> {
			MaodieS2CPacket pkt = MaodieS2CPacket.read(buf);
			client.execute(() -> {
				if (pkt.bound) {
					ClientMemeState.get().onMaodieBind(pkt.catId);
					LaowuMemeMod.LOGGER.info("[maodie] 收到绑定包 catId={}", pkt.catId);
				} else {
					ClientMemeState.get().onMaodieUnbind(pkt.catId);
					LaowuMemeMod.LOGGER.info("[maodie] 收到解除包 catId={}", pkt.catId);
				}
			});
		});

		// 铲子拍扁：flat=true 压扁渲染，flat=false 恢复
		ClientPlayNetworking.registerGlobalReceiver(FlatS2CPacket.ID, (client, handler, buf, responseSender) -> {
			FlatS2CPacket pkt = FlatS2CPacket.read(buf);
			client.execute(() ->
					ClientMemeState.get().onFlat(pkt.catId, pkt.flat)
			);
		});

		// 每 tick 检查玩家是否靠近耄耋猫，进入半径则播放一次音频
		ClientTickEvents.START_CLIENT_TICK.register(mc -> ClientMemeState.get().tickMaodieAudio());

		LaowuMemeMod.LOGGER.info("[laowu meme] 客户端初始化完成");
	}
}
