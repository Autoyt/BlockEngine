package audit;

import dev.auto.blockengine.api.CustomBlockSystem;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import dev.auto.blockengine.api.display.DisplayPersistence;
import dev.auto.blockengine.api.display.DisplaySpec;
import dev.auto.blockengine.api.event.BlockEngineBlockPlaceEvent;
import dev.auto.blockengine.api.event.BlockEngineChunkSavedEvent;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.placement.BlockOrientation;
import dev.auto.blockengine.runtime.BlockDataManager;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.runtime.GravityManager;
import dev.auto.blockengine.runtime.RuntimeBlockView;
import dev.auto.blockengine.structure.SudoBlockManager;
import dev.auto.blockengine.types.BlockLocationKey;
import dev.auto.blockengine.world.ManagedWorld;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** Installed only in the temporary world created by run.py. */
public final class PersistenceProbe extends JavaPlugin implements Listener, CustomBlockSystem {
    private static final String DEMO = "blockengine_test:demo_state";
    private static final int Y = 75;
    private static final int Z = 49;

    private World world;
    private ManagedWorld managed;
    private int ticks;
    private boolean editDuringSave;
    private boolean vetoPlacement;
    private boolean protectExplosion;

    @Override
    public String pluginVersion() { return "1"; }

    @Override
    public String getNamespace() { return "audit"; }

    @Override
    public List<BlockAdapter> registerAdapters() {
        return List.of(new BlockAdapter() {
            public String name() { return "ticker"; }
            public void define(BlockDefinition.Builder builder) {
                builder.state("default", state -> state.textures(texture -> texture.all("minecraft:block/stone")));
            }
            public BlockData createDefaultData(BlockCreateContext context) { return context.createData(); }
            public boolean ticking() { return true; }
            public void onTick(dev.auto.blockengine.api.blocks.BlockContext context) { ticks++; }
        });
    }

    @Override
    public void onEnable() {
        world = Bukkit.getWorlds().getFirst();
        managed = new ManagedWorld(world);
        boolean restarting = getConfig().getBoolean("restart");
        // A server with no connected players can unload these chunks before the assertions run.
        // Hold them across discovery and release them with this plugin at shutdown.
        world.addPluginChunkTicket(3, 3, this);
        world.addPluginChunkTicket(4, 3, this);
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                if (restarting) {
                    verifyRestart();
                    getLogger().info("PERSISTENCE PHASE 2 PASS");
                } else {
                    verifyWrites();
                    getConfig().set("restart", true);
                    saveConfig();
                    getLogger().info("PERSISTENCE PHASE 1 PASS");
                }
            } catch (Throwable failure) {
                getLogger().log(Level.SEVERE, "Persistence probe failed", failure);
            } finally {
                Bukkit.shutdown();
            }
        }, 20);
    }

    @EventHandler
    public void onSaved(BlockEngineChunkSavedEvent event) {
        if (editDuringSave && event.chunk().getX() == 3 && event.chunk().getZ() == 3) {
            editDuringSave = false;
            place(50, DEMO, "green");
            place(65, DEMO, "purple");
        }
    }

    @EventHandler
    public void onPlacement(BlockEngineBlockPlaceEvent event) {
        if (vetoPlacement && event.block().equals(block(53))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosion(BlockExplodeEvent event) {
        if (protectExplosion) {
            event.blockList().remove(block(104));
        }
    }

    private void verifyWrites() {
        place(49, "audit:ticker", null);
        editDuringSave = true;
        ChunkEngine.flushNow();
        ChunkEngine.flushNow();
        require(!editDuringSave && hasState(50, "green") && hasState(65, "purple"), "callback writes in two chunks");

        var displays = ManagedDisplayManager.getInstance();
        var first = displays.createBlockAttached(block(50), "slot", spec(50));
        var attached = displays.createBlockAttached(block(50), "slot", spec(50));
        require(!first.valid(), "replaced display handle");
        var context = BlockDataManager.getInstance().context(block(50), view(50), null);
        context.setState("purple");
        require(BlockDataManager.getInstance().save(block(50), context), "saved block state");

        var moving = displays.create(spec(51), DisplayPersistence.PERSISTENT_WORLD);
        ChunkEngine.flushNow();
        require(attached.valid() && moving.valid(), "live handles after chunk save");
        require(view(50).storedBlock().displays().size() == 1, "attached display after state update");
        require(moving.update(spec(67)), "cross-chunk display move");
        ChunkEngine.flushNow();
        var oldChunk = ChunkEngine.Data.load(block(51).getChunk(), ChunkEngine.dataKey());
        require(oldChunk.displays().stream().noneMatch(display -> display.id().equals(moving.id())), "old display storage cleared");
        getConfig().set("moving", moving.id().toString());
        getConfig().set("attached", attached.id().toString());

        var markers = SudoBlockManager.getInstance();
        markers.placeMarker(block(53), DEMO, "green");
        vetoPlacement = true;
        require(!markers.convertMarkerNow(block(53)) && markers.isSudoMarker(block(53)), "veto preserves marker");
        vetoPlacement = false;

        markers.placeMarker(block(54), DEMO, "purple");
        var template = markers.transformGeneratedMarker(world, 55, Y, Z, block(54).getState());
        block(55).setType(Material.CHEST, false);
        template.copy(block(55).getLocation()).update(true, false);
        markers.convertGeneratedMarkers(block(55).getChunk());
        require(hasState(55, "purple"), "generated marker retained state");

        markers.placeMarker(block(56), DEMO, "green", BlockOrientation.EAST);
        require(markers.convertMarkerNow(block(56))
                && view(56).storedBlock().orientation() == BlockOrientation.EAST, "marker direction");
        require(markers.convertCustomBlockToMarker(block(56), view(56)), "wand preserves orientation in marker");
        var rotatedChest = (org.bukkit.block.data.Directional) block(56).getBlockData();
        rotatedChest.setFacing(org.bukkit.block.BlockFace.SOUTH);
        block(56).setBlockData(rotatedChest, false);
        require(markers.convertMarkerNow(block(56))
                && view(56).storedBlock().orientation() == BlockOrientation.SOUTH, "rotated structure marker");

        verifyDestruction();
        ChunkEngine.flushNow();
    }

    private void verifyDestruction() {
        place(104, DEMO, "green");
        explode(ExplosionResult.DESTROY, true);
        require(view(104) != null, "cancelled explosion");
        explode(ExplosionResult.KEEP, false);
        require(view(104) != null, "non-destructive explosion");
        protectExplosion = true;
        explode(ExplosionResult.DESTROY, false);
        require(view(104) != null, "protected explosion target");
        protectExplosion = false;
        explode(ExplosionResult.DESTROY, false);
        require(view(104) == null && block(104).getType().isAir(), "approved explosion");

        world.getBlockAt(106, 70, Z).setType(Material.STONE, false);
        place(106, "blockengine_test:demo_movement", "default");
        require(GravityManager.getInstance().check(block(106)), "gravity movement");
        require(view(106) == null && world.getBlockAt(106, 71, Z).getType() == Material.BARRIER,
                "landing before animation");
    }

    private void verifyRestart() {
        require(ticks > 0, "already-loaded block ticks after adapter discovery");
        verifyRestoredState(50, "purple");
        verifyRestoredState(65, "purple");
        require(view(56).storedBlock().orientation() == BlockOrientation.SOUTH, "direction survived restart");
        var displays = ManagedDisplayManager.getInstance();
        var attached = displays.get(UUID.fromString(getConfig().getString("attached")));
        var moving = displays.get(UUID.fromString(getConfig().getString("moving")));
        require(attached != null && moving != null && moving.spec().x() == 67.5, "restored display handles");

        var markers = SudoBlockManager.getInstance();
        require(markers.togglePreview(block(53)) == SudoBlockManager.PreviewToggle.HIDDEN, "restored marker preview");
        require(markers.convertMarkerNow(block(53)) && hasState(53, "green"), "accepted marker conversion");
        require(managed.removeBlock(50, Y, Z, false) && !attached.valid(), "owner removal invalidates display");
    }

    private void explode(ExplosionResult result, boolean cancel) {
        Block origin = block(103);
        var event = new BlockExplodeEvent(origin, origin.getState(), new ArrayList<>(List.of(block(104))), 0f, result);
        event.setCancelled(cancel);
        event.callEvent();
    }

    private void verifyRestoredState(int x, String expected) {
        if (!hasState(x, expected)) {
            Block block = block(x);
            var disk = ChunkEngine.Data.load(block.getChunk(), ChunkEngine.dataKey());
            throw new AssertionError("Restored block at x=" + x + " expected " + expected + ", found "
                    + view(x) + ", material=" + block.getType() + ", stored=" + disk.blockAt(x & 15, Y, Z & 15));
        }
    }

    private Block block(int x) {
        return world.getBlockAt(x, Y, Z);
    }

    private RuntimeBlockView view(int x) {
        return ChunkEngine.getBlock(new BlockLocationKey(world.getUID(), x, Y, Z));
    }

    private boolean hasState(int x, String state) {
        var block = view(x);
        return block != null && block.storedBlock().stateId().equals(state);
    }

    private void place(int x, String id, String state) {
        require(managed.setBlock(x, Y, Z, id, state, null, null), "place " + id + " at x=" + x);
    }

    private DisplaySpec spec(int x) {
        return DisplaySpec.builder(block(x).getLocation().add(0.5, 0.5, 0.5))
                .itemStack(new ItemStack(Material.BRICK))
                .build();
    }

    private static void require(boolean passed, String operation) {
        if (!passed) {
            throw new AssertionError(operation);
        }
    }
}
