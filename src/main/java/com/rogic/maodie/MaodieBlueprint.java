package com.rogic.maodie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rogic.LaowuMemeMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.core.Vec3di;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 耄耋多方块结构蓝图。
 * 数据来自 scripts 导出的 JSON（经 litematic_to_blueprint.py 从 Litematica .litematic 解析），
 * 内置到 /data/laowu_meme/schematics/maodie.json，运行时由服务端读取。
 *
 * 匹配策略：对每个 part，取世界中对应方块 → 比对**木系方块家族后缀**（_planks/_stairs/_trapdoor/_slab，不限制具体木种）
 * + 逐个状态属性（通用比较 String.valueOf(actualValue).equals(expected)，waterlogged/facing/half/open 等都覆盖）。
 * 活板门朝向(facing)放宽；楼梯等其他方块的朝向仍严格卡。不旋转。
 *
 * 锚点（用于反推结构原点的参照格）= 蓝图中楼梯 [4,1,0]。
 * 猫座位（猫实际 teleport 落点）= 同一格 [4,1,0] 的【上方一格】（坐楼梯顶）。
 */
public class MaodieBlueprint {
	public static final String MAODIE_NAME = "耄耋";
	public static final int SCAN_RADIUS = 24;
	public static final int SCAN_INTERVAL = 20;
	public static final double CALL_RADIUS = 10.0;
	public static final double MAODIE_PROXIMITY_RADIUS = 5.0;
	/** 猫座位参照（蓝图坐标，相对 origin [0,0,0]）。用户指定 [4,1,0]（一座楼梯），猫 teleport 到该格【上方一格】坐在楼梯顶。锚点也用同一格。 */
	public static final Vec3di SEAT_OFFSET = new Vec3di(4, 1, 0);

	public static class Part {
		public final Vec3di offset;
		public final String block;
		public final Map<String, String> state;

		public Part(int x, int y, int z, String block, Map<String, String> state) {
			this.offset = new Vec3di(x, y, z);
			this.block = block;
			this.state = state;
		}
	}

	public final List<Part> parts;
	/** 锚点：反推结构原点用的参照（楼梯 [4,1,0]）。猫座位也用同一格的【上方一格】。 */
	public final Vec3di anchorOffset;
	/** 猫座位：猫实际落点（相对 origin）。 */
	public final Vec3di seatOffset;

	private MaodieBlueprint(List<Part> parts, Vec3di anchor, Vec3di seat) {
		this.parts = parts;
		this.anchorOffset = anchor;
		this.seatOffset = seat;
	}

	public static MaodieBlueprint load() {
		try (InputStream in = MaodieBlueprint.class.getResourceAsStream("/data/laowu_meme/schematics/maodie.json")) {
			if (in == null) {
				LaowuMemeMod.LOGGER.warn("[maodie] 内置蓝图 /data/laowu_meme/schematics/maodie.json 缺失");
				return null;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			List<Part> parts = new ArrayList<>();
			// 锚点 = 蓝图 [4,1,0] 的楼梯；猫座位也用同一格
			Vec3di anchor = new Vec3di(4, 1, 0);

			JsonArray arr = root.getAsJsonArray("parts");
			if (arr != null) {
				for (JsonElement e : arr) {
					JsonObject p = e.getAsJsonObject();
					JsonArray pos = p.getAsJsonArray("pos");
					int x = pos.get(0).getAsInt();
					int y = pos.get(1).getAsInt();
					int z = pos.get(2).getAsInt();
					String block = p.get("block").getAsString();
					Map<String, String> state = new HashMap<>();
					if (p.has("state")) {
						for (Map.Entry<String, JsonElement> en : p.getAsJsonObject("state").entrySet()) {
							state.put(en.getKey(), en.getValue().getAsString());
						}
					}
					parts.add(new Part(x, y, z, block, state));
				}
			}
			LaowuMemeMod.LOGGER.info("[maodie] 蓝图加载完成：{} 个部件，锚点 {}，猫座位 {}", parts.size(), anchor, SEAT_OFFSET);
			return new MaodieBlueprint(parts, anchor, SEAT_OFFSET);
		} catch (Exception ex) {
			LaowuMemeMod.LOGGER.error("[maodie] 加载蓝图失败", ex);
			return null;
		}
	}

	/** 以 origin（蓝图 [0,0,0] 对应的世界坐标）为基准、按 rot（0/1/2/3 = 0°/90°/180°/270° 逆时针）逐格匹配结构。
	 * 支持结构任意水平旋转：扫描时对每个候选锚点尝试 4 个旋转，任一匹配即识别成功。
	 * 木系方块不限制具体木种（oak/spruce/birch/jungle/acacia/dark_oak/cherry/mangrove 均可），只比对类型后缀。
	 * 活板门朝向(facing)放宽（任意摆）；楼梯朝向随结构旋转一起旋转后严格卡（锚点靠楼梯朝向防误激活）。 */
	public boolean matches(BlockGetter world, BlockPos origin, int rot) {
		for (Part p : parts) {
			Vec3di ro = rotateOffset(p.offset, rot);
			BlockPos wp = origin.offset(ro);
			BlockState actual = world.getBlockState(wp);
			// 木系通用匹配：只比对方块类型后缀，忽略木种前缀
			if (!isWoodFamilyMatch(actual, p.block)) return false;
			for (Map.Entry<String, String> en : p.state.entrySet()) {
				String key = en.getKey();
				String expectedVal = en.getValue();
				// 活板门(trapdoor)朝向放宽：玩家摆错朝向也能识别
				if (key.equals("facing") && p.block.endsWith("trapdoor")) continue;
				// 楼梯朝向随结构旋转而旋转
				if (key.equals("facing") && rot != 0) expectedVal = rotateFacing(expectedVal, rot);
				Property<?> prop = findProp(actual, key);
				if (prop == null) return false;
				Comparable<?> val = actual.getValue(prop);
				if (!expectedVal.equals(String.valueOf(val))) return false;
			}
		}
		return true;
	}

	/** 绕 Y 轴逆时针旋转 rot×90° 的偏移变换（与楼梯 facing 旋转一致）。 */
	public static Vec3di rotateOffset(Vec3di off, int rot) {
		int x = off.getX(), y = off.getY(), z = off.getZ();
		switch (rot & 3) {
			case 0: return off;
			case 1: return new Vec3di(z, y, -x);
			case 2: return new Vec3di(-x, y, -z);
			default: return new Vec3di(-z, y, x); // case 3
		}
	}

	/** 楼梯/朝向随结构逆时针旋转：east→north→west→south（每 90° 一步）。 */
	private static final String[] FACING_CCW = {"east", "north", "west", "south"};
	private static String rotateFacing(String facing, int rot) {
		for (int i = 0; i < FACING_CCW.length; i++) {
			if (FACING_CCW[i].equalsIgnoreCase(facing)) {
				return FACING_CCW[(i + (rot & 3)) & 3];
			}
		}
		return facing; // 未知朝向保持原值
	}

	/**
	 * 木系方块家族匹配：expectedId 如 minecraft:jungle_stairs → 接受任意 *_stairs。
	 * 非木系方块（无下划线后缀）回退精确匹配。
	 */
	private static boolean isWoodFamilyMatch(BlockState actual, String expectedId) {
		int lastUs = expectedId.lastIndexOf('_');
		if (lastUs > 0) {
			String suffix = expectedId.substring(lastUs); // e.g. "_stairs"
			String actualId = Registries.BLOCK.getKey(actual.getBlock()).toString();
			return actualId.endsWith(suffix);
		}
		// 无下划线 → 精确匹配（本蓝图不应走到这里）
		Block expected = Registries.BLOCK.get(Identifier.tryParse(expectedId));
		return expected != null && actual.getBlock() == expected;
	}

	private static Property<?> findProp(BlockState s, String name) {
		for (Property<?> p : s.getProperties()) {
			if (p.getName().equals(name)) return p;
		}
		return null;
	}
}
