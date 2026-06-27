package dev.auto.turtle.api.blocks;

public interface BlockAdapter {
    float getBlockHardness();

    default int getBlockLightLevel() {
        return 0;
    };

    /** @return true if the block can be wiped away by a fluid */
    default boolean washable() {
        return false;
    };

    /** @return true if the block can be broken */
    default boolean breakable() {
        return true;
    };

    default void onBreak() {}

}
