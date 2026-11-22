package audit;

import com.mojang.authlib.GameProfile;
import dev.auto.blockengine.api.blocks.BlockAdapter;
import dev.auto.blockengine.api.blocks.BlockContext;
import dev.auto.blockengine.api.blocks.BlockCreateContext;
import dev.auto.blockengine.api.blocks.BlockData;
import dev.auto.blockengine.api.blocks.BlockDefinition;
import dev.auto.blockengine.api.event.BlockEngineBlockBreakEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockBrokenEvent;
import dev.auto.blockengine.api.event.BlockEngineBlockPlaceEvent;
import dev.auto.blockengine.items.ItemManager;
import dev.auto.blockengine.entity.ManagedDisplayManager;
import dev.auto.blockengine.placement.BlockOrientation;
import dev.auto.blockengine.registry.BlockRegistry;
import dev.auto.blockengine.runtime.ChunkEngine;
import dev.auto.blockengine.types.BlockLocationKey;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class BlockLifecycleProbe extends JavaPlugin implements Listener {
    private ServerPlayer player;
    private CraftWorld world;
    private PlayerInventory inventory;
    private EquipmentSlot observedHand;
    private InteractionHand swingHand;
    private SwingAnimation swingAnimation;
    private boolean swingSentToSelf;
    private boolean denyInteract, denyPlace, denyEnginePlace, denyBreak, denyEngineBreak, suppressDrops;
    private int checks, breakEvents, brokenEvents, swings, sequence, blockInteractions, burstTick;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        List<Runnable> steps = List.of(
                this::createPlayer, this::testCarrierAnimations, this::testPlacementBurst, this::testPlacementVetoes,
                this::testTwoHands, this::testCreativePlacement, this::testAdventure, this::testMining,
                () -> testOrientation(BlockDefinition.Placement.HORIZONTAL_FACING, BlockOrientation.NORTH, BlockFace.UP, 14),
                () -> testOrientation(BlockDefinition.Placement.AXIS, BlockOrientation.AXIS_X, BlockFace.EAST, 16),
                () -> testOrientation(BlockDefinition.Placement.DIRECTIONAL, BlockOrientation.UP, BlockFace.UP, 18),
                this::testDispenser, this::testInventoryClick);

        new BukkitRunnable() {
            int next;

            @Override
            public void run() {
                try {
                    steps.get(next++).run();
                    if (next < steps.size()) return;
                    getLogger().info("LIFECYCLE PROBE PASS (" + checks + " checks)");
                } catch (Throwable failure) {
                    getLogger().log(Level.SEVERE, "LIFECYCLE PROBE FAIL", failure);
                }
                cancel();
                Bukkit.shutdown();
            }
        }.runTaskTimer(this, 5L, 1L);
    }

    private void createPlayer() {
        world = (CraftWorld) Bukkit.getWorlds().getFirst();
        var server = ((CraftServer) Bukkit.getServer()).getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "LifecycleProbe");
        player = new ServerPlayer(server, world.getHandle(), profile, ClientInformation.createDefault()) {
            @Override
            public boolean isImmobile() {
                // Paper treats players without a connected client as immobile at packet entry.
                return false;
            }

            @Override
            public boolean swing(InteractionHand hand, SwingAnimation animation, boolean sendToSelf) {
                swings++;
                swingHand = hand;
                swingAnimation = animation;
                swingSentToSelf = sendToSelf;
                return super.swing(hand, animation, sendToSelf);
            }
        };
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(profile, false));
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setPos(4.5, 121, 3.5);
        inventory = player.getBukkitEntity().getInventory();
    }

    private void testCarrierAnimations() {
        ItemStack custom = ItemManager.create(BlockRegistry.getBlock("blockengine_test:red_block"));
        inventory.setItemInMainHand(custom);
        var managed = player.getItemInHand(InteractionHand.MAIN_HAND);
        var vanilla = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
        check(custom.getType() == Material.PAPER && managed.getInteractAnimation().type() == SwingAnimationType.NONE
                && managed.getInteractAnimation().duration() == 0, "fresh carrier suppresses the client's use swing");
        check(managed.getAttackAnimation().equals(vanilla.getAttackAnimation()), "carrier preserves vanilla attack animation");

        ItemStack legacy = custom.withType(Material.KNOWLEDGE_BOOK).asQuantity(7);
        legacy.resetData(DataComponentTypes.INTERACT_ANIMATION);
        Component name = Component.text("Renamed before upgrade");
        legacy.setData(DataComponentTypes.CUSTOM_NAME, name);
        inventory.setItemInOffHand(legacy);
        // Raw slot 45 is the offhand in the player's own inventory view.
        new PlayerInventorySlotChangeEvent(player.getBukkitEntity(), 45, ItemStack.empty(), legacy).callEvent();

        var updated = inventory.getItemInOffHand();
        check(updated.getType() == Material.PAPER && updated.getAmount() == 7
                && name.equals(updated.getData(DataComponentTypes.CUSTOM_NAME))
                && ItemManager.blockId(custom).equals(ItemManager.blockId(updated)),
                "legacy offhand carrier upgrades without losing identity, name or count");
        check(player.getItemInHand(InteractionHand.OFF_HAND).getInteractAnimation().type() == SwingAnimationType.NONE,
                "upgraded carrier suppresses the client's use swing");
    }

    private void testPlacementBurst() {
        ItemStack custom = ItemManager.create(BlockRegistry.getBlock("blockengine_test:red_block"));
        inventory.setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        inventory.setItemInOffHand(custom.asQuantity(3));

        Block target = target(4);
        burstTick = Bukkit.getCurrentTick();
        packetClick(target, InteractionHand.OFF_HAND);
        check(placed(target) && offhandCount() == 2, "survival offhand places and charges one");
        check(observedHand == EquipmentSlot.OFF_HAND && inventory.getItemInMainHand().getType() == Material.DIAMOND_PICKAXE,
                "Bukkit reports the offhand and the main slot stays intact");
        check(swings == 1 && swingHand == InteractionHand.OFF_HAND && swingSentToSelf
                && swingAnimation.type() == SwingAnimationType.WHACK, "approved offhand swings visibly once");

        inventory.setItemInMainHand(custom.asQuantity(3));
        for (int x = 5; x <= 8; x++) {
            Block next = target(x);
            packetClick(next, x % 2 == 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            check(next.getType().isAir() && !placed(next), "same-tick burst cannot place at x=" + x);
        }
        check(swings == 1 && offhandCount() == 2 && inventory.getItemInMainHand().getAmount() == 3,
                "burst across distinct targets and both hands only consumes and swings once");

        inventory.setItemInMainHand(new ItemStack(Material.COBBLESTONE, 2));
        Block vanillaTarget = target(3);
        packetClick(vanillaTarget, InteractionHand.MAIN_HAND);
        check(vanillaTarget.getType() == Material.COBBLESTONE && inventory.getItemInMainHand().getAmount() == 1,
                "vanilla block placement still works during the custom placement limit");
    }

    private void testPlacementVetoes() {
        check(Bukkit.getCurrentTick() == burstTick + 1, "next attempts run on the immediately following tick");
        int before = swings;
        Block target = target(5);
        denyPlace = true;
        packetClick(target, InteractionHand.OFF_HAND);
        check(target.getType().isAir() && !placed(target) && offhandCount() == 2 && swings == before,
                "Bukkit placement veto preserves the stack without a swing");
        denyPlace = false;
        denyEnginePlace = true;
        packetClick(target, InteractionHand.OFF_HAND);
        check(target.getType().isAir() && !placed(target) && offhandCount() == 2 && swings == before,
                "BlockEngine placement veto preserves the stack without a swing");
        denyEnginePlace = false;
        denyInteract = true;
        packetClick(target, InteractionHand.OFF_HAND);
        check(!placed(target) && offhandCount() == 2 && swings == before, "interaction veto suppresses the swing");
        denyInteract = false;

        target = target(6);
        player.setPos(6.5, 121, 4.5);
        click(target, InteractionHand.OFF_HAND);
        check(!placed(target) && offhandCount() == 2 && swings == before, "player collision preserves stack and swing");
        player.setPos(4.5, 121, 3.5);

        target = target(7);
        target.setType(Material.SHORT_GRASS, false);
        clickOn(target, InteractionHand.OFF_HAND);
        check(placed(target) && offhandCount() == 1 && swings == before + 1,
                "vetoes leave this tick available for placing into a replaceable block");
        var air = new PlayerInteractEvent(player.getBukkitEntity(), Action.RIGHT_CLICK_AIR,
                inventory.getItemInOffHand(), null, BlockFace.SELF, EquipmentSlot.OFF_HAND);
        Bukkit.getPluginManager().callEvent(air);
        check(air.useItemInHand() == Event.Result.DENY && offhandCount() == 1, "air click suppresses carrier use");
    }

    private void testTwoHands() {
        ItemStack custom = ItemManager.create(BlockRegistry.getBlock("blockengine_test:red_block"));
        Block target = target(8);
        inventory.setItemInMainHand(custom.asQuantity(3));
        int before = swings;
        click(target, InteractionHand.MAIN_HAND);
        click(target, InteractionHand.OFF_HAND);
        check(placed(target) && inventory.getItemInMainHand().getAmount() == 2 && offhandCount() == 1,
                "two hands targeting the same click charge only once");
        check(swings == before + 1 && swingHand == InteractionHand.MAIN_HAND && swingSentToSelf
                && swingAnimation.type() == SwingAnimationType.WHACK, "approved main hand swings visibly once");
    }

    private void testCreativePlacement() {
        player.getBukkitEntity().setGameMode(GameMode.CREATIVE);
        Block target = target(9);
        click(target, InteractionHand.MAIN_HAND);
        check(placed(target) && inventory.getItemInMainHand().getAmount() == 2, "creative retains stack");
        var pick = new PlayerPickBlockEvent(player.getBukkitEntity(), target, new ItemStack(Material.BARRIER), false, 3, -1);
        pick.callEvent();
        check(!player.getBukkitEntity().hasPermission("blockengine.structure")
                && "blockengine_test:red_block".equals(ItemManager.blockId(inventory.getItem(3))),
                "ordinary creative players pick a full custom block");
        inventory.setHeldItemSlot(0);
        player.getBukkitEntity().setGameMode(GameMode.SURVIVAL);
    }

    private void testAdventure() {
        player.getBukkitEntity().setGameMode(GameMode.ADVENTURE);
        ItemStack item = ItemManager.create(BlockRegistry.getBlock("blockengine_test:red_block"));
        inventory.setItemInOffHand(item.asQuantity(2));
        Block target = target(10);
        click(target, InteractionHand.OFF_HAND);
        check(!placed(target) && offhandCount() == 2, "adventure player requires a placement predicate");

        // The state condition distinguishes two blocks of the same material.
        var allowed = Bukkit.getItemFactory().createItemStack(
                "minecraft:knowledge_book[minecraft:can_place_on=[{blocks:'minecraft:oak_log',state:{axis:'x'}}]]");
        allowed.editPersistentDataContainer(data -> item.getItemMeta().getPersistentDataContainer().copyTo(data, true));
        inventory.setItemInOffHand(allowed.asQuantity(2));
        var support = target.getRelative(BlockFace.DOWN);
        support.setBlockData(Bukkit.createBlockData("minecraft:oak_log[axis=y]"), false);
        click(target, InteractionHand.OFF_HAND);
        check(!placed(target) && offhandCount() == 2, "adventure rejects substrate with the wrong block state");

        support.setBlockData(Bukkit.createBlockData("minecraft:oak_log[axis=x]"), false);
        click(target, InteractionHand.OFF_HAND);
        check(placed(target) && offhandCount() == 1, "adventure accepts matching substrate state");
        player.getBukkitEntity().setGameMode(GameMode.SURVIVAL);
    }

    private void testMining() {
        inventory.setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        player.getBukkitEntity().getAttribute(Attribute.BLOCK_BREAK_SPEED).setBaseValue(10000);
        int initialDrops = world.getEntitiesByClass(Item.class).size();

        Block target = target(4);
        denyBreak = true;
        mine(target);
        check(placed(target) && damage() == 0 && breakEvents == 1 && brokenEvents == 0, "Bukkit break veto");
        denyBreak = false;
        denyEngineBreak = true;
        mine(target);
        check(placed(target) && damage() == 0 && breakEvents == 2 && brokenEvents == 0, "BlockEngine break veto");
        denyEngineBreak = false;

        suppressDrops = true;
        mine(target);
        check(target.getType().isAir() && !placed(target) && breakEvents == 3 && brokenEvents == 1
                && world.getEntitiesByClass(Item.class).size() == initialDrops && damage() == 1,
                "approved instant break suppresses drops and wears tool once");
        suppressDrops = false;

        target = target(7);
        player.setPos(7.5, 121, 3.5);
        mine(target);
        check(!placed(target) && breakEvents == 4 && brokenEvents == 2
                && world.getEntitiesByClass(Item.class).size() == initialDrops + 1 && damage() == 2,
                "survival harvest drops one item and wears tool once");

        player.getBukkitEntity().setGameMode(GameMode.CREATIVE);
        target = target(8);
        mine(target);
        check(!placed(target) && breakEvents == 5 && brokenEvents == 3
                && world.getEntitiesByClass(Item.class).size() == initialDrops + 1 && damage() == 2,
                "creative break preserves durability and suppresses drops");
    }

    private void testOrientation(BlockDefinition.Placement mode, BlockOrientation expected, BlockFace face, int x) {
        player.setYRot(0);
        var definition = BlockRegistry.registerBlock(new BlockAdapter() {
            @Override
            public String name() {
                return "probe_" + mode.name().toLowerCase(java.util.Locale.ROOT);
            }

            @Override
            public void define(BlockDefinition.Builder builder) {
                builder.placement(mode).state("default", state -> state.textures(textures -> textures.all("test")));
            }

            @Override
            public BlockData createDefaultData(BlockCreateContext context) {
                return context.createData();
            }

            @Override
            public void onPlace(BlockContext context) {
                context.data().integer("placed", 1);
            }
        }, "blockengine_test", this);

        Block base = target(x).getRelative(BlockFace.DOWN);
        Block target = base.getRelative(face);
        inventory.setItemInMainHand(ItemManager.create(definition));
        clickOn(base, InteractionHand.MAIN_HAND, face);

        var view = ChunkEngine.getBlock(new BlockLocationKey(world.getUID(), target.getX(), target.getY(), target.getZ()));
        check(view != null && view.storedBlock().orientation() == expected && view.storedBlock().data().integer("placed") == 1,
                mode + " orientation survives the adapter's onPlace save");
        var spec = ManagedDisplayManager.getInstance().defaultBlockDisplay(view, ItemManager.display(view)).spec();
        var rotation = expected.rotation();
        check(spec.leftRotationX() == rotation.x() && spec.leftRotationY() == rotation.y()
                        && spec.leftRotationZ() == rotation.z() && spec.leftRotationW() == rotation.w(),
                mode + " display uses the stored rotation");
    }

    private void testDispenser() {
        Block source = world.getBlockAt(5, 123, 4);
        source.setType(Material.DISPENSER, false);
        Directional direction = (Directional) source.getBlockData();
        direction.setFacing(BlockFace.EAST);
        source.setBlockData(direction, false);

        Dispenser dispenser = (Dispenser) source.getState();
        dispenser.getInventory().setItem(5, ItemManager.create(BlockRegistry.getBlock("blockengine_test:demo_movement")));
        dispenser.dispense();
        check(placed(source.getRelative(BlockFace.EAST)) && dispenser.getInventory().getItem(5) == null,
                "native dispenser placement consumes its last item");
    }

    private void testInventoryClick() {
        player.getBukkitEntity().setItemOnCursor(new ItemStack(Material.APPLE));
        var click = new InventoryClickEvent(player.getBukkitEntity().getOpenInventory(), InventoryType.SlotType.CONTAINER,
                9, ClickType.LEFT, InventoryAction.NOTHING) {
            int writes;

            @Override
            public void setCursor(ItemStack stack) {
                writes++;
                super.setCursor(stack);
            }
        };
        check(click.getCursor() != click.getCursor(), "Paper returns distinct cursor mirrors");
        Bukkit.getPluginManager().callEvent(click);
        check(click.writes == 0 && click.getCursor().getType() == Material.APPLE, "unrelated cursor is never rewritten");
    }

    private Block target(int x) {
        Block base = world.getBlockAt(x, 120, 4);
        base.setType(Material.STONE, false);
        return base.getRelative(BlockFace.UP);
    }

    private void click(Block target, InteractionHand hand) {
        clickOn(target.getRelative(BlockFace.DOWN), hand);
    }

    private void clickOn(Block block, InteractionHand hand) {
        clickOn(block, hand, BlockFace.UP);
    }

    private void clickOn(Block block, InteractionHand hand, BlockFace face) {
        player.gameMode.useItemOn(player, world.getHandle(), player.getItemInHand(hand), hand, hit(block, face));
    }

    private void packetClick(Block target, InteractionHand hand) {
        var hit = hit(target.getRelative(BlockFace.DOWN), BlockFace.UP);
        int before = blockInteractions;
        player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(hand, hit, ++sequence));
        if (blockInteractions != before + 1) {
            throw new AssertionError("Use-on packet " + sequence + " did not reach the Bukkit interaction handler once");
        }
    }

    private BlockHitResult hit(Block block, BlockFace face) {
        var position = new BlockPos(block.getX(), block.getY(), block.getZ());
        var point = new Vec3(block.getX() + .5 + face.getModX() * .5, block.getY() + .5 + face.getModY() * .5,
                block.getZ() + .5 + face.getModZ() * .5);
        return new BlockHitResult(point, Direction.valueOf(face.name()), position, false);
    }

    private void mine(Block block) {
        player.gameMode.handleBlockBreakAction(new BlockPos(block.getX(), block.getY(), block.getZ()),
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, world.getMaxHeight(), 1);
    }

    private int offhandCount() {
        return inventory.getItemInOffHand().getAmount();
    }

    private int damage() {
        return ((Damageable) inventory.getItemInMainHand().getItemMeta()).getDamage();
    }

    private boolean placed(Block block) {
        return block.getType() == Material.BARRIER && ChunkEngine.getBlock(new BlockLocationKey(
                world.getUID(), block.getX(), block.getY(), block.getZ())) != null;
    }

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
        getLogger().info("PASS " + description);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) blockInteractions++;
        if (denyInteract) event.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        observedHand = event.getHand();
        if (denyPlace) event.setCancelled(true);
    }

    @EventHandler
    public void onEnginePlace(BlockEngineBlockPlaceEvent event) {
        if (denyEnginePlace) event.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        breakEvents++;
        if (denyBreak) event.setCancelled(true);
        if (suppressDrops) event.setDropItems(false);
    }

    @EventHandler
    public void onEngineBreak(BlockEngineBlockBreakEvent event) {
        if (denyEngineBreak) event.setCancelled(true);
    }

    @EventHandler
    public void onBroken(BlockEngineBlockBrokenEvent event) {
        brokenEvents++;
    }
}
