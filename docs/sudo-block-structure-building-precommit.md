# Placeholder Block Structure Building Pre-Commit

## Intent

Add editor-only placeholder blocks that let builders place BlockEngine full blocks inside vanilla structures. A placeholder block represents a full block without registering a full BlockEngine block record until the structure is generated or explicitly converted.

## Planned workflow

- Generate one placeholder structure-building item for every registered full block.
- Mark placeholder items with BlockEngine PDC and add prominent red warning lore so they are not confused with full block items.
- Place placeholder items as chests carrying the full block id in tile PDC.
- Spawn a persistent BlockEngine-style preview display on the placeholder chest.
- Add `/blockengine wand` as the structure-authoring entry point.
- Let the wand hide placeholder previews and convert full blocks into placeholder marker chests.
- Let creative middle-click/pick-block select the matching placeholder item for full blocks and placeholder chests, gated by permission.
- Add an `AsyncStructureGenerateEvent` transformer that detects placeholder marker chests, records their block ids, replaces them with barriers, and schedules server-thread BlockEngine placement after generation.
- Flush pending conversions on shutdown so generated barriers do not remain without BlockEngine records.

## Explicit non-goal

Do not add a separate chunk transaction or batch storage abstraction in this pass. Structure conversions should apply directly through existing placement and chunk persistence APIs.
