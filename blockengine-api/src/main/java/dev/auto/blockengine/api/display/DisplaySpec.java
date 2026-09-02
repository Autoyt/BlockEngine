package dev.auto.blockengine.api.display;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable state used to render a managed item-display entity.
 *
 * <p>A display spec contains three groups of data:</p>
 *
 * <ul>
 *     <li>Anchor location: world id, position, yaw, and pitch.</li>
 *     <li>Appearance: rendered {@link ItemStack} plus optional item model key.</li>
 *     <li>Display metadata: transform, billboard, brightness, view range, glow,
 *     shadow, dimensions, interpolation, and audience.</li>
 * </ul>
 *
 * <p>Specs are immutable snapshots. To change a live display, build a new spec
 * with {@link #toBuilder()} and pass it to
 * {@link ManagedDisplayHandle#update(DisplaySpec)}, or use one of the handle's
 * convenience methods.</p>
 */
public final class DisplaySpec {
    /**
     * Billboard mode where the display does not rotate to face the camera.
     */
    public static final byte BILLBOARD_FIXED = 0;

    /**
     * Billboard mode where the display rotates vertically.
     */
    public static final byte BILLBOARD_VERTICAL = 1;

    /**
     * Billboard mode where the display rotates horizontally.
     */
    public static final byte BILLBOARD_HORIZONTAL = 2;

    /**
     * Billboard mode where the display rotates around its center to face the
     * camera.
     */
    public static final byte BILLBOARD_CENTER = 3;

    /**
     * Item display context commonly used for world-space fixed models.
     */
    public static final byte DISPLAY_CONTEXT_FIXED = 8;

    private final @NotNull UUID worldId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final @NotNull ItemStack itemStack;
    private final @Nullable NamespacedKey itemModel;
    private final byte displayContext;
    private final int brightness;
    private final float scaleX;
    private final float scaleY;
    private final float scaleZ;
    private final float translationX;
    private final float translationY;
    private final float translationZ;
    private final float viewRange;
    private final float leftRotationX;
    private final float leftRotationY;
    private final float leftRotationZ;
    private final float leftRotationW;
    private final float rightRotationX;
    private final float rightRotationY;
    private final float rightRotationZ;
    private final float rightRotationW;
    private final byte billboard;
    private final int transformationInterpolationDelay;
    private final int transformationInterpolationDuration;
    private final int posRotInterpolationDuration;
    private final float shadowRadius;
    private final float shadowStrength;
    private final float width;
    private final float height;
    private final boolean glowing;
    private final boolean invisible;
    private final int glowColorOverride;
    private final @NotNull DisplayAudience audience;

    private DisplaySpec(@NotNull Builder builder) {
        this.worldId = Objects.requireNonNull(builder.worldId, "worldId");
        this.x = builder.x;
        this.y = builder.y;
        this.z = builder.z;
        this.yaw = builder.yaw;
        this.pitch = builder.pitch;
        this.itemStack = builder.itemStack.clone();
        this.itemModel = builder.itemModel;
        this.displayContext = builder.displayContext;
        this.brightness = builder.brightness;
        this.scaleX = builder.scaleX;
        this.scaleY = builder.scaleY;
        this.scaleZ = builder.scaleZ;
        this.translationX = builder.translationX;
        this.translationY = builder.translationY;
        this.translationZ = builder.translationZ;
        this.viewRange = builder.viewRange;
        this.leftRotationX = builder.leftRotationX;
        this.leftRotationY = builder.leftRotationY;
        this.leftRotationZ = builder.leftRotationZ;
        this.leftRotationW = builder.leftRotationW;
        this.rightRotationX = builder.rightRotationX;
        this.rightRotationY = builder.rightRotationY;
        this.rightRotationZ = builder.rightRotationZ;
        this.rightRotationW = builder.rightRotationW;
        this.billboard = builder.billboard;
        this.transformationInterpolationDelay = builder.transformationInterpolationDelay;
        this.transformationInterpolationDuration = builder.transformationInterpolationDuration;
        this.posRotInterpolationDuration = builder.posRotInterpolationDuration;
        this.shadowRadius = builder.shadowRadius;
        this.shadowStrength = builder.shadowStrength;
        this.width = builder.width;
        this.height = builder.height;
        this.glowing = builder.glowing;
        this.invisible = builder.invisible;
        this.glowColorOverride = builder.glowColorOverride;
        this.audience = builder.audience;
    }

    /**
     * Creates a display spec builder from a Bukkit location.
     *
     * @param location location with a loaded world
     * @return builder initialized with the location
     * @throws IllegalArgumentException if the location has no world
     */
    public static @NotNull Builder builder(@NotNull Location location) {
        return new Builder(location);
    }

    /**
     * Creates a display spec builder from raw location fields.
     *
     * <p>This is useful when rebuilding specs from persisted data where a Bukkit
     * {@link org.bukkit.World} object may not be available yet.</p>
     *
     * @param worldId Bukkit world UUID
     * @param x world x coordinate
     * @param y world y coordinate
     * @param z world z coordinate
     * @param yaw entity yaw
     * @param pitch entity pitch
     * @return builder initialized with the raw location
     */
    public static @NotNull Builder builder(@NotNull UUID worldId, double x, double y, double z, float yaw, float pitch) {
        return new Builder(worldId, x, y, z, yaw, pitch);
    }

    /**
     * Creates a builder seeded with this spec's values.
     *
     * @return mutable builder copy
     */
    public @NotNull Builder toBuilder() {
        return new Builder(this);
    }

    /** @return Bukkit world UUID containing the display anchor */
    public @NotNull UUID worldId() { return worldId; }
    /** @return world x coordinate */
    public double x() { return x; }
    /** @return world y coordinate */
    public double y() { return y; }
    /** @return world z coordinate */
    public double z() { return z; }
    /** @return entity yaw */
    public float yaw() { return yaw; }
    /** @return entity pitch */
    public float pitch() { return pitch; }
    /** @return clone of the rendered item stack */
    public @NotNull ItemStack itemStack() { return itemStack.clone(); }
    /** @return item model override key, or null to use the stack's default model */
    public @Nullable NamespacedKey itemModel() { return itemModel; }
    /** @return item display context metadata value */
    public byte displayContext() { return displayContext; }
    /** @return packed brightness override, or -1 for vanilla lighting */
    public int brightness() { return brightness; }
    /** @return display transform x scale */
    public float scaleX() { return scaleX; }
    /** @return display transform y scale */
    public float scaleY() { return scaleY; }
    /** @return display transform z scale */
    public float scaleZ() { return scaleZ; }
    /** @return display transform x translation */
    public float translationX() { return translationX; }
    /** @return display transform y translation */
    public float translationY() { return translationY; }
    /** @return display transform z translation */
    public float translationZ() { return translationZ; }
    /** @return client render view range */
    public float viewRange() { return viewRange; }
    /** @return left quaternion x component */
    public float leftRotationX() { return leftRotationX; }
    /** @return left quaternion y component */
    public float leftRotationY() { return leftRotationY; }
    /** @return left quaternion z component */
    public float leftRotationZ() { return leftRotationZ; }
    /** @return left quaternion w component */
    public float leftRotationW() { return leftRotationW; }
    /** @return right quaternion x component */
    public float rightRotationX() { return rightRotationX; }
    /** @return right quaternion y component */
    public float rightRotationY() { return rightRotationY; }
    /** @return right quaternion z component */
    public float rightRotationZ() { return rightRotationZ; }
    /** @return right quaternion w component */
    public float rightRotationW() { return rightRotationW; }
    /** @return billboard metadata value */
    public byte billboard() { return billboard; }
    /** @return transformation interpolation delay in ticks */
    public int transformationInterpolationDelay() { return transformationInterpolationDelay; }
    /** @return transformation interpolation duration in ticks */
    public int transformationInterpolationDuration() { return transformationInterpolationDuration; }
    /** @return position/rotation interpolation duration in ticks */
    public int posRotInterpolationDuration() { return posRotInterpolationDuration; }
    /** @return shadow radius */
    public float shadowRadius() { return shadowRadius; }
    /** @return shadow strength */
    public float shadowStrength() { return shadowStrength; }
    /** @return display width metadata value */
    public float width() { return width; }
    /** @return display height metadata value */
    public float height() { return height; }
    /** @return whether the display has the glowing entity flag */
    public boolean glowing() { return glowing; }
    /** @return whether the display has the invisible entity flag */
    public boolean invisible() { return invisible; }
    /** @return glow color override, or -1 for default team/entity color */
    public int glowColorOverride() { return glowColorOverride; }
    /** @return audience rule controlling which players receive this display */
    public @NotNull DisplayAudience audience() { return audience; }
    /** @return appearance object containing the item stack and model key */
    public @NotNull DisplayAppearance appearance() {
        return itemModel == null ? DisplayAppearance.item(itemStack) : DisplayAppearance.model(itemStack, itemModel);
    }

    /**
     * Mutable builder for {@link DisplaySpec}.
     */
    public static final class Builder {
        private @Nullable UUID worldId;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        private @NotNull ItemStack itemStack = new ItemStack(org.bukkit.Material.AIR);
        private @Nullable NamespacedKey itemModel;
        private byte displayContext = DISPLAY_CONTEXT_FIXED;
        private int brightness = -1;
        private float scaleX = 2.0f;
        private float scaleY = 2.0f;
        private float scaleZ = 2.0f;
        private float translationX;
        private float translationY;
        private float translationZ;
        private float viewRange = 1.0f;
        private float leftRotationX;
        private float leftRotationY;
        private float leftRotationZ;
        private float leftRotationW = 1.0f;
        private float rightRotationX;
        private float rightRotationY;
        private float rightRotationZ;
        private float rightRotationW = 1.0f;
        private byte billboard = BILLBOARD_FIXED;
        private int transformationInterpolationDelay;
        private int transformationInterpolationDuration;
        private int posRotInterpolationDuration;
        private float shadowRadius = 1.0f;
        private float shadowStrength = 1.0f;
        private float width = 1.0f;
        private float height = 1.0f;
        private boolean glowing;
        private boolean invisible;
        private int glowColorOverride = -1;
        private @NotNull DisplayAudience audience = DisplayAudience.everyone();

        private Builder(@NotNull Location location) {
            location(location);
        }

        private Builder(@NotNull UUID worldId, double x, double y, double z, float yaw, float pitch) {
            location(worldId, x, y, z, yaw, pitch);
        }

        private Builder(@NotNull DisplaySpec source) {
            worldId = source.worldId;
            x = source.x;
            y = source.y;
            z = source.z;
            yaw = source.yaw;
            pitch = source.pitch;
            itemStack = source.itemStack.clone();
            itemModel = source.itemModel;
            displayContext = source.displayContext;
            brightness = source.brightness;
            scaleX = source.scaleX;
            scaleY = source.scaleY;
            scaleZ = source.scaleZ;
            translationX = source.translationX;
            translationY = source.translationY;
            translationZ = source.translationZ;
            viewRange = source.viewRange;
            leftRotationX = source.leftRotationX;
            leftRotationY = source.leftRotationY;
            leftRotationZ = source.leftRotationZ;
            leftRotationW = source.leftRotationW;
            rightRotationX = source.rightRotationX;
            rightRotationY = source.rightRotationY;
            rightRotationZ = source.rightRotationZ;
            rightRotationW = source.rightRotationW;
            billboard = source.billboard;
            transformationInterpolationDelay = source.transformationInterpolationDelay;
            transformationInterpolationDuration = source.transformationInterpolationDuration;
            posRotInterpolationDuration = source.posRotInterpolationDuration;
            shadowRadius = source.shadowRadius;
            shadowStrength = source.shadowStrength;
            width = source.width;
            height = source.height;
            glowing = source.glowing;
            invisible = source.invisible;
            glowColorOverride = source.glowColorOverride;
            audience = source.audience;
        }

        /**
         * Replaces the display anchor from a Bukkit location.
         *
         * @param location location with a loaded world
         * @return this builder
         * @throws IllegalArgumentException if the location has no world
         */
        public @NotNull Builder location(@NotNull Location location) {
            if (location.getWorld() == null) {
                throw new IllegalArgumentException("Display location must have a world.");
            }
            worldId = location.getWorld().getUID();
            x = location.getX();
            y = location.getY();
            z = location.getZ();
            yaw = location.getYaw();
            pitch = location.getPitch();
            return this;
        }

        /**
         * Replaces the display anchor from raw location fields.
         *
         * @param worldId Bukkit world UUID
         * @param x world x coordinate
         * @param y world y coordinate
         * @param z world z coordinate
         * @param yaw entity yaw
         * @param pitch entity pitch
         * @return this builder
         */
        public @NotNull Builder location(@NotNull UUID worldId, double x, double y, double z, float yaw, float pitch) {
            this.worldId = Objects.requireNonNull(worldId, "worldId");
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            return this;
        }

        /**
         * Sets the item stack rendered by the display.
         *
         * @param itemStack rendered stack, or air when null
         * @return this builder
         */
        public @NotNull Builder itemStack(@Nullable ItemStack itemStack) {
            this.itemStack = itemStack == null ? new ItemStack(org.bukkit.Material.AIR) : itemStack.clone();
            return this;
        }

        /**
         * Sets the item model override for the rendered stack.
         *
         * @param itemModel model key, or null to use the stack's default model
         * @return this builder
         */
        public @NotNull Builder itemModel(@Nullable NamespacedKey itemModel) {
            this.itemModel = itemModel;
            return this;
        }

        /**
         * Sets the item model override from a generated model.
         *
         * @param itemModel generated model, or null to clear the override
         * @return this builder
         */
        public @NotNull Builder itemModel(@Nullable dev.auto.blockengine.api.resourcepack.GeneratedItemModel itemModel) {
            this.itemModel = itemModel == null ? null : itemModel.key();
            return this;
        }

        /**
         * Sets both the rendered item stack and model override.
         *
         * @param appearance item appearance snapshot
         * @return this builder
         */
        public @NotNull Builder appearance(@NotNull DisplayAppearance appearance) {
            this.itemStack = appearance.itemStack();
            this.itemModel = appearance.itemModel();
            return this;
        }

        /**
         * Sets the item display context metadata value.
         *
         * @param displayContext display context byte
         * @return this builder
         */
        public @NotNull Builder displayContext(byte displayContext) { this.displayContext = displayContext; return this; }
        /**
         * Sets a packed brightness override.
         *
         * <p>Use {@code -1} for vanilla lighting. Packed brightness follows the
         * Minecraft display metadata format: block light in bits 4-7 and sky
         * light in bits 20-23.</p>
         *
         * @param brightness packed brightness value, or -1
         * @return this builder
         */
        public @NotNull Builder brightness(int brightness) { this.brightness = brightness; return this; }
        /**
         * Sets the display transform scale.
         *
         * @param x x scale
         * @param y y scale
         * @param z z scale
         * @return this builder
         */
        public @NotNull Builder scale(float x, float y, float z) { scaleX = x; scaleY = y; scaleZ = z; return this; }
        /**
         * Sets the display transform translation.
         *
         * @param x x translation
         * @param y y translation
         * @param z z translation
         * @return this builder
         */
        public @NotNull Builder translation(float x, float y, float z) { translationX = x; translationY = y; translationZ = z; return this; }
        /**
         * Sets the client render view range.
         *
         * @param viewRange view range in blocks
         * @return this builder
         */
        public @NotNull Builder viewRange(float viewRange) { this.viewRange = viewRange; return this; }
        /**
         * Sets the left transform quaternion.
         *
         * @param x quaternion x component
         * @param y quaternion y component
         * @param z quaternion z component
         * @param w quaternion w component
         * @return this builder
         */
        public @NotNull Builder leftRotation(float x, float y, float z, float w) { leftRotationX = x; leftRotationY = y; leftRotationZ = z; leftRotationW = w; return this; }
        /**
         * Sets the right transform quaternion.
         *
         * @param x quaternion x component
         * @param y quaternion y component
         * @param z quaternion z component
         * @param w quaternion w component
         * @return this builder
         */
        public @NotNull Builder rightRotation(float x, float y, float z, float w) { rightRotationX = x; rightRotationY = y; rightRotationZ = z; rightRotationW = w; return this; }
        /**
         * Sets the billboard metadata mode.
         *
         * @param billboard one of the {@code BILLBOARD_*} constants
         * @return this builder
         */
        public @NotNull Builder billboard(byte billboard) { this.billboard = billboard; return this; }
        /**
         * Sets the number of ticks before transform interpolation starts.
         *
         * @param ticks interpolation delay in ticks
         * @return this builder
         */
        public @NotNull Builder transformationInterpolationDelay(int ticks) { transformationInterpolationDelay = ticks; return this; }
        /**
         * Sets the number of ticks used for transform interpolation.
         *
         * @param ticks interpolation duration in ticks
         * @return this builder
         */
        public @NotNull Builder transformationInterpolationDuration(int ticks) { transformationInterpolationDuration = ticks; return this; }
        /**
         * Sets the number of ticks used for position and rotation interpolation.
         *
         * @param ticks interpolation duration in ticks
         * @return this builder
         */
        public @NotNull Builder posRotInterpolationDuration(int ticks) { posRotInterpolationDuration = ticks; return this; }
        /**
         * Sets the display shadow radius metadata value.
         *
         * @param shadowRadius shadow radius
         * @return this builder
         */
        public @NotNull Builder shadowRadius(float shadowRadius) { this.shadowRadius = shadowRadius; return this; }
        /**
         * Sets the display shadow strength metadata value.
         *
         * @param shadowStrength shadow strength
         * @return this builder
         */
        public @NotNull Builder shadowStrength(float shadowStrength) { this.shadowStrength = shadowStrength; return this; }
        /**
         * Sets the display bounding dimensions.
         *
         * @param width display width
         * @param height display height
         * @return this builder
         */
        public @NotNull Builder dimensions(float width, float height) { this.width = width; this.height = height; return this; }
        /**
         * Sets whether the glowing entity flag is enabled.
         *
         * @param glowing true to render the display as glowing
         * @return this builder
         */
        public @NotNull Builder glowing(boolean glowing) { this.glowing = glowing; return this; }
        /**
         * Sets whether the invisible entity flag is enabled.
         *
         * <p>Item display contents can still render when this flag is set,
         * matching vanilla display-entity behavior.</p>
         *
         * @param invisible true to enable the invisible flag
         * @return this builder
         */
        public @NotNull Builder invisible(boolean invisible) { this.invisible = invisible; return this; }
        /**
         * Sets the display glow color override.
         *
         * @param glowColorOverride RGB color value, or -1 for the default
         * @return this builder
         */
        public @NotNull Builder glowColorOverride(int glowColorOverride) { this.glowColorOverride = glowColorOverride; return this; }
        /**
         * Sets which players can see the display.
         *
         * @param audience audience rule
         * @return this builder
         */
        public @NotNull Builder audience(@NotNull DisplayAudience audience) {
            this.audience = Objects.requireNonNull(audience, "audience");
            return this;
        }

        /**
         * Builds an immutable display specification.
         *
         * @return immutable display spec
         */
        public @NotNull DisplaySpec build() {
            return new DisplaySpec(this);
        }
    }
}
