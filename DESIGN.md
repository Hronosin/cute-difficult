# Cute Difficult — Design Spec

This document captures the full design vision from the brainstorm. The
**MVP** column marks what ships in v0.1 vs later releases.

## Core philosophy

The mod operates on three principles:

1. **Lore-justified cruelty.** No mechanic is "just hard for hardness'
   sake." Every punishing system has a narrative reason rooted in the
   spiritual fiction (kitsune, Inari, the Hollow Dragon).
2. **Visible, transparent code.** No obfuscation. Every cruelty has a
   comment in the source explaining the design intent. Players who think
   they've found a bug should find the rationale instead.
3. **The escape hatch is itself a mechanic.** Path of Peace lets anyone
   reduce difficulty to vanilla-plus, but at the permanent cost of all
   spiritual content. It is not hidden — it is humiliating.

## Difficulty mechanics

| Mechanic | MVP | Notes |
|---|---|---|
| Half player HP | ✅ | Attribute modifier, removable for Path of Peace. |
| Permanent hunger I | ✅ | Refreshed every 5s. |
| Charged + invisible creepers | ✅ | NBT + infinite invisibility effect. |
| Harder recipes / less efficient tools | | Recipe override + tool attribute nerfs. |
| Tools rust in inventory | | Tick-based durability decay. |
| Smelting × 2 fuel + × 2 time | | Mixin into furnace tick. |
| Furnace overheat explosion | | Heat accumulator per BlockEntity; threshold → TNT. |
| Crafting table sabotage | | Random shuffle on close; small inventory chance. |
| 50% loot from chests | | Loot table modifiers (chest_loot tag). |
| Always-aggressive neutral mobs | | Brain goal injection mixin per species. |
| Wolves tame after 26 player hits | | Per-entity counter via NBT. |
| Worst-stat horses | | Spawn-time NBT rewrite. |
| Crops 250% slower | | Random tick bias on crop block tags. |
| Phantom drone behavior | | Goal replacement + explosion on contact. |
| Wither → wither skeleton projectiles | | Projectile spawn replacement. |
| Villager prices only up | | Trade demand mixin; clamp discounts to 0. |
| Totem → random spirit effect, no save | | Mixin LivingEntity#tryUseTotem. |

## Spirit system

Scoreboard objective `cd_spirit`, range −100 to +100.

| Tier | Range | Unlocks |
|---|---|---|
| Mortal | 0–15 | Default. 1–3 tail fox interaction only. |
| Awakened | 15–30 | Up to 5-tail interaction. Offering ritual usable. |
| Enlightened | 30–50 | 7-tail interaction. Yurei kitsune become visible. |
| Sage | 50–75 | Kyuubi approachable. Hidden shrines reveal. |
| One With Spirits | 75–100 | Spirit Projection (5min astral form). |
| Hollow | −1 to −100 | Dark aura; foxes flee; mobs see through walls. |

**Decay**: −1 per in-game day (passive). Implemented in MVP.

**Gain sources** (future):
- Meditation at shrine: +1 per 5 minutes real-time sitting
- 3 days without sentient kills: +1
- Correct offering to a fox: +0.5
- Sacred spring drink (weekly): +2–5
- Sleep under full moon outdoors: +1
- Visiting each elemental shrine first time: +3 each
- Full elemental shrine set bonus: +20

**Loss sources** (future):
- Kill fox: −20 immediate + spiritual contamination
- Break shrine: −50
- Kill villager: −10
- Death: −10% of current Spirit
- Wearing cursed items: −1/day
- Time in the End: −1 per 5 min
- Passive decay: −1/day **(MVP)**

## Kitsune system

Nine elements, nine tail-tiers. Each fox has:
- An element (visual: tail-particle color)
- Hidden personality stats (Pride, Trust, Curiosity, Memory, Greed,
  Magical Sensitivity, Trauma) determining preferred offerings and
  bonding curve
- A social network within ~500 blocks — actions propagate

**Tails**: invisible aging; growth gated on no fox-kills in nearby
chunks during that lifetime.

| Tails | Ability |
|---|---|
| 1 | Standard. |
| 3 | Casts kitsunebi (blue fireball at hostile mobs). |
| 5 | Teleports leaving fire trail. |
| 7 | Creates 6-block sacred zone (no hostile spawn, no hunger tick). |
| 9 (Kyuubi) | Cancels one player death/day, teleports player at low HP, |
|   | erases mob to 5 HP on gaze. **First Kyuubi only at 100% power; |
|   | each subsequent Kyuubi at 50% of previous.** |

**Hit a Kyuubi**: 8–12 kitsunebi volley, dash teleport with flame trail,
3–5 illusionary copies, "void flame form" at 50% HP (1 damage cap),
teleports away forever at 30s. Kill it: Eternal Night curse 7 days +
"Mark of Fox's Wrath" unbreakable.

### Elements

| Element | Biome bias | Offering | Blessing |
|---|---|---|---|
| Kasai (fire) | Savanna, basalt | Magma cream | Fire Resistance + kitsunebi |
| Mizu (water) | Ocean, swamp | Tropical fish | Water Breathing + swim speed |
| Daichi (earth) | Mountains, caves | Amethyst shard | Haste + fall immunity |
| Kaze (wind) | High peaks | Phoenix feather | Slow Falling + Speed |
| Kaminari (thunder) | Any in storm | Lightning-charged copper | Summon lightning |
| Mori (forest) | Jungle, dark forest | Biome-endemic flower | Crops grow normally |
| Kori (ice) | Snow taiga | Blue ice | Cold Resistance + freeze-on-hit |
| Yurei (spirit) | Near Ancient City | Echo shard | Warden-invisible + see through walls |
| Tengoku (sky) | End, Y>200 | Dragon's breath | Permanent Regen + Saturation |

## Karma & spiritual contamination

Per-chunk pollution from violence. Spreads to neighbors over time.
Half-life 30 in-game days. Spirit Compass item detects it. Cursed earth
ruins crops, taints food, can spawn Yurei mobs.

## End Dragon — Hollow Lord overhaul

Spiritual antipode of Kyuubi (−100 spirit incarnate). All foxes hate
it; while alive, fox spawns −50%, karma growth +50%, corruption patches
spread, Kyuubi wear sadness aura.

**4 phases**: aerial fire + meteor strikes → tower void-cannons + explosive
lightning → cataclysm (cursed crystals, fire ring, crumbling arena) →
manifested Hollow (transparent, only spirit-edge weapons damage, reality
glitches, void-pocket grab).

**Two finales**:
- **Brute force**: Cursed Egg drains spirit, +50 karma, Kyuubi gone 30d,
  permanent Void Scar zone.
- **Liberation** (requires 75+ Spirit + 9 element Spirit Pearls):
  Awakened Dragon Egg → companion dragon; −100 karma; Kyuubi cinematic;
  Liberator's Crown (+20 max Spirit); End stops draining Spirit.

## Path of Peace

Activated by typing `я казуал` / `i am casual` in chat. Immediate effects:

- Screen desaturates 3s, mournful chime, public chat announcement
- Difficulty drops to vanilla-plus (HP, recipes, mobs)
- Visible grey fox-icon particle above player permanently
- `[Покой]` / `[Peace]` chat prefix
- All spiritual content locked: foxes ignore, Dragon unkillable,
  villagers refuse trade

**Redemption** via `я готов страдать`: ritual phase (10 min Weakness V),
then full Cruel restored + "Scar of Doubt" (−25% all Spirit gains permanently)
and `[Возвращённый]` / `[Returned]` prefix.

**Iron Will achievement**: complete the mod 100% without ever sending the
surrender word. Golden Kitsune Mask cosmetic.

## HBM-tier future systems (post-1.0)

- Spirit splits into 9 element-specific values + Purity + Compatibility
- Constellation system (9 constellations, ritual timing requirements,
  seasonal visibility)
- Inner Alchemy (pill forging with poison-on-wrong-ratio)
- Research system (no tooltips until you fill the Codex via Scrolls of
  Inquiry)
- Per-chunk spiritual contamination spread
- Fox social network with reputation propagation

## Open questions

- Multiplayer Path of Peace: per-player or per-world? Currently world.
- Should Iron Will track chat globally or per-player?
- Should casual-mode players appear in different visual "dimension"
  (creates server-side social caste)?
- Hollow recovery quest: codify or leave to player creativity?
