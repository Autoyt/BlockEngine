# BlockEngine lifecycle audit

November 22, 2026 · Paper 26.3 · PacketEvents 2.14.0 · Java 25

The audit started from checkpoint `5ebed33`. On that build, a survival player holding a BlockEngine item in the offhand could use the item, lose it, and leave the target empty. The native Paper regression reproduces this with Minecraft's server-side player interaction code. Both placement listeners filtered out the offhand and let vanilla use the knowledge-book carrier.

## Findings and fixes

| Area | Failure observed in code or at runtime | Corrected behavior |
| --- | --- | --- |
| Player placement | Handlers skipped the offhand and attempted main-hand placement at two event priorities. A failed placement could leave vanilla free to consume the carrier. | One interaction path owns both hands, checks the destination and cancellation, and charges exactly one matching item from the initiating slot. Creative keeps the stack. |
| Dispensers | The dispense listener searched for a matching stack after vanilla had split it. Dispensing the final item could place a free block. | The pre-dispense event identifies the actual dispenser slot before the split. A rejected placement preserves the stack. |
| Placement direction | The engine applied direction to a barrier's block data. The resulting model used the same rotation for every face. | Horizontal, axis and directional placements rotate the display. The stored orientation follows state changes, moves, restarts and structure conversions. |
| Player breaking and drops | Custom mining could finish outside the normal Bukkit break event. The configured mining multiplier was ignored. Some tool wear and reward paths disagreed with the final break decision. | Native completion dispatches the ordinary break event. Custom removal honors cancellation, drop eligibility and creative mode, charges wear after success, and guards against callbacks replacing the target. |
| Explosions | The handler could process cancelled explosions and expand the set of vanilla blocks destroyed. | Damageable custom blocks enter the explosion event before protection listeners run. Removal follows the approved list, result and yield. |
| Gravity | The source was removed before an animation finished. A stop, unload or occupied destination could interrupt the move while the only copy was in memory. | The source and destination records change together on the server thread. The falling display animates the committed move. |
| Save, unload and restart | A save callback could change a collection being flushed or have its changes cleared with the old batch. Chunks loaded before adapter discovery could miss ticking blocks. | Callback edits survive the flush. World and chunk lifecycle handlers persist pending writes, and startup indexes already-loaded ticking blocks after discovery. |
| Block data and displays | Saving adapter data could drop attached displays. Saving a chunk invalidated live display handles. Moving a world display could leave another copy in the old chunk. | State and payload saves preserve attachments, surviving handles remain usable, and moving a display removes its old storage entry. Older chunk records remain readable. |
| Visibility | Crossing a chunk boundary or teleporting could refresh from the old player position. A change across a chunk edge could leave a newly exposed block invisible. | Visibility refreshes after movement and respawn. Changed cells and adjacent chunk edges update exposure. Active displays receive position updates when their anchors move. |
| Structures | A conversion replaced the marker with a barrier before the placement veto. Retries could all run in one tick. Preview handles could be lost after restart, and rotated structures could be scanned in the wrong direction. | A generated marker retains its identity until conversion succeeds. Previews survive restart, and structure bounds and marker facing follow rotation and mirroring. |
| Commands and inventories | An invalid state or vetoed replacement could remove the old block. Large fills could overflow their size calculation. Creative stack wrappers and catalog sessions could cause unintended inventory writes. | Commands validate states, coordinates and limits before mutation. The placement veto precedes removal. Creative conversion and catalog handling act on the intended items and open inventory. |
| Registration and resource packs | A failing contributor could interrupt later registrations or pack generation. Republishing a download could keep serving the previous file. | Contributor failures are isolated. Downloads resolve the current file, and generated ZIPs are installed after they finish writing. |

## Verification

- The native gameplay probe passed 28 checks across placement from both hands, exact consumption, blocked and occupied targets, Adventure predicates, creative inventory, final-item dispensing, breaking, drops, tool wear and display rotation.
- The persistence probe starts Paper twice against the same temporary world. It checks edits made by a save listener in the same and neighboring chunks, ticking after discovery, display movement and ownership, marker conversion, explosion filtering, and gravity.
- The compatibility smoke test started Trims, AutoLib, BlockEngine, Flare, the Flare inspector and PacketEvents together. Trims registered six blocks and BlockEngine generated its resource pack.
- The Gradle suites passed 60 BlockEngine tests and seven Flare inspector tests. Coverage includes chunk codecs, placement inventory, orientation, mining, gravity, fill bounds, structure bounds, item handling and existing API behavior.

The native probes and their invocation are in the [Paper regression guide](../../tests/paper/README.md).

## Compatibility and remaining limits

The reader accepts chunk formats 2 through 7. Existing blocks retain their previous appearance. Chunks containing a stored orientation use format 7. Chunks with only identity rotations continue to write format 6. Restoring an older plugin after format 7 has been written requires a matching pre-upgrade world backup.

The gameplay probe uses a native server-side player. Client rendering and input latency were not visually tested with a connected Minecraft client. Blast reach for barrier-backed custom blocks remains an estimate based on Paper's explosion footprint. Cancellation and the final affected-block list were tested independently of that estimate.
