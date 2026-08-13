package de.dennisthegamer.breedtimer.util;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The player-dependent half of the visibility tests, worked out once instead of per entity.
 *
 * <p>{@code BreedCooldownHelper} already had a four-argument {@code isOutOfFieldOfView} written for
 * exactly this, with a javadoc explaining that a caller looping over many entities should hoist the
 * eye position, look vector and cosine — but every one of the eight call sites used the two-argument
 * form, which recomputes all three per entity per frame. This class is that hoist, made impossible
 * to bypass, and it also replaces the third, independently written FOV formula in
 * {@code BlockLabelScanner}.
 *
 * <p>{@link #fade(Entity)} mirrors {@code BreedCooldownHelper.getDistanceFade}, measuring from the
 * player's feet ({@code Player.position()}) to the entity's feet. {@link #fade(double, double, double)}
 * instead measures from the eye ({@code Player.getEyePosition}), because that is what
 * {@code BlockLabelScanner} always did for its floating block labels — the two were already different
 * formulas before this class existed, and hoisting them must not quietly merge them into one.
 */
public final class Visibility {

    private final Vec3 eyePos;
    private final Vec3 lookDir;
    private final double fovCos;
    private final double fadeStart;
    private final double fadeEnd;
    private final Vec3 playerPos;

    public Visibility(Player player, BreedTimerConfig config) {
        this.eyePos = player.getEyePosition(1.0f);
        this.lookDir = player.getLookAngle();
        this.fovCos = BreedCooldownHelper.fovCos();
        this.fadeStart = config.fadeStartDistance;
        this.fadeEnd = config.fadeEndDistance;
        this.playerPos = player.position();
    }

    public boolean inView(Entity entity) {
        return !BreedCooldownHelper.isOutOfFieldOfView(eyePos, lookDir, fovCos, entity);
    }

    /** For block labels: the same test, against a point instead of an entity's centre. */
    public boolean inView(double x, double y, double z) {
        double dx = x - eyePos.x, dy = y - eyePos.y, dz = z - eyePos.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4) return 0.0 >= fovCos;
        return lookDir.x * dx + lookDir.y * dy + lookDir.z * dz >= fovCos * len;
    }

    /** Feet-to-feet, matching {@code BreedCooldownHelper.getDistanceFade}. */
    public float fade(Entity entity) {
        return fadeFor(playerPos.distanceTo(entity.position()));
    }

    /** Eye-to-point, matching {@code BlockLabelScanner}'s original label fade. */
    public float fade(double x, double y, double z) {
        double dx = x - eyePos.x, dy = y - eyePos.y, dz = z - eyePos.z;
        return fadeFor(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private float fadeFor(double dist) {
        if (dist <= fadeStart) return 1.0f;
        if (dist >= fadeEnd) return 0.0f;
        return 1.0f - (float) ((dist - fadeStart) / (fadeEnd - fadeStart));
    }
}
