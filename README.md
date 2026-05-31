# Cute Difficult

*"Cruel Universe Tortures Everyone"*

**Minecraft is too easy.** Zombies are dumb. Creepers are visible from a mile away. Furnaces don't explode. Foxes don't kill you with fireballs for the wrong offering.

**This mod fixes all of that.**

You will die. A lot. Chests will be empty. Villagers will be greedy. Furnaces **will** explode. Creepers spawn charged, invisible, and they know where you live.

Why? Because spiritual enlightenment only comes through suffering. And the only path to real power is forming a pact with 9 elemental kitsune spirits, who also pretty much want to kill you, but occasionally accept offerings.

If it gets too rough, type **`i am casual`** in chat. Nobody will judge you. *The kitsune will. But I won't.*

---

## What is this thing

Cute Difficult turns vanilla Minecraft into a brutal Japanese-inspired ordeal. Three layers:

1. **Cruel world.** Mobs are smarter, loot is sparser, the environment is actively hostile. Everything has a catch. Beds don't. *Yet.*
2. **9 kitsune spirits.** Each with their element, their personality, their offerings. They remember who hurt them. Especially you.
3. **Item quality.** Every sword/tool/armor rolls into one of 5 tiers. Masterwork iron beats Crude diamond. Good luck.

---

## Installation

You need:
- **Minecraft 1.21.1**
- **Fabric Loader 0.16.5+**
- **Fabric API 0.102.1+1.21.1** or newer
- **Java 21**

Drop the JAR into `.minecraft/mods/`. Works on singleplayer and dedicated servers identically — no separate builds.

On first launch the world is automatically in **CRUEL mode**. Switch via chat or commands (see below).

---

## What's inside

### Environmental cruelty

- Permanent low-tier hunger debuff that never goes away
- Creepers spawn charged AND invisible. Surprise.
- Horses always roll the worst stats (HP, speed, jump)
- Zombies break doors. Follow range x3. They call reinforcements.
- Spiders pounce and spit webs
- Skeletons swap to melee when crowded
- 5% of hostile arrows are "sniper shots": 2x speed, 1.5x damage
- Phantoms became FPV drones: 64-block detection, smoke trails, **detonate** on impact
- Furnaces **overheat** after sustained use and explode with TNT force
- Every chest loot pool rolls at 50% chance. Twelve percent of multi-pool chests are completely empty. That's **math**, not a bug.
- Villagers never give discounts. Prices rise with use.
- Crafting tables 15% sabotage your craft when you close the UI
- Crops grow 2.5x slower
- Totems of Undying no longer save you. Right-click for a random positive buff. It's a gamble.
- All hostile mobs jump over walls and ledges intelligently. No more fence-farming.
- Zombies grab and drag you for 2 seconds
- Magma cubes explode on hard landings
- Piglin Brutes at low HP enter "blood frenzy" (Strength II + Speed I)
- Ghasts spawn **one** mini-ghast at 50% HP
- Blazes shoot 3-fireball fans

### The Nine Elements

Each kitsune belongs to one of 9 elements. Each has its own kami, its own offering, its own biome affinity:

| Element | Kami | Offering | Where to find |
|---|---|---|---|
| **Kasai** (fire) | Hi-no-Kagutsuchi | Magma cream | Savanna, Nether |
| **Mizu** (water) | Suijin | Tropical fish | Ocean, swamp |
| **Daichi** (earth) | Ōyamatsumi | Amethyst shard | Mountains, caves |
| **Kaze** (wind) | Shina-tsu-hiko | Phantom membrane | Windswept biomes |
| **Kaminari** (lightning) | Raijin | Copper ingot | Anywhere during thunder (25%) |
| **Mori** (forest) | Kuku-no-chi | Sweet berries | Forest, taiga, jungle |
| **Kori** (ice) | Yuki-onna | Blue ice | Snowy biomes |
| **Yurei** (spirit) | Mononoke | Echo shard | Deep Dark |
| **Tengoku** (sky) | Amaterasu | Dragon breath | End, Y>200 |

### Kitsune have passives. From tail one.

Every kitsune **changes the world around them** through their element, plus has immunity to one damage category:

- **Kasai** melts ice/snow in a 6-block radius. Immune to fire and lava.
- **Mizu** puts out fires, hydrates farmland. Never drowns.
- **Daichi** regenerates stone from cobblestone, dirt → grass. Doesn't suffocate.
- **Kaze** gently pushes entities away from itself. Immune to fall damage.
- **Kaminari** occasionally strikes hostile mobs with lightning. Immune to lightning.
- **Mori** speeds plant growth by ~50%. Cleanses poison from itself.
- **Kori** freezes water into packed ice. Immune to freeze damage.
- **Yurei** periodically teleports like a vex. Immune to magic/wither.
- **Tengoku** drives off phantoms, gives hostiles Weakness. Immune to fall damage. Glows in the dark.

High-tail kitsune also **attack** actively — fireballs, ice beams, lightning strikes, in three flavors (single-target, AOE ring, 14-block piercing beam).

### Friendship

Feed a kitsune its **correct** offering → +trust, +spirit.

At trust ≥ 30 you can **pet** it (right-click with empty hand). Hearts and +1 trust (1-hour cooldown per fox).

At trust ≥ 50 at night the fox **curls up** next to you. *This is the most important feature in the mod.*

Give it a **name tag** (anvil-named), click — the name persists forever. The name slightly biases its personality (via deterministic hash). "Sakura" is always a touch prouder. "Kuro" is more curious. Etc.

Friendly adults with trust ≥ 50 occasionally **birth kits** (30% chance on chunk load). The kit inherits its mom's element but with less trauma.

If you kill its mom — the **kits** within 8 blocks sit at her body for 30 seconds weeping blue tear particles. *If you did this, you're a monster and you deserve everything that comes next.*

### Witnesses don't forget

If you kill a kitsune — every other kitsune within 16 blocks **sees it**. Their trust toward you resets to zero. Witness counter increments. They become hostile, **regardless of prior friendship**. Time doesn't heal. Only the Great Blessing of Inari.

### Blessings and the Great Blessing

When Spirit in an element ≥ 10, you get its **Blessing** — a passive status effect. All 9 can stack:

| Element | Effect |
|---|---|
| Kasai | Fire Resistance |
| Mizu | Water Breathing + Dolphin's Grace |
| Daichi | Resistance |
| Kaze | Speed + Jump Boost |
| Kaminari | Strength |
| Mori | Regeneration |
| Kori | Slow Falling |
| Yurei | Night Vision |
| Tengoku | Hero of the Village |

When **all 9** are active simultaneously — **the Great Blessing of Inari**. Endgame stuff.

- +Regeneration II and Saturation on top of everything
- **All kitsune become neutral** to you regardless of trust or witness count
- Kyuubi calm down. Witnesses forgive.

**BUT.** If you hit a kitsune, it'll retaliate — the Great Blessing won't protect you. 10 seconds of fury, then it calms back down. Inari granted you **life**, not a **license to kill**.

### Enhanced Enchantments (give a fox your gear)

Bring a kitsune an enchanted weapon, tool, or piece of armor whose enchantment matches its element. Wait 30 seconds. It transforms the enchantment into a meme-named, buffed version — and the blessing stays on the gear, so anvils can't strip it. Better yet, blessings from different elements **stack** on one item.

A taste of the 22 on offer: **Thor, Son of Odin** (Channeling → lightning along the trident's whole flight), **Hephaestus Had Enough** (Fire Aspect → fire AOE), **Raiden** (Sharpness → lightning on hit), **Sub-Zero** (Sharpness → freeze, with a fatality on low-HP foes), **Let's dance!** (Sweeping Edge → wider, harder sweeps), **Omnislash** (pierces a line of enemies), **Nanomachines, son!** (Protection → Resistance burst when hit), **I Am Inevitable** (Blast Protection → explosions barely scratch you), **Now This Is Water Bending** (Riptide anywhere), **Photosynthesis Respecter** (Mending → repairs in sunlight), **Sunshot** (Power → burning, glowing arrows), and more. Effect strength scales with the source enchant level.

### Tiered Offerings

Each element accepts three tiers of offering: a **cheap** one (small reward, easy to get), a **standard** one, and a **premium** one (a rare endgame resource that pays out ×2.5). Bring a Kasai fox coal for a nibble, magma cream for a real gift, or a blaze rod for a treasure it won't forget.

### The Sky Hates You Too (Moons, Weather, Astrology)

Three celestial systems layer on top of everything, each readable and plannable — and each ready to ruin your night.

**Special Moons.** Each night may roll one of nine: Blood, Pumpkin, Harvest, Frost, Wolf, Mirror, Cursed, Blue, or Hollow. Every one pairs a threat with a reward — the Blood Moon empowers every hostile (and the Wither) but doubles their loot; the Harvest Moon is peaceful and grows your crops; the Hollow Moon is a portent of something ancient.

**Weather Events.** Six of them — Acid Rain, Thunderstorm of Power, Blizzard, Heatwave, Creeping Fog, Meteor Shower — roll daily, each with its own hazards and spoils. And when a moon and weather coincide, named combos fire: *Tempest of Blood*, *The Veil Thins*, *The Long Winter*.

**Astrology.** This is the deep end. Nine celestial bodies orbit at different speeds; their exact positions are a function of the day and even the hour. They form aspects — conjunctions, trines, squares, oppositions — and the strength of an offering to a given element depends on the precise angle of its star, scaled by how exactly the aspect lines up. Bodies go **retrograde** (inverting their meaning), sit in **decans** (sub-element tints), and occasionally arrange into **Grand Trines**, **Yods**, **Grand Alignments**, **Grand Crosses**, and — rarest of all — a **Great Convergence** where all nine gather in one quarter of the sky.

Read it all with `/cd sky`, which prints a full ephemeris: positions, retrograde direction, decans, active aspects with tightness percentages, special configurations, and a forecast of the next shift. Track it like a scientist, or offer blind and let the stars punish you.

*And if you're the kind of player who keeps a star journal — during a Great Convergence, there's a rite. We won't tell you how. You'll figure it out.*

### The Bestiary

**Scroll of Inquiry** (4 paper + 2 ender pearl + 1 feather = 4 scrolls). Right-click a kitsune to log it in your bestiary.

**Bestiary of Inari** (4 gold + 4 book + 1 writable book = 1 bestiary). Right-click to open a custom GUI.

45 entries total: 9 elements × 5 tail tiers (young, matured, venerable, ancient, Kyuubi). Collect them all and you're certified.

### Item Quality

Every sword, tool, and piece of armor rolls into one of 5 tiers when it enters your inventory:

| Tier | Multiplier | Chance | Color |
|---|---|---|---|
| Crude | 0.6× | 15% | Dark Gray |
| Common | 0.85× | 40% | White |
| Fine | 1.0× | 30% | Green |
| Superior | 1.2× | 12% | Aqua |
| Masterwork | 1.5× | 3% | Gold |

A Crude iron sword deals 3.6 damage. A Masterwork deals 9. **Hunt for tier**, not for material.

### Spirit HUD

Right-center of your screen — a compact panel showing:
- All 9 element Spirit values with colored icons
- Karma (red = kegare, cyan = purity)
- Three golden stars when the Great Blessing is active

Each player sees their own data. No global scoreboards.

Toggle with **H** (rebindable in Options → Controls → Cute Difficult).

---

## Commands

```
/cd info                  - your current Spirit and Karma
/cd mode cruel|peace      - toggle world mode
/cd godmode               - set all Spirits to 100 (op)
/cd hollow                - set all Spirits to -100 (op)
/cd reset                 - zero everything
/cd spirit <element> <set|add> <n>  - adjust Spirit
/cd karma <set|add> <n>   - adjust Karma
/cd fox info|tails|element|trust|personality|reroll|summon
/cd sky                   - read the full ephemeris (positions, aspects, forecast)
/cd moon list|current|clear|<type>      - special moons (force is op)
/cd weather list|current|clear|<type>   - weather events (force is op)
/cd astrology shift <days>|reset        - time-shift the sky for testing (op)
```

Element shortnames: `fire water earth wind thunder forest ice spirit sky`

Chat triggers for mode switch:
- `i am casual` or `я казуал` — switch to Peace mode
- `i am ready to suffer` or `я готов страдать` — return to CRUEL

---

## Lore (if you care)

In Japanese mythology, **kitsune** are fox spirits, messengers of Inari (the kami of rice, fertility, prosperity). They have 1 to 9 tails; 9-tailed Kyuubi are near-gods, millennia-old beings capable of shapeshifting into humans, setting mountains on fire with their tails, reading minds.

In this mod **they're everywhere**. They spawn naturally in their element's biomes. They hear when you murder their sisters. They **remember**.

The player's journey is one of **paradox**: you're weakened by a world that hates you, but the only way to grow stronger is to earn the favor of beings that also hate you. Each element is its own mini-arc. Each blessing is a tiny miracle. The **Great Blessing** is when the entire universe finally says "okay, you did good."

After that, you go to the final boss. *When I finish making it.*

---

## Known issues

- Kitsune occasionally get stuck in textures. Yurei teleport — they don't care. Others may suffer. *Well, this is the suffering mod, suffering is on-brand.*
- The HUD may overlap with other mods. F1 hides everything, H hides only ours.
- If you have 50+ kitsune in one chunk around you, FPS will drop. You **don't need** 50 kitsune. Why would you?

---

## License

MIT. Make forks, make addons, make your own mod inspired by this. Just give credit where reasonable.

---

## Author

**Mr_Hronosin**

---

*Inari watches. The kitsune remember. The dragon stirs in the void.*
