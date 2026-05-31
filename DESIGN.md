# DESIGN.md

*The "why" behind Cute Difficult.*

This document is for those who want to understand what **we were trying to do**, not just what shipped. If you just want to play — read the README. If you want to fork it, or you're just curious how an idea became code — stick around.

---

## Fundamental principles

### 1. Cruelty must be **justified**, not random

Every "evil" mechanic must have a **reason in lore**. 50% of chest pools roll back not because "the mod hates you" but because **the chest spirits are greedy and take their cut**. Furnaces explode because **they get tired** (fire kami can burn out too). Creepers are invisible because in this world the environment is **actively** hostile — not just passively dangerous.

The goal — the player should **see the system**, not a bug. When they die to an invisible creeper, they should think "oh, that's the mod," not "oh, that's a glitch."

### 2. Spiritual progression runs parallel to technical

Vanilla Minecraft grants power through materials: wood → stone → iron → diamond. Cute Difficult adds a **second** track: 9 blessings of Inari through kitsune friendship. This track **doesn't replace** materials — it complements them.

This means mid-game has **two tasks**:
- Level up your armor/weapon quality (the quality system)
- Accumulate Spirit in multiple elements

Endgame is when **both** tracks are maxed. One without the other isn't enough.

### 3. The Path of Peace is an **honest** option

Not every player wants to suffer. There's `i am casual`, which disables most cruelty mechanics. This is **not** an "easy mode" with shaming — it's a **harmony mode**, where the player has chosen to live in accord with the world rather than war with it.

Kitsune are still there. Offerings still work. The dragon still waits. The passive ambient pressure is just removed.

We **don't punish** Peace mode. No shame particles. No locked content. *Okay, maybe the final boss has one secret unavailable in Peace, but that's a spoiler.*

---

## The Shinto flavor

I started building this mod with a surface-level understanding of "Japanese mythology," and gradually through **game-design intuition** accidentally reproduced an academically accurate Shinto structure:

- **Kami** — spirits of natural phenomena. For us, the 9 elemental kitsune.
- **Kegare** — ritual pollution. For us, Karma (rises from violence, especially against kitsune).
- **Misogi** — ritual purification. For us, the **future** Sacred Springs mechanic (TODO).
- **Torii** — gates marking sacred space. **Future** Shrines of Inari.
- **Jinja** — shrines. **Future** structures.

The central ethical axis of Japanese culture isn't "good vs evil" but **pure vs polluted** (jōe vs kegare). This is **exactly** why in this mod, Spirit and Karma are **separate** numbers, not opposites. A player can have high Spirit AND high Karma — a powerful kitsune-favorite with blood on their hands. That's a **tragic** figure, not a "bad guy." Geometry matters.

The kami names are real Shinto deities (Hi-no-Kagutsuchi, Suijin, etc.). The assignment of them to 9 elements is our creative interpretation. Actual Shinto has no 9-element system; "5 elements" is **Chinese** Wuxing, not Japanese.

---

## The cosmos as a system to study (v1.1)

The celestial systems — moons, weather, and especially astrology — exist to answer a design question: *what does late-game mastery look like in a mod about spirits?*

The early game is about survival and first contact. The mid game is about building trust with nine elements. But once you've befriended everyone, what's left? The answer this update gives: **the sky becomes a clock you learn to read.**

Astrology is deliberately, unapologetically **dense** — modeled after the kind of opaque, self-taught mechanics in HBM's Nuclear Tech Mod, where the reward for understanding a system is the ability to exploit it. Nothing about the orbital math is explained in-game beyond `/cd sky`. A casual player ignores it and offers blind, eating the occasional misfire. A dedicated player learns when Hoshi-Kasai conjuncts Hoshi-Kaminari, notes the date, and shows up with a stack of magma cream to farm spirit at ×2 during the tight peak of the orb.

This creates a **knowledge economy**. The information — when aspects peak, when retrograde flips them, when the Great Convergence comes — is the real endgame resource. You can't trade it; you earn it by paying attention. The hidden Rite of the Nine is the ultimate expression: a reward gated not behind a boss or a grind, but behind *noticing*.

The moons and weather are the accessible on-ramp to this mindset. They're legible at a glance (a Blood Moon announces itself), they pair downside with upside so they're decisions rather than punishments, and they teach the player that the world's state matters before astrology asks them to do trigonometry about it.

## Player Journey

### Hours 1-5: Confused suffering

The player joins, dies twice to charged invisible creepers, loses a crafting table to sabotage, the furnace explodes. They type "wtf this mod" in chat. They find a fox, take a fireball to the face, respawn near the same fox at 4 HP. They Google.

**The goal:** the player finds the README or this Design doc, realizes **it's all intentional**, and either quits or commits. We want **more** commits than rage-quits. Which means early deaths must be **legible**, not random. Every death needs a **reason** the player can point at.

### Hours 5-20: Making the deal

The player learns to feed kitsune. Builds a fox-friendly base near a savanna and farms magma cream for a Kasai kitsune they've named Akaibara. First Fire Resistance blessing. First Scroll of Inquiry, first bestiary entry.

This is when the spiritual path becomes **real**. The player has invested in a fox; they won't kill it now; they **care** about what it remembers.

### Hours 20-50: Grinding all 9

The player travels — ocean for Mizu, mountains for Daichi, Deep Dark for Yurei (scary), End for Tengoku. Each element brings a new kitsune to befriend, a new offering to source, a new blessing to unlock.

The mid-game pressure is **quality scarcity**. Common-tier is everywhere; Superior is rare; Masterwork is a celebration. Players hunt for tier through crafting volume and loot luck.

### Hours 50+: The Great Blessing

When all 9 elements have crossed the threshold — the Great Blessing of Inari fires. Chat block. Pantheon effects. Universal kitsune peace. The player has built something the world **recognizes**.

After that — the final boss (TODO — Hollow Lord, an End Dragon overhaul) and one of two endings: **Liberation** (freeing the Awakened Dragon as a companion) or **Iron Will** (refusing the easy ending).

---

## What's implemented (v1.1.0-beta)

Full list in the README. Briefly:

**Environmental cruelty:**
- All baseline mechanics (hunger, creepers, horses, mobs, furnaces, chests, villagers, totems, crafting, crops)
- Jumping hostile mobs
- Zombie grab + reinforcement (with hard-cap)
- Nether buffs (blaze fan, ghast minion, magma cube explode, brute frenzy)

**Kitsune:**
- 9 elements with unique offerings and personalities
- Trust system via offerings and petting
- Naming via name tag + hash-based personality bias
- Baby kitsune (kits) with mourning logic
- Witnesses never forget
- Sleeping pose at night

**Kitsune passives:**
- 9 environmental auras (Kasai melts, Mori grows, etc.)
- 9 damage immunities (Kasai → fire, Kaminari → lightning, etc.)

**Combat:**
- 3 attack variants per element (basic 60% / AOE 25% / beam 15%)
- Line-of-sight check for all attacks
- FoxRageHandler for retaliation under the Great Blessing

**Spirit & Blessings:**
- 9 parallel blessings (per-element)
- Great Blessing of Inari when all 9 active
- Spirit HUD with icons (right-center)
- Toggle via H keybind

**Bestiary:**
- Scroll of Inquiry + Bestiary of Inari
- Custom GUI screen
- 45 entries (9 elements × 5 tail tiers)

**Item Quality:**
- 5 tiers with weighted distribution
- Auto-roll on inventory entry
- Combat multipliers (attacker/defender)

**Enhanced Enchantments (v1.1):**
- 22 meme-named transforms, fox-applied to gear (not books), stackable markers
- Effects across attack / passive / projectile / damage hooks, scaled by level

**Tiered Offerings (v1.1):**
- cheap / standard / premium per element, with reward multipliers

**Smart Wither (v1.1):**
- 3 HP phases, leading shots, kiting, berserk teleport-dodge, guaranteed Nether Star

**Celestial systems (v1.1):**
- 9 special moons (random nightly, paired up/downside, admin commands)
- 6 weather events + moon/weather combos
- HBM-grade astrology: 9 orbital bodies, intraday motion, retrograde, decans, orb-tightness gradient, Grand Trine / Yod / Grand Alignment / Grand Cross / Great Convergence
- Hidden Rite of the Nine (Mark of the Nine reward)
- `/cd sky` ephemeris with forecast

---

## What's pending (post-release)

Roughly by priority:

1. **Shrines of Inari** — procedural structures with torii and offering basins. Passive Spirit regen on meditation.
2. **Sacred Springs** — rare water structures. Cleanse Karma.
3. **Spiritual contamination per-chunk** — kegare radiation from violence, spreads, treated by misogi.
4. **Hollow Lord** — End Dragon overhaul. 4-phase boss with cannons from subordinate towers, two ending paths.
5. **9-element compatibility matrix** — Kasai kitsune don't get along with Mizu, specific pairings give synergies.
6. **Inner alchemy / pills** — HBM-tier deep mechanic. Purified spiritual essence for tail growth on bound kitsune.
7. **Mark of Fox's Wrath** — tracked curse activating on multi-kitsune-killers.
8. **Greater Penance ritual** — clear witness memory + Mark of Wrath at heavy cost.
9. **Awakened Dragon companion** — Liberation ending at endgame.
10. **Iron Will achievement** — for players reaching the dragon without ever feeding a kitsune.
11. **Onigiri & dango** — crafted food that works with any element.
12. **Wolf Moon pack spawns & Mirror Moon damage reflection** — these two moons currently have loot/visuals but their signature effects (spawning wolf packs; reflecting damage both ways) are not yet wired.
13. **Weather → element bonus** — weather events give hazards and loot but don't yet boost their themed element's offerings (Acid Rain → Mizu, etc.).
14. **Visible tail-count rendering** — needs a render-mixin on FoxEntityRenderer.

---

## Design constraints we honored

1. **No mixin on FoxEntity.** Replaced with a KitsuneEntity subclass. Saved hours of debugging.
2. **No exponential mob spawn chains.** Zombies and ghasts can summon at most one reinforcement each, and the reinforcement is permanently marked as un-summonable. Hard cap.
3. **No client/server-split bugs.** Every multiplayer-sensitive feature uses proper packet flow (custom payloads, Server*Events).
4. **Persistence is correct.** FoxData (now KitsuneData) lives in entity NBT, round-tripped on save/load, in-memory cache as fast read path. After world reload, fox memory survives.

## Design constraints that bit us

1. **`net.minecraft.entity.passive.FoxEntity` has an inner class `FoxData`.** When our KitsuneEntity extends FoxEntity, Java auto-resolves "FoxData" to the **vanilla** inner type, not ours (inherited types win priority over imports). Cost **hours** of dev session pain. We renamed ours to `KitsuneData`.
2. **`UseItemCallback` order is fragile.** Multiple subscribers can step on each other.
3. **`Item.use()` is unreliable** in mods with active UseItemCallback subscribers. Move logic to callback handlers, not Item subclass.
4. **`PrioritizedGoal.hashCode()` NPEs** if a Goal subclass didn't call `setControls()` in its constructor. Always call it, even with `EnumSet.noneOf(Control.class)`.
5. **Vanilla `addTypeSpecificGoals` NPEs** on our KitsuneEntity subclass. Wrapped in try/catch in `readCustomDataFromNbt`.
6. **Compact Middle Packages in IntelliJ** — renders `assets/cutedifficult/lang/` as `assets.cutedifficult.lang` (dotted), looks like one package, actually nested folders. If resources mysteriously won't load — **check this first**. Cost hours.
7. **Race condition in `/cd fox summon`** — vanilla FoxEntity was spawning, then getting instantly replaced by KitsuneEntity with random data through FoxSpawnHandler. Fix: spawn KitsuneEntity directly, bypassing vanilla.
8. **`SpiritData.set` silently no-ops** if the scoreboard objective doesn't exist. There must be an `ensureObjectives` call on server start. The method **existed**, nobody was calling it.

## Architectural analysis

**Cache + NBT hybrid is the right architecture for entity-attached data.**

Pure NBT round-tripping triggers vanilla read paths (`readNbt → addTypeSpecificGoals`) which can NPE on custom subclasses. Pure in-memory cache loses data on save.

Hybrid: cache as live read path, NBT only at save/load boundaries. This **survives both** pitfalls.

**Mass refactor through sed works for broad semantic changes.**

When we renamed FoxData → KitsuneData across the whole project (15+ files), a sed script with regex did it in one pass. Key: have distinct enough patterns to not catch extra stuff.

---

## Lore philosophy

The main **emotional** bet of the mod is the **witness mechanic**. It's the thing that makes the mod **humanistic** despite all its cruelty.

In normal games you kill a monster, it's dead, end of story. In Cute Difficult:
- If you have **a reputation** with one kitsune and you kill **another** in its presence — it'll remember. Forever. No amount of gifts brings it back.
- If you have a **kit** near its mom and you kill the mom — the kit **sits at her body** and weeps.

These mechanics don't make the game **harder**. They make it **heavier**. Emotionally. And that's **deliberate**.

Cute Difficult is an **exploration** of the idea that **every** action has witnesses. That even in a virtual world your victims have **families**. That a **Great Blessing** doesn't grant the **right** to impunity.

This is **rare** in games. Usually games either **completely** ignore moral implications of killing (Skyrim — civilian is sleeping, you kill, everyone's fine), or make it **black-and-white** (Dishonored — chaos meter, bad vs good). Cute Difficult is grey. The kitsune don't **judge** you. They **remember**. And they behave accordingly. And you **know** they remember.

It's a small bet. Maybe nobody will notice. But if someone does — **they'll never forget** this mod.
