package com.cutedifficult.spirit;

/**
 * Nine celestial bodies, one per element, on coprime orbits. Position is a
 * deterministic function of a continuous "sky time" (day + fraction of day),
 * so aspects rise and peak within intraday windows you must catch.
 *
 * <p>Full HBM-grade clockwork:
 * <ul>
 *   <li><b>Continuous motion</b> — {@link #anglePrecise} takes a double day
 *       (e.g. 3.5 = midday of day 3), so aspects sharpen and fade hour to hour.</li>
 *   <li><b>Retrograde</b> — each body periodically reverses apparent motion;
 *       {@link #isRetrograde} flips aspect polarity in the handler.</li>
 *   <li><b>Decans</b> — each 360° circle splits into 36 decans of 10°; the decan
 *       a body sits in tints its element with a sub-element.</li>
 * </ul>
 */
public enum CelestialBody {
    HOSHI_KASAI(Element.KASAI, 7, 0),
    HOSHI_MIZU(Element.MIZU, 11, 40),
    HOSHI_DAICHI(Element.DAICHI, 13, 80),
    HOSHI_KAZE(Element.KAZE, 17, 120),
    HOSHI_KAMINARI(Element.KAMINARI, 19, 160),
    HOSHI_MORI(Element.MORI, 23, 200),
    HOSHI_KORI(Element.KORI, 29, 240),
    HOSHI_YUREI(Element.YUREI, 31, 280),
    HOSHI_TENGOKU(Element.TENGOKU, 37, 320);

    public final Element element;
    public final int period;
    public final int startAngle;

    CelestialBody(Element element, int period, int startAngle) {
        this.element = element;
        this.period = period;
        this.startAngle = startAngle;
    }

    /** Position at an integer day (kept for back-compat / coarse queries). */
    public double angleOn(long day) {
        return anglePrecise(day);
    }

    /**
     * Precise position at a continuous sky-day. Includes a small epicyclic
     * wobble so retrograde periods produce genuine backward motion rather
     * than just a flag.
     */
    public double anglePrecise(double dayFraction) {
        double degPerDay = 360.0 / period;
        double mean = startAngle + dayFraction * degPerDay;
        // Epicycle: a secondary term that occasionally overpowers the mean
        // motion, creating apparent retrograde loops. Amplitude tuned per period.
        double epi = (period * 0.9) * Math.sin(dayFraction * (2 * Math.PI / period) * 1.3);
        double raw = mean + epi;
        double mod = raw % 360.0;
        return mod < 0 ? mod + 360.0 : mod;
    }

    /**
     * Is the body retrograde (apparent angle decreasing) at this sky-day?
     * Computed by sampling the precise angle a tiny step apart.
     */
    public boolean isRetrograde(double dayFraction) {
        double a0 = anglePrecise(dayFraction - 0.02);
        double a1 = anglePrecise(dayFraction + 0.02);
        double delta = a1 - a0;
        // Normalize across the 0/360 wrap.
        if (delta > 180) delta -= 360;
        if (delta < -180) delta += 360;
        return delta < 0;
    }

    /** Decan index 0..35 (each 10°) the body currently occupies. */
    public int decan(double dayFraction) {
        return (int) (anglePrecise(dayFraction) / 10.0) % 36;
    }

    /** The element ruling the decan a body sits in (sub-element flavor). */
    public Element decanElement(double dayFraction) {
        // Map the 36 decans onto the 9 elements, 4 decans each.
        int idx = decan(dayFraction) / 4;
        return Element.values()[idx % Element.values().length];
    }

    public static CelestialBody of(Element e) {
        for (CelestialBody b : values()) if (b.element == e) return b;
        return HOSHI_TENGOKU;
    }

    public String displayName() {
        return "Hoshi-" + element.kamiName();
    }

    public enum Aspect {
        CONJUNCTION(0, "Conjunction", true),
        SEXTILE(60, "Sextile", true),
        SQUARE(90, "Square", false),
        TRINE(120, "Trine", true),
        OPPOSITION(180, "Opposition", false);

        public final double separation;
        public final String label;
        public final boolean harmonious;

        Aspect(double separation, String label, boolean harmonious) {
            this.separation = separation;
            this.label = label;
            this.harmonious = harmonious;
        }
    }

    public static final double ORB = 6.0;

    public static double separation(CelestialBody a, CelestialBody b, double dayFraction) {
        double diff = Math.abs(a.anglePrecise(dayFraction) - b.anglePrecise(dayFraction)) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    public static Aspect aspectBetween(CelestialBody a, CelestialBody b, double dayFraction) {
        if (a == b) return null;
        double sep = separation(a, b, dayFraction);
        for (Aspect asp : Aspect.values()) {
            if (Math.abs(sep - asp.separation) <= ORB) return asp;
        }
        return null;
    }

    /**
     * Orb tightness 0..1 for the aspect between two bodies (1 = exact, 0 = edge
     * of orb). Used for the strength gradient — catch the peak for max effect.
     */
    public static double orbTightness(CelestialBody a, CelestialBody b, double dayFraction) {
        double sep = separation(a, b, dayFraction);
        for (Aspect asp : Aspect.values()) {
            double off = Math.abs(sep - asp.separation);
            if (off <= ORB) return 1.0 - (off / ORB);
        }
        return 0.0;
    }
}
