package de.dennisthegamer.breedtimer.mixin;

import de.dennisthegamer.breedtimer.util.RenderStateEntityId;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every render state a slot for the id of the entity it was extracted from. */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements RenderStateEntityId {

    @Unique
    private int breedtimer$entityId = -1;

    @Override
    public int breedtimer$getEntityId() {
        return breedtimer$entityId;
    }

    @Override
    public void breedtimer$setEntityId(int id) {
        breedtimer$entityId = id;
    }
}
