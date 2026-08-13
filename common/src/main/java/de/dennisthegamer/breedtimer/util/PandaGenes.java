package de.dennisthegamer.breedtimer.util;

/**
 * The distribution of a panda cub's personality, given both parents' genes.
 *
 * <p>Everything vanilla needs to decide this is synced -- {@code MAIN_GENE_ID} and {@code HIDDEN_GENE_ID} are
 * both {@code EntityDataAccessor<Byte>} -- and the roll itself is four lines of {@code setGeneFromParents}. That
 * makes this the one prediction in the mod that is exactly right rather than estimated, so it is worth getting
 * exactly right.
 *
 * <p>Two things a hand-written table would get wrong, both verified against 26.2 bytecode:
 * <ul>
 *   <li>{@code Gene.getRandom} is a weighted 16-way draw, not a uniform pick over seven: NORMAL 5/16,
 *       WEAK 5/16, BROWN 2/16, and 1/16 each for lazy, worried, playful and aggressive. A spontaneous WEAK
 *       mutation is six times likelier than a spontaneous BROWN one.</li>
 *   <li>The cub's main and hidden genes are <em>not</em> independent when the parents differ, because both are
 *       drawn from the same pair of parents in one coin-flipped order. Multiplying the two marginals for the
 *       brown case overstates it by roughly sixty percentage points, so the full 7x7 joint is kept.</li>
 * </ul>
 *
 * <p>The arithmetic here deliberately touches no Minecraft type, exactly like {@link BabyGrowthTracker}, so it
 * can be proven in a plain JVM. Genes are their vanilla ids: 0 normal, 1 lazy, 2 worried, 3 playful, 4 brown,
 * 5 weak, 6 aggressive.
 */
public final class PandaGenes {

    private PandaGenes() {}

    public static final int GENES = 7;
    public static final int NORMAL = 0, BROWN = 4, WEAK = 5;

    /** {@code Gene.isRecessive}: brown and weak only. */
    public static boolean isRecessive(int gene) {
        return gene == BROWN || gene == WEAK;
    }

    /** {@code Gene.getRandom}'s weights out of 16, in gene-id order. */
    private static final double[] MUTATION = {5 / 16.0, 1 / 16.0, 1 / 16.0, 1 / 16.0, 2 / 16.0, 5 / 16.0, 1 / 16.0};
    /** Both re-rolls are {@code random.nextInt(32) == 0}. */
    private static final double MUTATION_CHANCE = 1 / 32.0;

    /**
     * The chance of each expressed personality for a cub of these two parents.
     *
     * @return an array of length {@link #GENES}, summing to 1
     */
    public static double[] cubOdds(int mainA, int hiddenA, int mainB, int hiddenB) {
        double[] pA = parentDraw(mainA, hiddenA);
        double[] pB = parentDraw(mainB, hiddenB);

        // joint[u][v] = P(cub main == u AND cub hidden == v)
        double[][] joint = new double[GENES][GENES];
        for (int x = 0; x < GENES; x++) {
            for (int y = 0; y < GENES; y++) {
                // The parent-order coin flip: main from A and hidden from B, or the other way round.
                double pre = 0.5 * pA[x] * pB[y] + 0.5 * pB[x] * pA[y];
                if (pre == 0.0) continue;
                for (int u = 0; u < GENES; u++) {
                    double mu = mutate(u, x);
                    if (mu == 0.0) continue;
                    for (int v = 0; v < GENES; v++) {
                        joint[u][v] += pre * mu * mutate(v, y);
                    }
                }
            }
        }

        double[] expressed = new double[GENES];
        for (int u = 0; u < GENES; u++) {
            for (int v = 0; v < GENES; v++) {
                // Gene.getVariantFromGenes, re-implemented because it is private on 26.x:
                // a recessive main gene only shows when the hidden gene matches it, otherwise normal.
                int shown = !isRecessive(u) ? u : (u == v ? u : NORMAL);
                expressed[shown] += joint[u][v];
            }
        }
        return expressed;
    }

    /** {@code getOneOfGenesRandomly()}: an even coin flip between this panda's two genes. */
    private static double[] parentDraw(int main, int hidden) {
        double[] p = new double[GENES];
        p[main] += 0.5;
        p[hidden] += 0.5;
        return p;
    }

    /** One slot's independent 1-in-32 re-roll onto the weighted table. */
    private static double mutate(int result, int inherited) {
        double keep = result == inherited ? 1.0 - MUTATION_CHANCE : 0.0;
        return keep + MUTATION_CHANCE * MUTATION[result];
    }
}
