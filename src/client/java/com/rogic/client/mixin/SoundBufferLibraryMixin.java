package com.rogic.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.LoopingAudioStream;
import net.minecraft.client.sound.SoundLibrary;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.audio.OggAudioStream;
import com.rogic.client.sound.SoundIdCodec;

/**
 * 拦截 laowu_meme:sounds/imported/<hex名>.ogg 的资源读取，hex 解码出真实文件名后直接从
 * config/laowu_meme/sounds/<名>.ogg 读取并用 JOrbis 解码，使导入音频无需进资源包即可播放。
 *
 * 1.20.1 版本：使用 Identifier 而非 Identifier。
 */
@Mixin(SoundLibrary.class)
public class SoundBufferLibraryMixin {
	@Inject(method = "getStream(Lnet/minecraft/util/Identifier;Z)Ljava/util/concurrent/CompletableFuture;",
			at = @At("HEAD"), cancellable = true)
	private void laowuInterceptImportedStream(Identifier id, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
		if (!id.getNamespace().equals("laowu_meme")) return;
		String path = id.getPath();
		// SoundEngine.play 调用 getStream 时传入的是 Sound.getPath() 结果：
		// laowu_meme:sounds/imported/<hex>.ogg（带 sounds/ 前缀和 .ogg 后缀）
		if (!path.startsWith("sounds/imported/")) return;
		String enc = path.substring("sounds/imported/".length());  // 形如 <hex>.ogg
		if (enc.isEmpty()) return;
		String hex = enc.endsWith(".ogg") ? enc.substring(0, enc.length() - 4) : enc;
		String name = SoundIdCodec.decode(hex);
		if (name.isEmpty()) return;
		File f = new File(MinecraftClient.getInstance().runDirectory, "config/laowu_meme/sounds/" + name + ".ogg");
		if (!f.isFile()) return;
		try {
			InputStream in = Files.newInputStream(f.toPath());
			AudioStream stream;
			if (looping) {
				LoopingAudioStream.AudioStreamProvider provider = (InputStream s) -> (AudioStream) (Object) new OggAudioStream(s);
				stream = (AudioStream) (Object) new LoopingAudioStream(provider, in);
			} else {
				stream = (AudioStream) (Object) new OggAudioStream(in);
			}
			cir.setReturnValue(CompletableFuture.completedFuture(stream));
		} catch (IOException | RuntimeException e) {
			// 读取/解码失败：放行给原逻辑（按缺失资源处理），不崩溃；给玩家提示便于排查
			System.out.println("[laowu meme] 导入音频解码失败（已忽略）：" + name + " —— " + e);
			MinecraftClient.getInstance().toastManager.addToast(
					new net.minecraft.client.toast.SystemToast(
							net.minecraft.client.toast.SystemToast.Type.PERIODIC_NOTIFICATION,
							net.minecraft.text.Text.literal("laowu meme"),
							net.minecraft.text.Text.literal("导入音频解码失败：" + name)));
		}
	}
}
