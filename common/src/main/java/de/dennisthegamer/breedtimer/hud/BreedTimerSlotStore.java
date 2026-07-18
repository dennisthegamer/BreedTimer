package de.dennisthegamer.breedtimer.hud;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.hudlib.position.HudPreset;
import de.dennisthegamer.hudlib.ui.HudSlotStore;

import java.util.List;

/** Bildet die HUD-Preset-Slots von {@link BreedTimerConfig} auf die hudlib-ui-Schnittstelle ab. */
public class BreedTimerSlotStore implements HudSlotStore {

    @Override
    public List<HudPreset> slots() {
        return BreedTimerConfig.get().hudSlots;
    }

    @Override
    public void save() {
        BreedTimerConfig.save();
    }
}
