# Changelog

All notable changes to Cute Difficult, in reverse chronological order.

Versioning was informal during development. v1.0.0-beta is the **first public release**.

---

## v1.0.0-beta — First Public Release

The mod is feature-complete enough to release. There will be bugs, there are TODOs, but **the core works**.

### Release state
- 9 elemental kitsune with unique personalities, offerings, and passives
- 9 parallel blessings + the Great Blessing
- Friendship arc with petting, naming, baby kitsune
- Witness memory with propagation
- Spirit HUD with compact icons
- 5-tier quality system
- Custom bestiary with GUI

### What got done in v0.9.x → v1.0.0

#### v0.9.8 — Kitsune Passives Update

All kitsune from tail 1 now have environmental auras and element immunities.

**Added:**
- 9 environmental auras (Kasai melts ice, Mori speeds growth, Kaminari strikes hostiles, etc.)
- 9 damage immunities (Kasai → fire/lava, Kaminari → lightning, etc.)
- KitsunePassivesHandler — both layers in one handler, server tick + ALLOW_DAMAGE event

#### v0.9.7 — Fox Rage + Icon HUD

**Added:**
- FoxRageHandler — kitsune remembers the player who hit them and retaliates for 10 seconds **even** under the Great Blessing
- Compact icon HUD — replaced the "✦ Great Blessing of Inari ✦" text that overflowed the panel with three golden stars + per-element icons (sparkle for active, gray dot for inactive)

**Changed:**
- FoxHostility.canAttack now checks rage **before** the Great Blessing check

#### v0.9.6 — Critical Fixes

**Fixed:**
- `/cd fox summon` was spawning the wrong fox. Race condition: a vanilla FoxEntity was being spawned → instantly replaced by FoxSpawnHandler with **random** element and **random** tail count, overwriting what the player requested. Fix — spawn KitsuneEntity directly, bypassing vanilla.
- ResonanceBlessingHandler skipped players in Creative. Which meant during testing (always in Creative) blessings **never** worked. Removed the creative skip — only Spectator is excluded now.

#### v0.9.5 — Spirit System Repair

**Fixed:**
- Spirit commands (`/cd spirit X add N`) showed the right text but **saved nothing**. Root cause: `SpiritData.ensureObjectives` existed as a method but **nobody was calling it**. SpiritData.set with null objective was silently no-op. Fix — added the call to SERVER_STARTED.
- Players were joining without `SpiritData.initializePlayer` being called. Fix — added it to the JOIN event.

#### v0.9.4 — The Great Refactor

The most painful session in the mod's history. We spent **hours** trying to compile this error:
```
required: Type
found: Element,FoxPersonality,int,int,long,int,String,long
```

Turned out — **vanilla `net.minecraft.entity.passive.FoxEntity` has an inner class named `FoxData`**. When our `KitsuneEntity extends FoxEntity` referenced `FoxData`, Java auto-resolves to the vanilla inner type (inherited types win priority over imports), not ours. All those "method not found" errors were about the vanilla FoxData, not ours.

**Changed:**
- Mass rename: `com.cutedifficult.spirit.FoxData` → `com.cutedifficult.spirit.KitsuneData`. 15+ files via sed.
- Split `KitsuneData` (data holder with public final fields) and `FoxStorage` (cache + NBT logic). One class one responsibility.
- Removed back-compat 6-arg constructor, replaced with `KitsuneData.of6(...)` static factory.

#### v0.9.x — Client HUD + Persistence

**Added:**
- Spirit HUD overlay (server-to-client packet flow). Replaced broken vanilla sidebar scoreboard.
- Toggle keybind (H by default, rebindable in Controls).
- Background fade at 60% opacity for readability.
- Right-center positioning.

**Fixed:**
- FoxData persistence across world reload. Hybrid cache + NBT, with try/catch around `super.readCustomDataFromNbt` for the vanilla NPE.

---

## v0.8 — The Cute Update

Soft features balancing the cruelty.

**Added:**
- **Baby kitsune (kits)** — 30% chance to spawn next to a loaded trusted (trust ≥ 50) adult kitsune.
- **Mourning** — when an adult kitsune dies, all kits within 8 blocks sit at her body and weep splash particles for 30 seconds.
- **Sleeping pose** — at night (game time 13000–23000), kitsune with trust ≥ 50 and a player nearby curl up.
- **Petting** — right-click eligible (trust ≥ 30) kitsune with empty main hand. +1 trust, 1-hour real-time cooldown per fox.
- **Name binding via name tag** — apply a vanilla-named name tag to bind a permanent custom name. Hash deterministically biases personality.
- **All hostile mobs jump intelligently** — no more standing on a fence and farming.

---

## v0.7 — Quality Over Quantity

Restored HP, shifted difficulty to gear quality.

**Added:**
- 5 quality tiers (Crude / Common / Fine / Superior / Masterwork)
- Auto-roll on first inventory entry
- Combat multipliers (attack + defense scaling)

**Changed:**
- Player max HP restored to 20 hearts
- Legacy half-HP modifier stripped on join

---

## v0.6.x — Stability & Persistence

A long bugfix sequence that produced the final architecture.

**Fixed:**
- FoxData persistence across world reloads (hybrid cache + NBT)
- Foxes attacking through walls (added LOS raycast)
- Trust gate leaking (centralized in FoxHostility.canAttack)
- Witnessed-killing override
- Bestiary GUI render layering (renderBackground override)
- Bestiary GUI opening reliability (custom Screen via packet flow)

**Added:**
- Three attack variants per element (Basic 60% / AOE 25% / Beam 15%)

---

## v0.6 — The Resonance Pivot

Rewrote blessings.

**Changed:**
- 9 parallel blessings instead of one-dominant
- Great Blessing of Inari = all 9 active simultaneously

**Added:**
- Bestiary system (Scroll of Inquiry + Bestiary of Inari)

---

## v0.5 — Social Layer

**Added:**
- Blessings (element status effects on offering milestones)
- Curses (mirror system for offended kitsune)
- Witness memory

---

## v0.4.x — Custom AI

Painful AI rewrite.

**Changed:**
- Abandoned the FoxEntityMixin approach. KitsuneEntity extends FoxEntity as a separate type.
- Custom goal set (initGoals doesn't call super).

**Fixed:**
- PrioritizedGoal.hashCode() NPE (always call setControls in constructor)
- Vanilla addTypeSpecificGoals NPE on KitsuneEntity (try/catch)

**Added:**
- Close-range dash attack (KitsuneMeleeGoal)
- Proactive aggression (FoxAggressionHandler)

---

## v0.3 — The Kitsune Birth

The most ambitious session. Built the core spiritual system from nothing.

**Added:**
- 9 elements with kami names, offerings, colors, biome affinity
- FoxData record (later renamed to KitsuneData)
- FoxPersonality with 7 hidden traits
- Tail HP scaling (1 → 10 HP, 9 → 100 HP)
- Element aura particles
- Flight for Yurei/Tengoku 5+ tails
- FoxOfferingHandler
- Per-element elemental attacks
- SpiritData scoreboard objectives
- /cd fox debug commands

---

## v0.2 — Cruelty Foundation

**Added:**
- Permanent hunger
- Charged invisible creepers
- Worst-stats horses
- Smart mobs (3× follow range, door-breaking)
- Spider abilities (pounce + web)
- Skeleton melee swap
- Sniper arrows (5%, 2× speed / 1.5× damage)
- FPV phantom drones with LOS detonation
- Furnace overheat → explosion
- Greedy chests (50% per pool)
- Greedy villagers (rising prices)
- Crafting sabotage (15% per close)
- Totem of Undying gamble
- 2.5× slower crops
- /cd debug commands

---

## v0.1 — Initial Skeleton

- Fabric mod setup (Loom 1.7-SNAPSHOT, Loader 0.16.5, Fabric API 0.102.1+1.21.1, Yarn 1.21.1+build.3, Java 21)
- Path of Peace via chat triggers
- DESIGN.md started

---

## Notes from the Journey

This mod was built in a long collaborative pair-coding session. Several recurring pitfalls worth flagging for future devs (including future me):

1. **The Compact Middle Packages trap.** IntelliJ Project View can render `assets/cutedifficult/models/item/` as `assets.cutedifficult.model.item` — three dots looking like one folder with a long name. The file system is correct; IntelliJ is just being clever. If resources mysteriously won't load — **check this setting first**. Cost of skipping: hours.

2. **A vanilla inner class shadows your import.** If you `extends` a vanilla class and write an identifier that matches a vanilla inner type — inherited types win priority. Use unique names or fully-qualified imports.

3. **`Item.use()` is unreliable.** Other UseItemCallback subscribers can intercept the chain. Logic in callback handlers, not the Item subclass.

4. **Cache + NBT is the right architecture for entity-attached data.** Pure NBT round-tripping triggers vanilla code paths (`readNbt → addTypeSpecificGoals`) that can NPE on custom subclasses. Pure in-memory cache loses data on save. The hybrid survives both.

5. **Exponential spawn bugs are surprisingly easy to write.** Always cap reinforcement chains: parent flag "has summoned", children flag "cannot summon". Test that two consecutive low-HP triggers don't compound.

6. **Spirit/Karma are orthogonal, not opposites.** High Spirit + high Karma = tragic hero. Not opposition, but the **geometry** of the Shinto ethical model.

7. **When you see weird symbol errors across thousands of lines** — check if there's an impostor class shadowing your import. See lesson #2.

8. **When commands "work" by text but have no effect** — check that underlying state actually persists. Scoreboard objectives must exist before you can set scores on them.

9. **Refactoring through `sed`** — if all errors are one pattern, mass replacement via regex is faster than file-by-file editing. The cost of a regex error is one too — but it surfaces all at once.
