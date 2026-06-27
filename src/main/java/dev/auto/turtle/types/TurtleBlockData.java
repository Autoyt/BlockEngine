package dev.auto.turtle.types;

import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class TurtleBlockData {
    public static final class Type implements PersistentDataType<PersistentDataContainer, TurtleBlockData> {
        public static final Type INSTANCE = new Type();
        @Override
        public @NotNull Class<PersistentDataContainer> getPrimitiveType() {
            return PersistentDataContainer.class;
        }

        @Override
        public @NotNull Class<TurtleBlockData> getComplexType() {
            return TurtleBlockData.class;
        }

        @Override
        public @NonNull PersistentDataContainer toPrimitive(@NonNull TurtleBlockData complex, @NotNull PersistentDataAdapterContext context) {
            return null;
        }

        @Override
        public @NonNull TurtleBlockData fromPrimitive(@NonNull PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            return null;
        }
    }

    public final @NotNull BlockLocationKey id;
    public final @NotNull TurtleItemData item;

    public TurtleBlockData(@NotNull BlockLocationKey id, @NotNull TurtleItemData item) {
        this.id = id;
        this.item = item;
    }
}
