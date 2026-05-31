package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.SpecialMoon;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Random;

/**
 * Special-moon system. Each night, rolled at dusk, may become one of nine
 * {@link SpecialMoon} events — each with a paired downside and upside.
 * Admins/testers can force a specific moon via {@code /cd moon <type>}.
 *
 * <p>Roughly 45% of nights are "ordinary"; the rest roll a weighted lottery
 * among the nine moons. The chosen moon lasts the night and clears at dawn.
 */
public final class LunarCycleHandler {

    private static final Random RANDOM = new Random();

    /** The moon active tonight, or null for an ordinary night. */
    private static SpecialMoon activeMoon = null;
    /** An admin-forced moon for the NEXT dusk roll (overrides the lottery). */
    private static SpecialMoon forcedMoon = null;
    private static boolean forcedClear = false;

    private static boolean announcedTonight = false;
    private static long lastCheckedDay = -1;

    /** Chance that a night becomes special at all. */
    private static final double SPECIAL_NIGHT_CHANCE = 0.55;

    private LunarCycleHandler() {}

    // ===== Public state accessors (used by other systems) =====
    public static SpecialMoon getActiveMoon() { return activeMoon; }
    /** Public API for upcoming weather/astrology systems; not all callers exist yet. */
    @SuppressWarnings("unused")
    public static boolean isActive(SpecialMoon m) { return activeMoon == m; }
    public static boolean isBloodMoon() { return activeMoon == SpecialMoon.BLOOD; }
    public static boolean isNewMoon(ServerWorld world) { return world.getMoonPhase() == 4; }
    /** Public API for upcoming systems; not yet called internally. */
    @SuppressWarnings("unused")
    public static boolean isFullMoon(ServerWorld world) { return world.getMoonPhase() == 0; }

    // ===== Command hooks =====
    /** Force a specific moon at the next dusk (or immediately if already night). */
    public static void forceMoon(MinecraftServer server, SpecialMoon moon) {
        activeMoon = moon;
        forcedMoon = null;
        forcedClear = false;
        announcedTonight = false;
        announce(server);
    }

    public static void clearMoon() {
        activeMoon = null;
        forcedMoon = null;
        forcedClear = true;
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            if (activeMoon == null) return;
            if (!(world instanceof ServerWorld sw)) return;
            if (!sw.isNight()) return;
            if (entity instanceof HostileEntity hostile) {
                applyMoonSpawnBuffs(hostile);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) return;

            long timeOfDay = overworld.getTimeOfDay() % 24000L;
            long day = overworld.getTimeOfDay() / 24000L;

            // Dusk roll, once per night.
            if (timeOfDay >= 13000 && timeOfDay < 13100 && day != lastCheckedDay) {
                lastCheckedDay = day;
                announcedTonight = false;
                rollNight(server);
            }

            // Dawn clear.
            if (timeOfDay >= 23500 && activeMoon != null) {
                activeMoon = null;
            }

            // Per-tick ambient + ongoing effects for the active moon.
            if (activeMoon != null) {
                tickMoonAmbient(server, overworld);
            }
        });

        // Death-based loot bonuses (Blood/Pumpkin/Frost/Wolf etc.).
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            if (activeMoon == null) return;
            if (!(entity instanceof HostileEntity)) return;
            if (!(entity.getWorld() instanceof ServerWorld sw)) return;
            if (!sw.isNight()) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity)) return;
            applyMoonDeathLoot(sw, entity);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] LunarCycleHandler registered.");
    }

    private static void rollNight(MinecraftServer server) {
        if (forcedClear) {
            activeMoon = null;
            forcedClear = false;
        } else if (forcedMoon != null) {
            activeMoon = forcedMoon;
            forcedMoon = null;
        } else if (RANDOM.nextDouble() < SPECIAL_NIGHT_CHANCE) {
            activeMoon = weightedPick();
        } else {
            activeMoon = null;
        }
        if (activeMoon != null) announce(server);
    }

    private static SpecialMoon weightedPick() {
        int total = SpecialMoon.totalWeight();
        int roll = RANDOM.nextInt(total);
        int cursor = 0;
        for (SpecialMoon m : SpecialMoon.values()) {
            cursor += m.weight;
            if (roll < cursor) return m;
        }
        return SpecialMoon.BLOOD; // fallback
    }

    private static void announce(MinecraftServer server) {
        if (announcedTonight || activeMoon == null) return;
        announcedTonight = true;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.literal(activeMoon.announcement)
                .formatted(activeMoon.color, Formatting.BOLD), false);
        }
    }

    // ===== Spawn-time buffs =====
    private static void applyMoonSpawnBuffs(HostileEntity hostile) {
        switch (activeMoon) {
            case BLOOD -> {
                infinite(hostile, StatusEffects.STRENGTH, 0);
                infinite(hostile, StatusEffects.SPEED, 0);
            }
            case PUMPKIN -> {
                // Masked horrors: give them a pumpkin helmet + slight strength.
                hostile.equipStack(net.minecraft.entity.EquipmentSlot.HEAD,
                    new ItemStack(Items.CARVED_PUMPKIN));
                infinite(hostile, StatusEffects.STRENGTH, 0);
            }
            case FROST -> {
                // Slower but tougher.
                infinite(hostile, StatusEffects.SLOWNESS, 0);
                infinite(hostile, StatusEffects.RESISTANCE, 0);
                infinite(hostile, StatusEffects.HEALTH_BOOST, 1);
            }
            case CURSED -> infinite(hostile, StatusEffects.STRENGTH, 0);
            case BLUE -> infinite(hostile, StatusEffects.RESISTANCE, 0);
            case MIRROR, WOLF, HARVEST, HOLLOW -> { /* handled elsewhere / no spawn buff */ }
        }
    }

    private static void infinite(LivingEntity e, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int amp) {
        e.addStatusEffect(new StatusEffectInstance(effect, Integer.MAX_VALUE, amp, false, false, false));
    }

    // ===== Death loot =====
    private static void applyMoonDeathLoot(ServerWorld sw, net.minecraft.entity.Entity entity) {
        double x = entity.getX(), y = entity.getY() + 0.5, z = entity.getZ();
        switch (activeMoon) {
            case BLOOD -> {
                ExperienceOrbEntity.spawn(sw, entity.getPos(), 8 + RANDOM.nextInt(8));
                sw.spawnParticles(ParticleTypes.GLOW, x, y, z, 6, 0.3, 0.3, 0.3, 0.02);
            }
            case PUMPKIN -> {
                if (RANDOM.nextInt(3) == 0) {
                    dropItem(sw, x, y, z, new ItemStack(Items.SUGAR, 1 + RANDOM.nextInt(2)));
                }
                if (RANDOM.nextInt(8) == 0) {
                    dropItem(sw, x, y, z, new ItemStack(Items.PUMPKIN_PIE));
                }
                if (RANDOM.nextInt(20) == 0) {
                    dropItem(sw, x, y, z, new ItemStack(Items.GOLD_INGOT));
                }
            }
            case FROST -> {
                if (RANDOM.nextInt(4) == 0) {
                    dropItem(sw, x, y, z, new ItemStack(Items.PACKED_ICE));
                }
            }
            case WOLF -> {
                if (RANDOM.nextInt(2) == 0) {
                    dropItem(sw, x, y, z, new ItemStack(Items.BONE, 1 + RANDOM.nextInt(2)));
                }
            }
            case BLUE -> {
                ExperienceOrbEntity.spawn(sw, entity.getPos(), 15 + RANDOM.nextInt(15));
                if (RANDOM.nextInt(10) == 0) {
                    dropItem(sw, x, y, z, new ItemStack(Items.DIAMOND));
                }
            }
            case CURSED -> {
                // Cursed loot: occasional ominous bottle / echo shard.
                if (RANDOM.nextInt(15) == 0) {
                    dropItem(sw, x, y, z, new ItemStack(Items.ECHO_SHARD));
                }
            }
            case HOLLOW -> {
                if (RANDOM.nextInt(12) == 0) {
                    dropItem(sw, x, y, z, new ItemStack(Items.SOUL_SOIL));
                }
            }
            case MIRROR, HARVEST -> { /* no special death loot */ }
        }
    }

    private static void dropItem(ServerWorld sw, double x, double y, double z, ItemStack stack) {
        net.minecraft.entity.ItemEntity ie = new net.minecraft.entity.ItemEntity(sw, x, y, z, stack);
        sw.spawnEntity(ie);
    }

    // ===== Per-tick ambient & ongoing =====
    private static void tickMoonAmbient(MinecraftServer server, ServerWorld overworld) {
        long t = overworld.getTime();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getWorld() instanceof ServerWorld pw)) continue;
            if (!pw.isNight()) continue;

            switch (activeMoon) {
                case BLOOD -> {
                    // force=true guarantees the player sees them even with low
                    // particle settings; tighter spread + higher count = visible.
                    if (t % 20 == 0) pw.spawnParticles(player, ParticleTypes.DUST_PLUME, true,
                        player.getX(), player.getY() + 2.2, player.getZ(), 12, 1.5, 1.0, 1.5, 0.01);
                }
                case HARVEST -> {
                    if (t % 60 == 0) {
                        player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SATURATION, 40, 0, false, false, false));
                    }
                    if (t % 20 == 0) accelerateCrops(pw, player);
                    if (t % 20 == 0) pw.spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, true,
                        player.getX(), player.getY() + 1.5, player.getZ(), 8, 1.5, 1.0, 1.5, 0.0);
                }
                case FROST -> {
                    if (t % 40 == 0 && pw.isSkyVisible(player.getBlockPos())) {
                        player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SLOWNESS, 60, 0, false, false, false));
                        player.setFrozenTicks(Math.min(player.getFrozenTicks() + 30, 140));
                    }
                    if (t % 15 == 0) pw.spawnParticles(player, ParticleTypes.SNOWFLAKE, true,
                        player.getX(), player.getY() + 2.0, player.getZ(), 10, 1.5, 1.5, 1.5, 0.02);
                }
                case WOLF -> {
                    if (t % 30 == 0) pw.spawnParticles(player, ParticleTypes.SMOKE, true,
                        player.getX(), player.getY() + 1.0, player.getZ(), 6, 1.5, 0.5, 1.5, 0.01);
                }
                case MIRROR -> {
                    if (t % 25 == 0) pw.spawnParticles(player, ParticleTypes.GLOW, true,
                        player.getX(), player.getY() + 1.5, player.getZ(), 8, 1.5, 1.0, 1.5, 0.0);
                }
                case HOLLOW -> {
                    if (t % 60 == 0) {
                        player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.DARKNESS, 80, 0, false, false, false));
                    }
                    if (t % 15 == 0) pw.spawnParticles(player, ParticleTypes.SCULK_SOUL, true,
                        player.getX(), player.getY() + 1.5, player.getZ(), 8, 1.2, 1.2, 1.2, 0.02);
                }
                case CURSED -> {
                    if (t % 20 == 0) pw.spawnParticles(player, ParticleTypes.WITCH, true,
                        player.getX(), player.getY() + 2.0, player.getZ(), 10, 1.5, 1.2, 1.5, 0.0);
                }
                case BLUE -> {
                    if (t % 20 == 0) pw.spawnParticles(player, ParticleTypes.END_ROD, true,
                        player.getX(), player.getY() + 2.0, player.getZ(), 8, 1.5, 1.5, 1.5, 0.01);
                }
                default -> {}
            }
        }
    }

    private static void accelerateCrops(ServerWorld world, ServerPlayerEntity player) {
        net.minecraft.util.math.BlockPos center = player.getBlockPos();
        net.minecraft.util.math.random.Random mc = world.getRandom();
        int r = 5;
        net.minecraft.util.math.BlockPos.Mutable cur = new net.minecraft.util.math.BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    cur.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    var state = world.getBlockState(cur);
                    if (state.getBlock() instanceof net.minecraft.block.CropBlock && RANDOM.nextInt(4) == 0) {
                        state.randomTick(world, cur.toImmutable(), mc);
                    }
                }
            }
        }
    }
}
