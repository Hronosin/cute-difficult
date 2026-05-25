package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.entity.KitsuneEntity;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.Random;

/**
 * Element-specific passive abilities. All kitsune have these from tail 1.
 *
 * <p>Two layers:
 * <ul>
 *   <li><b>Environmental aura</b> — runs every {@link #AURA_INTERVAL} ticks.
 *       Modifies blocks/weather/light/entities in a small radius around
 *       the kitsune. Examples: Kasai melts ice, Mori speeds plant growth.</li>
 *   <li><b>Damage immunity</b> — runs synchronously via the damage event.
 *       Foxes of certain elements ignore damage from sources thematic to
 *       them (Kasai → fire, Kaminari → lightning, etc).</li>
 * </ul>
 *
 * <p>Performance: scans all FoxEntity / KitsuneEntity in each world once
 * per aura interval. The interval is 40 ticks (2 seconds) — slow enough
 * that hundreds of foxes stay cheap, fast enough that effects feel
 * immediate.
 */
public final class KitsunePassivesHandler {

    /** How often the aura tick runs. 40 ticks = 2 seconds. */
    private static final int AURA_INTERVAL = 40;
    private static long tickCounter = 0;
    private static final Random RANDOM = new Random();

    private KitsunePassivesHandler() {}

    public static void register() {
        // Environmental aura tick.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            tickCounter++;
            if (tickCounter % AURA_INTERVAL != 0) return;
            for (ServerWorld world : server.getWorlds()) {
                for (Entity entity : world.iterateEntities()) {
                    if (entity instanceof KitsuneEntity kitsune && kitsune.isAlive()) {
                        applyAura(world, kitsune);
                    }
                }
            }
        });

        // Damage immunity check.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof KitsuneEntity kitsune)) return true;
            KitsuneData data = FoxStorage.peekCache(kitsune);
            if (data == null) return true;
            return !isImmune(data.element, source);
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] KitsunePassivesHandler registered.");
    }

    /**
     * Should this kitsune ignore damage from this source based on its element?
     */
    private static boolean isImmune(Element element, DamageSource source) {
        return switch (element) {
            case KASAI -> source.isOf(DamageTypes.IN_FIRE) || source.isOf(DamageTypes.ON_FIRE)
                    || source.isOf(DamageTypes.LAVA) || source.isOf(DamageTypes.HOT_FLOOR);
            case MIZU -> source.isOf(DamageTypes.DROWN);
            case KAMINARI -> source.isOf(DamageTypes.LIGHTNING_BOLT);
            case KORI -> source.isOf(DamageTypes.FREEZE);
            case YUREI -> source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.WITHER)
                    || source.isOf(DamageTypes.INDIRECT_MAGIC);
            case TENGOKU -> source.isOf(DamageTypes.FALL) || source.isOf(DamageTypes.FLY_INTO_WALL);
            case DAICHI -> source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.STALAGMITE);
            case MORI -> false; // Mori protects via poison cleanup, not damage immunity
            case KAZE -> source.isOf(DamageTypes.FALL);
        };
    }

    private static void applyAura(ServerWorld world, KitsuneEntity fox) {
        KitsuneData data = FoxStorage.peekCache(fox);
        if (data == null) return;
        BlockPos pos = fox.getBlockPos();

        switch (data.element) {
            case KASAI -> auraKasai(world, fox, pos);
            case MIZU -> auraMizu(world, fox, pos);
            case DAICHI -> auraDaichi(world, fox, pos);
            case KAZE -> auraKaze(world, fox, pos);
            case KAMINARI -> auraKaminari(world, fox, pos);
            case MORI -> auraMori(world, fox, pos);
            case KORI -> auraKori(world, fox, pos);
            case YUREI -> auraYurei(world, fox, pos);
            case TENGOKU -> auraTengoku(world, fox, pos);
        }

        // Mori bonus: foxes immune to poison via status effect cleansing.
        if (data.element == Element.MORI && fox.hasStatusEffect(StatusEffects.POISON)) {
            fox.removeStatusEffect(StatusEffects.POISON);
        }
        // Mizu bonus: never drown.
        if (data.element == Element.MIZU) {
            fox.setAir(fox.getMaxAir());
        }
        // Tengoku bonus: emits a faint glow particle so it's visible at night.
        if (data.element == Element.TENGOKU) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    fox.getX(), fox.getY() + 0.5, fox.getZ(),
                    1, 0.2, 0.2, 0.2, 0.01);
        }
    }

    // ===== Kasai — melts ice and snow, ignites flammable blocks =====
    private static void auraKasai(ServerWorld world, KitsuneEntity fox, BlockPos center) {
        scanRadius(world, center, 6, (p, state) -> {
            if (state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE)) {
                world.setBlockState(p, Blocks.WATER.getDefaultState());
                world.spawnParticles(ParticleTypes.SMOKE, p.getX()+0.5, p.getY()+0.5, p.getZ()+0.5, 2, 0.2, 0.2, 0.2, 0.01);
            } else if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK)) {
                world.setBlockState(p, Blocks.AIR.getDefaultState());
                world.spawnParticles(ParticleTypes.SMOKE, p.getX()+0.5, p.getY()+0.5, p.getZ()+0.5, 1, 0.1, 0.1, 0.1, 0.01);
            } else if (state.isOf(Blocks.BLUE_ICE)) {
                world.setBlockState(p, Blocks.ICE.getDefaultState());
            }
        });
    }

    // ===== Mizu — extinguishes fires, hydrates farmland =====
    private static void auraMizu(ServerWorld world, KitsuneEntity fox, BlockPos center) {
        scanRadius(world, center, 6, (p, state) -> {
            if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) {
                world.setBlockState(p, Blocks.AIR.getDefaultState());
            } else if (state.isOf(Blocks.FARMLAND)) {
                // Set moisture to max.
                BlockState moistFarm = Blocks.FARMLAND.getDefaultState()
                        .with(net.minecraft.block.FarmlandBlock.MOISTURE, 7);
                if (!state.equals(moistFarm)) {
                    world.setBlockState(p, moistFarm);
                }
            }
        });
    }

    // ===== Daichi — restores natural stone (cobble → stone) =====
    private static void auraDaichi(ServerWorld world, KitsuneEntity fox, BlockPos center) {
        scanRadius(world, center, 4, (p, state) -> {
            if (state.isOf(Blocks.COBBLESTONE) && RANDOM.nextInt(20) == 0) {
                world.setBlockState(p, Blocks.STONE.getDefaultState());
            } else if (state.isOf(Blocks.DIRT) && world.getBlockState(p.up()).isAir()
                    && RANDOM.nextInt(30) == 0) {
                world.setBlockState(p, Blocks.GRASS_BLOCK.getDefaultState());
            }
        });
    }

    // ===== Kaze — light push on nearby entities, no harm =====
    private static void auraKaze(ServerWorld world, KitsuneEntity fox, BlockPos center) {
        Box box = new Box(center).expand(8);
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box, e -> e != fox)) {
            // Just a gentle outward push.
            Vec3d outward = le.getPos().subtract(fox.getPos()).normalize().multiply(0.05);
            le.addVelocity(outward.x, 0.02, outward.z);
            le.velocityModified = true;
        }
    }

    // ===== Kaminari — strike a hostile mob with mini-lightning, charge copper =====
    private static void auraKaminari(ServerWorld world, KitsuneEntity fox, BlockPos center) {
        if (RANDOM.nextInt(10) != 0) return; // 10% per aura tick = ~once per 20s avg
        Box box = new Box(center).expand(10);
        var hostiles = world.getEntitiesByClass(HostileEntity.class, box, HostileEntity::isAlive);
        if (hostiles.isEmpty()) return;
        HostileEntity target = hostiles.get(RANDOM.nextInt(hostiles.size()));
        var bolt = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.setPosition(target.getX(), target.getY(), target.getZ());
            bolt.setCosmetic(false);
            world.spawnEntity(bolt);
        }
    }

    // ===== Mori — accelerates plant growth, ignores random tick gating =====
    private static void auraMori(ServerWorld world, KitsuneEntity fox, BlockPos center) {
        net.minecraft.util.math.random.Random mcRandom = world.getRandom();
        scanRadius(world, center, 6, (p, state) -> {
            Block block = state.getBlock();
            if (block instanceof CropBlock || block instanceof SaplingBlock || block instanceof SugarCaneBlock) {
                if (RANDOM.nextInt(3) == 0) {
                    state.randomTick(world, p, mcRandom);
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                            p.getX()+0.5, p.getY()+0.7, p.getZ()+0.5, 1, 0.2, 0.2, 0.2, 0.01);
                }
            } else if (state.isOf(Blocks.SWEET_BERRY_BUSH) && RANDOM.nextInt(5) == 0) {
                state.randomTick(world, p, mcRandom);
            }
        });
    }

    // ===== Kori — freezes water into packed ice =====
    private static void auraKori(ServerWorld world, KitsuneEntity fox, BlockPos center) {
        scanRadius(world, center, 6, (p, state) -> {
            if (state.isOf(Blocks.WATER) && state.getFluidState().isStill()) {
                if (RANDOM.nextInt(8) == 0) {
                    world.setBlockState(p, Blocks.PACKED_ICE.getDefaultState());
                    world.spawnParticles(ParticleTypes.SNOWFLAKE,
                            p.getX()+0.5, p.getY()+0.7, p.getZ()+0.5, 3, 0.2, 0.1, 0.2, 0.01);
                }
            } else if (state.isOf(Blocks.FIRE)) {
                world.setBlockState(p, Blocks.AIR.getDefaultState());
            }
        });
    }

    // ===== Yurei — periodic vex-like teleport through nearby walls =====
    private static void auraYurei(ServerWorld world, KitsuneEntity fox, BlockPos center) {
        if (RANDOM.nextInt(15) != 0) return;
        // Random short teleport, ignoring walls — mimics vex/wraith feel.
        double dx = (RANDOM.nextDouble() - 0.5) * 8;
        double dz = (RANDOM.nextDouble() - 0.5) * 8;
        BlockPos target = center.add((int) dx, 0, (int) dz);
        // Find solid ground at that x/z.
        for (int dy = 4; dy >= -4; dy--) {
            BlockPos check = target.add(0, dy, 0);
            if (!world.getBlockState(check).isAir() && world.getBlockState(check.up()).isAir()
                    && world.getBlockState(check.up(2)).isAir()) {
                fox.teleport(check.getX() + 0.5, check.getY() + 1, check.getZ() + 0.5, false);
                world.spawnParticles(ParticleTypes.PORTAL,
                        fox.getX(), fox.getY() + 0.5, fox.getZ(),
                        8, 0.3, 0.5, 0.3, 0.1);
                return;
            }
        }
    }

    // ===== Tengoku — drives off phantoms, gives nearby hostiles weakness =====
    private static void auraTengoku(ServerWorld world, KitsuneEntity fox, BlockPos center) {
        Box box = new Box(center).expand(8);
        // Phantoms flee — give them levitation upward briefly so they can't dive bomb.
        for (PhantomEntity phantom : world.getEntitiesByClass(PhantomEntity.class, box, PhantomEntity::isAlive)) {
            phantom.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.LEVITATION, 60, 1, false, false, true));
            Vec3d away = phantom.getPos().subtract(fox.getPos()).normalize().multiply(0.4);
            phantom.addVelocity(away.x, 0.1, away.z);
            phantom.velocityModified = true;
        }
        // Other hostiles get Weakness from being near the heavenly fox.
        for (HostileEntity h : world.getEntitiesByClass(HostileEntity.class, box, HostileEntity::isAlive)) {
            h.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.WEAKNESS, 60, 0, false, false, true));
        }
    }

    /**
     * Iterate a cube of blocks centered on {@code center} with side
     * {@code radius * 2 + 1}, calling the visitor for each one.
     */
    private static void scanRadius(ServerWorld world, BlockPos center, int radius, BlockVisitor visitor) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = world.getBlockState(cursor);
                    if (state.isAir()) continue;
                    visitor.visit(cursor.toImmutable(), state);
                }
            }
        }
    }

    @FunctionalInterface
    private interface BlockVisitor {
        void visit(BlockPos pos, BlockState state);
    }
}