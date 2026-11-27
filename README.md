# Lead Lines

Tie leads to any block, not just fences. String ropes, power lines and decorations between
logs, walls, fences, stone, anything.

Fabric mod for Minecraft 26.3. Needs Fabric API. Install on the server and every client.

## How to use

1. Hold a lead and **right-click any block**. A knot appears where you clicked and the rope
   follows your hand. (No sneaking, so it works while flying in creative.)
2. **Right-click another block** (or another knot) to tie the other end there. The lead is used
   up when the rope is tied. Click the knot you started from to put the rope down instead.

- On a fence, the knot snaps to the center of the post and wraps around it, wherever on the
  fence you click.
- Right-click a knot with a lead to run another rope from it.
- Right-click a knot with an **empty hand** to untie its newest rope and pick it back up, still
  tied at its other end, so you can move it.
- Right-click a knot with a **stick** to change how much its ropes sag: pulled tight, slight,
  medium (the default), loose, very loose.
- **Punch** a knot or use **shears** on it to cut its ropes. The leads drop (not when punching
  in creative).
- Breaking the block a knot is on drops its leads.
- Ropes can be up to 50 blocks long. Walking farther than that while stringing one drops it.
- Animals on leads are vanilla: tie them to fence posts as usual.

## How it works

Ropes and knots are plain data saved with the world and synced to players, not entities. So
they never tick, don't show up as entities on minimaps like Xaero's, can't be removed by
`/kill @e`, and can't snap when a chunk unloads.

Worlds with ropes from Lead Lines 1.x are converted automatically: each old knot turns its lead
into rope data the first time it loads, then removes itself.

Idea from [Leash Fences](https://codeberg.org/jodeks-datapacks/leash-fences) by Jodek.
