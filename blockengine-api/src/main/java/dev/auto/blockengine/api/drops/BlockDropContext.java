package dev.auto.blockengine.api.drops;

import dev.auto.blockengine.api.blocks.BlockContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.event.BlockEngineBlockRemovedEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Context for resolving drops on the server thread before a block is removed.
 *
 * <p>The player, identifiers, tool, and enchantments are captured at construction.
 * The block context and its data remain live for synchronous adapter callbacks.
 * Callbacks should return drops without spawning items or removing the block.</p>
 */
public final class BlockDropContext {
    private final @NotNull BlockContext block;
    private final @Nullable Player player;
    private final @NotNull String blockId;
    private final @NotNull String stateId;
    private final @NotNull ItemStack tool;
    private final @NotNull BlockDrop selfDrop;
    private final @NotNull BlockEngineBlockRemovedEvent.Reason reason;
    private final int fortuneLevel;
    private final boolean silkTouch;

    /**
     * Captures the inputs for one drop resolution.
     *
     * @param block context for the block before removal
     * @param tool tool used for removal, or an air stack for a tool-less removal
     * @param selfItem item representing this block; its amount is ignored
     * @param reason cause of the removal
     */
    public BlockDropContext(
            @NotNull BlockContext block,
            @NotNull ItemStack tool,
            @NotNull ItemStack selfItem,
            @NotNull BlockEngineBlockRemovedEvent.Reason reason
    ) {
        this.block = Objects.requireNonNull(block, "block");
        this.tool = Objects.requireNonNull(tool, "tool").clone();
        this.selfDrop = new BlockDrop(selfItem, 1);
        this.reason = Objects.requireNonNull(reason, "reason");

        this.player = block.player();
        this.blockId = block.blockId();
        this.stateId = block.stateId();
        this.silkTouch = this.tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        this.fortuneLevel = silkTouch ? 0 : Math.max(0, this.tool.getEnchantmentLevel(Enchantment.FORTUNE));
    }

    /** @return live context for the block before removal */
    public @NotNull BlockContext block() {
        return block;
    }

    /** @return player responsible for removal, or null for other causes */
    public @Nullable Player player() {
        return player;
    }

    /** @return full block id captured before drop resolution */
    public @NotNull String blockId() {
        return blockId;
    }

    /** @return state id captured before drop resolution */
    public @NotNull String stateId() {
        return stateId;
    }

    /** @return live placed-block data available during drop resolution */
    public @NotNull BlockData data() {
        return block.data();
    }

    /** @return independent copy of the tool captured for this removal */
    public @NotNull ItemStack tool() {
        return tool.clone();
    }

    /** @return one block item with the engine's normal self-drop metadata */
    public @NotNull BlockDrop selfDrop() {
        return selfDrop;
    }

    /**
     * Returns the effective Fortune level captured from the tool.
     *
     * @return nonnegative Fortune level, or zero when Silk Touch is present
     */
    public int fortuneLevel() {
        return fortuneLevel;
    }

    /** @return whether the captured tool has Silk Touch */
    public boolean silkTouch() {
        return silkTouch;
    }

    /** @return cause of the removal */
    public @NotNull BlockEngineBlockRemovedEvent.Reason reason() {
        return reason;
    }
}
