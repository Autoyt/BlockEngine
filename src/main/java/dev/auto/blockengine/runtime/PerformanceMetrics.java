package dev.auto.blockengine.runtime;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PerformanceMetrics {
    public static final String CHUNK_LOAD = "chunk-load";
    public static final String CHUNK_SAVE = "chunk-save";
    public static final String CHUNK_ENCODE = "chunk-encode";
    public static final String CHUNK_DECODE = "chunk-decode";
    public static final String BLOCK_READ = "block-read";
    public static final String BLOCK_WRITE = "block-write";
    public static final String ADAPTER_LOAD = "adapter-load";
    public static final String ADAPTER_SAVE = "adapter-save";
    public static final String TICKER = "ticker";
    public static final String INTEGRITY = "integrity";
    public static final String EXPLOSION = "explosion";
    public static final String VISIBILITY = "visibility";

    private static final Map<String, Series> SERIES = new HashMap<>();
    private static boolean enabled = true;

    private PerformanceMetrics() {
    }

    public static synchronized void enabled(boolean value) {
        enabled = value;
    }

    public static synchronized boolean enabled() {
        return enabled;
    }

    public static void record(@NotNull String key, long nanos) {
        record(key, nanos, 0, 0);
    }

    public static synchronized void record(@NotNull String key, long nanos, long units, long bytes) {
        if (!enabled) {
            return;
        }
        SERIES.computeIfAbsent(key, ignored -> new Series()).record(Math.max(0L, nanos), Math.max(0L, units), Math.max(0L, bytes));
    }

    public static synchronized @NotNull Snapshot snapshot(@NotNull String key) {
        return SERIES.computeIfAbsent(key, ignored -> new Series()).snapshot();
    }

    public static synchronized void clear() {
        SERIES.clear();
    }

    public static @NotNull String ms(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static final class Series {
        private static final int WINDOW = 240;
        private static final long STALE_NANOS = 10_000_000_000L;

        private final ArrayDeque<Sample> samples = new ArrayDeque<>();
        private long totalSamples;
        private long totalNanos;
        private long totalUnits;
        private long totalBytes;
        private long lastRecorded = -1L;

        private void record(long nanos, long units, long bytes) {
            samples.add(new Sample(nanos, units, bytes));
            totalSamples++;
            totalNanos += nanos;
            totalUnits += units;
            totalBytes += bytes;
            lastRecorded = System.nanoTime();
            while (samples.size() > WINDOW) {
                samples.poll();
            }
        }

        private @NotNull Snapshot snapshot() {
            if (samples.isEmpty()) {
                return new Snapshot(0, totalSamples, 0, 0, 0, 0, 0, 0, 0, totalUnits, totalBytes, -1L);
            }

            List<Sample> window = new ArrayList<>(samples);
            List<Long> nanos = window.stream().map(Sample::nanos).sorted().toList();
            long windowNanos = 0L;
            long windowUnits = 0L;
            long windowBytes = 0L;
            for (Sample sample : window) {
                windowNanos += sample.nanos();
                windowUnits += sample.units();
                windowBytes += sample.bytes();
            }
            long avgNanos = windowNanos / window.size();
            int p95Index = Math.max(0, (int) Math.ceil(nanos.size() * 0.95) - 1);
            long p95Nanos = nanos.get(Math.min(nanos.size() - 1, p95Index));
            long maxNanos = nanos.get(nanos.size() - 1);
            long avgUnits = windowUnits / window.size();
            long avgBytes = windowBytes / window.size();
            long nanosPerUnit = windowUnits <= 0L ? 0L : windowNanos / windowUnits;
            long lastAge = lastRecorded < 0L ? -1L : System.nanoTime() - lastRecorded;
            return new Snapshot(window.size(), totalSamples, avgNanos, p95Nanos, maxNanos,
                    avgUnits, avgBytes, nanosPerUnit, windowNanos, totalUnits, totalBytes, lastAge);
        }
    }

    private record Sample(long nanos, long units, long bytes) {
    }

    public record Snapshot(
            int windowSamples,
            long totalSamples,
            long avgNanos,
            long p95Nanos,
            long maxNanos,
            long avgUnits,
            long avgBytes,
            long nanosPerUnit,
            long windowNanos,
            long totalUnits,
            long totalBytes,
            long lastSampleAgeNanos
    ) {
        public boolean empty() {
            return windowSamples <= 0;
        }

        public boolean stale() {
            return lastSampleAgeNanos > Series.STALE_NANOS;
        }
    }
}
