package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.entity.HollowLordEntity;
import com.cutedifficult.entity.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Summoning ritual for the Hollow Lord.
 *
 * <p>In the End, near the central exit portal, the player sets up at least
 * {@link #REQUIRED_ANCHORS} <b>fully-charged Respawn Anchors</b> (which do
 * nothing on their own in the End — so no vanilla dragon spawns) and drops a
 * Nether Star. The star is consumed, a black hole forms ~10 blocks above the
 * portal, collapses into a singularity over {@link #ANIM_TICKS} ticks, and the
 * Hollow Lord rises from it.
 */
public final class HollowLordRitualHandler {

    private static final int REQUIRED_ANCHORS = 4;
    private static final int ANIM_TICKS = 100; // 5 seconds of black-hole animation
    private static final BlockPos SINGULARITY = new BlockPos(0, 73, 0); // ~10 above portal

    private static long tick = 0;
    private static int animTicksLeft = 0; // >0 while the black hole is forming
    private static final java.util.List<BlockPos> ritualAnchors = new java.util.ArrayList<>();

    private HollowLordRitualHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            ServerWorld end = server.getWorld(World.END);
            if (end == null) return;

            if (animTicksLeft > 0) {
                tickAnimation(end);
                animTicksLeft--;
                if (animTicksLeft == 0) summon(end);
                return;
            }

            if (tick % 10 == 0) scanForRitual(end);
        });
        CuteDifficult.LOGGER.info("[CuteDifficult] HollowLordRitualHandler registered.");
    }

    private static void scanForRitual(ServerWorld end) {
        Box centre = new Box(-8, 50, -8, 8, 90, 8);
        var stars = end.getEntitiesByClass(ItemEntity.class, centre,
            ie -> ie.getStack().isOf(Items.NETHER_STAR));
        if (stars.isEmpty()) return;

        // Don't summon if a dragon is already present.
        Box wide = new Box(-64, 0, -64, 64, 128, 64);
        if (!end.getEntitiesByClass(EnderDragonEntity.class, wide, e -> true).isEmpty()) return;

        // Require fully-charged respawn anchors near the centre. These do
        // nothing vanilla in the End, so no ordinary dragon is triggered.
        java.util.List<BlockPos> anchors = findChargedAnchors(end);
        if (anchors.size() < REQUIRED_ANCHORS) return;

        // Ritual met — consume one star, remember the anchors, begin animation.
        ItemEntity star = stars.get(0);
        star.getStack().decrement(1);
        if (star.getStack().isEmpty()) star.discard();

        ritualAnchors.clear();
        ritualAnchors.addAll(anchors);

        animTicksLeft = ANIM_TICKS;
        end.playSound(null, SINGULARITY,
            SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 4.0f, 0.3f);
        for (var player : end.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal("The star is devoured. Something tears open above the portal...")
                .formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), false);
        }
    }

    /** Find fully-charged respawn anchors (charge == max) near the centre. */
    private static java.util.List<BlockPos> findChargedAnchors(ServerWorld end) {
        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        BlockPos.Mutable cur = new BlockPos.Mutable();
        for (int x = -8; x <= 8; x++) {
            for (int y = 50; y <= 80; y++) {
                for (int z = -8; z <= 8; z++) {
                    cur.set(x, y, z);
                    BlockState st = end.getBlockState(cur);
                    if (st.isOf(Blocks.RESPAWN_ANCHOR)
                        && st.get(RespawnAnchorBlock.CHARGES) >= RespawnAnchorBlock.MAX_CHARGES) {
                        found.add(cur.toImmutable());
                    }
                }
            }
        }
        return found;
    }

    /** Black hole forming: an inward spiral collapsing into a dense singularity. */
    private static void tickAnimation(ServerWorld end) {
        double cx = SINGULARITY.getX() + 0.5;
        double cy = SINGULARITY.getY() + 0.5;
        double cz = SINGULARITY.getZ() + 0.5;

        // Progress 0..1 as the animation proceeds (0 at start, 1 at collapse).
        double progress = 1.0 - (animTicksLeft / (double) ANIM_TICKS);
        // Radius shrinks as it collapses.
        double radius = 6.0 * (1.0 - progress) + 0.5;

        double phase = tick * 0.4;
        int arms = 24;
        for (int i = 0; i < arms; i++) {
            double t = i / (double) arms;
            double angle = phase + t * Math.PI * 6;
            double sx = cx + Math.cos(angle) * radius;
            double sy = cy + Math.sin(t * Math.PI * 2) * radius * 0.4;
            double sz = cz + Math.sin(angle) * radius;
            Vec3d inward = new Vec3d(cx - sx, cy - sy, cz - sz).normalize().multiply(0.15 + progress * 0.2);
            end.spawnParticles(ParticleTypes.PORTAL,
                sx, sy, sz, 0, inward.x, inward.y, inward.z, 1.0);
        }
        // Dark dense core grows as it collapses.
        int coreCount = (int) (4 + progress * 20);
        end.spawnParticles(ParticleTypes.SQUID_INK, cx, cy, cz, coreCount, 0.3, 0.3, 0.3, 0.0);
        end.spawnParticles(ParticleTypes.REVERSE_PORTAL, cx, cy, cz, (int)(2 + progress * 8),
            0.5, 0.5, 0.5, 0.02);

        // Rising hum.
        if (tick % 10 == 0) {
            float pitch = 0.3f + (float) progress * 0.7f;
            end.playSound(null, SINGULARITY,
                SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.HOSTILE, 2.0f, pitch);
        }

        // Shrinking warning rings over each anchor — radius collapses toward
        // detonation, giving players a fair "get clear" cue.
        double ringRadius = 2.5 * (1.0 - progress) + 0.2;
        for (BlockPos anchor : ritualAnchors) {
            double ax = anchor.getX() + 0.5;
            double ay = anchor.getY() + 1.1;
            double az = anchor.getZ() + 0.5;
            int pts = 20;
            for (int i = 0; i < pts; i++) {
                double a = (i / (double) pts) * Math.PI * 2;
                double rx = ax + Math.cos(a) * ringRadius;
                double rz = az + Math.sin(a) * ringRadius;
                end.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, rx, ay, rz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void summon(ServerWorld end) {
        // Collapse burst.
        double cx = SINGULARITY.getX() + 0.5, cy = SINGULARITY.getY() + 0.5, cz = SINGULARITY.getZ() + 0.5;
        end.spawnParticles(ParticleTypes.SCULK_SOUL, cx, cy, cz, 200, 1.0, 1.0, 1.0, 0.3);
        end.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 1, 0, 0, 0, 0);
        end.playSound(null, SINGULARITY,
            SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 4.0f, 0.4f);

        HollowLordEntity boss = ModEntities.HOLLOW_LORD.create(end);
        if (boss == null) {
            CuteDifficult.LOGGER.warn("[CuteDifficult] Failed to create Hollow Lord entity.");
            return;
        }
        boss.refreshPositionAndAngles(cx, cy, cz, 0, 0);
        boss.grantSpawnInvulnerability(60); // 3 seconds — can't be cheesed on arrival
        end.spawnEntity(boss);

        // Rebuild the obsidian pillars' End crystals (they heal the boss, like a
        // vanilla respawn) and seal the exit portal so there's no easy escape.
        regenerateCrystals(end);
        sealExitPortal(end);

        // Detonate the anchors — they gave their energy to the Lord.
        for (BlockPos anchor : ritualAnchors) {
            end.removeBlock(anchor, false);
            end.createExplosion(null,
                anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5,
                5.0f, false, World.ExplosionSourceType.BLOCK);
        }
        ritualAnchors.clear();

        for (var player : end.getServer().getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal("From the hollow between stars, the Lord descends.")
                .formatted(Formatting.DARK_PURPLE, Formatting.BOLD), false);
        }
        CuteDifficult.LOGGER.info("[CuteDifficult] The Hollow Lord has been summoned.");
    }

    /** Place End crystals atop the obsidian pillars, as a vanilla respawn does. */
    private static void regenerateCrystals(ServerWorld end) {
        // The ten obsidian pillars sit on a ~43-block-radius ring around (0,0).
        // We approximate by placing crystals on a ring; vanilla snaps them to
        // pillar tops, but a ring of crystals reads correctly and heals the boss.
        int count = 10;
        double radius = 43.0;
        for (int i = 0; i < count; i++) {
            double angle = (i / (double) count) * Math.PI * 2;
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);
            // Find the pillar top via heightmap.
            BlockPos top = end.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
                new BlockPos(x, 0, z));
            net.minecraft.entity.decoration.EndCrystalEntity crystal =
                new net.minecraft.entity.decoration.EndCrystalEntity(end,
                    x + 0.5, top.getY() + 1, z + 0.5);
            crystal.setShowBottom(true);
            end.spawnEntity(crystal);
        }
    }

    /** Seal the exit portal so non-bedrock portal blocks are deactivated. */
    private static void sealExitPortal(ServerWorld end) {
        BlockPos.Mutable cur = new BlockPos.Mutable();
        int broken = 0;
        // Search a generous box around the central exit-portal location. The
        // portal blocks sit near y=64 but we scan wide to be safe.
        for (int x = -6; x <= 6; x++) {
            for (int y = 55; y <= 75; y++) {
                for (int z = -6; z <= 6; z++) {
                    cur.set(x, y, z);
                    if (end.getBlockState(cur).isOf(Blocks.END_PORTAL)) {
                        end.setBlockState(cur, Blocks.AIR.getDefaultState());
                        broken++;
                    }
                }
            }
        }
        com.cutedifficult.CuteDifficult.LOGGER.info(
            "[CuteDifficult] sealExitPortal: removed {} END_PORTAL blocks.", broken);
    }
}
