package com.rogic;

import com.rogic.maodie.MaodieStructureManager;
import com.rogic.network.FlatS2CPacket;
import com.rogic.network.MaodieS2CPacket;
import com.rogic.network.MemeStopS2CPacket;
import com.rogic.network.MemeTriggerS2CPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主入口（* 环境，服务端/客户端都会执行）。
 * 网络包 ID 在各包类中以 Identifier 常量定义，服务端和客户端各自引用。
 * 把服务端逻辑挂到 ServerTick 与右键事件上。
 */
public class LaowuMemeMod implements ModInitializer {
	public static final String MOD_ID = "laowu_meme";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 网络包类型无需显式注册（1.20.1 旧版 API：收发两端用同一个 Identifier 即可）

		// 服务端每 tick 推进猫的状态机
		ServerTickEvents.END_SERVER_TICK.register(server -> ServerMemeManager.serverTick(server));
		// 耄耋多方块结构：每 tick 扫描 / 召猫 / 破坏检测
		ServerTickEvents.END_SERVER_TICK.register(server -> MaodieStructureManager.serverTick(server));

		// 右键猫 → 手持铲子拍扁；否则若在对头配对中则释放（服务端权威）
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			// 客户端线程不处理（单机集成服务器下客户端事件也会触发，交给服务端线程）
			if (world.isClientSide) return InteractionResult.PASS;
			return ServerMemeManager.onRightClick(entity instanceof Cat c ? c : null, player, hand);
		});

		LOGGER.info("[laowu meme] 服务端初始化完成（服务端权威架构）");
	}
}
