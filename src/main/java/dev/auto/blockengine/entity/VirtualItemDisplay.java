package dev.auto.blockengine.entity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class VirtualItemDisplay {
    public static final byte BILLBOARD_FIXED = 0;
    public static final byte BILLBOARD_VERTICAL = 1;
    public static final byte BILLBOARD_HORIZONTAL = 2;
    public static final byte BILLBOARD_CENTER = 3;

    public static final byte DISPLAY_CONTEXT_NONE = 0;
    public static final byte DISPLAY_CONTEXT_THIRD_PERSON_LEFT_HAND = 1;
    public static final byte DISPLAY_CONTEXT_THIRD_PERSON_RIGHT_HAND = 2;
    public static final byte DISPLAY_CONTEXT_FIRST_PERSON_LEFT_HAND = 3;
    public static final byte DISPLAY_CONTEXT_FIRST_PERSON_RIGHT_HAND = 4;
    public static final byte DISPLAY_CONTEXT_HEAD = 5;
    public static final byte DISPLAY_CONTEXT_GUI = 6;
    public static final byte DISPLAY_CONTEXT_GROUND = 7;
    public static final byte DISPLAY_CONTEXT_FIXED = 8;

    private static final int ENTITY_FLAGS_INDEX = 0;
    private static final byte ENTITY_FLAG_INVISIBLE = 0x20;
    private static final byte ENTITY_FLAG_GLOWING = 0x40;

    private static final int DISPLAY_TRANSFORMATION_INTERPOLATION_DELAY_INDEX = 8;
    private static final int DISPLAY_TRANSFORMATION_INTERPOLATION_DURATION_INDEX = 9;
    private static final int DISPLAY_POS_ROT_INTERPOLATION_DURATION_INDEX = 10;
    private static final int DISPLAY_TRANSLATION_INDEX = 11;
    private static final int DISPLAY_SCALE_INDEX = 12;
    private static final int DISPLAY_LEFT_ROTATION_INDEX = 13;
    private static final int DISPLAY_RIGHT_ROTATION_INDEX = 14;
    private static final int DISPLAY_BILLBOARD_INDEX = 15;
    private static final int DISPLAY_BRIGHTNESS_INDEX = 16;
    private static final int DISPLAY_VIEW_RANGE_INDEX = 17;
    private static final int DISPLAY_SHADOW_RADIUS_INDEX = 18;
    private static final int DISPLAY_SHADOW_STRENGTH_INDEX = 19;
    private static final int DISPLAY_WIDTH_INDEX = 20;
    private static final int DISPLAY_HEIGHT_INDEX = 21;
    private static final int DISPLAY_GLOW_COLOR_OVERRIDE_INDEX = 22;
    private static final int ITEM_STACK_INDEX = 23;
    private static final int ITEM_DISPLAY_CONTEXT_INDEX = 24;

    private final int id;
    private final UUID uuid;

    private Location location;
    private ItemStack itemStack = new ItemStack(Material.AIR);
    private byte displayContext = DISPLAY_CONTEXT_FIXED;
    private int brightness = -1;
    private float scaleX = 2.0f;
    private float scaleY = 2.0f;
    private float scaleZ = 2.0f;
    private float translationX;
    private float translationY;
    private float translationZ;
    private float viewRange = 1.0f;
    private Quaternion4f leftRotation = new Quaternion4f(0.0f, 0.0f, 0.0f, 1.0f);
    private Quaternion4f rightRotation = new Quaternion4f(0.0f, 0.0f, 0.0f, 1.0f);
    private byte billboard = BILLBOARD_FIXED;
    private int transformationInterpolationDelay;
    private int transformationInterpolationDuration;
    private int posRotInterpolationDuration;
    private float shadowRadius;
    private float shadowStrength = 1.0f;
    private float width = 1.0f;
    private float height = 1.0f;
    private boolean glowing;
    private boolean invisible;
    private int glowColorOverride = -1;

    public VirtualItemDisplay(int id) {
        this(id, UUID.randomUUID());
    }

    public VirtualItemDisplay(int id, UUID uuid) {
        this.id = id;
        this.uuid = uuid;
        this.shadowRadius = 1.0f;
    }

    public int getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Location getLocation() {
        return location;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public byte getDisplayContext() {
        return displayContext;
    }

    public int getBrightness() {
        return brightness;
    }

    public float getScaleX() {
        return scaleX;
    }

    public float getScaleY() {
        return scaleY;
    }

    public float getScaleZ() {
        return scaleZ;
    }

    public float getTranslationX() {
        return translationX;
    }

    public float getTranslationY() {
        return translationY;
    }

    public float getTranslationZ() {
        return translationZ;
    }

    public float getViewRange() {
        return viewRange;
    }

    public Quaternion4f getLeftRotation() {
        return leftRotation;
    }

    public Quaternion4f getRightRotation() {
        return rightRotation;
    }

    public byte getBillboard() {
        return billboard;
    }

    public int getTransformationInterpolationDelay() {
        return transformationInterpolationDelay;
    }

    public int getTransformationInterpolationDuration() {
        return transformationInterpolationDuration;
    }

    public int getPosRotInterpolationDuration() {
        return posRotInterpolationDuration;
    }

    public float getShadowRadius() {
        return shadowRadius;
    }

    public float getShadowStrength() {
        return shadowStrength;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public boolean isInvisible() {
        return invisible;
    }

    public int getGlowColorOverride() {
        return glowColorOverride;
    }

    public VirtualItemDisplay location(Location location) {
        this.location = location == null ? null : location.clone();
        return this;
    }

    public VirtualItemDisplay itemStack(ItemStack itemStack) {
        this.itemStack = itemStack == null ? new ItemStack(Material.AIR) : itemStack.clone();
        return this;
    }

    public VirtualItemDisplay displayContext(byte displayContext) {
        this.displayContext = displayContext;
        return this;
    }

    public VirtualItemDisplay brightness(int brightness) {
        this.brightness = brightness;
        return this;
    }

    public VirtualItemDisplay scale(float x, float y, float z) {
        this.scaleX = x;
        this.scaleY = y;
        this.scaleZ = z;
        return this;
    }

    public VirtualItemDisplay translation(float x, float y, float z) {
        this.translationX = x;
        this.translationY = y;
        this.translationZ = z;
        return this;
    }

    public VirtualItemDisplay viewRange(float viewRange) {
        this.viewRange = viewRange;
        return this;
    }

    public VirtualItemDisplay leftRotation(float x, float y, float z, float w) {
        this.leftRotation = new Quaternion4f(x, y, z, w);
        return this;
    }

    public VirtualItemDisplay leftRotation(Quaternion4f leftRotation) {
        this.leftRotation = leftRotation;
        return this;
    }

    public VirtualItemDisplay rightRotation(float x, float y, float z, float w) {
        this.rightRotation = new Quaternion4f(x, y, z, w);
        return this;
    }

    public VirtualItemDisplay rightRotation(Quaternion4f rightRotation) {
        this.rightRotation = rightRotation;
        return this;
    }

    public VirtualItemDisplay billboard(byte billboard) {
        this.billboard = billboard;
        return this;
    }

    public VirtualItemDisplay transformationInterpolationDelay(int ticks) {
        this.transformationInterpolationDelay = ticks;
        return this;
    }

    public VirtualItemDisplay transformationInterpolationDuration(int ticks) {
        this.transformationInterpolationDuration = ticks;
        return this;
    }

    public VirtualItemDisplay posRotInterpolationDuration(int ticks) {
        this.posRotInterpolationDuration = ticks;
        return this;
    }

    public VirtualItemDisplay shadowRadius(float shadowRadius) {
        this.shadowRadius = shadowRadius;
        return this;
    }

    public VirtualItemDisplay shadowStrength(float shadowStrength) {
        this.shadowStrength = shadowStrength;
        return this;
    }

    public VirtualItemDisplay dimensions(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public VirtualItemDisplay glowing(boolean glowing) {
        this.glowing = glowing;
        return this;
    }

    public VirtualItemDisplay invisible(boolean invisible) {
        this.invisible = invisible;
        return this;
    }

    public VirtualItemDisplay glowColorOverride(int glowColorOverride) {
        this.glowColorOverride = glowColorOverride;
        return this;
    }

    public VirtualItemDisplay brightness(int blockLight, int skyLight) {
        int clampedBlock = Math.clamp(blockLight, 0, 15);
        int clampedSky = Math.clamp(skyLight, 0, 15);
        this.brightness = packBrightness(clampedBlock, clampedSky);
        return this;
    }

    public void spawn(Player player) {
        if (location == null) {
            throw new IllegalStateException("Location must be set before spawning a virtual item display.");
        }

        var spawnPacket = new WrapperPlayServerSpawnEntity(
                id,
                uuid,
                EntityTypes.ITEM_DISPLAY,
                SpigotConversionUtil.fromBukkitLocation(location),
                0.0f,
                0,
                new Vector3d(0, 0, 0)
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawnPacket);
        updateMetadata(player);
    }

    public void spawnForAll() {
        spawnFor(Bukkit.getOnlinePlayers());
    }

    public void spawnFor(Collection<? extends Player> players) {
        for (Player player : players) {
            spawn(player);
        }
    }

    public void updateMetadata(Player player) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, buildEntityFlags()));
        metadata.add(new EntityData<>(DISPLAY_TRANSFORMATION_INTERPOLATION_DELAY_INDEX, EntityDataTypes.INT, transformationInterpolationDelay));
        metadata.add(new EntityData<>(DISPLAY_TRANSFORMATION_INTERPOLATION_DURATION_INDEX, EntityDataTypes.INT, transformationInterpolationDuration));
        metadata.add(new EntityData<>(DISPLAY_POS_ROT_INTERPOLATION_DURATION_INDEX, EntityDataTypes.INT, posRotInterpolationDuration));
        metadata.add(new EntityData<>(DISPLAY_TRANSLATION_INDEX, EntityDataTypes.VECTOR3F, new Vector3f(translationX, translationY, translationZ)));
        metadata.add(new EntityData<>(DISPLAY_SCALE_INDEX, EntityDataTypes.VECTOR3F, new Vector3f(scaleX, scaleY, scaleZ)));
        metadata.add(new EntityData<>(DISPLAY_LEFT_ROTATION_INDEX, EntityDataTypes.QUATERNION, leftRotation));
        metadata.add(new EntityData<>(DISPLAY_RIGHT_ROTATION_INDEX, EntityDataTypes.QUATERNION, rightRotation));
        metadata.add(new EntityData<>(DISPLAY_BILLBOARD_INDEX, EntityDataTypes.BYTE, billboard));
        metadata.add(new EntityData<>(DISPLAY_BRIGHTNESS_INDEX, EntityDataTypes.INT, brightness));
        metadata.add(new EntityData<>(DISPLAY_VIEW_RANGE_INDEX, EntityDataTypes.FLOAT, viewRange));
        metadata.add(new EntityData<>(DISPLAY_SHADOW_RADIUS_INDEX, EntityDataTypes.FLOAT, shadowRadius));
        metadata.add(new EntityData<>(DISPLAY_SHADOW_STRENGTH_INDEX, EntityDataTypes.FLOAT, shadowStrength));
        metadata.add(new EntityData<>(DISPLAY_WIDTH_INDEX, EntityDataTypes.FLOAT, width));
        metadata.add(new EntityData<>(DISPLAY_HEIGHT_INDEX, EntityDataTypes.FLOAT, height));
        metadata.add(new EntityData<>(DISPLAY_GLOW_COLOR_OVERRIDE_INDEX, EntityDataTypes.INT, glowColorOverride));
        metadata.add(new EntityData<>(ITEM_STACK_INDEX, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(itemStack)));
        metadata.add(new EntityData<>(ITEM_DISPLAY_CONTEXT_INDEX, EntityDataTypes.BYTE, displayContext));

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityMetadata(id, metadata));
    }

    public void updateMetadataForAll() {
        updateMetadataFor(Bukkit.getOnlinePlayers());
    }

    public void updateMetadataFor(Collection<? extends Player> players) {
        for (Player player : players) {
            updateMetadata(player);
        }
    }

    public void teleport(Player player, double x, double y, double z, float yaw, float pitch) {
        if (location == null) {
            throw new IllegalStateException("Location must be set before teleporting a virtual item display.");
        }

        location.setX(x);
        location.setY(y);
        location.setZ(z);
        location.setYaw(yaw);
        location.setPitch(pitch);

        var teleportPacket = new WrapperPlayServerEntityTeleport(
                id,
                new Vector3d(x, y, z),
                Vector3d.zero(),
                yaw,
                pitch,
                RelativeFlag.NONE,
                false
        );

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, teleportPacket);
    }

    public void teleportForAll(double x, double y, double z, float yaw, float pitch) {
        teleportFor(Bukkit.getOnlinePlayers(), x, y, z, yaw, pitch);
    }

    public void teleportFor(Collection<? extends Player> players, double x, double y, double z, float yaw, float pitch) {
        for (Player player : players) {
            teleport(player, x, y, z, yaw, pitch);
        }
    }

    public void teleport(Player player, Location location) {
        teleport(player, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public void teleportForAll(Location location) {
        teleportFor(Bukkit.getOnlinePlayers(), location);
    }

    public void teleportFor(Collection<? extends Player> players, Location location) {
        teleportFor(players, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public void destroy(Player player) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerDestroyEntities(id));
    }

    public void destroyForAll() {
        destroyFor(Bukkit.getOnlinePlayers());
    }

    public void destroyFor(Collection<? extends Player> players) {
        for (Player player : players) {
            destroy(player);
        }
    }

    private byte buildEntityFlags() {
        byte flags = 0;
        if (invisible) {
            flags |= ENTITY_FLAG_INVISIBLE;
        }
        if (glowing) {
            flags |= ENTITY_FLAG_GLOWING;
        }
        return flags;
    }

    private static int packBrightness(int blockLight, int skyLight) {
        return (blockLight << 4) | (skyLight << 20);
    }
}
