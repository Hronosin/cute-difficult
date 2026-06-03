package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The cinematic death of the Hollow Lord, played out over ~5 seconds as a
 * timed, multi-stage sequence (one instance per death, ticked by
 * {@link HollowLordCombatHandler}).
 *
 * <p>Stages (tick 0 → DURATION):
 * <ol>
 *   <li><b>Implosion sphere</b> — a shell of sculk/soul-fire particles
 *       collapses inward onto the death point.</li>
 *   <li><b>Rupture blast</b> — an explosion clears the old portal and arena
 *       clutter; the Lord "tears apart."</li>
 *   <li><b>Expanding ring</b> — a huge ring of sculk + soul fire races outward
 *       along the ground, breaking blocks as it goes.</li>
 *   <li><b>New portal</b> — a nine-rayed star portal forms at the centre, and
 *       the reward drops.</li>
 * </ol>
 */
public final class HollowLordDeathSequence {

    private static final int DURATION = 100; // ~5 seconds

    // Stage boundaries (ticks).
    private static final int SPHERE_END = 35;   // implosion sphere
    private static final int BLAST_AT = 36;     // rupture explosion (one-shot)
    private static final int RING_START = 40;   // expanding ring begins
    private static final int RING_END = 85;
    private static final int PORTAL_AT = 88;    // portal forms (one-shot)

    private final ServerWorld world;
    private final Vec3d center;
    private final com.cutedifficult.entity.HollowLordEntity lord;
    private int tick = 0;
    private boolean blastDone = false;
    private boolean portalDone = false;
    private boolean finished = false;

    public HollowLordDeathSequence(ServerWorld world,
                                   com.cutedifficult.entity.HollowLordEntity lord,
                                   Vec3d centerPos) {
        this.world = world;
        this.lord = lord;
        this.center = centerPos;
    }

    public boolean isFinished() { return finished; }

    /** Advance one tick of the animation. */
    public void tick() {
        if (finished) return;

        // Keep the (still-alive) Lord pinned at centre and frozen during the show.
        if (lord != null && lord.isAlive()) {
            lord.setVelocity(0, 0, 0);
            lord.velocityModified = true;
            lord.requestTeleport(center.x, center.y, center.z);
            // Fade it out near the rupture, remove it at the blast.
            if (tick >= BLAST_AT) {
                lord.discard();
            }
        }

        if (tick <= SPHERE_END) {
            implosionSphere();
        }
        if (tick == BLAST_AT && !blastDone) {
            ruptureBlast();
            blastDone = true;
        }
        if (tick >= RING_START && tick <= RING_END) {
            expandingRing();
        }
        if (tick == PORTAL_AT && !portalDone) {
            dropRewardAndCrystals();
            announce();
            portalDone = true;
        }

        tick++;
        if (tick > DURATION) finished = true;
    }

    /** Stage 1: a particle shell collapsing inward to the centre. */
    private void implosionSphere() {
        double progress = tick / (double) SPHERE_END;     // 0 → 1
        double radius = 8.0 * (1.0 - progress) + 0.5;     // shrinks inward
        int points = 60;
        for (int i = 0; i < points; i++) {
            // Distribute points on a sphere (golden-spiral-ish).
            double phi = Math.acos(1 - 2 * (i + 0.5) / points);
            double theta = Math.PI * (1 + Math.sqrt(5)) * i;
            double sx = center.x + radius * Math.sin(phi) * Math.cos(theta);
            double sy = center.y + radius * Math.cos(phi);
            double sz = center.z + radius * Math.sin(phi) * Math.sin(theta);
            particle(ParticleTypes.SCULK_SOUL, sx, sy, sz, 1, 0, 0, 0, 0.0);
            if (i % 3 == 0) {
                particle(ParticleTypes.SOUL_FIRE_FLAME, sx, sy, sz, 1, 0, 0, 0, 0.0);
            }
        }
        if (tick % 8 == 0) {
            playAt(world, BlockPos.ofFloored(center),
                SoundEvents.BLOCK_BEACON_AMBIENT, 2.0f, 0.4f);
        }
    }

    /** Stage 2: the rupture — explosion clears old portal/clutter. */
    private void ruptureBlast() {
        particle(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 3, 2, 2, 2, 0);
        particle(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, 300, 3, 3, 3, 0.5);
        playAt(world, BlockPos.ofFloored(center),
            SoundEvents.ENTITY_ENDER_DRAGON_DEATH, 5.0f, 0.5f);
        playAt(world, BlockPos.ofFloored(center),
            SoundEvents.ENTITY_GENERIC_EXPLODE, 5.0f, 0.6f);

        // Clear surrounding clutter (ritual obsidian etc.) but NEVER touch the
        // exit portal or the bedrock frame — the player needs the vanilla portal
        // intact to leave.
        BlockPos c = BlockPos.ofFloored(center);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -8; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos p = c.add(dx, dy, dz);
                    var st = world.getBlockState(p);
                    if (st.isOf(Blocks.END_PORTAL) || st.isOf(Blocks.BEDROCK)) continue;
                    world.setBlockState(p, Blocks.AIR.getDefaultState());
                }
            }
        }
    }

    /** Stage 3: an expanding ground ring of sculk + soul fire that breaks blocks. */
    private void expandingRing() {
        double t = (tick - RING_START) / (double) (RING_END - RING_START); // 0 → 1
        double radius = 4 + t * 30; // grows from 4 to ~34 blocks
        double groundY = 63; // platform level

        int points = (int) (radius * 6);
        for (int i = 0; i < points; i++) {
            double a = (i / (double) points) * Math.PI * 2;
            double rx = center.x + Math.cos(a) * radius;
            double rz = center.z + Math.sin(a) * radius;
            particle(ParticleTypes.SOUL_FIRE_FLAME, rx, groundY + 1, rz, 1, 0, 0.1, 0, 0.01);
            if (i % 2 == 0) {
                particle(ParticleTypes.SCULK_CHARGE_POP, rx, groundY + 1, rz, 1, 0, 0, 0, 0.0);
            }

            // Break the surface block at the ring edge for a shockwave feel.
            BlockPos gp = BlockPos.ofFloored(rx, groundY, rz);
            var state = world.getBlockState(gp);
            if (!state.isAir() && state.getHardness(world, gp) >= 0
                && !state.isOf(Blocks.BEDROCK) && !state.isOf(Blocks.END_PORTAL)) {
                world.breakBlock(gp, false);
            }
        }
        if (tick % 6 == 0) {
            playAt(world, BlockPos.ofFloored(center),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.7f);
        }
    }

    /** Stage 4: drop the reward (Nether Stars) and 4 End crystals so the player
     *  can restore the exit portal themselves via the vanilla mechanic —
     *  keeping the normal dragon-respawn cycle intact (no custom portal that
     *  would clobber it). */
    private void dropRewardAndCrystals() {
        double cy = 67;
        // Reward: 2 Nether Stars.
        world.spawnEntity(new ItemEntity(world, 0.5, cy, 0.5,
            new ItemStack(Items.NETHER_STAR, 2)));
        // Exit kit: 4 End crystals.
        world.spawnEntity(new ItemEntity(world, 0.5, cy, 0.5,
            new ItemStack(Items.END_CRYSTAL, 4)));

        particle(ParticleTypes.TOTEM_OF_UNDYING, 0.5, cy + 1, 0.5, 150, 4, 2, 4, 0.4);
        particle(ParticleTypes.REVERSE_PORTAL, 0.5, cy + 1, 0.5, 120, 3, 2, 3, 0.3);
        playAt(world, BlockPos.ofFloored(0.5, cy, 0.5),
            SoundEvents.BLOCK_END_PORTAL_SPAWN, 4.0f, 0.8f);
    }

    /**
     * Spawn particles via the player-targeted force=true overload for every
     * nearby player. The broadcast overload is unreliable on default particle
     * settings (this bit us with moons and the cosmos), so force them.
     */
    private void particle(net.minecraft.particle.ParticleEffect effect,
                          double x, double y, double z, int count,
                          double dx, double dy, double dz, double speed) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(x, y, z) > 128 * 128) continue;
            world.spawnParticles(p, effect, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    private void announce() {
        for (ServerPlayerEntity p : world.getPlayers()) {
            p.sendMessage(Text.literal("The Hollow Lord is unmade. Use the crystals to reopen the way home.")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);
            // Great purification: −100 karma for players who were in the fight.
            if (p.squaredDistanceTo(center.x, center.y, center.z) < 200 * 200) {
                RedemptionHandler.greatPurification(world, p);
            }
        }
        CuteDifficult.LOGGER.info("[CuteDifficult] Hollow Lord death sequence complete.");
    }

    /**
     * Play a sound at a position, accepting either a direct {@link net.minecraft.sound.SoundEvent}
     * or a {@link net.minecraft.registry.entry.RegistryEntry} wrapper — in 1.21.1
     * some SoundEvents fields are one and some the other, and which is which
     * isn't predictable, so we normalize here and never have to care.
     */
    private void playAt(ServerWorld world, BlockPos pos, Object sound, float volume, float pitch) {
        net.minecraft.sound.SoundEvent event;
        if (sound instanceof net.minecraft.sound.SoundEvent se) {
            event = se;
        } else if (sound instanceof net.minecraft.registry.entry.RegistryEntry<?> entry
                   && entry.value() instanceof net.minecraft.sound.SoundEvent se) {
            event = se;
        } else {
            return;
        }
        world.playSound(null, pos, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
