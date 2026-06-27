package dev.auto.turtle.types;

import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public class TurtleChunkData {
    public static final class Type implements PersistentDataType<PersistentDataContainer, TurtleChunkData> {
        public static final Type INSTANCE = new Type();

        @Override
        public @NotNull Class<PersistentDataContainer> getPrimitiveType() {
            return null;
        }

        @Override
        public @NotNull Class<TurtleChunkData> getComplexType() {
            return null;
        }

        @Override
        public @NonNull PersistentDataContainer toPrimitive(@NonNull TurtleChunkData complex, @NotNull PersistentDataAdapterContext context) {
            return null;
        }

        @Override
        public @NonNull TurtleChunkData fromPrimitive(@NonNull PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            return null;
        }
    }

    public Map<BlockLocationKey, TurtleItemData> blocks;

    public TurtleChunkData() {
    }
}
