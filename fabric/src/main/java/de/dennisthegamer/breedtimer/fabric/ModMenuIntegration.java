package de.dennisthegamer.breedtimer.fabric;

import de.dennisthegamer.breedtimer.config.BreedTimerConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BreedTimerConfigScreen::createConfigScreen;
    }
}
