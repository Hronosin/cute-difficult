# Changelog

All notable changes to Cute Difficult, in reverse chronological order.

Versioning was informal during development. v1.0.0-beta is the **first public release**.

---

## v1.2.0 — The Hollowing Update

### A small confession first

For two whole releases, the literal face of this mod — the kitsune — could not be found anywhere in the world. We were so busy bolting on cosmic systems, enchantment economies and celestial mechanics that we, uh, forgot to actually let the foxes *spawn*. The nine-tailed spirit at the center of "a Shinto kitsune difficulty mod" was summon-only. Oops. They're back now, they spawn naturally, they grow up, and they hold grudges. Sorry, little guys. You were the whole point.

With that out of the way:

### Added — The Hollow Lord (final boss)

- A true endgame boss, summoned in the End. Drop a Nether Star among four fully-charged Respawn Anchors near the center; a black hole forms, the anchors detonate, and the Lord descends.
- Summoning **seals the exit portal** — there is no walking away from this fight.
- **Four phases**, each harder than the last: The Watching → The Hollowing → The Devouring → The Last Light. The boss bar renames and turns blood-red as it falls.
- **Telegraphed attacks, not hitscan.** Lasers paint a warning beam before they fire; lightning marks the ground before it strikes. Read the tells, dodge the death. (You asked, we listened.)
- Explosive fireballs, fire-breath cones, random hollow-curses (Wither/Poison/Slowness/Blindness), and ramming dash maneuvers.
- **Crystal turrets** — the End crystals around the arena fire on you. Destroy them to silence them, or suffer.
- A **cinematic scripted death**: the Lord implodes, ruptures the arena in an expanding shockwave ring, and unmakes itself — without ever breaking the exit portal.
- **The way home is a puzzle.** The Lord drops 2 Nether Stars and 4 End Crystals. Figuring out how to leave is on you. (Hint: you already know how End crystals and the exit portal interact. The clever interrupt the ritual. The rest fight a vanilla dragon.)
- Slaying the Hollow Lord is the **Great Purification** — see Karma below.

### Added — Kitsune finally spawn (and grow)

- Kitsune now spawn naturally across the Overworld, **their element chosen by biome**: Kori in the frozen places, Mizu in oceans and rivers, Mori in forests and taiga, Daichi in the mountains, Kasai in badlands, Kaminari on the savanna, Kaze on the open plains — with a ~15% wildcard chance, because spirits are not predictable.
- **Tails grow over time.** Young foxes are common; ancient nine-tailed Kyuubi are rare and earned. A kitsune ages on its own, and a **well-fed fox grows ~50% faster** — reach Kyuubi in roughly 10 in-game days of care, or ~15 left to fend for itself.

### Added — The Karma / Kegare system

Karma was a number that did almost nothing and could only ever go up. Now it is a full cycle of pollution and redemption, capped at 200, across four tiers (Pure → Tainted → Defiled → Cursed).

- **Consequences of a stained spirit:** passive Unluck (and Weakness when Cursed), offerings rejected 50% of the time, blessings withdrawn entirely, more hostile mobs drawn to you, and an oppressive dread in the air.
- **The kitsune turn on you.** At Defiled karma and above, every kitsune becomes hostile *regardless of trust* — even ones that loved you. Linger near them and they lay element-themed **curses** on you.
- **Redemption is only through peaceful, life-giving acts** — never more violence. Harvest mature crops (−1), breed animals (−3), trade with villagers (−5). And slaying the Hollow Lord performs a **Great Purification** (−100), washing deep stain from your spirit.

### Added — Commands

- `/cd fox stage` — a public (no-OP) command: look at a kitsune and learn its growth stage (young → matured → venerable → ancient → Kyuubi), with a little flavor.
- `/cd enchant <marker> [level]` — (OP) apply any of the custom enhanced enchantments directly to your held item, with tab-completion for every marker.

### Changed — Data storage rebuilt for safety

- Spirit and Karma no longer live in scoreboards (fragile, player-visible, wiped by commands). They now persist in proper world save data. Invisible, durable, and command-proof. Existing players start fresh — old scoreboard data was being lost across updates anyway.

### Fixed

- **Kitsune not spawning in the world.** See the confession above. Fixed.
- No more natural-growth gap: kitsune actually mature now instead of being frozen at their spawn tail count forever.
- The **"Worlds using Experimental Settings… Here be dragons!"** warning that nagged on every single world load (caused by the custom Horizon dimension) is now suppressed in-mod — no companion mod required.
- The Hollow Lord's death sequence no longer destroys the exit portal it leaves you to use.

---



The world now answers to the sky. This update adds three intertwined celestial systems, a full enhanced-enchantment economy, smarter bosses, and per-element kitsune passives — turning the spiritual layer from a side path into a science you have to study.

### Added — Kitsune Passives
- Every kitsune, from tail one, now reshapes the world around it through its element (Kasai melts ice, Mori speeds growth, Kaminari strikes hostiles, Kori freezes water, Yurei teleports, and so on).
- Each element also grants the fox immunity to its thematic damage type (Kasai → fire/lava, Kaminari → lightning, Kori → freeze, Yurei → magic/wither, etc.).

### Added — Enhanced Enchantments (22 meme-named blessings)
- Give a kitsune an enchanted weapon/tool/armor matching its element; after 30 seconds it transforms into a meme-named enhanced version with a buffed effect.
- Markers live on the gear itself (not books) and **stack** — one item can carry multiple different-element blessings.
- Effect strength scales with the source enchant level.
- Highlights: Thor Son of Odin (lightning along trident flight), Hephaestus Had Enough (fire AOE), Raiden, Sub-Zero (freeze + fatality), Let's dance! (wider sweep), Omnislash (pierce line), Nanomachines son! (Resistance on hit), I Am Inevitable (explosion negation), Sunshot (burning arrows), and more.

### Added — Tiered Offerings
- Each element now accepts three offering tiers: cheap (×0.5 reward), standard (×1.0), and premium (×2.5, an endgame resource).
- Premium offerings get special feedback and feel like treasures.

### Added — Smart Wither Boss
- Three HP-keyed phases: Artillery (leads its shots), Kiter (fan volleys + dash AOE), Berserk (teleport-dodge, omnidirectional spam, Wither Storm).
- Anti-cheese: sees through invisibility, teleports out of sniping holes.
- Guaranteed Nether Star delivery — even if the star burns in lava during the fight.
- Empowered by the Blood Moon.

### Added — Special Moons (9)
- Each night may roll a special moon: Blood, Pumpkin, Harvest, Frost, Wolf, Mirror, Cursed, Blue, Hollow — each with a paired downside and upside.
- Admin/test commands: `/cd moon list | current | clear | <type>`.

### Added — Weather Events (6)
- Acid Rain, Thunderstorm of Power, Blizzard, Heatwave, Creeping Fog, Meteor Shower — rolled daily, each with hazards and rewards.
- Moon + weather **combos**: Tempest of Blood, The Veil Thins, The Long Winter.
- Commands: `/cd weather list | current | clear | <type>`.

### Added — Astrology (HBM-grade clockwork)
- Nine celestial bodies orbit at coprime periods; positions are a deterministic function of continuous sky-time (intraday motion).
- Bodies form aspects (conjunction, sextile, square, trine, opposition) whose effect on offerings scales with orb tightness — catch the peak.
- Retrograde periods invert aspect polarity. Decans tint each body with a sub-element. Multi-body configurations: Grand Trine, Yod, Grand Alignment, Grand Cross.
- **Great Convergence** — the rarest window, when all nine bodies gather within a 40° arc.
- **The Rite of the Nine** — a hidden, undocumented ritual: during a Great Convergence, offer one premium offering of every element together to earn the Mark of the Nine (a permanent boost to all nine spirits).
- Read the sky with `/cd sky` (full ephemeris: positions, retrograde, decans, aspects with tightness, special configs, and a forecast of the next aspect shift). Test with `/cd astrology shift <days>`.

### Changed
- Bestiary GUI rebuilt as a clean scrollable list (no more book page-flip), showing tier badges and offering icons per element.
- Spirit gain now folds in lunar (new moon ×1.5) and astrological multipliers on top of trust, greed, and offering tier.

### Fixed
- Anvil no longer strips enhanced-enchant blessings — markers go straight onto gear given to the fox rather than onto books.
- Various `LivingEntity.damage` / `PlayerEntity.damage` signature corrections for 1.21.1.
- Particle visibility — special-event particles now use forced player-target delivery so they show even on low particle settings.

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
