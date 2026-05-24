# Update v0.1.1 — Scoreboard fix + FPV Phantoms

## Files in this update

Drop these into your project, **replacing** the existing files where applicable:

| File | Action | Path in project |
|---|---|---|
| `SpiritScoreboard.java` | **REPLACE** | `src/main/java/com/cutedifficult/util/` |
| `PlayerJoinHandler.java` | **REPLACE** | `src/main/java/com/cutedifficult/event/` |
| `CuteDifficult.java` | **REPLACE** | `src/main/java/com/cutedifficult/` |
| `PhantomDroneHandler.java` | **NEW** | `src/main/java/com/cutedifficult/event/` |
| `CdCommand.java` | **NEW** | `src/main/java/com/cutedifficult/command/` |

You'll need to create the new package: in IntelliJ, right-click on `com.cutedifficult` → New → Package → name it `command`.

## After dropping files in

1. IntelliJ should auto-detect the changes. If not: `Build` → `Rebuild Project`.
2. Stop Minecraft if it's running, restart via `Minecraft Client`.

No `Reload Gradle` needed — these are pure source changes, no dependencies added.

## What changed

### Scoreboard fix

**Problem:** Spirit/Karma data was being tracked silently, but the player couldn't see anything because no display slot was bound. Also a bug in the init-to-5 logic meant the default value often didn't get applied.

**Fix:**
- Spirit is now bound to the **sidebar** slot — you'll see `Spirit` with your value on the right side of the screen the moment you join.
- Initialization now uses a persistent player tag (`cd_initialized`) so each player gets their starting Spirit=5 exactly once.

### New: `/cd` commands

For testing the system without waiting for natural game events:

- `/cd info` — shows your mode, spirit, karma, and current spirit tier (Mortal/Awakened/etc.)
- `/cd spirit` — shows your spirit
- `/cd spirit set 50` — sets spirit (op only)
- `/cd spirit add -20` — adds to spirit (op only)
- `/cd karma set 100` — same for karma

In single-player you're automatically op. On a dev server you can grant yourself op with `/op @s`.

### New: FPV Phantoms

Every phantom in the world is now a kamikaze drone:

- **Detection radius: 64 blocks.** Phantoms see through walls, terrain, anything. There is no hiding.
- **Behavior:** the moment a player is in range, the phantom drops all vanilla swoop/circle behavior and flies directly at the player at constant speed.
- **Contact = boom.** At under 1.8 blocks, the phantom detonates with creeper-grade explosion (power 2.5) and removes itself.
- **Visual warning:** 
  - A line of smoke particles connects the phantom to the player at all times when targeted — this is the "I'm being hunted" indicator.
  - Soul-fire flames swirl around the phantom's body so you can spot it in the sky.

The smoke trail intensifies as the phantom gets closer — close phantom = dense trail = clear "RUN" signal.

## Test plan

1. `/cd info` should show `Mode: CRUEL, Spirit: 5 (Mortal), Karma: 0`.
2. Sidebar should show `Spirit: 5`.
3. `/cd spirit add 50` → sidebar updates to `Spirit: 55`, tier becomes `Sage`.
4. `/cd karma add 100` → karma stat updates (run `/cd info` to verify).
5. To test phantoms quickly: `/time set midnight` then `/summon minecraft:phantom ~ ~10 ~` — phantom should immediately turn toward you with a smoke trail and dive-bomb.

## Known things to ignore

- The smoke particles may look subtle in bright daylight. If we want them more dramatic later, we can swap to `DUST_PLUME` or a custom-colored `DustParticleEffect`. Easy change.
- Phantoms still spawn naturally based on insomnia — no change to spawn rate, just behavior. We can crank spawn rate as a separate mechanic later.
- If you see `Spirit: 0` on a player who joined before the update — they're missing the `cd_initialized` tag from before. Either run `/cd spirit set 5` once, or `/tag @s remove cd_initialized` and rejoin.
