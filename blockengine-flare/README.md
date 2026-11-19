# BlockEngine Flare inspector

This optional plugin renders a debug panel while the player holds the existing BlockEngine wand in the main hand. It inspects the block under the crosshair without adding click handlers or changing the wand's structure-building actions.

Install this plugin alongside BlockEngine, Flare, and PacketEvents 2.14.0 on Flare's Paper 26.3 target. The base BlockEngine plugin has no Flare dependency. Both plugins share the separately installed PacketEvents runtime; BlockEngine no longer embeds or terminates it.

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
- `/be inspector` or `/beinspector` opens the full diagnostic dialog.
- `/be inspector on` and `off` change the player's selection.
- `/be inspector resend` resends the selected Flare transport and add-on packs.
- From the console, use `flare admin addons add blockengine_inspector <player>`, `flare admin addons remove blockengine_inspector <player>`, or `flare admin addons resend <player>`.

Configure `plugins/blockengine-flare/config.yml` before restarting: `public-url` must be reachable by players, `port` defaults to 8128, `target-range` defaults to 8 blocks, and `update-interval-ticks` defaults to 2. Flare independently owns its transport host and refresh cadence.

The HUD shows the target kind, block/state names, backing material, world, signed XYZ coordinates, chunk coordinates, stored display count, exposure, adapter payload byte count, hardness, mining speed, and unbreakable status. Custom blocks, structure placeholders, and vanilla blocks are distinguished. Runtime-only properties show `N/A` for vanilla blocks and placeholders; placeholder preview displays are not counted as stored custom-block displays. Floating values use two decimals in the HUD; the dialog shows full server values.

The dialog also reports both resource-pack states, current offer UUID, hosting URL, poll interval, target range, schema size, full untruncated identifiers, world UUID, and local chunk coordinates. The palette uses satin violet/blue with gold and sea-glass accents.

## API and pack boundaries

Flare discovers the inspector's plugin main class through `FlareAddonProvider` at startup. The registration callback declares `blockengine_inspector` and its `target` v1 schema, which uses **124 bytes** in profiles where the add-on is enabled. Every target update is an atomic publication unit. Names use fixed ASCII buffers (block 32, state 24, material 24, world 16 bytes); unsupported characters become `?`, uppercase is used for the pixel font, and a final `~` marks truncation. These bytes are display text, not string-based shader property access.

The consumer shader imports `flare:schemas/blockengine_inspector/target.glsl` and reads generated methods such as `getBlockId(index)`, `getX()`, `getHardness()`, and `getVisible()`. It captures/validates the Flare bus and restores the carrier using public Flare post passes. Its 32×32 capture target works with all supported compact bus sizes. No core shader is replaced, and no Flare assets are bundled in this pack.

The visual pack is hosted by this plugin with an immutable hash URL. Each player offer gets a new UUID, so a stale success event cannot activate a replacement. The HUD requires the add-on and permission, Flare's loaded profile, and the current inspector pack to be loaded. Removing the add-on removes only its visual effect and pack. It is also removed from the effect list when the wand is not held.

Other integrations can use the new `BlockEngine.inspection()` service on the server thread. `inspect(Block)` returns an immutable `BlockInspection`; `isWand(ItemStack)` recognizes the real structure tool. Inspection rejects unloaded chunks and avoids adapter callbacks or mutable payload exposure.

## Validation

Unit tests cover signed chunk coordinates, invalid inspection counts, bounded text and stale-tail clearing, Unicode replacement, offer replacement/disable/failure races, HTTP GET/HEAD and path isolation, and pack ownership/public getter imports. New tests were run failing before implementation, then passing. The shared PacketEvents change and optional plugin compile in their respective target API builds. Native multiplayer acceptance still requires loading both packs in a Minecraft client.

The combined Flare GPU harness compiled 18 programs and passed 227,370 assertions, including the production inspector schema at 124 bytes and a 146-byte profile with its offsets shifted by another add-on. It checked generated getter values, preserved carrier data, and invalid-bus behavior, then rendered the panel for visual inspection. The inspector's post-effect chain also passed Minecraft's actual resource codec validation.

An isolated Paper 26.3 smoke server enabled PacketEvents, Flare, BlockEngine, and this add-on in dependency order with no linkage errors. It registered the 140-byte catalog (124 inspector + 16 probe), served the inspector's immutable pack with matching content hash, accepted diagnostic console commands, and stopped all plugins and HTTP listeners cleanly. The normal development server was not changed.

The static 5×7 font and labels can be regenerated with `python blockengine-flare/dev/generate-labels.py`; this reads only this add-on's own files.
