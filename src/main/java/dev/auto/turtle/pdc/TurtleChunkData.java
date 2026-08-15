package dev.auto.turtle.pdc;

import dev.auto.turtle.types.BlockLocationKey;
import dev.auto.turtle.types.TurtleBlockInstance;
import org.bukkit.entity.Turtle;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

public class TurtleChunkData {
    public static final int version = 1;
    private Map<BlockLocationKey, TurtleBlockInstance> blocks = new HashMap<>();


    class TurtleChunkDataType implements PersistentDataType<byte[], TurtleChunkData> {
        @Override
        public @NotNull Class<byte[]> getPrimitiveType() {
            return byte[].class;
        }

        @Override
        public @NotNull Class<TurtleChunkData> getComplexType() {
            return TurtleChunkData.class;
        }

        @Override
        public @NonNull byte[] toPrimitive(@NonNull TurtleChunkData turtleChunkData, @NotNull PersistentDataAdapterContext persistentDataAdapterContext) {
            return new byte[0];
        }

        @Override
        public @NonNull TurtleChunkData fromPrimitive(@NonNull byte[] bytes, @NotNull PersistentDataAdapterContext persistentDataAdapterContext) {
            return null;
        }
    }
}

