package com.ysmragdoll.client;

import com.ysmragdoll.YsmRagdollLog;
import com.ysmragdoll.config.YsmRagdollConfig;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;

/** 诊断模式下以 100 Hz 记录布娃娃姿态；独立文件避免污染普通运行日志。 */
final class ClientIntensiveLogger {
    private static final long SAMPLE_INTERVAL_NANOS = 10_000_000L;
    private static final int MAX_CATCH_UP_SAMPLES = 20;
    private static long nextSampleNanos;
    private static Path logFile;

    private ClientIntensiveLogger() {
    }

    static void update() {
        if (!YsmRagdollConfig.INTENSIVE_TEST.get()) {
            reset();
            return;
        }
        if (nextSampleNanos == 0L) {
            nextSampleNanos = System.nanoTime();
        }
    }

    /** 在渲染线程调用；用单调时钟补齐低帧率期间错过的采样时刻。 */
    static void recordFrame() {
        if (!YsmRagdollConfig.INTENSIVE_TEST.get()) {
            reset();
            return;
        }
        long now = System.nanoTime();
        if (nextSampleNanos == 0L) {
            nextSampleNanos = now;
        }
        int samples = 0;
        while (now >= nextSampleNanos && samples++ < MAX_CATCH_UP_SAMPLES) {
            writeSample(nextSampleNanos);
            nextSampleNanos += SAMPLE_INTERVAL_NANOS;
        }
        if (now - nextSampleNanos > SAMPLE_INTERVAL_NANOS * MAX_CATCH_UP_SAMPLES) {
            nextSampleNanos = now + SAMPLE_INTERVAL_NANOS;
        }
    }

    static void reset() {
        nextSampleNanos = 0L;
        logFile = null;
    }

    private static void writeSample(long sampleNanos) {
        try {
            if (logFile == null) {
                String projectDir = System.getProperty("ysmragdoll.projectDir");
                Path project = projectDir == null || projectDir.isBlank()
                        ? Path.of("").toAbsolutePath().normalize() : Path.of(projectDir);
                Path root = project.resolve("ysm-ragdoll-logs");
                Files.createDirectories(root);
                logFile = root.resolve("ysm-ragdoll-intensive.log");
            }
            Minecraft minecraft = Minecraft.getInstance();
            StringBuilder line = new StringBuilder(512);
            line.append(OffsetDateTime.now()).append(" [INTENSIVE] sampleNanos=")
                    .append(sampleNanos).append(" fps=").append(minecraft.getFps())
                    .append(" frameTime=").append(ClientVersion.partialTick())
                    .append(' ').append(ClientRagdollManager.intensiveState());
            Files.writeString(logFile, line.append(System.lineSeparator()).toString(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException exception) {
            YsmRagdollLog.warn("无法写入密集测试日志", exception);
            reset();
        }
    }
}
