package com.rogic.client.mixin;

import com.rogic.LaowuMemeMod;
import com.rogic.client.ClientMemeState;
import com.rogic.maodie.MaodieBlueprint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.CatEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在猫模型 setupAnim 的 TAIL：
 *  - 老吴整活：head.zRot 歪头（绕 Z 轴）+ 弓背哈气（head 低头 + body 微弓 + tail2 翘）。
 *  - 耄耋猫：猫转头盯最近的玩家（head.yRot/xRot），同样放 TAIL 避免被原版覆盖。
 *
 * 1.20.1 版本：直接从 ClientMemeState 按 entity id 读取状态（没有 RenderState 系统）。
 * 目标类是 CatEntityModel（1.20.1 不分 Adult/Baby FelineModel，统一是 CatEntityModel）。
 */
@Mixin(CatEntityModel.class)
public class CatEntityModelMixin {

	/** 歪头角度：45°，roll 为 ±1，相乘得镜像歪头 */
	private static final float HEAD_ROLL = (float) (Math.PI / 4.0);
	/** 弓背哈气：头下低（绕 X 轴，正值=头端朝下、低头哈气） */
	private static final float HEAD_DIP = 0.3f;
	/** 弓背哈气：身体仅微弓（绕 X 轴） */
	private static final float BODY_PITCH = 0.10f;
	/** 弓背哈气：尾巴翘起 */
	private static final float TAIL_LIFT = 0.9f;
	/** 耄耋头瞄准诊断日志节流计数器（每 32 tick 打一条） */
	private static long maodieHeadDebugTick = 0;
	/** 腿形变（yScale）补偿 */
	private static final float HIND_SCALE = 1.4f;
	private static final float FRONT_SCALE = 0.85f;
	private static final float LEG_SCALE_DEFAULT = 1.0f;
	/** 拍扁"X"形：四肢外撇角度 */
	private static final float FLAT_LEG_SPLAY = 0.96f;
	/** 拍扁"X"形：四肢拉长倍数 */
	private static final float FLAT_LEG_STRETCH = 3.0f;

	@Inject(method = "setAngles(Lnet/minecraft/entity/passive/CatEntity;FFFFF)V", at = @At("TAIL"), require = 0)
	private void laowuTilt(CatEntity cat, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		try {
			ClientMemeState cs = ClientMemeState.get();
			int id = cat.getId();
			boolean active = cs.isActive(id);
			float roll = cs.getRollSign(id);
			boolean maodieBound = cs.isMaodieBound(id);
			boolean flat = cs.isFlattened(id);

			// 通过 root 获取各部件
			ModelPart root = ((CatEntityModel) (Object) this).root();
			if (root == null) {
				return;
			}

			ModelPart leftHind = root.getChild("left_hind_leg");
			ModelPart rightHind = root.getChild("right_hind_leg");
			ModelPart leftFront = root.getChild("left_front_leg");
			ModelPart rightFront = root.getChild("right_front_leg");

			// 非老吴整活：腿形变复位
			if (!active) {
				if (leftHind != null) leftHind.yScale = LEG_SCALE_DEFAULT;
				if (rightHind != null) rightHind.yScale = LEG_SCALE_DEFAULT;
				if (leftFront != null) leftFront.yScale = LEG_SCALE_DEFAULT;
				if (rightFront != null) rightFront.yScale = LEG_SCALE_DEFAULT;
			}

			if (active) {
				// 老吴整活：歪头 + 低头哈气 + 弓背 + 翘尾 + 腿形变
				ModelPart head = root.getChild("head");
				if (head != null) {
					head.zRot = roll * HEAD_ROLL;
					head.xRot += HEAD_DIP;
				}
				ModelPart body = root.getChild("body");
				if (body != null) body.xRot += BODY_PITCH;
				ModelPart tail2 = root.getChild("tail2");
				if (tail2 != null) tail2.xRot += TAIL_LIFT;
				if (leftHind != null) leftHind.yScale = HIND_SCALE;
				if (rightHind != null) rightHind.yScale = HIND_SCALE;
				if (leftFront != null) leftFront.yScale = FRONT_SCALE;
				if (rightFront != null) rightFront.yScale = FRONT_SCALE;
			}

			// 耄耋猫：玩家进入 5 格范围时转头盯最近玩家
			if (maodieBound) {
				ModelPart head = root.getChild("head");
				if (head != null) {
					Minecraft mc = MinecraftClient.getInstance();
					if (mc.world != null) {
						double cx = cat.getX(), cy = cat.getY(), cz = cat.getZ();
						double best = Double.MAX_VALUE;
						Player nearest = null;
						for (Player p : mc.world.players()) {
							double d = p.squaredDistanceTo(cx, cy, cz);
							if (d < best) { best = d; nearest = p; }
						}
						if (nearest != null) {
							double dist = Math.sqrt(best);
							if (dist <= MaodieBlueprint.MAODIE_PROXIMITY_RADIUS) {
								double dx = nearest.getX() - cx;
								double dz = nearest.getZ() - cz;
								double dy = nearest.getY() - cy;
								// 目标 yaw（弧度）
								double targetYaw = Math.atan2(-dx, -dz);
								// 模型整体被渲染器按 (180 - bodyYaw)° 旋转
								double modelRot = Math.toRadians(180.0 - cat.yBodyRot);
								// head.yRot 为相对量
								float desiredYaw = (float) (targetYaw - modelRot);
								// 俯仰
								double horiz = Math.sqrt(dx * dx + dz * dz);
								float desiredPitch = (float) Math.atan2(-dy, horiz);
								// 最短路径插值
								head.yRot = lerpAngleShortest(head.yRot, desiredYaw, 0.4f);
								head.xRot = lerpAngleShortest(head.xRot, desiredPitch, 0.4f);
								if ((maodieHeadDebugTick++ & 0x1FL) == 0) {
									LaowuMemeMod.LOGGER.info("[maodie-head] dist={} bodyRot={} targetYaw={} modelRot={} desiredYaw={} head.yRot={} head.xRot={}",
											String.format("%.2f", dist), cat.yBodyRot, targetYaw, modelRot, desiredYaw, head.yRot, head.xRot);
								}
							}
						}
					}
				}
			}

			// 铲子拍扁：四肢拉长 + 对角交叉外撇
			if (flat) {
				if (leftHind != null) { leftHind.zRot = -FLAT_LEG_SPLAY; leftHind.yScale = FLAT_LEG_STRETCH; }
				if (rightHind != null) { rightHind.zRot = FLAT_LEG_SPLAY; rightHind.yScale = FLAT_LEG_STRETCH; }
				if (leftFront != null) { leftFront.zRot = FLAT_LEG_SPLAY; leftFront.yScale = FLAT_LEG_STRETCH; }
				if (rightFront != null) { rightFront.zRot = -FLAT_LEG_SPLAY; rightFront.yScale = FLAT_LEG_STRETCH; }
			} else {
				// 非扁平态：腿 yScale 复位（xRot 由原版 setupAnim 每 tick 覆盖）
				if (leftHind != null && !active) leftHind.yScale = LEG_SCALE_DEFAULT;
				if (rightHind != null && !active) rightHind.yScale = LEG_SCALE_DEFAULT;
				if (leftFront != null && !active) leftFront.yScale = LEG_SCALE_DEFAULT;
				if (rightFront != null && !active) rightFront.yScale = LEG_SCALE_DEFAULT;
			}
		} catch (Throwable t) {
			// 静默兜底，绝不崩渲染器
		}
	}

	/** 最短路径角度插值（弧度） */
	private static float lerpAngleShortest(float cur, float target, float t) {
		float diff = target - cur;
		diff = (float) (diff - 2.0 * Math.PI * Math.floor((diff + Math.PI) / (2.0 * Math.PI)));
		return cur + diff * t;
	}
}
