package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.spirit.KitsuneData;
import com.cutedifficult.spirit.FoxStorage;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Renders an element-colored particle aura around every fox.
 *
 * <p><b>v0.3.1 fix:</b> in Minecraft 1.21.1, {@link DustParticleEffect}
 * constructor takes a {@link Vector3f} (JOML — the math library Minecraft
 * uses everywhere), not a packed int. In 1.21.8+ it was reverted back
 * to int. We use Vector3f because we target 1.21.1.
 *
 * <p>Aura intensity scales with tail count:
 * <ul>
 *   <li>1-tail fox — almost invisible glow, only spotted up close.</li>
 *   <li>3-tail fox — clear small aura, visible within 10 blocks.</li>
 *   <li>9-tail Kyuubi — brilliant, dense plume, visible halfway across
 *       a chunk. With the Kyuubi bonus, peaks at 13 particles per emission.</li>
 * </ul>
 *
 * <p>Spawn probability per tick = {@code tails / 9}. Particle count per
 * spawn = {@code tails}, plus a fixed {@link #KYUUBI_BONUS_PARTICLES}
 * bonus when at 9 tails. So:
 * <ul>
 *   <li>1 tail: 11% chance × 1 particle = avg 0.11/tick = ~2/sec</li>
 *   <li>5 tails: 55% × 5 = ~2.75/tick = ~55/sec</li>
 *   <li>9 tails: 100% × 13 = ~13/tick = ~260/sec</li>
 * </ul>
 */
public final class FoxAuraHandler {

    private static final int KYUUBI_BONUS_PARTICLES = 4;
    private static final Random RANDOM = new Random();

    private FoxAuraHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;

            for (ServerWorld world : server.getWorlds()) {
                for (var entity : world.iterateEntities()) {
                    if (entity instanceof FoxEntity fox && fox.isAlive()) {
                        emitAura(world, fox);
                    }
                }
            }
        });

        CuteDifficult.LOGGER.info("[CuteDifficult] FoxAuraHandler registered.");
    }

    private static void emitAura(ServerWorld world, FoxEntity fox) {
        KitsuneData data = FoxStorage.getOrCreate(fox, RANDOM);
        int tails = data.tails;
        if (tails <= 0) return;

        if (RANDOM.nextInt(KitsuneData.MAX_TAILS) >= tails) return;

        int count = tails;
        if (tails >= KitsuneData.MAX_TAILS) {
            count += KYUUBI_BONUS_PARTICLES;
        }

        DustParticleEffect dust = particleFor(data.element);
        Vec3d pos = fox.getPos().add(0, fox.getHeight() * 0.6, 0);
        double spread = 0.2 + (tails * 0.04);

        world.spawnParticles(
            dust,
            pos.x, pos.y, pos.z,
            count,
            spread, spread * 0.5, spread,
            0.0
        );
    }

    /**
     * Build a DustParticleEffect with the element's color. In 1.21.1 the
     * constructor wants a {@link Vector3f} where each component is in
     * the 0..1 range (normalized RGB).
     */
    private static DustParticleEffect particleFor(Element element) {
        Vector3f color = switch (element) {
            case KASAI    -> new Vector3f(1.00f, 0.27f, 0.13f); // burning red-orange
            case MIZU     -> new Vector3f(0.20f, 0.67f, 1.00f); // sky-water blue
            case DAICHI   -> new Vector3f(0.78f, 0.54f, 0.29f); // warm amber-brown
            case KAZE     -> new Vector3f(0.92f, 0.94f, 1.00f); // pale silver-white
            case KAMINARI -> new Vector3f(1.00f, 0.90f, 0.25f); // crackling yellow
            case MORI     -> new Vector3f(0.33f, 0.88f, 0.38f); // verdant green
            case KORI     -> new Vector3f(0.60f, 0.90f, 1.00f); // pale frost-blue
            case YUREI    -> new Vector3f(0.80f, 0.40f, 1.00f); // ghostly purple
            case TENGOKU  -> new Vector3f(1.00f, 1.00f, 0.80f); // golden starlight
        };
        return new DustParticleEffect(color, 1.0f);
    }
}
