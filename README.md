# Cute Difficult

> **C**ruel **U**niverse **T**ortures **E**veryone

A Fabric mod for Minecraft **1.21.1** that turns the game into a spiritual
suffering simulator. Every cruelty has lore. Every grind has meaning. The
code is open and the philosophy is documented.

There is a Path of Peace for those who surrender. The kitsune will
remember.

## Status

**v0.1.0-alpha** — skeleton with foundation mechanics:

- [x] Half max HP for players (10 HP / 5 hearts)
- [x] Permanent low-tier hunger
- [x] All naturally loaded creepers become charged + invisible
- [x] Spirit / Karma scoreboard tracking (foundation)
- [x] Path of Peace chat activation (`я казуал` / `i am casual`)
- [x] Redemption phrase (`я готов страдать`)
- [ ] Totem-of-undying random-effect replacement
- [ ] Wither shooting wither-skeleton projectiles
- [ ] Villager prices only go up
- [ ] Furnace overheat explosion
- [ ] Crafting table sabotage
- [ ] 50% loot chests
- [ ] Hostile-by-default neutral mobs
- [ ] 26-hit wolf taming
- [ ] Worst-stat horses
- [ ] 250%-slower crop growth
- [ ] Phantom drone behavior
- [ ] Kitsune / 9-element / Spirit progression system
- [ ] End Dragon Hollow Lord overhaul

Full design spec lives in [`DESIGN.md`](./DESIGN.md).

## Build

Requirements: **JDK 21**, internet access for Gradle dependencies.

This skeleton does **not** ship a Gradle wrapper. Generate one before
the first build (or copy `gradlew`, `gradlew.bat`, and `gradle/wrapper/`
from the [fabric-example-mod](https://github.com/FabricMC/fabric-example-mod)):

```bash
gradle wrapper --gradle-version 8.10
./gradlew build
```

Output JAR appears in `build/libs/`.

## Develop

```bash
./gradlew runClient   # launches a dev Minecraft client with the mod
./gradlew runServer   # launches a dev server
```

The first invocation downloads Yarn mappings and remaps Minecraft, which
takes 1–3 minutes. Subsequent runs are fast.

## Project layout

```
src/main/java/com/cutedifficult/
├── CuteDifficult.java          # entry point — registers all handlers
├── event/                      # per-system event handlers
│   ├── ChatCommandHandler.java # Path of Peace / redemption phrases
│   ├── MobSpawnHandler.java    # charged invisible creepers
│   ├── PlayerJoinHandler.java  # half HP on join
│   └── PlayerTickHandler.java  # hunger refresh, Spirit decay
├── mixin/                      # bytecode patches (Mixin)
│   └── PlayerEntityMixin.java  # placeholder, will host totem/craft logic
├── registry/                   # (empty) future: items, blocks, entities
└── util/
    ├── DifficultyMode.java     # CRUEL vs PATH_OF_PEACE
    └── SpiritScoreboard.java   # Spirit & Karma scoreboard wrapper
```

## License

MIT. The open-source guarantee is part of the mod's design — if you think
something is broken, you can read the code and find the comment explaining
why it isn't.

---

*"The world watches you. Walk gently."*
