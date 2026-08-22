package dev.auto.blockengine.api.display;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Defines which players can see a managed display.
 *
 * <p>Audience filtering happens at packet visibility time. It does not create
 * separate persisted entities per player; it controls which viewers receive the
 * client-side entity. Persistent displays keep their audience rules when saved
 * and reloaded.</p>
 */
public final class DisplayAudience {
    private static final @NotNull DisplayAudience EVERYONE = new DisplayAudience(Mode.EVERYONE, Set.of());

    private final @NotNull Mode mode;
    private final @NotNull Set<UUID> players;

    private DisplayAudience(@NotNull Mode mode, @NotNull Set<UUID> players) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.players = Collections.unmodifiableSet(new LinkedHashSet<>(players));
    }

    /**
     * Makes a display visible to every player in normal visibility range.
     *
     * @return shared audience allowing all viewers
     */
    public static @NotNull DisplayAudience everyone() {
        return EVERYONE;
    }

    /**
     * Makes a display visible only to the given player.
     *
     * @param player player allowed to see the display
     * @return include-only audience for that player
     */
    public static @NotNull DisplayAudience only(@NotNull Player player) {
        return only(player.getUniqueId());
    }

    /**
     * Makes a display visible only to the given player id.
     *
     * @param playerId player UUID allowed to see the display
     * @return include-only audience for that player id
     */
    public static @NotNull DisplayAudience only(@NotNull UUID playerId) {
        return only(Set.of(playerId));
    }

    /**
     * Makes a display visible only to the given player ids.
     *
     * @param playerIds player UUIDs allowed to see the display
     * @return include-only audience for the supplied ids
     */
    public static @NotNull DisplayAudience only(@NotNull Set<UUID> playerIds) {
        return new DisplayAudience(Mode.INCLUDE, playerIds);
    }

    /**
     * Makes a display visible to everyone except the given player.
     *
     * @param player player excluded from seeing the display
     * @return exclude audience for that player
     */
    public static @NotNull DisplayAudience except(@NotNull Player player) {
        return except(player.getUniqueId());
    }

    /**
     * Makes a display visible to everyone except the given player id.
     *
     * @param playerId player UUID excluded from seeing the display
     * @return exclude audience for that player id
     */
    public static @NotNull DisplayAudience except(@NotNull UUID playerId) {
        return except(Set.of(playerId));
    }

    /**
     * Makes a display visible to everyone except the given player ids.
     *
     * @param playerIds player UUIDs excluded from seeing the display
     * @return exclude audience for the supplied ids
     */
    public static @NotNull DisplayAudience except(@NotNull Set<UUID> playerIds) {
        return new DisplayAudience(Mode.EXCLUDE, playerIds);
    }

    /**
     * Returns the audience mode used to interpret {@link #players()}.
     *
     * @return audience matching mode
     */
    public @NotNull Mode mode() {
        return mode;
    }

    /**
     * Returns the immutable player id set used by include/exclude modes.
     *
     * @return immutable player UUID set
     */
    public @NotNull Set<UUID> players() {
        return players;
    }

    /**
     * Tests whether the given player id is included by this audience.
     *
     * @param playerId viewer UUID
     * @return true when the player should receive the display packets
     */
    public boolean visibleTo(@NotNull UUID playerId) {
        return switch (mode) {
            case EVERYONE -> true;
            case INCLUDE -> players.contains(playerId);
            case EXCLUDE -> !players.contains(playerId);
        };
    }

    /**
     * Matching strategy for display audiences.
     */
    public enum Mode {
        /**
         * Every viewer in normal visibility range can see the display.
         */
        EVERYONE,

        /**
         * Only ids present in {@link DisplayAudience#players()} can see it.
         */
        INCLUDE,

        /**
         * Every viewer except ids present in {@link DisplayAudience#players()}
         * can see it.
         */
        EXCLUDE
    }
}
