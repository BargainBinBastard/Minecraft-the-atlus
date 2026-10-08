package io.github.bargainbinbastard.altus.history;

/**
 * The shape of the Altus: the Woods, and the Mountain at their center. Pure math, and independent
 * of the world seed, so the Altus has the same shape in every world.
 */
public final class Terrain {
    private Terrain() {}

    /** Top ground block in the Woods; players stand at {@code BASE + 1}. */
    public static final int BASE = 63;
    public static final double MOUNTAIN_RADIUS = 170;
    public static final double PEAK = 150;
    /** Beyond this distance the dream simply ends. */
    public static final int EDGE = 1600;
    public static final int CLEARING_X = 0, CLEARING_Z = 250;
    public static final int TREELINE = 125, SNOWLINE = 185;

    /** Height of the top solid block at (x, z), or -1 past the edge of the dream. */
    public static int height(int x, int z) {
        if (Math.abs(x) > EDGE || Math.abs(z) > EDGE) return -1;
        double d = Math.sqrt((double) x * x + (double) z * z);
        double dc = Math.sqrt(sq(x - CLEARING_X) + sq(z - CLEARING_Z));
        double calm = clamp((dc - 18) / 12.0, 0, 1);
        double woods = (1.2 * noise(x / 23.0, z / 23.0) + 0.6 * noise(x / 9.0, z / 9.0)) * calm;
        double m = 0;
        if (d < MOUNTAIN_RADIUS) {
            double t = 1 - d / MOUNTAIN_RADIUS;
            m = PEAK * Math.pow(t, 1.7);
            double theta = Math.atan2(z, x);
            m += 7 * t * Math.sin(5 * theta + d / 18.0);
            m += 4 * t * noise(x / 14.0, z / 14.0);
        }
        return (int) Math.round(BASE + woods + m);
    }

    public static boolean onMountain(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z) < MOUNTAIN_RADIUS - 8;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Smooth value noise in [-1, 1] from a fixed hash: no world seed involved. */
    public static double noise(double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        double u = fx * fx * (3 - 2 * fx), v = fz * fz * (3 - 2 * fz);
        double a = hash(x0, z0), b = hash(x0 + 1, z0), c = hash(x0, z0 + 1), d = hash(x0 + 1, z0 + 1);
        return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v;
    }

    private static double hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return ((h & 0xFFFFFF) / (double) 0xFFFFFF) * 2 - 1;
    }
}
