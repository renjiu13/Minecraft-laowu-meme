package com.rogic.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screen.Screen;

/**
 * ModMenu 集成入口：fabric.mod.json 的 entrypoints.modmenu 指向本类。
 * ModMenu 加载时调用 getModConfigScreenFactory() 拿到配置屏工厂。
 * 没装 ModMenu 时本类不会被加载（entrypoint 由 ModMenu 提供），不影响 mod 运行。
 */
public class LaowuModMenuApi implements ModMenuApi {
	@Override
	public ConfigScreenFactory<Screen> getModConfigScreenFactory() {
		return LaowuConfigScreen::new;
	}
}
