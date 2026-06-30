package dev.auto.turtle.api.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Simple map-backed merge container.
 */
public final class DataComb {
    private final @NotNull Map<String, Object> values;

    public enum MergeMode {
        IGNORE,
        REPLACE,
        FILL_MISSING,
        APPEND,
        MERGE_MAPS,
        DEEP
    }

    public DataComb() {
        this.values = new LinkedHashMap<>();
    }

    public DataComb(@NotNull Map<String, Object> values) {
        Objects.requireNonNull(values, "values");
        this.values = new LinkedHashMap<>();
        values.forEach((key, value) -> this.values.put(key, copyMutableValue(value)));
    }

    public static @NotNull DataComb of(@NotNull Map<String, Object> values) {
        return new DataComb(values);
    }

    public @NotNull Map<String, Object> values() {
        return values;
    }

    public @Nullable Object get(@NotNull String key) {
        return values.get(key);
    }

    public @Nullable Object getProperty(@NotNull String key) {
        return get(key);
    }

    public @NotNull DataComb put(@NotNull String key, @Nullable Object value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            values.remove(key);
            return this;
        }
        values.put(key, copyMutableValue(value));
        return this;
    }

    public @NotNull DataComb setProperty(@NotNull String key, @Nullable Object value) {
        return put(key, value);
    }

    public @NotNull DataComb setProperties(@NotNull Map<String, Object> properties) {
        Objects.requireNonNull(properties, "properties");
        properties.forEach(this::setProperty);
        return this;
    }

    public boolean contains(@NotNull String key) {
        return values.containsKey(key);
    }

    public @NotNull DataComb mergeFrom(@NotNull DataComb other) {
        return mergeFrom(other, MergeMode.REPLACE);
    }

    public @NotNull DataComb mergeFrom(@NotNull DataComb other, @NotNull MergeMode mode) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(mode, "mode");

        for (Map.Entry<String, Object> entry : other.values.entrySet()) {
            mergeKey(entry.getKey(), entry.getValue(), mode);
        }
        return this;
    }

    public @NotNull DataComb mergeFrom(@NotNull DataComb other, @NotNull Map<String, MergeMode> overrides) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(overrides, "overrides");

        for (Map.Entry<String, Object> entry : other.values.entrySet()) {
            MergeMode mode = overrides.getOrDefault(entry.getKey(), MergeMode.REPLACE);
            mergeKey(entry.getKey(), entry.getValue(), mode);
        }
        return this;
    }

    private void mergeKey(@NotNull String key, @Nullable Object incomingValue, @NotNull MergeMode mode) {
        Object currentValue = values.get(key);
        Object merged = mergeValue(currentValue, incomingValue, mode);

        if (merged == null) {
            values.remove(key);
            return;
        }
        values.put(key, merged);
    }

    private static @Nullable Object mergeValue(@Nullable Object currentValue, @Nullable Object incomingValue, @NotNull MergeMode mode) {
        return switch (mode) {
            case IGNORE -> currentValue;
            case REPLACE -> incomingValue == null ? currentValue : copyMutableValue(incomingValue);
            case FILL_MISSING -> currentValue == null ? copyMutableValue(incomingValue) : currentValue;
            case APPEND -> appendValue(currentValue, incomingValue);
            case MERGE_MAPS -> mergeMaps(currentValue, incomingValue);
            case DEEP -> deepMerge(currentValue, incomingValue);
        };
    }

    private static @Nullable Object appendValue(@Nullable Object currentValue, @Nullable Object incomingValue) {
        if (incomingValue == null) {
            return currentValue;
        }
        if (currentValue == null) {
            return copyMutableValue(incomingValue);
        }
        if (currentValue instanceof List<?> currentList && incomingValue instanceof Collection<?> incomingCollection) {
            List<Object> merged = new ArrayList<>(currentList);
            merged.addAll(incomingCollection);
            return merged;
        }
        if (currentValue instanceof Set<?> currentSet && incomingValue instanceof Collection<?> incomingCollection) {
            Set<Object> merged = new LinkedHashSet<>(currentSet);
            merged.addAll(incomingCollection);
            return merged;
        }
        return copyMutableValue(incomingValue);
    }

    private static @Nullable Object mergeMaps(@Nullable Object currentValue, @Nullable Object incomingValue) {
        if (incomingValue == null) {
            return currentValue;
        }
        if (currentValue == null) {
            return copyMutableValue(incomingValue);
        }
        if (currentValue instanceof Map<?, ?> currentMap && incomingValue instanceof Map<?, ?> incomingMap) {
            Map<Object, Object> merged = new LinkedHashMap<>(currentMap);
            merged.putAll(incomingMap);
            return merged;
        }
        return copyMutableValue(incomingValue);
    }

    private static @Nullable Object deepMerge(@Nullable Object currentValue, @Nullable Object incomingValue) {
        if (incomingValue == null) {
            return currentValue;
        }
        if (currentValue == null) {
            return copyMutableValue(incomingValue);
        }
        if (currentValue instanceof DataComb currentComb && incomingValue instanceof DataComb incomingComb) {
            currentComb.mergeFrom(incomingComb);
            return currentComb;
        }
        if (currentValue instanceof Map<?, ?> || incomingValue instanceof Map<?, ?>) {
            return mergeMaps(currentValue, incomingValue);
        }
        if (currentValue instanceof Collection<?> || incomingValue instanceof Collection<?>) {
            return appendValue(currentValue, incomingValue);
        }
        return copyMutableValue(incomingValue);
    }

    private static @Nullable Object copyMutableValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof DataComb comb) {
            return new DataComb(comb.values);
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Set<?> set) {
            return new LinkedHashSet<>(set);
        }
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(map);
        }
        return value;
    }
}
