# Sudo Block Structure Building Pre-Commit

## Intent

Add editor-only sudo blocks that let builders place BlockEngine custom blocks inside vanilla structures. A sudo block represents a real custom block without registering a real BlockEngine block record until the structure is generated or explicitly converted.

## Planned workflow

- Generate one sudo structure-building item for every registered custom block.
- Mark sudo items with BlockEngine PDC and add prominent red warning lore so they are not confused with real custom block items.
- Place sudo items as chests carrying the real custom block id in tile PDC.
- Spawn a persistent BlockEngine-style preview display above the sudo chest.
- Add a Block Engine Wand for structure-authoring actions.
- Let the wand hide sudo previews and convert real custom blocks into sudo marker chests.
- Let creative middle-click/pick-block select the matching sudo item for real custom blocks and sudo chests, gated by permission.
- Add an `AsyncStructureGenerateEvent` transformer that detects sudo marker chests, records their block ids, replaces them with barriers, and schedules server-thread BlockEngine placement after generation.
- Flush pending conversions on shutdown so generated barriers do not remain without BlockEngine records.

## Explicit non-goal

Do not add a separate chunk transaction or batch storage abstraction in this pass. Structure conversions should apply directly through existing placement and chunk persistence APIs.
