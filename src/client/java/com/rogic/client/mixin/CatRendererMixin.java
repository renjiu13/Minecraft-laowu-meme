package com.rogic.client.mixin;

import net.minecraft.client.util.math.MatrixStack;
import com.rogic.client.ClientMemeState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.CatEntityRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.entity.passive.CatEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.20.1 版本 CatEntityRendererMixin：
 *  1) 对锁定猫放大 1.25 倍（注入 render 方法，缩放 MatrixStack）
 *  2) 命名"耄耋"换皮、命名"奶猫"换皮（注入 getTexture）
 *  3) 铲子拍扁 y 轴压扁（注入 render 方法）
 *
 * 由于 1.20.1 没有 RenderState 系统，所有状态直接从 ClientMemeState 按 entity id 读取。
 */
@Mixin(CatEntityRenderer.class)
public class CatEntityRendererMixin {

	/**
	 * 纹理替换：命名"耄耋"→ 换成对应花色的哈基米贴图；命名"奶猫"→ 换成奶猫贴图。
	 * 幼猫不换皮（保持原版）。
	 * TAIL 注入：先获取原版返回值，再替换成我们的贴图，避免递归调用。
	 */
	@Inject(method = "getTexture(Lnet/minecraft/entity/passive/CatEntity;)Lnet/minecraft/util/Identifier;", at = @At("TAIL"), cancellable = true, require = 0)
	private void laowuMaodieTexture(CatEntity cat, CallbackInfoReturnable<Identifier> cir) {
		boolean baby = cat.isBaby();
		if (baby) return;

		// 奶猫换皮优先级最高
		boolean milkcat = cat.getCustomName() != null && "奶猫".equals(cat.getCustomName().getString());
		if (milkcat) {
			cir.setReturnValue(new Identifier("laowu_meme", "textures/entity/cat/cat_milkcat.png"));
			return;
		}

		// 耄耋换皮
		boolean maodie = cat.getCustomName() != null && "耄耋".equals(cat.getCustomName().getString());
		if (maodie) {
			// 从原版纹理路径中提取花色名，构造 mod 自带贴图路径
			Identifier original = cir.getReturnValue();
			String path = original.getPath();
			// 原路径格式：textures/entity/cat/<花色>.png
			// 我们的格式：textures/entity/cat/cat_<花色>.png
			String fileName = path.substring(path.lastIndexOf('/') + 1); // e.g. "tabby.png"
			String variant = fileName.substring(0, fileName.length() - 4); // e.g. "tabby"
			cir.setReturnValue(new Identifier("laowu_meme", "textures/entity/cat/cat_" + variant + ".png"));
		}
	}

	/**
	 * 渲染缩放：老吴整活放大 1.25 倍 + 铲子拍扁 y 轴压扁。
	 * 注入到 render 方法 HEAD，在 super.render 之前缩放 MatrixStack。
	 */
	@Inject(method = "render(Lnet/minecraft/entity/passive/CatEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), require = 0)
	private void laowuScale(CatEntity cat, float entityYaw, float partialTicks, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight, CallbackInfo ci) {
		try {
			ClientMemeState cs = ClientMemeState.get();
			int id = cat.getId();

			// 老吴整活：整体放大 1.25 倍
			if (cs.isActive(id)) {
				poseStack.scale(1.25f, 1.25f, 1.25f);
			}

			// 铲子拍扁：y 轴压到 0.175
			if (cs.isFlattened(id)) {
				poseStack.scale(1f, 0.175f, 1f);
			}
		} catch (Throwable t) {
			// 渲染兜底，绝不崩
		}
	}
}
