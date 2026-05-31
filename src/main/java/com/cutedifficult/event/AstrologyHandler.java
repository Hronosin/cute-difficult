package com.cutedifficult.event;

import com.cutedifficult.CuteDifficult;
import com.cutedifficult.spirit.CelestialBody;
import com.cutedifficult.spirit.CelestialBody.Aspect;
import com.cutedifficult.spirit.Element;
import com.cutedifficult.util.DifficultyMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Maximum-density HBM-grade astrology. Bodies move continuously (intraday),
 * go retrograde, sit in decans, and form aspects whose strength scales with
 * orb tightness. Multi-body configurations (Grand Trine, Yod, Great Convergence)
 * unlock special effects and a hidden ritual.
 *
 * <p>"Sky time" is a continuous double: full-day index + fraction of the
 * current Minecraft day. So an exact conjunction may peak at, say, 14:30 and
 * be gone by dusk — you must catch the window.
 */
public final class AstrologyHandler {

    private static final Random RANDOM = new Random();

    private static long dayOffset = 0;
    private static long lastAnnouncedDay = -1;

    private AstrologyHandler() {}

    // ===== Sky time =====

    /** Continuous sky-day: integer day + fraction (0..1) of the current day. */
    private static double skyTime(ServerWorld world) {
        long total = world.getTimeOfDay() + dayOffset * 24000L;
        return total / 24000.0;
    }

    public static double now() {
        return cachedNow;
    }
    private static double cachedNow = 0;

    public static long today() {
        return (long) Math.floor(cachedNow);
    }

    // ===== Aspect scanning =====

    public record AspectHit(CelestialBody a, CelestialBody b, Aspect aspect, double tightness, boolean retro) {}

    public static List<AspectHit> activeAspects(double t) {
        List<AspectHit> hits = new ArrayList<>();
        CelestialBody[] bodies = CelestialBody.values();
        for (int i = 0; i < bodies.length; i++) {
            for (int j = i + 1; j < bodies.length; j++) {
                Aspect a = CelestialBody.aspectBetween(bodies[i], bodies[j], t);
                if (a != null) {
                    double tight = CelestialBody.orbTightness(bodies[i], bodies[j], t);
                    boolean retro = bodies[i].isRetrograde(t) || bodies[j].isRetrograde(t);
                    hits.add(new AspectHit(bodies[i], bodies[j], a, tight, retro));
                }
            }
        }
        return hits;
    }

    // ===== Public multiplier API =====

    /**
     * Spirit multiplier for an element, folding in every aspect its body is in,
     * scaled by orb tightness, with retrograde inverting polarity.
     */
    public static double spiritMultiplier(Element element) {
        double t = cachedNow;
        CelestialBody body = CelestialBody.of(element);
        double mult = 1.0;
        for (AspectHit hit : activeAspects(t)) {
            if (hit.a() != body && hit.b() != body) continue;
            boolean harmonious = hit.aspect().harmonious;
            // Retrograde inverts the moral polarity of the aspect.
            if (hit.retro()) harmonious = !harmonious;
            // Base strength by aspect type.
            double base = switch (hit.aspect()) {
                case CONJUNCTION -> 1.0;
                case TRINE -> 0.6;
                case SEXTILE -> 0.3;
                case SQUARE -> 0.4;
                case OPPOSITION -> 0.6;
            };
            // Scale by orb tightness (catch the peak).
            double scaled = base * hit.tightness();
            mult *= harmonious ? (1.0 + scaled) : Math.max(0.2, 1.0 - scaled);
        }
        // Decan resonance: if the body sits in its OWN element's decan, slight boost.
        if (body.decanElement(t) == element) mult *= 1.15;
        return mult;
    }

    public static boolean offeringMisfire(Element element) {
        double t = cachedNow;
        CelestialBody body = CelestialBody.of(element);
        for (AspectHit hit : activeAspects(t)) {
            if (hit.a() != body && hit.b() != body) continue;
            boolean harmonious = hit.aspect().harmonious;
            if (hit.retro()) harmonious = !harmonious;
            if (!harmonious && RANDOM.nextDouble() < hit.tightness() * 0.5) return true;
        }
        return false;
    }

    public static int harmoniousCount(double t) {
        int n = 0;
        for (AspectHit h : activeAspects(t)) {
            boolean harm = h.aspect().harmonious;
            if (h.retro()) harm = !harm;
            if (harm) n++;
        }
        return n;
    }

    public static int tenseCount(double t) {
        int n = 0;
        for (AspectHit h : activeAspects(t)) {
            boolean harm = h.aspect().harmonious;
            if (h.retro()) harm = !harm;
            if (!harm) n++;
        }
        return n;
    }

    public static boolean isGrandAlignment(double t) { return harmoniousCount(t) >= 3; }
    public static boolean isGrandCross(double t) { return tenseCount(t) >= 3; }

    // ===== Multi-body configurations (the deep cuts) =====

    /** Grand Trine: three bodies mutually in trine (120° triangle). */
    public static List<CelestialBody[]> grandTrines(double t) {
        return triadsWith(t, Aspect.TRINE);
    }

    /** Yod ("finger of fate"): two bodies sextile, both quincunx-ish to a third.
     *  Simplified: two sextile + the apex in square to both. */
    public static List<CelestialBody[]> yods(double t) {
        List<CelestialBody[]> out = new ArrayList<>();
        CelestialBody[] bs = CelestialBody.values();
        for (int i = 0; i < bs.length; i++)
            for (int j = i + 1; j < bs.length; j++) {
                if (CelestialBody.aspectBetween(bs[i], bs[j], t) != Aspect.SEXTILE) continue;
                for (CelestialBody apex : bs) {
                    if (apex == bs[i] || apex == bs[j]) continue;
                    if (CelestialBody.aspectBetween(apex, bs[i], t) == Aspect.SQUARE
                        && CelestialBody.aspectBetween(apex, bs[j], t) == Aspect.SQUARE) {
                        out.add(new CelestialBody[]{bs[i], bs[j], apex});
                    }
                }
            }
        return out;
    }

    private static List<CelestialBody[]> triadsWith(double t, Aspect want) {
        List<CelestialBody[]> out = new ArrayList<>();
        CelestialBody[] bs = CelestialBody.values();
        for (int i = 0; i < bs.length; i++)
            for (int j = i + 1; j < bs.length; j++)
                for (int k = j + 1; k < bs.length; k++) {
                    if (CelestialBody.aspectBetween(bs[i], bs[j], t) == want
                        && CelestialBody.aspectBetween(bs[j], bs[k], t) == want
                        && CelestialBody.aspectBetween(bs[i], bs[k], t) == want) {
                        out.add(new CelestialBody[]{bs[i], bs[j], bs[k]});
                    }
                }
        return out;
    }

    /** Great Convergence: ALL nine bodies within a 40° arc. The rarest window. */
    public static boolean isGreatConvergence(double t) {
        double min = 360, max = 0;
        // Find the tightest arc covering all bodies (handle wraparound by trying
        // each body as the arc start).
        double[] angles = new double[CelestialBody.values().length];
        int idx = 0;
        for (CelestialBody b : CelestialBody.values()) angles[idx++] = b.anglePrecise(t);
        java.util.Arrays.sort(angles);
        double best = 360;
        for (int i = 0; i < angles.length; i++) {
            // Arc from angles[i] going forward, wrapping.
            double lo = angles[i];
            double hi = angles[(i + angles.length - 1) % angles.length];
            double arc = (hi - lo + 360) % 360;
            best = Math.min(best, arc);
        }
        return best <= 40.0;
    }

    // ===== Registration =====

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CuteDifficult.currentMode != DifficultyMode.CRUEL) return;
            ServerWorld ow = server.getOverworld();
            if (ow == null) return;
            cachedNow = skyTime(ow);

            long day = today();
            long tod = ow.getTimeOfDay() % 24000L;
            if (day != lastAnnouncedDay && tod >= 0 && tod < 100) {
                lastAnnouncedDay = day;
                announceSky(server, cachedNow);
            }

            if (ow.getTime() % 40 == 0) tickAmbient(server, ow, cachedNow);
        });
        CuteDifficult.LOGGER.info("[CuteDifficult] AstrologyHandler registered.");
    }

    private static void announceSky(MinecraftServer server, double t) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (isGreatConvergence(t)) {
                p.sendMessage(Text.literal("THE GREAT CONVERGENCE. The Nine gather in one quarter of the sky. The veil is thin — a rite is possible.")
                    .formatted(Formatting.GOLD, Formatting.BOLD), false);
            } else if (isGrandAlignment(t)) {
                p.sendMessage(Text.literal("Grand Alignment — the heavens harmonize. The spirits are generous today.")
                    .formatted(Formatting.GOLD), false);
            } else if (isGrandCross(t)) {
                p.sendMessage(Text.literal("Grand Cross — the heavens war. Offer nothing you cannot lose.")
                    .formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            } else {
                p.sendMessage(Text.literal("The stars have shifted. Consult /cd sky before you offer.")
                    .formatted(Formatting.GRAY, Formatting.ITALIC), false);
            }
        }
    }

    private static void tickAmbient(MinecraftServer server, ServerWorld ow, double t) {
        boolean conv = isGreatConvergence(t);
        boolean align = isGrandAlignment(t);
        boolean cross = isGrandCross(t);
        if (!conv && !align && !cross) return;
        long time = ow.getTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getWorld() instanceof ServerWorld pw)) continue;
            if (conv && time % 20 == 0) {
                pw.spawnParticles(player, ParticleTypes.TOTEM_OF_UNDYING, true,
                    player.getX(), player.getY() + 2.5, player.getZ(), 10, 1.0, 1.5, 1.0, 0.1);
            } else if (align && time % 40 == 0) {
                pw.spawnParticles(player, ParticleTypes.END_ROD, true,
                    player.getX(), player.getY() + 3, player.getZ(), 6, 0.3, 2.0, 0.3, 0.0);
            }
            if (cross && time % 80 == 0) {
                player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.MINING_FATIGUE, 100, 0, false, true, true));
                pw.spawnParticles(player, ParticleTypes.SQUID_INK, true,
                    player.getX(), player.getY() + 1, player.getZ(), 8, 0.5, 0.5, 0.5, 0.02);
            }
        }
    }

    // ===== Ephemeris report (/cd sky) =====

    public static List<Text> ephemerisReport(double t) {
        List<Text> lines = new ArrayList<>();
        long day = (long) Math.floor(t);
        double frac = t - day;
        int hh = (int) ((frac * 24000 + 6000) % 24000) / 1000; // rough MC clock hour
        lines.add(Text.literal(String.format("═══ Ephemeris — Day %d, ~%02d:00 ═══", day, hh))
            .formatted(Formatting.GOLD, Formatting.BOLD));

        lines.add(Text.literal("Positions (→ direct, ← retrograde):").formatted(Formatting.GRAY));
        for (CelestialBody b : CelestialBody.values()) {
            double ang = b.anglePrecise(t);
            boolean retro = b.isRetrograde(t);
            Element decan = b.decanElement(t);
            lines.add(Text.literal(String.format("  %s %s %5.1f°  [decan: %s]",
                b.displayName(), retro ? "←" : "→", ang, decan.kamiName()))
                .formatted(b.element.color()));
        }

        List<AspectHit> hits = activeAspects(t);
        if (hits.isEmpty()) {
            lines.add(Text.literal("No aspects active.").formatted(Formatting.DARK_GRAY));
        } else {
            lines.add(Text.literal("Active Aspects (tightness%):").formatted(Formatting.GRAY));
            for (AspectHit h : hits) {
                boolean harm = h.aspect().harmonious;
                if (h.retro()) harm = !harm;
                Formatting c = harm ? Formatting.GREEN : Formatting.RED;
                lines.add(Text.literal(String.format("  %s %s %s  %.0f%% %s",
                    h.a().element.kamiName(), h.aspect().label, h.b().element.kamiName(),
                    h.tightness() * 100, h.retro() ? "(R)" : "")).formatted(c));
            }
        }

        // Special configs.
        for (CelestialBody[] tri : grandTrines(t)) {
            lines.add(Text.literal(String.format("◇ Grand Trine: %s–%s–%s",
                tri[0].element.kamiName(), tri[1].element.kamiName(), tri[2].element.kamiName()))
                .formatted(Formatting.AQUA, Formatting.BOLD));
        }
        for (CelestialBody[] y : yods(t)) {
            lines.add(Text.literal(String.format("⚸ Yod (apex %s): %s–%s",
                y[2].element.kamiName(), y[0].element.kamiName(), y[1].element.kamiName()))
                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        }
        if (isGreatConvergence(t)) {
            lines.add(Text.literal(">> GREAT CONVERGENCE — the rite of the Nine is possible <<")
                .formatted(Formatting.GOLD, Formatting.BOLD));
        }
        if (isGrandAlignment(t)) lines.add(Text.literal(">> Grand Alignment — offerings boosted <<").formatted(Formatting.GOLD));
        if (isGrandCross(t)) lines.add(Text.literal(">> Grand Cross — offerings risky <<").formatted(Formatting.DARK_RED));

        Double next = forecastNextChange(t, 30);
        if (next != null) {
            lines.add(Text.literal(String.format("Next aspect shift: day %.1f (in %.1f days)",
                next, next - t)).formatted(Formatting.AQUA));
        }
        return lines;
    }

    private static Double forecastNextChange(double t, int horizonDays) {
        String sig = aspectSignature(t);
        // Step in quarter-day increments for intraday resolution.
        for (double d = 0.25; d <= horizonDays; d += 0.25) {
            if (!aspectSignature(t + d).equals(sig)) return t + d;
        }
        return null;
    }

    private static String aspectSignature(double t) {
        StringBuilder sb = new StringBuilder();
        for (AspectHit h : activeAspects(t)) {
            sb.append(h.a().name()).append(h.aspect().name()).append(h.b().name())
              .append(h.retro() ? 'R' : 'D').append(';');
        }
        return sb.toString();
    }

    // ===== Admin =====

    public static void shiftDays(MinecraftServer server, long delta) {
        dayOffset += delta;
        ServerWorld ow = server.getOverworld();
        if (ow != null) cachedNow = skyTime(ow);
    }

    public static void resetShift(MinecraftServer server) {
        dayOffset = 0;
        ServerWorld ow = server.getOverworld();
        if (ow != null) cachedNow = skyTime(ow);
    }
}
