package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.SpecialMoon;
import com.cutedifficult.spirit.WeatherEvent;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Random;

/**
 * Special weather system. Each day (rolled at dawn) may carry a special
 * {@link WeatherEvent}. Mirrors {@link LunarCycleHandler} in structure.
 *
 * <p>Some events naturally pair with vanilla weather (Acid Rain / Blizzard
 * want rain; Heatwave / Fog want clear), but for simplicity and admin control
 * the event is just a tag that drives effects, and we nudge vanilla weather
 * to match when we set it.
 *
 * <p>Combos: when a special moon and a special weather coincide, a named
 * combo announcement fires and effects stack (handled by both this and the
 * lunar handler running together). See {@link #checkCombo}.
 */
public final class WeatherEventHandler {

    private static final Random RANDOM = new Random();

    private static WeatherEvent active = null;
    private static WeatherEvent forced = null;
    private static boolean forcedClear = false;
    private static boolean announcedToday = false;
    private static long lastCheckedDay = -1;
    private static String lastComboAnnounced = null;

    private static final double SPECIAL_DAY_CHANCE = 0.40;

    private WeatherEventHandler() {}

    public static WeatherEvent getActive() { return active; }

    public static void forceWeather(MinecraftServer server, WeatherEvent w) {
        active = w;
        forced = null;
        forcedClear = false;
        announcedToday = false;
        nudgeVanillaWeather(server, w);
        announce(server);
    }

    public static void clearWeather(MinecraftServer server) {
        active = null;
        forced = null;
        forcedClear = true;
        ServerWorld ow = server.getOverworld();
        if (ow != null) ow.setWeather(6000, 0, false, false);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) return;

            long timeOfDay = overworld.getTimeOfDay() % 24000L;
            long day = overworld.getTimeOfDay() / 24000L;

            // Dawn roll (~1000), once per day.
            if (timeOfDay >= 1000 && timeOfDay < 1100 && day != lastCheckedDay) {
                lastCheckedDay = day;
                announcedToday = false;
                rollDay(server);
            }

            // Clear at next dawn handled by re-roll; also clear if event "expires"
            // (we let it run the full day).

            if (active != null) {
                tickWeather(server, overworld);
                checkCombo(server);
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] WeatherEventHandler registered.");
    }

    private static void rollDay(MinecraftServer server) {
        if (forcedClear) {
            active = null;
            forcedClear = false;
        } else if (forced != null) {
            active = forced;
            forced = null;
            nudgeVanillaWeather(server, active);
        } else if (RANDOM.nextDouble() < SPECIAL_DAY_CHANCE) {
            active = weightedPick();
            nudgeVanillaWeather(server, active);
        } else {
            active = null;
        }
        if (active != null) announce(server);
    }

    private static WeatherEvent weightedPick() {
        int total = WeatherEvent.totalWeight();
        int roll = RANDOM.nextInt(total);
        int cursor = 0;
        for (WeatherEvent w : WeatherEvent.values()) {
            cursor += w.weight;
            if (roll < cursor) return w;
        }
        return WeatherEvent.FOG;
    }

    /** Push vanilla weather to match the event's feel. */
    private static void nudgeVanillaWeather(MinecraftServer server, WeatherEvent w) {
        ServerWorld ow = server.getOverworld();
        if (ow == null || w == null) return;
        switch (w) {
            case ACID_RAIN, BLIZZARD -> ow.setWeather(0, 12000, true, false);
            case THUNDERSTORM -> ow.setWeather(0, 12000, true, true);
            case HEATWAVE, FOG, METEOR_SHOWER -> ow.setWeather(12000, 0, false, false);
        }
    }

    private static void announce(MinecraftServer server) {
        if (announcedToday || active == null) return;
        announcedToday = true;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal(active.announcement)
                .formatted(active.color, Formatting.BOLD), false);
        }
    }

    private static void tickWeather(MinecraftServer server, ServerWorld overworld) {
        long t = overworld.getTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getWorld() instanceof ServerWorld pw)) continue;
            boolean exposed = pw.isSkyVisible(player.getBlockPos());

            switch (active) {
                case ACID_RAIN -> {
                    if (exposed && pw.isRaining() && t % 60 == 0) {
                        player.damage(pw.getDamageSources().magic(), 1.0f);
                    }
                    if (t % 10 == 0 && exposed) pw.spawnParticles(player, ParticleTypes.SNEEZE, true,
                        player.getX(), player.getY() + 2.0, player.getZ(), 6, 1.5, 0.5, 1.5, 0.01);
                }
                case THUNDERSTORM -> {
                    // Lightning hunts the exposed: occasional strike near player.
                    if (exposed && t % 200 == 0 && RANDOM.nextInt(3) == 0) {
                        var bolt = EntityType.LIGHTNING_BOLT.create(pw);
                        if (bolt != null) {
                            double ox = (RANDOM.nextDouble() - 0.5) * 8;
                            double oz = (RANDOM.nextDouble() - 0.5) * 8;
                            bolt.setPosition(player.getX() + ox, player.getY(), player.getZ() + oz);
                            pw.spawnEntity(bolt);
                        }
                    }
                }
                case BLIZZARD -> {
                    if (exposed && t % 40 == 0) {
                        player.setFrozenTicks(Math.min(player.getFrozenTicks() + 30, 140));
                        player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SLOWNESS, 60, 0, false, false, false));
                    }
                    if (t % 8 == 0) pw.spawnParticles(player, ParticleTypes.SNOWFLAKE, true,
                        player.getX(), player.getY() + 2.0, player.getZ(), 14, 2.0, 2.0, 2.0, 0.02);
                }
                case HEATWAVE -> {
                    if (pw.isDay() && exposed && t % 60 == 0) {
                        player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.HUNGER, 80, 0, false, false, false));
                        if (!player.isTouchingWater()) {
                            player.addStatusEffect(new StatusEffectInstance(
                                StatusEffects.WEAKNESS, 80, 0, false, false, false));
                        }
                    }
                    if (t % 20 == 0 && pw.isDay()) pw.spawnParticles(player, ParticleTypes.FLAME, true,
                        player.getX(), player.getY() + 1.0, player.getZ(), 4, 1.5, 0.5, 1.5, 0.0);
                }
                case FOG -> {
                    if (t % 60 == 0) {
                        player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.DARKNESS, 100, 0, false, false, false));
                    }
                    if (t % 10 == 0) pw.spawnParticles(player, ParticleTypes.CLOUD, true,
                        player.getX(), player.getY() + 1.0, player.getZ(), 8, 3.0, 1.0, 3.0, 0.0);
                }
                case METEOR_SHOWER -> {
                    if (pw.isNight() && t % 80 == 0 && RANDOM.nextInt(2) == 0) {
                        spawnMeteor(pw, player);
                    }
                }
            }
        }
    }

    /** A meteor: cosmetic fireball streak + impact that drops ore and burns. */
    private static void spawnMeteor(ServerWorld world, ServerPlayerEntity player) {
        double ox = (RANDOM.nextDouble() - 0.5) * 16;
        double oz = (RANDOM.nextDouble() - 0.5) * 16;
        double tx = player.getX() + ox;
        double tz = player.getZ() + oz;
        BlockPos impact = BlockPos.ofFloored(tx, player.getY(), tz);
        // Find ground.
        impact = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, impact);

        // Visual streak + impact.
        world.spawnParticles(ParticleTypes.FLAME, tx, impact.getY() + 6, tz, 30, 0.5, 3.0, 0.5, 0.1);
        world.spawnParticles(ParticleTypes.EXPLOSION, tx, impact.getY() + 0.5, tz, 2, 0.5, 0.5, 0.5, 0.0);
        world.playSound(null, impact, net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
            net.minecraft.sound.SoundCategory.WEATHER, 1.0f, 0.8f);

        // Light a small fire and drop a treasure.
        var loot = switch (RANDOM.nextInt(4)) {
            case 0 -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_INGOT, 1 + RANDOM.nextInt(2));
            case 1 -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.GOLD_INGOT);
            case 2 -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.NETHERITE_SCRAP);
            default -> new net.minecraft.item.ItemStack(net.minecraft.item.Items.COAL, 2 + RANDOM.nextInt(3));
        };
        var ie = new net.minecraft.entity.ItemEntity(world, tx, impact.getY() + 1, tz, loot);
        world.spawnEntity(ie);

        // Light damage to anything at impact (so it's a real hazard).
        Box box = new Box(impact).expand(2);
        for (var le : world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, box, e -> true)) {
            le.damage(world.getDamageSources().onFire(), 4.0f);
            le.setOnFireFor(3);
        }
    }

    /** Detect named moon+weather combos and announce them once each night. */
    private static void checkCombo(MinecraftServer server) {
        SpecialMoon moon = LunarCycleHandler.getActiveMoon();
        if (moon == null) return;

        String combo = null;
        Formatting color = Formatting.WHITE;
        if (moon == SpecialMoon.BLOOD && active == WeatherEvent.THUNDERSTORM) {
            combo = "Tempest of Blood — the storm and the red moon feed each other. Pray for dawn.";
            color = Formatting.DARK_RED;
        } else if (moon == SpecialMoon.HOLLOW && active == WeatherEvent.FOG) {
            combo = "The Veil Thins — fog and the Hollow Moon. The dead are very close tonight.";
            color = Formatting.LIGHT_PURPLE;
        } else if (moon == SpecialMoon.FROST && active == WeatherEvent.BLIZZARD) {
            combo = "The Long Winter — Frost Moon over a blizzard. Nothing warm survives the night.";
            color = Formatting.AQUA;
        }

        if (combo != null && !combo.equals(lastComboAnnounced)) {
            lastComboAnnounced = combo;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.literal(combo).formatted(color, Formatting.BOLD, Formatting.ITALIC), false);
            }
        } else if (combo == null) {
            lastComboAnnounced = null;
        }
    }
}
