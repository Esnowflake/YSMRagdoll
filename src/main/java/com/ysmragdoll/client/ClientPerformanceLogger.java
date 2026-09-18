package com.ysmragdoll.client;

import com.ysmragdoll.YsmRagdollLog;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** 低频汇总客户端帧率和本模组渲染耗时，供后续性能优化对比。 */
final class ClientPerformanceLogger {
    private static final long WINDOW_NANOS = 10_000_000_000L;

    private static long windowStartedAt;
    private static long renderedFrames;
    private static long ragdollRenderNanos;
    private static long maximumRagdollRenderNanos;
    private static long reportedFpsTotal;
    private static long reportedFpsSamples;
    private static int minimumReportedFps = Integer.MAX_VALUE;
    private static int maximumReportedFps;

    private ClientPerformanceLogger() {
    }

    static void recordFrame(long renderNanos, int ragdollCount, int physicsRagdollCount) {
        long now = System.nanoTime();
        if (windowStartedAt == 0L) {
            windowStartedAt = now;
        }
        int reportedFps = Math.max(0, Minecraft.getInstance().getFps());
        renderedFrames++;
        ragdollRenderNanos += Math.max(0L, renderNanos);
        maximumRagdollRenderNanos = Math.max(maximumRagdollRenderNanos, renderNanos);
        if (reportedFps > 0) {
            reportedFpsTotal += reportedFps;
            reportedFpsSamples++;
            minimumReportedFps = Math.min(minimumReportedFps, reportedFps);
            maximumReportedFps = Math.max(maximumReportedFps, reportedFps);
        }

        long elapsedNanos = now - windowStartedAt;
        if (elapsedNanos < WINDOW_NANOS || renderedFrames == 0L) {
            return;
        }
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double observedFps = renderedFrames / elapsedSeconds;
        double averageReportedFps = reportedFpsSamples == 0L ? 0.0
                : (double) reportedFpsTotal / reportedFpsSamples;
        double averageRenderMillis = ragdollRenderNanos / 1_000_000.0 / renderedFrames;
        double maximumRenderMillis = maximumRagdollRenderNanos / 1_000_000.0;
        YsmRagdollLog.performance(String.format(Locale.ROOT,
                "FPS窗口 %.1fs: 实测平均=%.1f, 游戏平均=%.1f, 游戏最低=%d, 游戏最高=%d, "
                        + "布娃娃=%d, 物理布娃娃=%d, 模组渲染平均=%.3fms, 模组渲染峰值=%.3fms",
                elapsedSeconds, observedFps, averageReportedFps,
                minimumReportedFps == Integer.MAX_VALUE ? 0 : minimumReportedFps,
                maximumReportedFps, ragdollCount, physicsRagdollCount,
                averageRenderMillis, maximumRenderMillis));
        resetAt(now);
    }

    static void reset() {
        resetAt(0L);
    }

    private static void resetAt(long startedAt) {
        windowStartedAt = startedAt;
        renderedFrames = 0L;
        ragdollRenderNanos = 0L;
        maximumRagdollRenderNanos = 0L;
        reportedFpsTotal = 0L;
        reportedFpsSamples = 0L;
        minimumReportedFps = Integer.MAX_VALUE;
        maximumReportedFps = 0;
    }
}
