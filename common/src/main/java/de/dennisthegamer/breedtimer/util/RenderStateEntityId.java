package de.dennisthegamer.breedtimer.util;

/**
 * Carries the network id of the entity a render state was extracted from.
 *
 * <p>Vanilla's {@code EntityRenderState} deliberately keeps no reference back to its entity, which
 * left the label renderer guessing: it queried the world for every living entity around the render
 * position and picked the nearest. That cost a world query per rendered entity per frame and still
 * picked the wrong one whenever two mobs overlapped. Stamping the id during extraction turns the
 * lookup into {@code Level.getEntity(int)}.
 *
 * <p>The id rather than the entity itself, so a render state can never keep a dead entity alive.
 */
public interface RenderStateEntityId {

    /** Network id of the source entity, or -1 if this state has not been extracted yet. */
    int breedtimer$getEntityId();

    void breedtimer$setEntityId(int id);
}
