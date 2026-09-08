package com.ysmragdoll.client.physics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.ysmragdoll.YsmRagdollLog;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 按 OpenYSM 的文件加载规则读取 ysm.json 与 Bedrock main.json，取得真实立方体顶点范围。
 * 文件只在首次遇到一种网格时读取，之后由骨架解析缓存直接复用。
 */
final class OpenYsmGeometryLoader {
    private static final String[] MODEL_ROOTS = {"builtin", "custom", "auth"};

    private OpenYsmGeometryLoader() {
    }

    static List<RagdollDefinition.Bone> enrich(List<RagdollDefinition.Bone> runtimeBones,
                                               List<String> identifiers) {
        Geometry geometry = findGeometry(runtimeBones, identifiers);
        if (geometry == null) {
            YsmRagdollLog.warn("未定位到当前模型的 OpenYSM main.json，使用骨骼枢轴尺寸后备");
            return runtimeBones;
        }

        Map<String, Integer> runtimeIndices = new HashMap<>();
        for (RagdollDefinition.Bone bone : runtimeBones) {
            runtimeIndices.putIfAbsent(normalize(bone.name()), bone.index());
        }
        List<RagdollDefinition.Bone> result = new ArrayList<>(runtimeBones.size());
        int geometryMatches = 0;
        for (RagdollDefinition.Bone bone : runtimeBones) {
            GeometryBone source = geometry.bones.get(normalize(bone.name()));
            if (source == null) {
                result.add(bone);
                continue;
            }
            geometryMatches++;
            int parent = source.parentName.isEmpty()
                    ? -1 : runtimeIndices.getOrDefault(normalize(source.parentName), -1);
            result.add(new RagdollDefinition.Bone(bone.index(), bone.name(), parent,
                    new Vector3f(source.pivot), new Vector3f(source.minimum),
                    new Vector3f(source.maximum), bone.initialGlobal()));
        }
        YsmRagdollLog.info("OpenYSM 几何匹配完成: 文件=" + geometry.source
                + ", 匹配骨骼=" + geometryMatches + "/" + runtimeBones.size());
        return List.copyOf(result);
    }

    private static Geometry findGeometry(List<RagdollDefinition.Bone> runtimeBones,
                                         List<String> identifiers) {
        Path ysmRoot = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("yes_steve_model");
        List<Path> descriptors = new ArrayList<>();
        for (String identifier : identifiers) {
            addDescriptorCandidates(ysmRoot, identifier, descriptors);
        }
        Geometry decrypted = bestDecryptedGeometry(ysmRoot, identifiers, runtimeBones);
        if (decrypted != null && score(decrypted, runtimeBones) >= runtimeBones.size() * 8) {
            return decrypted;
        }
        Geometry best = bestGeometry(descriptors, runtimeBones);
        if (best != null && score(best, runtimeBones) >= runtimeBones.size() * 8) {
            return best;
        }

        // capability 标识在不同 YSM 小版本中可能不是模型 ID；此时按骨骼名称和枢轴签名匹配。
        descriptors.clear();
        for (String rootName : MODEL_ROOTS) {
            Path root = ysmRoot.resolve(rootName);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.find(root, 5,
                    (path, attributes) -> attributes.isRegularFile()
                            && path.getFileName().toString().equals("ysm.json"))) {
                stream.forEach(descriptors::add);
            } catch (IOException exception) {
                YsmRagdollLog.warn("扫描 OpenYSM 模型目录失败: " + root + ", " + exception);
            }
        }
        best = bestGeometry(descriptors, runtimeBones);
        // 普通 YSM 模型共享大量标准骨骼名称；匹配不足时宁可使用当前模型自身的枢轴，
        // 也不能把另一个角色的立方体尺寸套进来。
        return best != null && score(best, runtimeBones) >= runtimeBones.size() * 10
                ? best : null;
    }

    /** 优先解密当前模型的 crypto=3 文件，取得二进制中已经烘焙好的真实面顶点。 */
    private static Geometry bestDecryptedGeometry(Path ysmRoot,
                                                   List<String> identifiers,
                                                   List<RagdollDefinition.Bone> runtimeBones) {
        Geometry best = null;
        int bestScore = Integer.MIN_VALUE;
        Set<Path> candidates = new HashSet<>();
        for (String identifier : identifiers) {
            String clean = identifier == null ? "" : identifier.replace('\\', '/');
            while (clean.startsWith("/")) clean = clean.substring(1);
            for (String rootName : MODEL_ROOTS) {
                Path root = ysmRoot.resolve(rootName).normalize();
                Path candidate = root.resolve(clean).normalize();
                if (candidate.startsWith(root) && Files.isRegularFile(candidate)
                        && candidate.getFileName().toString().endsWith(".ysm")) {
                    candidates.add(candidate);
                }
            }
        }
        for (Path candidate : candidates) {
            try {
                byte[] decrypted = OpenYsmCrypto.decryptYsmFile(Files.readAllBytes(candidate));
                RawYsmModel raw;
                try (YSMBinaryDeserializer parser = new YSMBinaryDeserializer(decrypted)) {
                    raw = parser.deserialize();
                }
                Geometry geometry = fromBinaryGeometry(candidate, raw);
                int score = score(geometry, runtimeBones);
                if (score > bestScore) {
                    best = geometry;
                    bestScore = score;
                }
                YsmRagdollLog.info("OpenYSM 加密模型解密完成: 文件=" + candidate
                        + ", format=" + raw.formatVersion + ", 几何骨骼="
                        + (raw.mainEntity.mainModel == null ? 0 : raw.mainEntity.mainModel.bones.size())
                        + ", 匹配分数=" + score);
            } catch (Throwable exception) {
                YsmRagdollLog.warn("OpenYSM 加密模型解密或解析失败: 文件=" + candidate
                        + ", 原因=" + exception.getClass().getSimpleName() + ": "
                        + String.valueOf(exception.getMessage()));
            }
        }
        return best;
    }

    private static Geometry fromBinaryGeometry(Path source, RawYsmModel raw) {
        Map<String, GeometryBone> bones = new HashMap<>();
        RawYsmModel.RawGeometry main = raw.mainEntity.mainModel;
        if (main == null) {
            return new Geometry(source, bones);
        }
        for (RawYsmModel.RawBone rawBone : main.bones) {
            Vector3f minimum = new Vector3f(Float.POSITIVE_INFINITY);
            Vector3f maximum = new Vector3f(Float.NEGATIVE_INFINITY);
            for (RawYsmModel.RawCube cube : rawBone.cubes) {
                for (RawYsmModel.RawFace face : cube.faces) {
                    for (float[] position : face.positions) {
                        if (position == null || position.length < 3) continue;
                        // 二进制面顶点使用像素坐标，与 YSM 的渲染骨骼坐标一致。
                        // OpenYSM 在解密时已经把面顶点烘焙为方块单位；这里不能再次除以 16。
                        float x = position[0];
                        float y = position[1];
                        float z = position[2];
                        minimum.x = Math.min(minimum.x, x);
                        minimum.y = Math.min(minimum.y, y);
                        minimum.z = Math.min(minimum.z, z);
                        maximum.x = Math.max(maximum.x, x);
                        maximum.y = Math.max(maximum.y, y);
                        maximum.z = Math.max(maximum.z, z);
                    }
                }
            }
            Vector3f pivot = rawBone.pivot == null || rawBone.pivot.length < 3
                    ? new Vector3f() : new Vector3f(rawBone.pivot);
            bones.putIfAbsent(normalize(rawBone.name), new GeometryBone(rawBone.name,
                    rawBone.parentName == null ? "" : rawBone.parentName,
                    pivot, minimum, maximum));
        }
        return new Geometry(source, bones);
    }

    private static void addDescriptorCandidates(Path ysmRoot, String identifier,
                                                List<Path> output) {
        String clean = identifier.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.endsWith("/ysm.json")) clean = clean.substring(0, clean.length() - 9);
        for (String rootName : MODEL_ROOTS) {
            Path root = ysmRoot.resolve(rootName).normalize();
            Path candidate = root.resolve(clean).normalize();
            if (candidate.startsWith(root)) {
                Path descriptor = candidate.resolve("ysm.json");
                if (Files.isRegularFile(descriptor) && !output.contains(descriptor)) {
                    output.add(descriptor);
                }
            }
        }
    }

    private static Geometry bestGeometry(List<Path> descriptors,
                                         List<RagdollDefinition.Bone> runtimeBones) {
        Geometry best = null;
        int bestScore = Integer.MIN_VALUE;
        Set<Path> visitedModels = new HashSet<>();
        for (Path descriptor : descriptors) {
            Path model = resolveMainModel(descriptor);
            if (model == null || !visitedModels.add(model)) {
                continue;
            }
            for (Geometry geometry : readGeometries(model)) {
                int score = score(geometry, runtimeBones);
                if (score > bestScore) {
                    best = geometry;
                    bestScore = score;
                }
            }
        }
        return bestScore > 0 ? best : null;
    }

    private static int score(Geometry geometry, List<RagdollDefinition.Bone> runtimeBones) {
        int score = 0;
        for (RagdollDefinition.Bone runtime : runtimeBones) {
            GeometryBone candidate = geometry.bones.get(normalize(runtime.name()));
            if (candidate == null) {
                continue;
            }
            score += 10;
            float pivotDifference = candidate.pivot.distance(runtime.pivot());
            if (pivotDifference < 0.02F) score += 8;
            else if (pivotDifference < 0.5F) score += 4;
        }
        return score;
    }

    private static Path resolveMainModel(Path descriptor) {
        try (Reader reader = Files.newBufferedReader(descriptor, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject files = object(root, "files");
            JsonObject player = object(files, "player");
            JsonObject model = object(player, "model");
            if (model == null || !model.has("main")) {
                return null;
            }
            Path base = descriptor.getParent().normalize();
            Path result = base.resolve(model.get("main").getAsString()).normalize();
            return result.startsWith(base) && Files.isRegularFile(result) ? result : null;
        } catch (RuntimeException | IOException ignored) {
            return null;
        }
    }

    private static List<Geometry> readGeometries(Path modelFile) {
        List<Geometry> result = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(modelFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray geometries = array(root, "minecraft:geometry");
            if (geometries == null) {
                return result;
            }
            for (JsonElement element : geometries) {
                if (!element.isJsonObject()) continue;
                JsonArray bones = array(element.getAsJsonObject(), "bones");
                if (bones == null) continue;
                Map<String, GeometryBone> parsed = new HashMap<>();
                for (JsonElement boneElement : bones) {
                    if (!boneElement.isJsonObject()) continue;
                    GeometryBone bone = readBone(boneElement.getAsJsonObject());
                    if (!bone.name.isEmpty()) {
                        parsed.putIfAbsent(normalize(bone.name), bone);
                    }
                }
                result.add(new Geometry(modelFile, parsed));
            }
        } catch (RuntimeException | IOException ignored) {
        }
        return result;
    }

    /** 与 OpenYSM YSMFolderDeserializer 相同的 X 镜像和立方体旋转规则。 */
    private static GeometryBone readBone(JsonObject object) {
        String name = string(object, "name");
        String parent = string(object, "parent");
        Vector3f pivot = vector(object, "pivot");
        pivot.x = -pivot.x;
        Vector3f minimum = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f maximum = new Vector3f(Float.NEGATIVE_INFINITY);
        float boneInflate = number(object, "inflate", 0.0F);
        JsonArray cubes = array(object, "cubes");
        if (cubes != null) {
            for (JsonElement cubeElement : cubes) {
                if (!cubeElement.isJsonObject()) continue;
                JsonObject cube = cubeElement.getAsJsonObject();
                Vector3f origin = vector(cube, "origin");
                Vector3f size = vector(cube, "size");
                float inflate = number(cube, "inflate", boneInflate);
                float x = -origin.x - size.x - inflate;
                float y = origin.y - inflate;
                float z = origin.z - inflate;
                float width = size.x + inflate * 2.0F;
                float height = size.y + inflate * 2.0F;
                float depth = size.z + inflate * 2.0F;
                Matrix4f rotation = cubeRotation(cube);
                for (int corner = 0; corner < 8; corner++) {
                    Vector3f point = new Vector3f(
                            x + ((corner & 1) == 0 ? 0.0F : width),
                            y + ((corner & 2) == 0 ? 0.0F : height),
                            z + ((corner & 4) == 0 ? 0.0F : depth)).mul(1.0F / 16.0F);
                    rotation.transformPosition(point);
                    minimum.x = Math.min(minimum.x, point.x);
                    minimum.y = Math.min(minimum.y, point.y);
                    minimum.z = Math.min(minimum.z, point.z);
                    maximum.x = Math.max(maximum.x, point.x);
                    maximum.y = Math.max(maximum.y, point.y);
                    maximum.z = Math.max(maximum.z, point.z);
                }
            }
        }
        return new GeometryBone(name, parent, pivot, minimum, maximum);
    }

    private static Matrix4f cubeRotation(JsonObject cube) {
        if (!cube.has("rotation") && !cube.has("pivot")) {
            return new Matrix4f();
        }
        Vector3f pivot = vector(cube, "pivot");
        Vector3f rotation = vector(cube, "rotation");
        return new Matrix4f().translate(-pivot.x / 16.0F, pivot.y / 16.0F, pivot.z / 16.0F)
                .rotateZ((float) Math.toRadians(rotation.z))
                .rotateY((float) -Math.toRadians(rotation.y))
                .rotateX((float) -Math.toRadians(rotation.x))
                .translate(pivot.x / 16.0F, -pivot.y / 16.0F, -pivot.z / 16.0F);
    }

    private static JsonObject object(JsonObject source, String name) {
        return source != null && source.has(name) && source.get(name).isJsonObject()
                ? source.getAsJsonObject(name) : null;
    }

    private static JsonArray array(JsonObject source, String name) {
        return source != null && source.has(name) && source.get(name).isJsonArray()
                ? source.getAsJsonArray(name) : null;
    }

    private static String string(JsonObject source, String name) {
        return source.has(name) && source.get(name).isJsonPrimitive()
                ? source.get(name).getAsString() : "";
    }

    private static float number(JsonObject source, String name, float fallback) {
        return source.has(name) && source.get(name).isJsonPrimitive()
                ? source.get(name).getAsFloat() : fallback;
    }

    private static Vector3f vector(JsonObject source, String name) {
        JsonArray array = array(source, name);
        if (array == null || array.size() < 3) {
            return new Vector3f();
        }
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(),
                array.get(2).getAsFloat());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record Geometry(Path source, Map<String, GeometryBone> bones) {
    }

    private record GeometryBone(String name, String parentName, Vector3f pivot,
                                Vector3f minimum, Vector3f maximum) {
    }
}
