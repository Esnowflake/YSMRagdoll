package com.ysmragdoll.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 玩家可调整的布娃娃生命周期、推动和调试设置。
 *
 * <p>所有范围同时在 Forge 配置规范和设置界面提交逻辑中校验。界面遇到负数、
 * 非数字或越界值时保留输入并提示修正，非法文本不会进入物理计算。</p>
 */
public final class YsmRagdollConfig {
    public static final int RAGDOLL_LIMIT = 10;
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue LIFETIME_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MANUAL_REMOVAL;
    public static final ForgeConfigSpec.IntValue MAX_RAGDOLLS;
    public static final ForgeConfigSpec.IntValue GROUND_FRICTION;
    public static final ForgeConfigSpec.IntValue EASY_PUSH_INDEX;
    public static final ForgeConfigSpec.IntValue EXPLOSION_IMPACT_INDEX;
    public static final ForgeConfigSpec.BooleanValue SHOW_COLLISION_BOXES;
    public static final ForgeConfigSpec.BooleanValue INTENSIVE_TEST;
    public static final ForgeConfigSpec.BooleanValue GRAVITY_GUN_MODE;
    public static final ForgeConfigSpec.DoubleValue COLLISION_OFFSET_X;
    public static final ForgeConfigSpec.DoubleValue COLLISION_OFFSET_Y;
    public static final ForgeConfigSpec.DoubleValue COLLISION_OFFSET_Z;
    public static final ForgeConfigSpec.DoubleValue RENDER_OFFSET_X;
    public static final ForgeConfigSpec.DoubleValue RENDER_OFFSET_Y;
    public static final ForgeConfigSpec.DoubleValue RENDER_OFFSET_Z;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("general");
        LIFETIME_SECONDS = builder.comment(
                        "布娃娃存在时间，单位为秒。",
                        "0 到 10000 表示对应秒数。大于 10000 表示永久存在。",
                        "永久存在时仍受数量上限和手动清除规则影响。")
                .defineInRange("lifetimeSeconds", 20, 0, Integer.MAX_VALUE);
        MANUAL_REMOVAL = builder.comment(
                        "开启后可空手右键布娃娃进行清除，并完全忽略存在时间。",
                        "数量上限及低于 Y=-64 的虚空清理仍然生效。")
                .define("manualRemoval", false);
        MAX_RAGDOLLS = builder.comment(
                        "客户端同时保留的布娃娃数量，范围 0 到 10，默认 2。",
                        "达到上限后生成新的布娃娃时，会优先删除最早生成的一个。")
                .defineInRange("maxRagdolls", 2, 0, RAGDOLL_LIMIT);
        builder.pop();
        builder.push("advanced");
        GROUND_FRICTION = builder.comment(
                        "布娃娃整体与地面的摩擦系数，范围 0 到 100。",
                        "0 为完全无摩擦，100 为停止受到推动后整体基本静止。",
                        "该设置不应阻止布娃娃肢体自身继续运动。")
                .defineInRange("groundFriction", 20, 0, 100);
        EASY_PUSH_INDEX = builder.comment(
                        "布娃娃接触玩家推动后接近目标速度的难易程度，范围 0 到 100。",
                        "0-99 使用渐进冲量，默认值为 90。",
                        "100 时布娃娃必须避让本地玩家碰撞箱，并跟随玩家推动速度。")
                .defineInRange("easyPushIndex", 90, 0, 100);
        EXPLOSION_IMPACT_INDEX = builder.comment(
                        "爆炸施加到布娃娃各肢体的冲击强度，范围 0 到 100。",
                        "0 表示完全不受爆炸推动，50 为标准强度，100 为标准强度的两倍。",
                        "该设置不改变爆炸的实际影响半径和方块遮挡判定。")
                .defineInRange("explosionImpactIndex", 50, 0, 100);
        SHOW_COLLISION_BOXES = builder.comment(
                        "是否显示每个布娃娃肢体实际参与 JBullet 求解的旋转碰撞箱。",
                        "该选项只用于调试显示，不改变物理计算。")
                .define("showCollisionBoxes", false);
        INTENSIVE_TEST = builder.comment(
                        "以 100 Hz 采样最新的客户端布娃娃快照，写入独立密集测试日志。",
                        "重复快照与快照年龄会明确记录；仅用于临时诊断，默认关闭。")
                .define("intensiveTest", false);
        COLLISION_OFFSET_X = offset(builder, "collisionOffsetX", "碰撞箱 X 偏移");
        COLLISION_OFFSET_Y = offset(builder, "collisionOffsetY", "碰撞箱 Y 偏移");
        COLLISION_OFFSET_Z = offset(builder, "collisionOffsetZ", "碰撞箱 Z 偏移");
        RENDER_OFFSET_X = offset(builder, "renderOffsetX", "布娃娃渲染 X 偏移");
        RENDER_OFFSET_Y = offset(builder, "renderOffsetY", "布娃娃渲染 Y 偏移");
        RENDER_OFFSET_Z = offset(builder, "renderOffsetZ", "布娃娃渲染 Z 偏移");
        builder.pop();
        builder.push("testing");
        GRAVITY_GUN_MODE = builder.comment("牵引模式：主手拿木棍，按住右键（使用键）牵引布娃娃，松开释放，滚轮调距。")
                .define("gravityGunMode", false);
        builder.pop();
        SPEC = builder.build();
    }

    private YsmRagdollConfig() {
    }

    private static ForgeConfigSpec.DoubleValue offset(ForgeConfigSpec.Builder builder,
                                                       String key, String description) {
        return builder.comment(description + "，单位为方块，允许负数和小数，范围 -4.0 到 4.0。",
                        "碰撞箱偏移与渲染偏移相互独立，便于现场校准模型和物理位置。")
                .defineInRange(key, 0.0, -4.0, 4.0);
    }
}
