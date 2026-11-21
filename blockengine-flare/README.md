# BlockEngine Flare inspector

This optional plugin renders a movable, resizable Flare widget while the player holds the BlockEngine wand in the main hand. It inspects the block under the crosshair while leaving the wand's structure-building controls available.

Install matching builds of this plugin and BlockEngine alongside Flare and PacketEvents 2.14.0 on Flare's Paper 26.3 target. The base BlockEngine plugin can run independently of Flare. Both plugins share the separately installed PacketEvents runtime.

## Build

With BlockEngine and Flare checked out in sibling directories:

```powershell
.\gradlew.bat build
.\gradlew.bat -p blockengine-flare build
```

For a different Flare location, supply its API module directory:

```powershell
.\gradlew.bat -p blockengine-flare -PflareApiDir=C:/projects/flare/flare-api build
```

The optional plugin is `blockengine-flare/build/libs/blockengine-flare-1.0.0.jar`. It contains its own visual pack and does not shade either public API. Its build uses isolated dependency output directories so it can run alongside the owning API projects.

## Use

`blockengine_inspector` is enabled by default for players with `blockengine.inspector`, which defaults to operators. Explicitly removing it is a persistent per-player opt-out. Granting the permission also allows a non-operator to enable the add-on and use its own controls.

- `/be wand` gives the existing wand (requires `blockengine.structure`).
- `/be inspector` opens the full diagnostic dialog. `/be inspector info` opens the same dialog.
- `/be inspector on` and `off` change the player's selection.
- `/be inspector resend` resends the selected Flare transport and add-on packs.
- Hold the wand and open `/flare widgets` to preview the inspector in Flare's editor. Click the widget body to move it or a corner to resize it, click again to place it, and select **Save** to keep the layout. The editor requires `flare.widgets.edit` and the `flare` add-on.
- From the console, use `flare admin addons add blockengine_inspector <player>`, `flare admin addons remove blockengine_inspector <player>`, or `flare admin addons resend <player>`.

Configure `plugins/blockengine-flare/config.yml` before restarting: `public-url` must be reachable by players, `port` defaults to 8128, `target-range` defaults to 8 blocks, and `update-interval-ticks` defaults to 2. Flare independently owns its transport host and refresh cadence.

The HUD groups block identity, world and XYZ, chunk and local XZ, and runtime diagnostics into ten rows. Custom blocks show stored display count, adapter payload bytes, hardness, mining speed, exposure, and unbreakable status. Vanilla blocks and structure placeholders show `N/A` for those fields. The HUD rounds floating-point values to two decimals.

The dialog shows full target identifiers and numeric values, the world UUID, both pack load states, the current offer UUID, hosting URL, poll interval, and target range.

## API and pack boundaries

Flare discovers the inspector's `FlareAddonProvider` at startup. The `blockengine_inspector` add-on uses **132 bytes** of the selected profile, with 124 for the atomic `target` v1 schema and 8 for the `inspector` widget placement. Names use fixed uppercase ASCII buffers (block 32, state 24, material 24, world 16 bytes). Unsupported characters become `?` and a final `~` marks truncation.

The consumer shader imports the generated target and widget schemas and uses Flare's bounds helpers to position and scale the panel. Flare saves each player's placement. The inspector captures and validates the bus and restores the carrier using public Flare post passes.

The visual pack is hosted by this plugin with an immutable hash URL. Each player offer gets a new UUID, so a stale success event cannot activate a replacement. The HUD requires the add-on and permission, Flare's loaded profile, and the current inspector pack to be loaded. Removing the add-on removes only its visual effect and pack. It is also removed from the effect list when the wand is not held.

Other integrations can use the new `BlockEngine.inspection()` service on the server thread. `inspect(Block)` returns an immutable `BlockInspection`; `isWand(ItemStack)` recognizes the real structure tool. Inspection rejects unloaded chunks and avoids adapter callbacks or mutable payload exposure.

## Validation

Run both Gradle builds above to test command wiring, inspection data, offer handling, pack serving, and widget shader imports. For an in-game check, load both packs in a Minecraft client, aim the wand at a block, and use Flare's editor to resize, save, and reopen the widget.

The static 5×7 font and labels can be regenerated with `python blockengine-flare/dev/generate-labels.py`; this reads only this add-on's own files.
