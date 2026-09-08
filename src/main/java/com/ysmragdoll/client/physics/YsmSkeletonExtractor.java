package com.ysmragdoll.client.physics;

import com.ysmragdoll.YsmRagdollLog;
import com.ysmragdoll.client.OpenYsmModelAdapter;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 按字段结构读取 YSM 的烘焙网格，并生成不可变的布娃娃骨架定义。
 *
 * <p>官方 YSM 2.6.5 的运行时类型经过混淆，因此这里只在首次遇到一个网格时反射
 * 骨骼名称、父级、枢轴与可用顶点。缺少立方体列表时，再由 {@link OpenYsmGeometryLoader}
 * 按 OpenYSM 规则读取或解密本地模型文件补齐真实几何。解析结果使用弱键缓存，不进入
 * 120 Hz 物理解算和逐帧渲染热路径。</p>
 */
public final class YsmSkeletonExtractor {
    private static final float COLLISION_SHRINK = 0.95F;
    private static final Map<Object, RagdollDefinition> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private YsmSkeletonExtractor() {
    }

    public static RagdollDefinition extract(OpenYsmModelAdapter.PhysicsModelView view) {
        RagdollDefinition cached = CACHE.get(view.mesh());
        if (cached != null) {
            return applyCapturedPose(cached, view.boneTransforms());
        }
        RagdollDefinition parsed = parse(view);
        CACHE.put(view.mesh(), parsed);
        return parsed;
    }

    /**
     * 几何、骨骼层级和部位映射可以按网格复用，但 initialGlobal 属于某一次死亡快照。
     * 每具布娃娃必须用自己的骨骼参数重建它，否则不同死亡动作会共享错误的绑定姿态。
     */
    private static RagdollDefinition applyCapturedPose(RagdollDefinition template, float[] params) {
        if (template.bones().isEmpty() || params.length < template.bones().size() * 12) {
            return template;
        }
        Matrix4f[] globals = calculateInitialMatrices(template.bones(), params);
        List<RagdollDefinition.Bone> posedBones = new ArrayList<>(template.bones().size());
        for (int index = 0; index < template.bones().size(); index++) {
            RagdollDefinition.Bone bone = template.bones().get(index);
            posedBones.add(new RagdollDefinition.Bone(bone.index(), bone.name(), bone.parentIndex(),
                    bone.pivot(), bone.minimum(), bone.maximum(), new Matrix4f(globals[index])));
        }
        return new RagdollDefinition(List.copyOf(posedBones), Map.copyOf(mapParts(posedBones)));
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static RagdollDefinition parse(OpenYsmModelAdapter.PhysicsModelView view) {
        try {
            int expectedBones = view.boneTransforms().length / 12;
            List<?> rawBones = findBoneList(view.mesh(), expectedBones);
            if (rawBones == null || rawBones.isEmpty()) {
                throw new IllegalStateException("未找到与骨骼参数数量匹配的烘焙骨骼列表");
            }

            Class<?> boneClass = rawBones.get(0).getClass();
            Field nameField = firstField(boneClass, String.class);
            List<Field> intFields = fieldsOfType(boneClass, int.class);
            List<Field> floatFields = fieldsOfType(boneClass, float.class);
            Field cubeListField = listField(boneClass);
            if (nameField == null || floatFields.size() < 3) {
                throw new IllegalStateException("YSM 骨骼字段结构不受支持: " + boneClass.getName());
            }
            Field parentField = chooseParentField(intFields, rawBones);

            List<RagdollDefinition.Bone> bones = new ArrayList<>(rawBones.size());
            Matrix4f[] globals = calculateInitialMatrices(rawBones, parentField, floatFields,
                    view.boneTransforms());
            for (int index = 0; index < rawBones.size(); index++) {
                Object rawBone = rawBones.get(index);
                String name = (String) nameField.get(rawBone);
                int parent = parentField == null ? -1 : parentField.getInt(rawBone);
                Vector3f pivot = new Vector3f(floatFields.get(0).getFloat(rawBone),
                        floatFields.get(1).getFloat(rawBone), floatFields.get(2).getFloat(rawBone));
                Bounds bounds = cubeListField == null ? new Bounds() : readBounds(cubeListField.get(rawBone));
                bones.add(new RagdollDefinition.Bone(index, name, validParent(parent, index, rawBones.size()),
                        pivot, bounds.minimum, bounds.maximum, new Matrix4f(globals[index])));
            }

            if (cubeListField == null) {
                bones = new ArrayList<>(OpenYsmGeometryLoader.enrich(bones, view.modelIdentifiers()));
                Matrix4f[] enrichedGlobals = calculateInitialMatrices(bones, view.boneTransforms());
                for (int index = 0; index < bones.size(); index++) {
                    RagdollDefinition.Bone bone = bones.get(index);
                    bones.set(index, new RagdollDefinition.Bone(bone.index(), bone.name(),
                            bone.parentIndex(), bone.pivot(), bone.minimum(), bone.maximum(),
                            enrichedGlobals[index]));
                }
            }

            Map<RagdollDefinition.Role, RagdollDefinition.Part> parts = mapParts(bones);
            YsmRagdollLog.info("YSM 物理骨架解析完成: 骨骼=" + bones.size()
                    + ", 主要刚体=" + parts.size()
                    + ", 数据源=" + (cubeListField == null ? "YSM 运行时骨架枢轴" : "OpenYSM 烘焙顶点")
                    + ", 映射=" + describeParts(parts, bones));
            return new RagdollDefinition(List.copyOf(bones), Map.copyOf(parts));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            YsmRagdollLog.warn("解析 YSM 物理骨架失败，将保留静态快照: " + exception);
            return new RagdollDefinition(List.of(), Map.of());
        }
    }

    private static List<?> findBoneList(Object mesh, int expectedBones) throws IllegalAccessException {
        List<?> fallback = null;
        for (Class<?> type = mesh.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType()) || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(mesh);
                if (value instanceof List<?> list && list.size() == expectedBones && !list.isEmpty()
                        && firstField(list.get(0).getClass(), String.class) != null
                        && fieldsOfType(list.get(0).getClass(), float.class).size() >= 3) {
                    // OpenYSM 网格还包含立方体列表；官方 YSM 2.6.5 只保留骨架枢轴。
                    if (listField(list.get(0).getClass()) != null) {
                        return list;
                    }
                    fallback = list;
                }
            }
        }
        return fallback;
    }

    private static Matrix4f[] calculateInitialMatrices(List<?> rawBones, Field parentField,
                                                        List<Field> floatFields, float[] params)
            throws IllegalAccessException {
        Matrix4f[] result = new Matrix4f[rawBones.size()];
        boolean[] visiting = new boolean[rawBones.size()];
        for (int i = 0; i < rawBones.size(); i++) {
            calculateInitialMatrix(i, rawBones, parentField, floatFields, params, result, visiting);
        }
        return result;
    }

    private static Matrix4f calculateInitialMatrix(int index, List<?> rawBones, Field parentField,
                                                    List<Field> floatFields, float[] params,
                                                    Matrix4f[] cache, boolean[] visiting)
            throws IllegalAccessException {
        if (cache[index] != null) {
            return cache[index];
        }
        if (visiting[index]) {
            return cache[index] = new Matrix4f();
        }
        visiting[index] = true;
        Object bone = rawBones.get(index);
        int parent = parentField == null ? -1 : parentField.getInt(bone);
        Matrix4f matrix = validParent(parent, index, rawBones.size()) >= 0
                ? new Matrix4f(calculateInitialMatrix(parent, rawBones, parentField, floatFields,
                params, cache, visiting)) : new Matrix4f();
        float px = floatFields.get(0).getFloat(bone);
        float py = floatFields.get(1).getFloat(bone);
        float pz = floatFields.get(2).getFloat(bone);
        matrix.mul(BonePoseMath.local(new Vector3f(px, py, pz), params, index));
        visiting[index] = false;
        return cache[index] = matrix;
    }

    private static Matrix4f[] calculateInitialMatrices(List<RagdollDefinition.Bone> bones,
                                                        float[] params) {
        Matrix4f[] result = new Matrix4f[bones.size()];
        boolean[] visiting = new boolean[bones.size()];
        for (int index = 0; index < bones.size(); index++) {
            calculateInitialMatrix(index, bones, params, result, visiting);
        }
        return result;
    }

    private static Matrix4f calculateInitialMatrix(int index,
                                                    List<RagdollDefinition.Bone> bones,
                                                    float[] params, Matrix4f[] cache,
                                                    boolean[] visiting) {
        if (cache[index] != null) return cache[index];
        if (visiting[index]) return cache[index] = new Matrix4f();
        visiting[index] = true;
        RagdollDefinition.Bone bone = bones.get(index);
        int parent = validParent(bone.parentIndex(), index, bones.size());
        Matrix4f matrix = parent >= 0
                ? new Matrix4f(calculateInitialMatrix(parent, bones, params, cache, visiting))
                : new Matrix4f();
        matrix.mul(BonePoseMath.local(bone.pivot(), params, index));
        visiting[index] = false;
        return cache[index] = matrix;
    }

    private static Bounds readBounds(Object cubesValue) throws ReflectiveOperationException {
        Bounds bounds = new Bounds();
        if (!(cubesValue instanceof List<?> cubes)) {
            return bounds;
        }
        for (Object cube : cubes) {
            Field quadsField = listField(cube.getClass());
            if (quadsField == null || !(quadsField.get(cube) instanceof List<?> quads)) {
                continue;
            }
            for (Object quad : quads) {
                Field positionsField = vectorArrayField(quad.getClass());
                if (positionsField == null || !(positionsField.get(quad) instanceof Object[] positions)) {
                    continue;
                }
                for (Object position : positions) {
                    if (position instanceof Vector3f vector) {
                        // YSM/OpenYSM 的烘焙网格顶点已经是方块单位。
                        bounds.include(vector.x, vector.y, vector.z);
                    }
                }
            }
        }
        return bounds;
    }

    private static Map<RagdollDefinition.Role, RagdollDefinition.Part> mapParts(
            List<RagdollDefinition.Bone> bones) {
        EnumMap<RagdollDefinition.Role, RagdollDefinition.Bone> mapped =
                new EnumMap<>(RagdollDefinition.Role.class);
        Set<Integer> used = new HashSet<>();
        for (RagdollDefinition.Role role : RagdollDefinition.Role.values()) {
            RagdollDefinition.Bone best = null;
            int bestScore = 0;
            for (RagdollDefinition.Bone bone : bones) {
                if (used.contains(bone.index())) {
                    continue;
                }
                int score = score(role, normalize(bone.name()));
                if (score > bestScore) {
                    best = bone;
                    bestScore = score;
                }
            }
            if (best == null) {
                continue;
            }
            used.add(best.index());
            mapped.put(role, best);
        }

        // AllHead 是 YSM 的层级容器，真正的头部网格通常位于其子骨骼 Head。
        // 优先选择带有真实网格的 Head，避免把只有颈部定位小方块的容器当成头部刚体。
        RagdollDefinition.Bone physicalHead = findBone(bones, "head");
        RagdollDefinition.Bone selectedHead = mapped.get(RagdollDefinition.Role.HEAD);
        if (physicalHead != null && physicalHead != selectedHead) {
            if (selectedHead != null) {
                used.remove(selectedHead.index());
            }
            mapped.put(RagdollDefinition.Role.HEAD, physicalHead);
            used.add(physicalHead.index());
        }

        EnumMap<RagdollDefinition.Role, RagdollDefinition.Part> result =
                new EnumMap<>(RagdollDefinition.Role.class);
        for (Map.Entry<RagdollDefinition.Role, RagdollDefinition.Bone> entry : mapped.entrySet()) {
            RagdollDefinition.Role role = entry.getKey();
            RagdollDefinition.Bone bone = entry.getValue();
            Vector3f center;
            Vector3f half;
            Bounds aggregate = role == RagdollDefinition.Role.BODY && !bone.hasGeometry()
                    ? aggregateBodyBounds(bone, bones) : null;
            if (aggregate != null && aggregate.isFinite()) {
                Vector3f size = new Vector3f(aggregate.maximum).sub(aggregate.minimum);
                center = new Vector3f(aggregate.minimum).add(aggregate.maximum).mul(0.5F);
                half = clampHalfExtents(role, size);
            } else if (bone.hasGeometry()) {
                Vector3f size = bone.sizeInBlocks();
                center = bone.center();
                half = clampHalfExtents(role, size);
            } else {
                center = fallbackCenter(role, bone, mapped);
                half = fallbackHalfExtents(role, bone, mapped);
            }
            half = shrinkHalfExtents(half);
            result.put(role, new RagdollDefinition.Part(role, bone.index(), center, half,
                    mass(role, half)));
        }
        return result;
    }

    private static RagdollDefinition.Bone findBone(List<RagdollDefinition.Bone> bones, String expectedName) {
        for (RagdollDefinition.Bone bone : bones) {
            if (normalize(bone.name()).equals(expectedName) && bone.hasGeometry()) {
                return bone;
            }
        }
        return null;
    }

    /** 把身体子骨骼的真实网格转换到 AllBody 的局部坐标后合并。 */
    private static Bounds aggregateBodyBounds(RagdollDefinition.Bone reference,
                                               List<RagdollDefinition.Bone> bones) {
        Bounds result = new Bounds();
        Matrix4f referenceInverse = new Matrix4f(reference.initialGlobal()).invert();
        for (RagdollDefinition.Bone bone : bones) {
            String name = normalize(bone.name());
            if (!bone.hasGeometry() || !name.contains("body") || name.equals("allbody")) {
                continue;
            }
            Matrix4f toReference = new Matrix4f(referenceInverse).mul(bone.initialGlobal());
            Vector3f minimum = bone.minimum();
            Vector3f maximum = bone.maximum();
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = new Vector3f(
                        (corner & 1) == 0 ? minimum.x : maximum.x,
                        (corner & 2) == 0 ? minimum.y : maximum.y,
                        (corner & 4) == 0 ? minimum.z : maximum.z);
                toReference.transformPosition(point);
                result.include(point.x, point.y, point.z);
            }
        }
        return result;
    }

    private static Vector3f fallbackCenter(RagdollDefinition.Role role,
                                           RagdollDefinition.Bone bone,
                                           Map<RagdollDefinition.Role, RagdollDefinition.Bone> mapped) {
        Vector3f pivot = pivotInBlocks(bone);
        RagdollDefinition.Role childRole = segmentChild(role);
        RagdollDefinition.Bone child = mapped.get(childRole);
        if (child != null) {
            return pivot.add(pivotInBlocks(child)).mul(0.5F);
        }
        if (role == RagdollDefinition.Role.BODY) {
            Vector3f sum = new Vector3f();
            int count = 0;
            for (RagdollDefinition.Role anchor : new RagdollDefinition.Role[]{
                    RagdollDefinition.Role.LEFT_UPPER_ARM,
                    RagdollDefinition.Role.RIGHT_UPPER_ARM,
                    RagdollDefinition.Role.LEFT_THIGH,
                    RagdollDefinition.Role.RIGHT_THIGH}) {
                RagdollDefinition.Bone anchorBone = mapped.get(anchor);
                if (anchorBone != null) {
                    sum.add(pivotInBlocks(anchorBone));
                    count++;
                }
            }
            if (count > 0) {
                return sum.mul(1.0F / count);
            }
        }
        if (role == RagdollDefinition.Role.HEAD) {
            RagdollDefinition.Bone body = mapped.get(RagdollDefinition.Role.BODY);
            if (body != null) {
                Vector3f upward = new Vector3f(pivot).sub(pivotInBlocks(body));
                float length = upward.length();
                if (length > 1.0E-4F) {
                    pivot.fma(clamp(length * 0.28F, 0.14F, 0.28F) / length, upward);
                }
            }
        }
        return pivot;
    }

    private static Vector3f fallbackHalfExtents(RagdollDefinition.Role role,
                                                RagdollDefinition.Bone bone,
                                                Map<RagdollDefinition.Role, RagdollDefinition.Bone> mapped) {
        if (role == RagdollDefinition.Role.BODY) {
            float shoulderWidth = distance(mapped.get(RagdollDefinition.Role.LEFT_UPPER_ARM),
                    mapped.get(RagdollDefinition.Role.RIGHT_UPPER_ARM), 0.65F);
            float bodyHeight = distance(bone, mapped.get(RagdollDefinition.Role.HEAD), 0.75F);
            return new Vector3f(clamp(shoulderWidth * 0.34F, 0.20F, 0.55F),
                    clamp(bodyHeight * 0.34F, 0.28F, 0.75F),
                    clamp(shoulderWidth * 0.24F, 0.17F, 0.42F));
        }
        if (role == RagdollDefinition.Role.HEAD) {
            float scale = distance(bone, mapped.get(RagdollDefinition.Role.BODY), 0.72F);
            // 加密模型无法读取头发、帽子等立方体外轮廓，头部后备箱需留出更充分的接地余量。
            float radius = clamp(scale * 0.42F, 0.32F, 0.46F);
            return new Vector3f(radius, clamp(radius * 1.15F, 0.36F, 0.52F), radius);
        }

        RagdollDefinition.Bone child = mapped.get(segmentChild(role));
        float length;
        if (child != null) {
            length = distance(bone, child, 0.34F);
        } else {
            RagdollDefinition.Bone parent = mapped.get(segmentParent(role));
            length = distance(parent, bone, 0.30F) * 0.42F;
        }
        float thickness = clamp(length * 0.21F, 0.085F, 0.20F);
        if (role == RagdollDefinition.Role.LEFT_FOOT || role == RagdollDefinition.Role.RIGHT_FOOT) {
            return new Vector3f(thickness, thickness, clamp(length * 0.65F, 0.12F, 0.32F));
        }
        return new Vector3f(thickness, clamp(length * 0.5F, 0.10F, 0.48F), thickness);
    }

    private static Vector3f clampHalfExtents(RagdollDefinition.Role role, Vector3f size) {
        if (role == RagdollDefinition.Role.HEAD) {
            // 头部碰撞体只取真实头部包围盒的最短边，保持中心不变并收缩成长宽高相等的正方体。
            float edge = clamp(Math.min(size.x, Math.min(size.y, size.z)) * 0.5F,
                    0.06F, 0.38F);
            return new Vector3f(edge, edge, edge);
        }
        return new Vector3f(
                clamp(size.x * 0.5F, 0.06F, role == RagdollDefinition.Role.BODY ? 0.65F : 0.38F),
                clamp(size.y * 0.5F, 0.06F, role == RagdollDefinition.Role.BODY ? 0.85F : 0.55F),
                clamp(size.z * 0.5F, 0.06F, role == RagdollDefinition.Role.BODY ? 0.55F : 0.38F));
    }

    private static Vector3f shrinkHalfExtents(Vector3f half) {
        return new Vector3f(Math.max(0.04F, half.x * COLLISION_SHRINK),
                Math.max(0.04F, half.y * COLLISION_SHRINK),
                Math.max(0.04F, half.z * COLLISION_SHRINK));
    }

    private static float distance(RagdollDefinition.Bone first, RagdollDefinition.Bone second,
                                  float fallback) {
        if (first == null || second == null) {
            return fallback;
        }
        return clamp(pivotInBlocks(first).distance(pivotInBlocks(second)), 0.12F, 1.8F);
    }

    private static Vector3f pivotInBlocks(RagdollDefinition.Bone bone) {
        return new Vector3f(bone.pivot()).mul(1.0F / 16.0F);
    }

    private static RagdollDefinition.Role segmentChild(RagdollDefinition.Role role) {
        return switch (role) {
            case LEFT_UPPER_ARM -> RagdollDefinition.Role.LEFT_FOREARM;
            case RIGHT_UPPER_ARM -> RagdollDefinition.Role.RIGHT_FOREARM;
            case LEFT_FOREARM -> RagdollDefinition.Role.LEFT_HAND;
            case RIGHT_FOREARM -> RagdollDefinition.Role.RIGHT_HAND;
            case LEFT_THIGH -> RagdollDefinition.Role.LEFT_SHIN;
            case RIGHT_THIGH -> RagdollDefinition.Role.RIGHT_SHIN;
            case LEFT_SHIN -> RagdollDefinition.Role.LEFT_FOOT;
            case RIGHT_SHIN -> RagdollDefinition.Role.RIGHT_FOOT;
            default -> null;
        };
    }

    private static RagdollDefinition.Role segmentParent(RagdollDefinition.Role role) {
        return switch (role) {
            case LEFT_FOREARM -> RagdollDefinition.Role.LEFT_UPPER_ARM;
            case RIGHT_FOREARM -> RagdollDefinition.Role.RIGHT_UPPER_ARM;
            case LEFT_HAND -> RagdollDefinition.Role.LEFT_FOREARM;
            case RIGHT_HAND -> RagdollDefinition.Role.RIGHT_FOREARM;
            case LEFT_SHIN -> RagdollDefinition.Role.LEFT_THIGH;
            case RIGHT_SHIN -> RagdollDefinition.Role.RIGHT_THIGH;
            case LEFT_FOOT -> RagdollDefinition.Role.LEFT_SHIN;
            case RIGHT_FOOT -> RagdollDefinition.Role.RIGHT_SHIN;
            default -> RagdollDefinition.Role.BODY;
        };
    }

    private static String describeParts(Map<RagdollDefinition.Role, RagdollDefinition.Part> parts,
                                        List<RagdollDefinition.Bone> bones) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<RagdollDefinition.Role, RagdollDefinition.Part> entry : parts.entrySet()) {
            if (!result.isEmpty()) {
                result.append(',');
            }
            result.append(entry.getKey()).append('=').append(
                    bones.get(entry.getValue().boneIndex()).name());
        }
        return result.toString();
    }

    private static int score(RagdollDefinition.Role role, String name) {
        boolean left = name.contains("left") || name.startsWith("l");
        boolean right = name.contains("right") || name.startsWith("r");
        return switch (role) {
            case BODY -> exact(name, "mallbody", "allbody", "root", "body", "torso");
            case HEAD -> exact(name, "allhead", "mhead", "head", "skull");
            case LEFT_UPPER_ARM -> left ? exact(name, "leftupperarm", "leftarm", "larm", "armleft") : 0;
            case RIGHT_UPPER_ARM -> right ? exact(name, "rightupperarm", "rightarm", "rarm", "armright") : 0;
            case LEFT_FOREARM -> left ? exact(name, "leftforearm", "leftlowerarm", "lforearm", "leftelbow") : 0;
            case RIGHT_FOREARM -> right ? exact(name, "rightforearm", "rightlowerarm", "rforearm", "rightelbow") : 0;
            case LEFT_HAND -> left ? exact(name, "lefthand", "lhand", "lefthandlocator") : 0;
            case RIGHT_HAND -> right ? exact(name, "righthand", "rhand", "righthandlocator") : 0;
            case LEFT_THIGH -> left ? exact(name, "leftupperleg", "leftleg", "lleg", "legleft") : 0;
            case RIGHT_THIGH -> right ? exact(name, "rightupperleg", "rightleg", "rleg", "legright") : 0;
            case LEFT_SHIN -> left ? exact(name, "leftlowerleg", "leftshin", "leftcalf") : 0;
            case RIGHT_SHIN -> right ? exact(name, "rightlowerleg", "rightshin", "rightcalf") : 0;
            case LEFT_FOOT -> left ? exact(name, "leftfoot", "leftsole", "lefttoe") : 0;
            case RIGHT_FOOT -> right ? exact(name, "rightfoot", "rightsole", "righttoe") : 0;
        };
    }

    private static int exact(String value, String... candidates) {
        for (int i = 0; i < candidates.length; i++) {
            if (value.equals(candidates[i])) {
                return 100 - i;
            }
        }
        return contains(value, candidates);
    }

    private static int contains(String value, String... candidates) {
        for (int i = 0; i < candidates.length; i++) {
            if (value.contains(candidates[i])) {
                return 70 - i;
            }
        }
        return 0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static float mass(RagdollDefinition.Role role, Vector3f half) {
        float volumeMass = Math.max(0.2F, half.x * half.y * half.z * 45.0F);
        return switch (role) {
            case BODY -> Math.max(5.0F, volumeMass);
            case HEAD -> Math.max(1.2F, volumeMass);
            case LEFT_THIGH, RIGHT_THIGH -> Math.max(1.5F, volumeMass);
            default -> Math.max(0.35F, volumeMass);
        };
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Field chooseParentField(List<Field> fields, List<?> bones) throws IllegalAccessException {
        Field best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Field field : fields) {
            int score = 0;
            for (int index = 0; index < bones.size(); index++) {
                int value = field.getInt(bones.get(index));
                if (value == -1) score += 3;
                else if (value >= 0 && value < index) score += 2;
                else if (value >= bones.size()) score -= 10;
            }
            if (score > bestScore) {
                bestScore = score;
                best = field;
            }
        }
        return best;
    }

    private static int validParent(int parent, int index, int size) {
        return parent >= 0 && parent < size && parent != index ? parent : -1;
    }

    private static Field firstField(Class<?> type, Class<?> fieldType) {
        List<Field> fields = fieldsOfType(type, fieldType);
        return fields.isEmpty() ? null : fields.get(0);
    }

    private static List<Field> fieldsOfType(Class<?> type, Class<?> fieldType) {
        List<Field> result = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == fieldType) {
                field.setAccessible(true);
                result.add(field);
            }
        }
        return result;
    }

    private static Field listField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && List.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static Field vectorArrayField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType().isArray()
                    && field.getType().getComponentType().getName().equals("org.joml.Vector3f")) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static final class Bounds {
        private final Vector3f minimum = new Vector3f(Float.POSITIVE_INFINITY);
        private final Vector3f maximum = new Vector3f(Float.NEGATIVE_INFINITY);

        private void include(float x, float y, float z) {
            minimum.x = Math.min(minimum.x, x);
            minimum.y = Math.min(minimum.y, y);
            minimum.z = Math.min(minimum.z, z);
            maximum.x = Math.max(maximum.x, x);
            maximum.y = Math.max(maximum.y, y);
            maximum.z = Math.max(maximum.z, z);
        }

        private boolean isFinite() {
            return Float.isFinite(minimum.x) && Float.isFinite(minimum.y)
                    && Float.isFinite(minimum.z) && Float.isFinite(maximum.x)
                    && Float.isFinite(maximum.y) && Float.isFinite(maximum.z);
        }
    }
}
