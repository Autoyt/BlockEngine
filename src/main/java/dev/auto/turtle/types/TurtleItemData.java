package dev.auto.turtle.types;

import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class TurtleItemData {
    public static final class Type implements PersistentDataType<PersistentDataContainer, TurtleItemData> {
        public static final Type INSTANCE = new Type();

        @Override
        public @NotNull Class<PersistentDataContainer> getPrimitiveType() {
            return null;
        }

        @Override
        public @NotNull Class<TurtleItemData> getComplexType() {
            return null;
        }

        @Override
        public @NonNull PersistentDataContainer toPrimitive(@NonNull TurtleItemData complex, @NotNull PersistentDataAdapterContext context) {
            return null;
        }

        @Override
        public @NonNull TurtleItemData fromPrimitive(@NonNull PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            return null;
        }
    }
}
