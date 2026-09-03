# Sudo Block Structure Building Post-Commit

## Completed

- Added sudo structure-building items generated from every registered custom block.
- Added red warning lore and sudo-specific item PDC so sudo items are distinct from real BlockEngine block items.
- Added sudo marker chests with tile PDC for block id, state id, and optional preview display id.
- Added persistent BlockEngine-style preview displays for placed sudo markers.
- Added the Block Engine Wand item, resource-pack model generation, and debug give command.
- Added wand actions for toggling sudo previews and converting real custom blocks into sudo markers.
- Changed creative pick-block for real custom blocks and sudo markers to give/select the corresponding sudo item behind creative-mode and permission checks.
- Added a shared sudo marker transformer that records sudo marker chests, replaces them with barriers, and schedules server-thread custom block placement from `AsyncStructureGenerateEvent` or plugin-driven `Structure#place(...)` calls.
- Added a bounded vanilla structure-block fallback scan for creative structure builders after structure-block interaction or redstone activation.
- Added shutdown flushing for pending structure conversions.
- Added `/catalog sudo` and `/blockengine catalog sudo` structure catalog support.
- Documented the workflow, permissions, wand behavior, structure-generation pipeline, and shutdown safety.

## Verification

- Direct Java compilation passed against the locally resolved Paper, Adventure, PacketEvents, Jackson, and Lombok dependencies.
- Documentation build passed with the new Structure Building guide.

## Limitation

Gradle tasks could not run in this host session because Gradle failed before project configuration with `java.io.IOException: Unable to establish loopback connection`. The Java sources were verified with a direct `javac` compile as a fallback.
