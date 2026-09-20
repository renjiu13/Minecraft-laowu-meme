package com.rogic.client;

import com.rogic.client.sound.AudioPool;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.text.Style;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;

/**
 * 轻量配置屏（手写，不依赖 YACL 以符合 mod 轻量定位）。
 * - 每条音频（固有 + 导入）都是一个可点击的 toggle 按钮：点击翻转「启用/禁用」，
 *   状态写入 config/laowu_meme/enabled.properties；禁用项不会被随机播放。
 * - 「打开音频文件夹」按钮：调系统文件管理器打开 config/laowu_meme/sounds/。
 * - 标题/提示用 TextWidget（AbstractWidget 子类，与按钮同一渲染管线，retained-mode 下可靠）。
 * - 布局：toggle 按钮逐行居中排布，底部「打开文件夹 / 返回」按钮与列表用留白隔开，不再叠字。
 */
public class LaowuConfigScreen extends Screen {
	private final Screen parent;

	public LaowuConfigScreen(Screen parent) {
		super(Text.literal("laowu meme 设置"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;

		// 打开配置界面时重新扫描磁盘导入文件夹（轻量：不引后台监听线程，
		// 仅打开 GUI 时扫一遍），确保删除/新增 .ogg 后列表即时反映。
		AudioPool.refreshImported();

		// 标题
		TextWidget title = new TextWidget(Text.literal("laowu meme 音频设置").withStyle(Style.EMPTY.withColor(0xFFFFFF)), this.font);
		title.setX(cx - title.getWidth() / 2);
		title.setY(16);
		this.addRenderableWidget(title);

		// 提示
		TextWidget hint = new TextWidget(Text.literal("点击切换启用/禁用；禁用项不会被随机播放").withStyle(Style.EMPTY.withColor(0xAAAAAA)), this.font);
		hint.setX(cx - hint.getWidth() / 2);
		hint.setY(38);
		this.addRenderableWidget(hint);

		// 音频条目：每条一个可点击 toggle 按钮
		int y = 64;
		int btnW = Math.min(300, this.width - 40);
		int btnX = cx - btnW / 2;

		for (String key : AudioPool.builtinKeys()) {
			String name = AudioPool.BUILTIN_DISPLAY.get(key);
			addToggle("builtin:" + key, name, btnX, y, btnW);
			y += 24;
		}
		for (String key : AudioPool.importedKeys()) {
			String name = key.substring("imported:".length());
			addToggle(key, "[导入] " + name, btnX, y, btnW);
			y += 24;
		}

		// 底部按钮：与列表留白隔开，避免叠字
		int bottomY = this.height - 56;
		if (bottomY < y + 12) bottomY = y + 12;
		this.addRenderableWidget(ButtonWidget.builder(Text.literal("打开音频文件夹"), b -> openSoundsFolder())
				.bounds(cx - 110, bottomY, 220, 20).build());
		this.addRenderableWidget(ButtonWidget.builder(Text.literal("返回"), b -> this.client.setScreen(this.parent))
				.bounds(cx - 110, bottomY + 26, 220, 20).build());
	}

	private void addToggle(String key, String displayName, int x, int y, int w) {
		boolean enabled = AudioPool.isEnabled(key);
		Text msg = makeMsg(displayName, enabled);
		Button btn = ButtonWidget.builder(msg, b -> {
			boolean now = AudioPool.toggleEnabled(key);
			b.setMessage(makeMsg(displayName, now));
		}).bounds(x, y, w, 20).build();
		this.addRenderableWidget(btn);
	}

	private static Text makeMsg(String name, boolean enabled) {
		String prefix = enabled ? "✓ " : "✗ ";
		int color = enabled ? 0x55FF55 : 0xFF7777;
		return Text.literal(prefix + name).withStyle(Style.EMPTY.withColor(color));
	}

	private void openSoundsFolder() {
		File dir = getSoundsDir();
		try {
			if (!dir.exists() && !dir.mkdirs()) {
				notify("无法创建文件夹：" + dir.getAbsolutePath());
				return;
			}
			boolean opened = false;
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				try {
					Desktop.getDesktop().open(dir);
					opened = true;
				} catch (IOException ignored) {
				}
			}
			if (!opened) opened = openViaOs(dir);
			if (opened) {
				notify("已打开音频文件夹");
			} else {
				copyToClipboard(dir.getAbsolutePath());
				notify("无法打开，路径已复制到剪贴板");
			}
		} catch (Exception e) {
			copyToClipboard(dir.getAbsolutePath());
			notify("无法打开，路径已复制到剪贴板");
		}
	}

	private static boolean openViaOs(File dir) {
		try {
			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("win")) {
				Runtime.getRuntime().exec(new String[]{"explorer.exe", dir.getAbsolutePath()});
			} else if (os.contains("mac")) {
				Runtime.getRuntime().exec(new String[]{"open", dir.getAbsolutePath()});
			} else {
				Runtime.getRuntime().exec(new String[]{"xdg-open", dir.getAbsolutePath()});
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void notify(String msg) {
		if (this.client != null && this.client.toastManager != null) {
			this.client.toastManager.addToast(
					new SystemToast(SystemToast.Type.PERIODIC_NOTIFICATION,
							Text.literal("laowu meme"), Text.literal(msg)));
		}
	}

	private static void copyToClipboard(String text) {
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		} catch (Exception ignored) {
		}
	}

	private static File getSoundsDir() {
		return new File(MinecraftClient.getInstance().runDirectory, "config/laowu_meme/sounds");
	}
}
