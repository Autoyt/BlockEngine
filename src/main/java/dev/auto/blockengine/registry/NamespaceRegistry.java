package dev.auto.blockengine.registry;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class NamespaceRegistry {
    private static final Set<String> loaded = new HashSet<>();

    private NamespaceRegistry() {
    }

    public static void clear() {
        loaded.clear();
    }

    public static void load(@NotNull String namespace) {
        loaded.add(namespace);
    }

    public static boolean loaded(@NotNull String namespace) {
        return loaded.contains(namespace);
    }

    public static Set<String> loaded() {
        return Collections.unmodifiableSet(loaded);
    }
}
