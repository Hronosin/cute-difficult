# Changelog

История изменений Cute Difficult, в обратном хронологическом порядке.

Версионирование было неформальным во время разработки. v1.0.0-beta — **первый публичный релиз**.

---

## v1.0.0-beta — First Public Release

The mod is feature-complete enough to release. Бывают баги, бывают TODO, но **главное работает**.

### Релизное состояние
- 9 элементальных кицунэ с уникальными характерами, подношениями, и пассивками
- 9 параллельных благословений + Великое Благословение
- Trust/friendship arc с петтингом, именованием, baby kitsune
- Witness memory с propagation
- Spirit HUD с компактными иконками
- 5-тиерная quality система
- Кастомный бестиарий с GUI

### Что было сделано в v0.9.x → v1.0.0

#### v0.9.8 — Kitsune Passives Update

Все кицунэ с 1-го хвоста теперь имеют environmental ауры и element-immunities.

**Added:**
- 9 environmental ауры (Kasai melts ice, Mori speeds growth, Kaminari strikes hostiles, etc.)
- 9 damage immunities (Kasai → fire/lava, Kaminari → lightning, etc.)
- KitsunePassivesHandler — оба слоя в одном handler'е, server tick + ALLOW_DAMAGE event

#### v0.9.7 — Fox Rage + Icon HUD

**Added:**
- FoxRageHandler — кицунэ помнит игрока который её ударил, активирует retaliate на 10 секунд **даже** под Великим Благословением
- Compact icon HUD — заменили "✦ Great Blessing of Inari ✦" text который overflow'ил панель на три золотые звезды + per-element иконки (сверкающая для активных, серая точка для неактивных)

**Changed:**
- FoxHostility.canAttack теперь проверяет rage **до** Great Blessing check

#### v0.9.6 — Critical Fixes

**Fixed:**
- `/cd fox summon` спавнил неправильную лису. Race condition: спавнилось vanilla FoxEntity → моментально замещалось через FoxSpawnHandler с **случайной** стихией и **случайным** числом хвостов, перезаписывая то что игрок просил. Fix — спавнить KitsuneEntity напрямую, минуя vanilla.
- ResonanceBlessingHandler пропускал игроков в Creative. Это означало что в testing'е (всегда в Creative) благословения **никогда** не работали. Removed creative skip — только Spectator теперь пропускается.

#### v0.9.5 — Spirit System Repair

**Fixed:**
- Spirit команды (`/cd spirit X add N`) показывали правильный текст но **ничего не сохраняли**. Корневая причина: `SpiritData.ensureObjectives` существовала как метод но **никто её не вызывал**. SpiritData.set с null objective — молча no-op. Fix — добавили вызов в SERVER_STARTED.
- Игроки заходили без вызова `SpiritData.initializePlayer`. Fix — добавили в JOIN event.

#### v0.9.4 — The Great Refactor

Самая болезненная сессия в истории мода. Мы потратили **часы** пытаясь скомпилировать ошибку:
```
required: Type
found: Element,FoxPersonality,int,int,long,int,String,long
```

Оказалось — **vanilla `net.minecraft.entity.passive.FoxEntity` имеет inner class с именем `FoxData`**. Когда наш `KitsuneEntity extends FoxEntity` ссылался на `FoxData`, Java auto-resolves к vanilla inner type (наследуемые typesв имеют приоритет над импортами), не наш. Все эти "method not found" ошибки были про vanilla FoxData, не наш.

**Changed:**
- Mass rename: `com.cutedifficult.spirit.FoxData` → `com.cutedifficult.spirit.KitsuneData`. 15+ файлов через sed.
- Разделили `KitsuneData` (data holder с public final fields) и `FoxStorage` (cache + NBT logic). Один класс одна ответственность.
- Удалили back-compat 6-arg конструктор, заменили на `KitsuneData.of6(...)` static factory.

#### v0.9.x — Client HUD + Persistence

**Added:**
- Spirit HUD overlay (server-to-client packet flow). Replaced broken vanilla sidebar scoreboard.
- Toggle keybind (H by default, rebindable в Controls).
- Background fade 60% opacity для читаемости.
- Right-center positioning.

**Fixed:**
- FoxData persistence across world reload. Гибрид cache + NBT, с try/catch вокруг `super.readCustomDataFromNbt` для vanilla NPE.

---

## v0.8 — The Cute Update

Soft features balancing the cruelty.

**Added:**
- **Baby kitsune (kits)** — 30% chance to spawn next to a loaded trusted (trust ≥ 50) adult kitsune.
- **Mourning** — when an adult kitsune dies, all kits within 8 blocks sit at her body and weep splash particles for 30 seconds.
- **Sleeping pose** — at night (game time 13000–23000), kitsune with trust ≥ 50 and a player nearby curl up.
- **Petting** — right-click eligible (trust ≥ 30) kitsune with empty main hand. +1 trust, 1-hour real-time cooldown per fox.
- **Name binding via name tag** — apply vanilla-named name tag to bind a permanent custom name. Hash deterministically biases personality.
- **All hostile mobs jump intelligently** — no more standing on a fence farming.

---

## v0.7 — Quality Over Quantity

Restored HP, shifted difficulty to gear quality.

**Added:**
- 5 quality tiers (Crude / Common / Fine / Superior / Masterwork)
- Auto-roll on first inventory entry
- Combat multipliers (attack + defense scaling)

**Changed:**
- Player max HP restored to 20 hearts
- Stripped legacy half-HP modifier on join

---

## v0.6.x — Stability & Persistence

Long bugfix sequence что дала финальную архитектуру.

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
- Abandoned FoxEntityMixin approach. KitsuneEntity extends FoxEntity as separate type.
- Custom goal set (initGoals doesn't call super).

**Fixed:**
- PrioritizedGoal.hashCode() NPE (always call setControls in constructor)
- Vanilla addTypeSpecificGoals NPE on KitsuneEntity (try/catch)

**Added:**
- Close-range dash attack (KitsuneMeleeGoal)
- Proactive aggression (FoxAggressionHandler)

---

## v0.3 — The Kitsune Birth

Самая амбициозная сессия. Built the core spiritual system from nothing.

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

Этот мод был построен в долгой коллаборативной пар-кодинг сессии. Несколько повторяющихся ловушек worth flagging для будущих разработчиков (включая будущего меня):

1. **The Compact Middle Packages trap.** IntelliJ Project View может рендерить `assets/cutedifficult/models/item/` как `assets.cutedifficult.model.item` — три точки выглядят как одна папка с длинным именем. File system правильный; IntelliJ просто умничает. Если ресурсы загадочно не работают — **первым делом** проверь эту настройку. Цена пропуска: часы.

2. **Vanilla inner class shadows your import.** Если ты `extends` vanilla класс и пишешь идентификатор который совпадает с vanilla inner type — наследуемые типы выигрывают приоритет. Используй уникальные имена или fully-qualified imports.

3. **`Item.use()` ненадёжно.** Другие UseItemCallback подписчики могут перехватить chain. Логика в callback handlers, не Item subclass.

4. **Cache + NBT — правильная архитектура для entity-attached data.** Pure NBT round-trip триггерит vanilla code paths (`readNbt → addTypeSpecificGoals`) которые могут NPE на custom subclasses. Pure in-memory cache теряет данные на save. Hybrid выживает обе ловушки.

5. **Exponential spawn bugs неожиданно легко написать.** Всегда cap reinforcement chains: parent flag "has summoned", children flag "cannot summon". Test что два consecutive low-HP triggera не compound'ятся.

6. **Spirit/Karma — orthogonal, не противоположные.** Высокий Spirit + высокая Karma = трагический герой. Не оппозиция, а **геометрия** этической модели Синто.

7. **Когда видишь странные symbol errors на тысячах строк** — проверь не impostor ли class shadow'ит твой import. См. lesson #2.

8. **Когда команды "работают" текстом но не имеют эффекта** — проверь что underlying state actually persists. Scoreboard objectives must exist before you can set scores on them.

9. **Refactoring through `sed`** — если все ошибки одного pattern'а, mass replacement по regex быстрее чем file-by-file editing. Цена ошибки регулярки тоже одна — но обнаруживается одновременно.
