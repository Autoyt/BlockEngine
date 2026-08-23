# Data-Driven Block Packs

## Goal

BlockEngine supports "non logical" custom blocks that are added with JSON files and resource-pack assets only. A pack author can define decorative, breakable, placeable, textured blocks without writing a `BlockAdapter`, compiling a plugin, or implementing callbacks.

The runtime sees these blocks as normal BlockEngine blocks. They use the existing registry, persistence, placement, mining, gravity, catalog, item, and resource-pack generation paths.

## Pack Layout

Packs live under the BlockEngine plugin data folder:

```text
plugins/blockengine/packs/
  example_pack/
    pack.json
    blocks/
      polished_basalt.json
      furniture/chair.json
    assets/
      assets/example/textures/block/polished_basalt.png
      assets/example/textures/block/chair_top.png
      assets/example/textures/block/chair_side.png
    pack.png
```

`pack.json` describes pack-level metadata:

```json
{
  "format": 1,
  "namespace": "example",
  "title": "<green>Example Blocks",
  "description": "Decorative blocks for BlockEngine",
  "prompt": "",
  "url-ending": "example",
  "required": true,
  "catalog": true,
  "icon": "pack.png",
  "assets": ["assets"]
}
```

Each file in `blocks/**/*.json` defines one block. If `name` is omitted, the path under `blocks/` becomes the block name.

```json
{
  "name": "polished_basalt",
  "vanilla-block": "barrier",
  "catalog": true,
  "placement": "directional",
  "item": {
    "material": "knowledge_book",
    "name": "<gray>Polished Basalt",
    "lore": ["<dark_gray>Decorative"],
    "glint": false,
    "placeable": true
  },
  "default-state": "default",
  "states": {
    "default": {
      "hardness": 1.25,
      "mining-speed": 1.0,
      "mining-profile": "stone",
      "preferred-tools": ["pickaxe"],
      "require-preferred-tool-for-drops": false,
      "require-silk-touch-for-drops": false,
      "unbreakable": false,
      "drops-item": true,
      "drop-in-creative": false,
      "movement": {
        "gravity": false,
        "dispenser-placeable": true,
        "breaks-via-gravity": false
      },
      "textures": {
        "all": "polished_basalt"
      },
      "sounds": {
        "place": "minecraft:block.stone.place",
        "break": "minecraft:block.stone.break",
        "mining": "minecraft:block.stone.hit",
        "step": "minecraft:block.stone.step"
      }
    }
  }
}
```

## Implementation

The internal `dev.auto.blockengine.datapack` package owns loading and registration:

- `BlockPackLoader` scans `plugins/blockengine/packs/*/pack.json`.
- `BlockPack` and `BlockPackBlock` hold immutable parsed data.
- `DataBlockAdapter` wraps parsed blocks and implements `BlockAdapter`.
- `DataBlockPacks` stores loaded packs and registers generated adapters during discovery.

Data blocks are discovered after code-backed `CustomBlockSystem` adapters. Duplicate `namespace:name` ids fail registration instead of silently replacing the first block.

## Resource Packs

Data packs get individual hosted resource packs, parallel to plugin-provided `CustomBlockSystem` packs:

- BlockEngine generates normal block and item model JSON for every data block.
- Declared asset roots are copied with the same allowlist used by plugin pack assets.
- `pack.png`, `title`, `description`, `prompt`, `required`, and `url-ending` come from `pack.json`.
- The combined `*` download still works by merging generated pack folders.

## Validation

The loader validates before registration:

- `format` must be `1`.
- `namespace` must match `^[a-z0-9._-]+$`.
- Block names and state ids must use lowercase path segments.
- Materials must resolve against Bukkit `Material`.
- Block material fields must be blocks; item material fields must be items.
- Every block must define at least one state.
- `default-state` must exist.
- Every state must define at least one texture.
- Enum values are case-insensitive and may use hyphens.
- Pack asset and icon paths must stay inside the pack folder.

A JSON Schema for editor and tooling support lives at `docs/data-driven-block-pack.schema.json`.

## Non-Goals

- No custom Java callbacks from data packs.
- No scripting engine.
- No adapter-private payloads beyond normal `BlockData`.
- No custom collision or model geometry beyond the existing generated cube models.
- No hot reload beyond the existing plugin reload lifecycle.
