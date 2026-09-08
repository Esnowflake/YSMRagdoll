package com.ysmragdoll.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ysmragdoll.YsmRagdollLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 将官方 YSM 已加载的模型转换成与玩家实体脱离的静态绘制快照。
 *
 * 模型网格和纹理是只读资源，可以在多具尸体间共享；两组骨骼数组包含动画结果，
 * 必须在死亡时复制。快照完成后直接调用 YSM 的底层网格方法，不再执行玩家的
 * capability、动画控制器或 {@code tickModel()}。
 */
@OnlyIn(Dist.CLIENT)
public final class OpenYsmModelAdapter {
    private static final String YSM_RENDERER_REGISTRY =
            "com.elfmcys.yesstevemodel.OOoO00ooO00OOO00O0o0000O";
    private static final String YSM_MESH_RENDERER =
            "com.elfmcys.yesstevemodel.ooOOo000OOO0ooO0oo0ooooO";
    private static final int CAPTURE_LIGHT = 0x00F000F0;

    private static final ThreadLocal<CaptureContext> ACTIVE_CAPTURE = new ThreadLocal<>();
    // 附加渲染层会通过 VertexMultiConsumer 合并缓冲；每次请求必须返回独立对象，避免 Duplicate delegates。
    private static final MultiBufferSource DISCARDING_BUFFERS = renderType -> new DiscardingVertexConsumer();

    private static boolean rendererInitialized;
    private static Object ysmPlayerRenderer;
    private static Method ysmPlayerRenderMethod;
    private static Method ysmMeshRenderMethod;
    private static Method ysmRenderTypeFactory;
    private static Field rendererTextureField;
    private static boolean renderTypeFactoryInitialized;
    private static boolean renderFailureLogged;
    private static boolean directRenderLogged;

    private OpenYsmModelAdapter() {
    }

    public static Optional<CapturedModel> capture(Player player, double x, double y, double z, float yaw) {
        if (!ModList.get().isLoaded("yes_steve_model")) {
            YsmRagdollLog.warn("未加载 yes_steve_model，无法捕获死亡模型");
            return Optional.empty();
        }

        CapturedModel model = new CapturedModel(player.getUUID(), player,
                resolveModelIdentifiers(player));
        // 死亡包在渲染线程处理，此时旧玩家的 YSM capability 仍然有效。必须在这里立即复制，
        // 不能等到下一帧，因为本地玩家复活时旧实体可能已从客户端世界移除并使 capability 失效。
        PoseStack capturePose = new PoseStack();
        boolean captured = captureFromPlayer(model, player, x, y, z, yaw,
                Minecraft.getInstance().getFrameTime(), capturePose, DISCARDING_BUFFERS,
                CAPTURE_LIGHT, true);
        if (captured) {
            YsmRagdollLog.info("已在死亡事件线程立即冻结 YSM 模型，不再等待下一帧玩家实体: "
                    + player.getName().getString());
        } else {
            YsmRagdollLog.warn("死亡事件线程未能立即取得 YSM 网格，进入有限重试队列: "
                    + player.getName().getString());
        }
        // 即使第一次捕获失败也返回轻量上下文，但它不会直接进入正式布娃娃列表。
        return Optional.of(model);
    }

    /** 判断死亡快照是否已经取得可直接重放的 YSM 网格。 */
    public static boolean hasSnapshot(CapturedModel model) {
        return model != null && model.snapshot != null;
    }

    /** 在有限重试队列中重新捕获一次 YSM 网格；成功后才允许创建正式物理对象。 */
    public static boolean retryCapture(CapturedModel model, Player player,
                                       double x, double y, double z, float yaw) {
        if (model == null || model.snapshot != null || player == null) {
            return model != null && model.snapshot != null;
        }
        PoseStack capturePose = new PoseStack();
        boolean captured = captureFromPlayer(model, player, x, y, z, yaw,
                Minecraft.getInstance().getFrameTime(), capturePose, DISCARDING_BUFFERS,
                CAPTURE_LIGHT, true);
        if (captured) {
            YsmRagdollLog.info("有限重试已取得独立 YSM 快照: 玩家=" + model.playerId);
        }
        return captured;
    }

    public static boolean render(CapturedModel model, double x, double y, double z,
                                 float yaw, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int light) {
        ModelSnapshot snapshot = model.snapshot;
        if (snapshot != null) {
            return renderSnapshot(snapshot, poseStack, buffers, light);
        }
        return captureAndRender(model, x, y, z, yaw, partialTick, poseStack, buffers, light);
    }

    /**
     * 向物理模块暴露快照中最小且可变的数据视图。网格本身只读共享，骨骼数组由当前尸体独占。
     */
    public static Optional<PhysicsModelView> physicsView(CapturedModel model) {
        ModelSnapshot snapshot = model.snapshot;
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.of(new PhysicsModelView(snapshot.mesh, snapshot.boneTransforms,
                snapshot.relativePose, model.modelIdentifiers));
    }

    static String describeBonePose(CapturedModel model) {
        ModelSnapshot snapshot = model == null ? null : model.snapshot;
        if (snapshot == null) {
            return "bones=pending";
        }
        float[] transforms = snapshot.boneTransforms;
        StringBuilder result = new StringBuilder("bones=").append(transforms.length / 12).append(':');
        for (int index = 0; index + 2 < transforms.length; index += 12) {
            result.append(index / 12).append('(')
                    .append(Float.toString(transforms[index])).append(',')
                    .append(Float.toString(transforms[index + 1])).append(',')
                    .append(Float.toString(transforms[index + 2])).append(')');
            if (index + 12 < transforms.length) {
                result.append(';');
            }
        }
        return result.toString();
    }

    /** 从官方 YSM 2.6.5 capability 读取当前模型标识，仅在死亡捕获时执行一次。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<String> resolveModelIdentifiers(Player player) {
        Set<String> identifiers = new LinkedHashSet<>();
        for (ClassLoader loader : classLoaders()) {
            if (loader == null) {
                continue;
            }
            try {
                Class<?> provider = Class.forName(
                        "com.elfmcys.yesstevemodel.O0OooOo0oOOoOoOoOooO000o", false, loader);
                Field capabilityField = provider.getDeclaredField("Oo0Oo0o00O00Oo0OOoOOoooo");
                capabilityField.setAccessible(true);
                Object handle = capabilityField.get(null);
                if (!(handle instanceof Capability capability)) {
                    continue;
                }
                Object value = player.getCapability(capability).resolve().orElse(null);
                if (value == null) {
                    continue;
                }
                collectStringValues(value, identifiers);
                break;
            } catch (Throwable ignored) {
                // 其他 YSM 版本会由几何骨骼签名后备匹配，不让模型捕获失败。
            }
        }
        if (!identifiers.isEmpty()) {
            YsmRagdollLog.info("死亡模型标识候选: " + identifiers);
        }
        return List.copyOf(identifiers);
    }

    private static void collectStringValues(Object value, Set<String> output) {
        for (Class<?> type = value.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    addIdentifier(output, (String) field.get(value));
                } catch (Throwable ignored) {
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                        || method.getReturnType() != String.class) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    addIdentifier(output, (String) method.invoke(value));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void addIdentifier(Set<String> output, String value) {
        if (value != null && !value.isBlank() && value.length() <= 256) {
            output.add(value.replace('\\', '/'));
        }
    }

    /**
     * 由可选 Mixin 在 YSM 即将提交网格前调用。这里仅复制本次尸体捕获需要的数据，
     * 普通玩家绘制以及已经完成快照后的尸体绘制都不会进入该分支。
     */
    public static boolean onYsmMeshRender(PoseStack.Pose pose, Object mesh,
                                          float[] boneTransforms, float[] secondaryState,
                                          int textureIndex, int renderPartMask,
                                          int packedLight, int packedOverlay,
                                          float red, float green, float blue, float alpha) {
        CaptureContext context = ACTIVE_CAPTURE.get();
        if (context == null) {
            return false;
        }

        if (context.draft == null && mesh != null
                && boneTransforms != null && boneTransforms.length > 0) {
            Matrix4f relativePose = new Matrix4f(context.basePose).invert().mul(pose.pose());
            Matrix3f relativeNormal = new Matrix3f(context.baseNormal).invert().mul(pose.normal());
            context.draft = new SnapshotDraft(mesh, boneTransforms.clone(),
                    secondaryState == null ? null : secondaryState.clone(),
                    textureIndex, renderPartMask, packedOverlay,
                    red, green, blue, alpha, relativePose, relativeNormal);
        }
        // 让 Mixin 只取消底层顶点提交，不打断外层 YSM 渲染器，保证其 pushPose/popPose 正常配对。
        return context.suppressMeshDrawing;
    }

    private static boolean captureAndRender(CapturedModel model, double x, double y, double z,
                                            float yaw, float partialTick, PoseStack poseStack,
                                            MultiBufferSource buffers, int light) {
        Player player = resolveCaptureCarrier(model);
        if (player == null) {
            logCaptureUnavailable(model, "当前世界中已没有可用于首次捕获的玩家实体");
            return false;
        }

        // 重试也必须使用完全独立的矩阵栈和丢弃缓冲，绝不在 LevelRenderer 的矩阵栈上调用 YSM。
        PoseStack capturePose = new PoseStack();
        if (!captureFromPlayer(model, player, x, y, z, yaw, partialTick,
                capturePose, DISCARDING_BUFFERS, CAPTURE_LIGHT, true)) {
            return false;
        }
        return renderSnapshot(model.snapshot, poseStack, buffers, light);
    }

    private static boolean captureFromPlayer(CapturedModel model, Player player,
                                             double x, double y, double z,
                                             float yaw, float partialTick,
                                             PoseStack poseStack, MultiBufferSource buffers,
                                             int light, boolean suppressMeshDrawing) {
        Object renderer = resolveYsmPlayerRenderer();
        Method renderMethod = ysmPlayerRenderMethod;
        if (renderer == null || renderMethod == null) {
            return false;
        }

        PlayerRenderState previous = PlayerRenderState.capture(player);
        CaptureContext context = new CaptureContext(poseStack.last(), suppressMeshDrawing);
        ACTIVE_CAPTURE.set(context);
        try {
            // 首次捕获仍需要让 YSM 生成一次最终骨骼数组。实体状态只在这次调用期间
            // 固定到死亡快照，调用结束后立即恢复，之后尸体不再使用该实体。
            player.deathTime = 0;
            player.setPos(x, y, z);
            player.xo = x;
            player.yo = y;
            player.zo = z;
            player.setYRot(yaw);
            player.yRotO = yaw;
            player.yBodyRot = yaw;
            player.yBodyRotO = yaw;
            player.yHeadRot = yaw;
            player.yHeadRotO = yaw;
            player.setXRot(0.0F);
            player.xRotO = 0.0F;

            renderMethod.invoke(renderer, player, yaw, partialTick, poseStack, buffers, light);
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            logRenderFailure("调用 YSM 玩家渲染器失败", exception);
            return false;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            logRenderFailure("YSM 玩家渲染器内部失败", cause);
            return false;
        } finally {
            ACTIVE_CAPTURE.remove();
            previous.restore(player);
        }

        if (context.draft == null) {
            logCaptureUnavailable(model, "YSM 底层网格钩子尚未返回数据");
            return false;
        }

        ResourceLocation texture = resolveRendererTexture(renderer, player);
        Method meshRenderMethod = resolveYsmMeshRenderMethod(context.draft.mesh.getClass());
        RenderType renderType = resolveYsmRenderType(renderer, texture);
        if (texture == null || meshRenderMethod == null || renderType == null) {
            logCaptureUnavailable(model, "快照缺少纹理、底层绘制方法或 RenderType");
            return false;
        }

        SnapshotDraft draft = context.draft;
        // 死亡事件发生时玩家仍处于 hurtTime，YSM 会把受伤闪红写入 packedOverlay。
        // 骨骼姿势可以保留死亡瞬间，但覆盖层必须清除，否则尸体会永久呈粉红色。
        int cleanOverlay = OverlayTexture.NO_OVERLAY;
        model.snapshot = new ModelSnapshot(draft.mesh, draft.boneTransforms,
                draft.secondaryState, draft.textureIndex, draft.renderPartMask,
                cleanOverlay, draft.red, draft.green, draft.blue, draft.alpha,
                draft.relativePose, draft.relativeNormal, texture, renderType);
        // 快照完成后立即释放玩家引用，确保永久尸体不会把旧实体和客户端世界留在内存中。
        model.initialPlayer = null;
        YsmRagdollLog.info("独立 YSM 快照捕获完成: 玩家=" + model.playerId
                + ", 骨骼参数=" + draft.boneTransforms.length
                + ", 辅助参数=" + (draft.secondaryState == null ? 0 : draft.secondaryState.length)
                + ", 已清除受伤覆盖=" + (draft.packedOverlay != cleanOverlay)
                + ", 纹理=" + texture);
        return true;
    }

    private static boolean renderSnapshot(ModelSnapshot snapshot, PoseStack poseStack,
                                          MultiBufferSource buffers, int light) {
        Method renderMethod = ysmMeshRenderMethod;
        if (renderMethod == null) {
            return false;
        }

        poseStack.pushPose();
        try {
            // 保存的是 YSM 相对尸体世界原点施加的模型矩阵，不包含捕获时的相机位置。
            poseStack.last().pose().mul(snapshot.relativePose);
            poseStack.last().normal().mul(snapshot.relativeNormal);
            VertexConsumer consumer = buffers.getBuffer(snapshot.renderType);
            renderMethod.invoke(null, consumer, poseStack.last(), snapshot.mesh,
                    snapshot.boneTransforms, snapshot.secondaryState,
                    snapshot.textureIndex, snapshot.renderPartMask,
                    light, snapshot.packedOverlay,
                    snapshot.red, snapshot.green, snapshot.blue, snapshot.alpha);
            if (!directRenderLogged) {
                directRenderLogged = true;
                YsmRagdollLog.info("首次直接重放独立 YSM 网格快照成功；后续不再读取玩家动画");
            }
            return true;
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            logRenderFailure("调用 YSM 底层快照绘制方法失败", exception);
            return false;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            logRenderFailure("YSM 底层快照绘制内部失败", cause);
            return false;
        } finally {
            poseStack.popPose();
        }
    }

    private static Player resolveCaptureCarrier(CapturedModel model) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Player current = minecraft.level.getPlayerByUUID(model.playerId);
            if (current != null) {
                return current;
            }
        }
        Player initialPlayer = model.initialPlayer;
        // 即使实体已从世界列表移除，死亡包处理时保存的对象也可能仍持有最后一刻的有效 capability。
        // 这里只允许它参与一次捕获；成功后会立即清空引用，绝不会用于后续逐帧绘制。
        return initialPlayer;
    }

    private static synchronized Object resolveYsmPlayerRenderer() {
        if (rendererInitialized) {
            return ysmPlayerRenderer;
        }
        rendererInitialized = true;

        for (ClassLoader classLoader : classLoaders()) {
            if (classLoader == null) {
                continue;
            }
            try {
                Class<?> registry = Class.forName(YSM_RENDERER_REGISTRY, false, classLoader);
                YsmRagdollLog.info("已找到 YSM 渲染器注册类: " + registry.getName());
                for (Method getter : registry.getDeclaredMethods()) {
                    if (!Modifier.isStatic(getter.getModifiers())
                            || getter.getParameterCount() != 0
                            || !LivingEntityRenderer.class.isAssignableFrom(getter.getReturnType())) {
                        continue;
                    }
                    getter.setAccessible(true);
                    Object candidate = getter.invoke(null);
                    Method playerRenderMethod = findPlayerRenderMethod(
                            candidate == null ? null : candidate.getClass());
                    if (candidate == null || playerRenderMethod == null) {
                        continue;
                    }
                    ysmPlayerRenderer = candidate;
                    ysmPlayerRenderMethod = playerRenderMethod;
                    YsmRagdollLog.info("已找到 YSM 玩家渲染器: " + candidate.getClass().getName());
                    YsmRagdollLog.info("已找到 YSM 首次捕获方法: "
                            + playerRenderMethod.getDeclaringClass().getName()
                            + "#" + playerRenderMethod.getName());
                    return candidate;
                }
            } catch (Throwable exception) {
                YsmRagdollLog.warn("读取 YSM 渲染器注册类失败: " + describe(exception));
            }
        }
        YsmRagdollLog.warn("未找到兼容的 YSM 玩家渲染器，静态尸体不会回退为原版模型");
        return null;
    }

    private static Method findPlayerRenderMethod(Class<?> rendererClass) {
        for (Class<?> type = rendererClass; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getReturnType() == void.class
                        && parameters.length == 6
                        && parameters[0] == Player.class
                        && parameters[1] == float.class
                        && parameters[2] == float.class
                        && parameters[3] == PoseStack.class
                        && MultiBufferSource.class.isAssignableFrom(parameters[4])
                        && parameters[5] == int.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static synchronized Method resolveYsmMeshRenderMethod(Class<?> meshClass) {
        if (ysmMeshRenderMethod != null) {
            return ysmMeshRenderMethod;
        }
        for (ClassLoader classLoader : classLoaders()) {
            if (classLoader == null) {
                continue;
            }
            try {
                Class<?> rendererClass = Class.forName(YSM_MESH_RENDERER, false, classLoader);
                for (Method method : rendererClass.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == void.class
                            && parameters.length == 13
                            && VertexConsumer.class.isAssignableFrom(parameters[0])
                            && parameters[1] == PoseStack.Pose.class
                            && parameters[2].isAssignableFrom(meshClass)
                            && parameters[3] == float[].class
                            && parameters[4] == float[].class
                            && parameters[5] == int.class
                            && parameters[6] == int.class
                            && parameters[7] == int.class
                            && parameters[8] == int.class
                            && parameters[9] == float.class
                            && parameters[10] == float.class
                            && parameters[11] == float.class
                            && parameters[12] == float.class) {
                        method.setAccessible(true);
                        ysmMeshRenderMethod = method;
                        YsmRagdollLog.info("已找到 YSM 独立网格重放方法: "
                                + rendererClass.getName() + "#" + method.getName());
                        return method;
                    }
                }
            } catch (Throwable exception) {
                YsmRagdollLog.warn("读取 YSM 底层网格方法失败: " + describe(exception));
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceLocation resolveRendererTexture(Object renderer, Player player) {
        try {
            if (rendererTextureField == null) {
                for (Class<?> type = renderer.getClass(); type != null; type = type.getSuperclass()) {
                    for (Field field : type.getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers())
                                && field.getType() == ResourceLocation.class) {
                            field.setAccessible(true);
                            rendererTextureField = field;
                            break;
                        }
                    }
                    if (rendererTextureField != null) {
                        break;
                    }
                }
            }
            Object value = rendererTextureField == null ? null : rendererTextureField.get(renderer);
            if (value instanceof ResourceLocation texture) {
                return texture;
            }
            // 部分 YSM 模型不会写入 currentTexture 字段，正式接口会从玩家 capability 返回默认纹理。
            if (renderer instanceof EntityRenderer entityRenderer) {
                return entityRenderer.getTextureLocation(player);
            }
            return null;
        } catch (Throwable exception) {
            YsmRagdollLog.warn("读取 YSM 当前纹理失败: " + describe(exception));
            return null;
        }
    }

    private static RenderType resolveYsmRenderType(Object renderer, ResourceLocation texture) {
        if (texture == null) {
            return null;
        }
        if (!renderTypeFactoryInitialized) {
            renderTypeFactoryInitialized = true;
            for (Method method : renderer.getClass().getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (RenderType.class.isAssignableFrom(method.getReturnType())
                        && parameters.length == 4
                        && parameters[0] == ResourceLocation.class
                        && parameters[1] == boolean.class
                        && parameters[2] == boolean.class
                        && parameters[3] == boolean.class) {
                    method.setAccessible(true);
                    ysmRenderTypeFactory = method;
                    YsmRagdollLog.info("已找到 YSM 自定义 RenderType 工厂: "
                            + method.getDeclaringClass().getName() + "#" + method.getName());
                    break;
                }
            }
        }

        if (ysmRenderTypeFactory != null) {
            try {
                // 可见、非轮廓、允许透明，保持 YSM 模型头发和半透明部件的效果。
                Object value = ysmRenderTypeFactory.invoke(renderer, texture, true, false, true);
                if (value instanceof RenderType renderType) {
                    return renderType;
                }
            } catch (Throwable exception) {
                YsmRagdollLog.warn("创建 YSM 自定义 RenderType 失败，改用原版透明类型: "
                        + describe(exception));
            }
        }
        return RenderType.entityTranslucent(texture);
    }

    private static ClassLoader[] classLoaders() {
        return new ClassLoader[]{
                Thread.currentThread().getContextClassLoader(),
                OpenYsmModelAdapter.class.getClassLoader(),
                Minecraft.class.getClassLoader()
        };
    }

    private static void logCaptureUnavailable(CapturedModel model, String reason) {
        model.captureAttempts++;
        if (!model.captureFailureLogged && model.captureAttempts >= 120) {
            model.captureFailureLogged = true;
            YsmRagdollLog.warn("独立 YSM 快照连续捕获失败 120 帧: 玩家=" + model.playerId
                    + ", 原因=" + reason + "。请确认 Mixin 已加载且 YSM 版本为 2.6.5");
        }
    }

    private static void logRenderFailure(String stage, Throwable exception) {
        if (!renderFailureLogged) {
            renderFailureLogged = true;
            YsmRagdollLog.warn(stage, exception);
        }
    }

    private static String describe(Throwable exception) {
        String message = exception.getMessage();
        return exception.getClass().getName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    public static final class CapturedModel {
        private final UUID playerId;
        private final List<String> modelIdentifiers;
        private Player initialPlayer;
        private ModelSnapshot snapshot;
        private int captureAttempts;
        private boolean captureFailureLogged;

        private CapturedModel(UUID playerId, Player initialPlayer, List<String> modelIdentifiers) {
            this.playerId = playerId;
            this.initialPlayer = initialPlayer;
            this.modelIdentifiers = modelIdentifiers;
        }
    }

    public record PhysicsModelView(Object mesh, float[] boneTransforms, Matrix4f relativePose,
                                   List<String> modelIdentifiers) {
    }

    private static final class CaptureContext {
        private final Matrix4f basePose;
        private final Matrix3f baseNormal;
        private final boolean suppressMeshDrawing;
        private SnapshotDraft draft;

        private CaptureContext(PoseStack.Pose pose, boolean suppressMeshDrawing) {
            this.basePose = new Matrix4f(pose.pose());
            this.baseNormal = new Matrix3f(pose.normal());
            this.suppressMeshDrawing = suppressMeshDrawing;
        }
    }

    /** 捕获时接收并丢弃 YSM 附加层产生的顶点，避免辅助绘制进入任何游戏渲染缓冲。 */
    private static final class DiscardingVertexConsumer implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    private record SnapshotDraft(Object mesh, float[] boneTransforms, float[] secondaryState,
                                 int textureIndex, int renderPartMask, int packedOverlay,
                                 float red, float green, float blue, float alpha,
                                 Matrix4f relativePose, Matrix3f relativeNormal) {
    }

    private record ModelSnapshot(Object mesh, float[] boneTransforms, float[] secondaryState,
                                 int textureIndex, int renderPartMask, int packedOverlay,
                                 float red, float green, float blue, float alpha,
                                 Matrix4f relativePose, Matrix3f relativeNormal,
                                 ResourceLocation texture, RenderType renderType) {
    }

    /** 保存并恢复首次捕获期间的玩家变换，避免修改正常游戏状态。 */
    private record PlayerRenderState(int deathTime,
                                     double x, double y, double z,
                                     double xo, double yo, double zo,
                                     float yRot, float yRotO,
                                     float bodyRot, float bodyRotO,
                                     float headRot, float headRotO,
                                     float xRot, float xRotO) {
        private static PlayerRenderState capture(Player player) {
            return new PlayerRenderState(player.deathTime,
                    player.getX(), player.getY(), player.getZ(),
                    player.xo, player.yo, player.zo,
                    player.getYRot(), player.yRotO,
                    player.yBodyRot, player.yBodyRotO,
                    player.yHeadRot, player.yHeadRotO,
                    player.getXRot(), player.xRotO);
        }

        private void restore(Player player) {
            player.deathTime = deathTime;
            player.setPos(x, y, z);
            player.xo = xo;
            player.yo = yo;
            player.zo = zo;
            player.setYRot(yRot);
            player.yRotO = yRotO;
            player.yBodyRot = bodyRot;
            player.yBodyRotO = bodyRotO;
            player.yHeadRot = headRot;
            player.yHeadRotO = headRotO;
            player.setXRot(xRot);
            player.xRotO = xRotO;
        }
    }
}
