package com.cutedifficult.command;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.FoxData;
import com.cutedifficult.spirit.FoxPersonality;
import com.cutedifficult.spirit.SpiritData;
import com.cutedifficult.util.DifficultyMode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Random;

/**
 * Comprehensive debug command tree for v0.3.0+.
 *
 * <p>Top-level: {@code /cd}. Subtrees:
 * <ul>
 *   <li>{@code info} — show player spirit matrix.</li>
 *   <li>{@code spirit &lt;element&gt; [set|add] &lt;n&gt;} — per-element manipulation.</li>
 *   <li>{@code karma [set|add] &lt;n&gt;} — karma manipulation.</li>
 *   <li>{@code reset} — reset player to defaults.</li>
 *   <li>{@code godmode} — max all elements.</li>
 *   <li>{@code hollow} — set all elements to -100.</li>
 *   <li>{@code mode &lt;cruel|peace&gt;} — force mode switch.</li>
 *   <li>{@code fox} — nearest-fox manipulation subtree (info, tails, element,
 *       trust, personality, reroll, summon).</li>
 * </ul>
 *
 * <p>Most commands require permission level 2 (op). Read-only commands
 * ({@code info}, {@code fox info}) are open.
 *
 * <p>Fox commands operate on the <b>nearest fox within 16 blocks</b> of
 * the caller. We could let the player target one via raycast or selector,
 * but "nearest" is the fastest dev workflow.
 */
public final class CdCommand {

    private static final double FOX_SEARCH_RADIUS = 16.0;
    private static final Random RANDOM = new Random();

    private static final SuggestionProvider<ServerCommandSource> ELEMENT_SUGGESTIONS = (ctx, builder) -> {
        for (Element e : Element.values()) builder.suggest(e.shortName());
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> PERSONALITY_TRAIT_SUGGESTIONS = (ctx, builder) -> {
        for (String t : new String[]{"pride", "trust", "curiosity", "memory", "greed", "sensitivity", "trauma"}) {
            builder.suggest(t);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> MODE_SUGGESTIONS = (ctx, builder) -> {
        builder.suggest("cruel");
        builder.suggest("peace");
        return builder.buildFuture();
    };

    private CdCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("cd")
                .then(CommandManager.literal("info").executes(CdCommand::showPlayerInfo))

                // /cd spirit <element> [show | set <n> | add <n>]
                .then(CommandManager.literal("spirit")
                    .then(CommandManager.argument("element", StringArgumentType.word())
                        .suggests(ELEMENT_SUGGESTIONS)
                        .executes(ctx -> {
                            Element el = resolveElement(ctx.getSource(), StringArgumentType.getString(ctx, "element"));
                            return el == null ? 0 : showElement(ctx.getSource(), el);
                        })
                        .then(CommandManager.literal("set").requires(op())
                            .then(CommandManager.argument("value", IntegerArgumentType.integer(-100, 100))
                                .executes(ctx -> {
                                    Element el = resolveElement(ctx.getSource(), StringArgumentType.getString(ctx, "element"));
                                    return el == null ? 0 : setElement(ctx.getSource(), el,
                                        IntegerArgumentType.getInteger(ctx, "value"));
                                })))
                        .then(CommandManager.literal("add").requires(op())
                            .then(CommandManager.argument("delta", IntegerArgumentType.integer(-200, 200))
                                .executes(ctx -> {
                                    Element el = resolveElement(ctx.getSource(), StringArgumentType.getString(ctx, "element"));
                                    return el == null ? 0 : addElement(ctx.getSource(), el,
                                        IntegerArgumentType.getInteger(ctx, "delta"));
                                })))))

                // /cd karma [show | set <n> | add <n>]
                .then(CommandManager.literal("karma")
                    .executes(ctx -> showKarma(ctx.getSource()))
                    .then(CommandManager.literal("set").requires(op())
                        .then(CommandManager.argument("value", IntegerArgumentType.integer())
                            .executes(ctx -> setKarma(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "value")))))
                    .then(CommandManager.literal("add").requires(op())
                        .then(CommandManager.argument("delta", IntegerArgumentType.integer())
                            .executes(ctx -> addKarma(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "delta"))))))

                // /cd reset
                .then(CommandManager.literal("reset").requires(op())
                    .executes(CdCommand::resetPlayer))

                // /cd godmode
                .then(CommandManager.literal("godmode").requires(op())
                    .executes(CdCommand::godmodePlayer))

                // /cd hollow
                .then(CommandManager.literal("hollow").requires(op())
                    .executes(CdCommand::hollowPlayer))

                // /cd mode <cruel|peace>
                .then(CommandManager.literal("mode").requires(op())
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests(MODE_SUGGESTIONS)
                        .executes(ctx -> setMode(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))

                // /cd fox <info | tails N | element X | trust N | personality T N | reroll | summon X N>
                .then(CommandManager.literal("fox")
                    .then(CommandManager.literal("info").executes(CdCommand::foxInfo))
                    .then(CommandManager.literal("tails").requires(op())
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, FoxData.MAX_TAILS))
                            .executes(ctx -> foxSetTails(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                    .then(CommandManager.literal("element").requires(op())
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .suggests(ELEMENT_SUGGESTIONS)
                            .executes(ctx -> foxSetElement(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                    .then(CommandManager.literal("trust").requires(op())
                        .then(CommandManager.argument("value", IntegerArgumentType.integer(0, 100))
                            .executes(ctx -> foxSetTrust(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "value")))))
                    .then(CommandManager.literal("personality").requires(op())
                        .then(CommandManager.argument("trait", StringArgumentType.word())
                            .suggests(PERSONALITY_TRAIT_SUGGESTIONS)
                            .then(CommandManager.argument("value", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> foxSetPersonality(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "trait"),
                                    IntegerArgumentType.getInteger(ctx, "value"))))))
                    .then(CommandManager.literal("reroll").requires(op())
                        .executes(CdCommand::foxReroll))
                    .then(CommandManager.literal("summon").requires(op())
                        .then(CommandManager.argument("element", StringArgumentType.word())
                            .suggests(ELEMENT_SUGGESTIONS)
                            .then(CommandManager.argument("tails", IntegerArgumentType.integer(1, FoxData.MAX_TAILS))
                                .executes(ctx -> foxSummon(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "element"),
                                    IntegerArgumentType.getInteger(ctx, "tails")))))))
            );
        });
    }

    // ===== Helpers =====

    private static java.util.function.Predicate<ServerCommandSource> op() {
        return src -> src.hasPermissionLevel(2);
    }

    private static Element resolveElement(ServerCommandSource src, String name) {
        for (Element e : Element.values()) {
            if (e.shortName().equalsIgnoreCase(name) || e.name().equalsIgnoreCase(name)) return e;
        }
        src.sendError(Text.literal("Unknown element: " + name
            + ". Valid: fire, water, earth, wind, thunder, forest, ice, spirit, sky"));
        return null;
    }

    /** Find the nearest fox to the command sender within FOX_SEARCH_RADIUS. */
    private static FoxEntity findNearestFox(ServerCommandSource src) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("Must be run as a player."));
            return null;
        }
        ServerWorld world = player.getServerWorld();
        Box searchBox = player.getBoundingBox().expand(FOX_SEARCH_RADIUS);
        List<FoxEntity> nearby = world.getEntitiesByClass(FoxEntity.class, searchBox, f -> f.isAlive());
        if (nearby.isEmpty()) {
            src.sendError(Text.literal("No fox within " + (int)FOX_SEARCH_RADIUS + " blocks."));
            return null;
        }
        FoxEntity nearest = nearby.get(0);
        double bestDist = nearest.squaredDistanceTo(player);
        for (FoxEntity f : nearby) {
            double d = f.squaredDistanceTo(player);
            if (d < bestDist) {
                bestDist = d;
                nearest = f;
            }
        }
        return nearest;
    }

    // ===== Player info / spirit =====

    private static int showPlayerInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || src.getServer() == null) {
            src.sendError(Text.literal("Must be run as a player."));
            return 0;
        }
        MinecraftServer server = src.getServer();

        src.sendFeedback(() -> Text.literal("=== Spirit Matrix ===").formatted(Formatting.GOLD), false);
        for (Element e : Element.values()) {
            int v = SpiritData.get(server, player, e);
            src.sendFeedback(() -> Text.literal("  " + e.kamiName() + ": ").formatted(e.color())
                .append(Text.literal(String.valueOf(v)).formatted(Formatting.WHITE)), false);
        }
        int total = SpiritData.totalSpirit(server, player);
        int reson = SpiritData.resonance(server, player);
        double pur = SpiritData.purity(server, player);
        int karma = SpiritData.getKarma(server, player);

        src.sendFeedback(() -> Text.literal("Total: ").formatted(Formatting.GRAY)
            .append(Text.literal(total + " (" + tierFor(total) + ")").formatted(Formatting.WHITE)), false);
        src.sendFeedback(() -> Text.literal("Resonance: ").formatted(Formatting.GRAY)
            .append(Text.literal(reson + " / 9").formatted(Formatting.WHITE)), false);
        src.sendFeedback(() -> Text.literal("Purity: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.format("%.1f%%", pur * 100)).formatted(Formatting.WHITE)), false);
        src.sendFeedback(() -> Text.literal("Karma: ").formatted(Formatting.RED)
            .append(Text.literal(String.valueOf(karma)).formatted(Formatting.WHITE)), false);
        src.sendFeedback(() -> Text.literal("Mode: ").formatted(Formatting.GRAY)
            .append(Text.literal(CuteDifficult.currentMode.name()).formatted(Formatting.WHITE)), false);
        return 1;
    }

    private static int showElement(ServerCommandSource src, Element element) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || src.getServer() == null) return 0;
        int v = SpiritData.get(src.getServer(), player, element);
        src.sendFeedback(() -> Text.literal(element.kamiName() + ": " + v).formatted(element.color()), false);
        return v;
    }

    private static int setElement(ServerCommandSource src, Element element, int value) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || src.getServer() == null) return 0;
        SpiritData.set(src.getServer(), player, element, value);
        src.sendFeedback(() -> Text.literal(element.kamiName() + " = " + value).formatted(element.color()), true);
        return 1;
    }

    private static int addElement(ServerCommandSource src, Element element, int delta) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || src.getServer() == null) return 0;
        SpiritData.add(src.getServer(), player, element, delta);
        int now = SpiritData.get(src.getServer(), player, element);
        src.sendFeedback(() -> Text.literal(element.kamiName() + " "
            + (delta >= 0 ? "+" : "") + delta + " → " + now).formatted(element.color()), true);
        return 1;
    }

    // ===== Karma =====

    private static int showKarma(ServerCommandSource src) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || src.getServer() == null) return 0;
        int k = SpiritData.getKarma(src.getServer(), player);
        src.sendFeedback(() -> Text.literal("Karma: " + k).formatted(Formatting.RED), false);
        return k;
    }

    private static int setKarma(ServerCommandSource src, int value) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || src.getServer() == null) return 0;
        int current = SpiritData.getKarma(src.getServer(), player);
        SpiritData.addKarma(src.getServer(), player, value - current);
        src.sendFeedback(() -> Text.literal("Karma = " + value).formatted(Formatting.RED), true);
        return 1;
    }

    private static int addKarma(ServerCommandSource src, int delta) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || src.getServer() == null) return 0;
        SpiritData.addKarma(src.getServer(), player, delta);
        int now = SpiritData.getKarma(src.getServer(), player);
        src.sendFeedback(() -> Text.literal("Karma "
            + (delta >= 0 ? "+" : "") + delta + " → " + now).formatted(Formatting.RED), true);
        return 1;
    }

    // ===== Player bulk ops =====

    private static int resetPlayer(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || src.getServer() == null) return 0;
        for (Element e : Element.values()) SpiritData.set(src.getServer(), player, e, SpiritData.DEFAULT_PER_ELEMENT);
        SpiritData.addKarma(src.getServer(), player, -SpiritData.getKarma(src.getServer(), player));
        src.sendFeedback(() -> Text.literal("Spirit & Karma reset to defaults.").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int godmodePlayer(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || src.getServer() == null) return 0;
        for (Element e : Element.values()) SpiritData.set(src.getServer(), player, e, 100);
        src.sendFeedback(() -> Text.literal("All Spirits → 100. One With Spirits.").formatted(Formatting.GOLD), true);
        return 1;
    }

    private static int hollowPlayer(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || src.getServer() == null) return 0;
        for (Element e : Element.values()) SpiritData.set(src.getServer(), player, e, -100);
        src.sendFeedback(() -> Text.literal("All Spirits → -100. Hollow.").formatted(Formatting.DARK_PURPLE), true);
        return 1;
    }

    // ===== Mode =====

    private static int setMode(ServerCommandSource src, String name) {
        switch (name.toLowerCase()) {
            case "cruel" -> {
                CuteDifficult.currentMode = DifficultyMode.CRUEL;
                src.sendFeedback(() -> Text.literal("Mode = CRUEL").formatted(Formatting.RED), true);
            }
            case "peace" -> {
                CuteDifficult.currentMode = DifficultyMode.PATH_OF_PEACE;
                src.sendFeedback(() -> Text.literal("Mode = PATH_OF_PEACE").formatted(Formatting.GRAY), true);
            }
            default -> {
                src.sendError(Text.literal("Unknown mode: " + name + ". Use 'cruel' or 'peace'."));
                return 0;
            }
        }
        return 1;
    }

    // ===== Fox commands =====

    private static int foxInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        FoxEntity fox = findNearestFox(src);
        if (fox == null) return 0;

        FoxData data = FoxData.getOrCreate(fox, RANDOM);
        FoxPersonality p = data.personality();

        src.sendFeedback(() -> Text.literal("=== Fox " + fox.getUuid().toString().substring(0, 8)
            + " ===").formatted(Formatting.GOLD), false);
        src.sendFeedback(() -> Text.literal("Element: ").formatted(Formatting.GRAY)
            .append(Text.literal(data.element().kamiName()).formatted(data.element().color())), false);
        src.sendFeedback(() -> Text.literal("Tails: ").formatted(Formatting.GRAY)
            .append(Text.literal(data.tails() + " / " + FoxData.MAX_TAILS).formatted(Formatting.WHITE)), false);
        src.sendFeedback(() -> Text.literal("Trust: ").formatted(Formatting.GRAY)
            .append(Text.literal(data.trustLevel() + " / 100").formatted(Formatting.WHITE)), false);
        src.sendFeedback(() -> Text.literal("Personality:").formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("  Pride: " + p.pride() + "  Trust: " + p.trust()
            + "  Curiosity: " + p.curiosity()).formatted(Formatting.DARK_GRAY), false);
        src.sendFeedback(() -> Text.literal("  Memory: " + p.memory() + "  Greed: " + p.greed()
            + "  Sensitivity: " + p.sensitivity() + "  Trauma: " + p.trauma()).formatted(Formatting.DARK_GRAY), false);
        src.sendFeedback(() -> Text.literal("Last fed at tick: " + data.lastFedTickStamp()).formatted(Formatting.DARK_GRAY), false);
        return 1;
    }

    private static int foxSetTails(ServerCommandSource src, int count) {
        FoxEntity fox = findNearestFox(src);
        if (fox == null) return 0;
        FoxData data = FoxData.getOrCreate(fox, RANDOM);
        FoxData.store(fox, data.withTails(count));
        src.sendFeedback(() -> Text.literal("Fox tails = " + count).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int foxSetElement(ServerCommandSource src, String name) {
        Element el = resolveElement(src, name);
        if (el == null) return 0;
        FoxEntity fox = findNearestFox(src);
        if (fox == null) return 0;
        FoxData data = FoxData.getOrCreate(fox, RANDOM);
        FoxData updated = new FoxData(el, data.personality(), data.tails(),
            data.trustLevel(), data.lastFedTickStamp(), 0);
        FoxData.store(fox, updated);
        src.sendFeedback(() -> Text.literal("Fox element = " + el.kamiName()).formatted(el.color()), true);
        return 1;
    }

    private static int foxSetTrust(ServerCommandSource src, int value) {
        FoxEntity fox = findNearestFox(src);
        if (fox == null) return 0;
        FoxData data = FoxData.getOrCreate(fox, RANDOM);
        FoxData.store(fox, data.withTrust(value));
        src.sendFeedback(() -> Text.literal("Fox trust = " + value).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int foxSetPersonality(ServerCommandSource src, String trait, int value) {
        FoxEntity fox = findNearestFox(src);
        if (fox == null) return 0;
        FoxData data = FoxData.getOrCreate(fox, RANDOM);
        FoxPersonality p = data.personality();
        FoxPersonality updated = switch (trait.toLowerCase()) {
            case "pride" -> new FoxPersonality(value, p.trust(), p.curiosity(), p.memory(), p.greed(), p.sensitivity(), p.trauma());
            case "trust" -> new FoxPersonality(p.pride(), value, p.curiosity(), p.memory(), p.greed(), p.sensitivity(), p.trauma());
            case "curiosity" -> new FoxPersonality(p.pride(), p.trust(), value, p.memory(), p.greed(), p.sensitivity(), p.trauma());
            case "memory" -> new FoxPersonality(p.pride(), p.trust(), p.curiosity(), value, p.greed(), p.sensitivity(), p.trauma());
            case "greed" -> new FoxPersonality(p.pride(), p.trust(), p.curiosity(), p.memory(), value, p.sensitivity(), p.trauma());
            case "sensitivity" -> new FoxPersonality(p.pride(), p.trust(), p.curiosity(), p.memory(), p.greed(), value, p.trauma());
            case "trauma" -> new FoxPersonality(p.pride(), p.trust(), p.curiosity(), p.memory(), p.greed(), p.sensitivity(), value);
            default -> {
                src.sendError(Text.literal("Unknown trait: " + trait
                    + ". Valid: pride, trust, curiosity, memory, greed, sensitivity, trauma"));
                yield null;
            }
        };
        if (updated == null) return 0;
        FoxData stored = new FoxData(data.element(), updated, data.tails(),
            data.trustLevel(), data.lastFedTickStamp(), 0);
        FoxData.store(fox, stored);
        src.sendFeedback(() -> Text.literal("Fox " + trait + " = " + value).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int foxReroll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        FoxEntity fox = findNearestFox(src);
        if (fox == null) return 0;
        FoxData fresh = FoxData.generate(RANDOM);
        FoxData.store(fox, fresh);
        src.sendFeedback(() -> Text.literal("Fox rerolled: " + fresh.element().kamiName()
            + ", " + fresh.tails() + " tails").formatted(fresh.element().color()), true);
        return 1;
    }

    private static int foxSummon(ServerCommandSource src, String name, int tails) {
        Element el = resolveElement(src, name);
        if (el == null) return 0;
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("Must be run as a player."));
            return 0;
        }
        ServerWorld world = player.getServerWorld();
        com.cutedifficult.entity.KitsuneEntity kitsune =
                com.cutedifficult.entity.ModEntities.KITSUNE.create(world);
        if (kitsune == null) {
            src.sendError(Text.literal("Failed to spawn kitsune."));
            return 0;
        }
        kitsune.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch());
        FoxData data = new FoxData(el, FoxPersonality.random(RANDOM), tails, 0, 0L, 0);
        FoxData.store(kitsune, data);
        com.cutedifficult.spirit.FoxStats.applyHpForTails(kitsune, tails);
        world.spawnEntity(kitsune);
        src.sendFeedback(() -> Text.literal("Summoned " + el.kamiName() + " kitsune with "
                + tails + " tails (" + (int)com.cutedifficult.spirit.FoxStats.hpForTails(tails)
                + " HP)").formatted(el.color()), true);
        return 1;
    }

    // ===== Tier =====

    private static String tierFor(int totalSpirit) {
        if (totalSpirit < 0) return "Hollow";
        if (totalSpirit < 15) return "Mortal";
        if (totalSpirit < 50) return "Awakened";
        if (totalSpirit < 150) return "Enlightened";
        if (totalSpirit < 400) return "Sage";
        return "One With Spirits";
    }
}
