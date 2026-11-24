# Lead Lines

Tie leads to any block, not just fences. String ropes, power lines and decorations between
logs, walls, fences, stone, anything.

Fabric mod for Minecraft 26.3. Needs Fabric API. Install on the server and every client.

## How to use

1. Hold a lead and **right-click any block**. A knot appears where you clicked and the lead
   follows your hand. (No sneaking, so it works while flying in creative.)
2. **Right-click another block** (or another knot) to tie the other end there.

- Right-click a knot with a lead to run another lead from it.
- Right-click a knot with an empty hand to pick up the leads tied to it.
- Punch a knot to remove it. Its leads drop as items.
- Shears on a knot cut its leads.
- Animals on a lead can be tied to any block the same way. Fences still use the vanilla knot.
- Leads can be up to 50 blocks long.
- Right-click a knot with a **stick** to change how much its leads sag: pulled tight, slight,
  medium (the default), loose, very loose.
- Breaking the block a knot is on drops its leads.

Unlike vanilla, a lead between knots doesn't snap when the chunk at the other end unloads
(the bug noted by Leash Fences, MC-300654). The knot waits for the other end to load again.

Idea from [Leash Fences](https://codeberg.org/jodeks-datapacks/leash-fences) by Jodek.
