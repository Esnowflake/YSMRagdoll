package com.ysmragdoll;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;

/**
 * 将模型捕获、骨架解析和物理初始化等关键诊断信息写入独立日志。
 *
 * <p>ForgeGradle 的开发运行会通过 {@code ysmragdoll.projectDir} 指定工程目录；
 * 普通游戏实例没有该属性时使用游戏进程的工作目录。这样工程移动或解压到另一台
 * 机器后不会继续写入某个开发者的绝对路径。</p>
 */
public final class YsmRagdollLog {
    private static final Object LOCK = new Object();
    private static final long MAX_LOG_BYTES = 8L * 1024L * 1024L;
    private static Path logFile;

    private YsmRagdollLog() {
    }

    public static void info(String message) {
        write("INFO", message);
    }

    public static void warn(String message) {
        write("WARN", message, true);
    }

    public static void warn(String message, Throwable exception) {
        StringWriter trace = new StringWriter();
        exception.printStackTrace(new PrintWriter(trace));
        warn(message + System.lineSeparator() + trace);
    }

    public static void performance(String message) {
        write("PERFORMANCE", message, false);
    }

    private static void write(String level, String message) {
        write(level, message, true);
    }

    private static void write(String level, String message, boolean mirrorToForge) {
        synchronized (LOCK) {
            try {
                if (logFile == null) {
                    String projectDir = System.getProperty("ysmragdoll.projectDir");
                    Path project = projectDir == null || projectDir.isBlank()
                            ? Path.of("").toAbsolutePath().normalize() : Path.of(projectDir);
                    Path root = project.resolve("ysm-ragdoll-logs");
                    Files.createDirectories(root);
                    logFile = root.resolve("ysm-ragdoll.log");
                }
                rotateIfNeeded();
                String line = OffsetDateTime.now() + " [" + level + "] " + message + System.lineSeparator();
                Files.writeString(logFile, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                if (!mirrorToForge) {
                    return;
                }
                if ("WARN".equals(level)) {
                    OpenYsmRagdollMod.LOGGER.warn("[ysmragdoll] {}", message);
                } else {
                    OpenYsmRagdollMod.LOGGER.info("[ysmragdoll] {}", message);
                }
            } catch (IOException exception) {
                OpenYsmRagdollMod.LOGGER.warn("无法写入 YSM Ragdoll 专属日志", exception);
            }
        }
    }

    private static void rotateIfNeeded() throws IOException {
        if (!Files.isRegularFile(logFile) || Files.size(logFile) < MAX_LOG_BYTES) {
            return;
        }
        Path previous = logFile.resolveSibling("ysm-ragdoll.previous.log");
        Files.move(logFile, previous, StandardCopyOption.REPLACE_EXISTING);
    }
}
