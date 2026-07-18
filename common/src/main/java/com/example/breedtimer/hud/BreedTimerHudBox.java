package com.example.breedtimer.hud;

import com.example.breedtimer.render.BreedTimerHud;
import de.dennisthegamer.hudlib.ui.HudBoxProvider;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Adaptiert das BreedTimer-HUD an die hudlib-ui-Schnittstelle. */
public class BreedTimerHudBox implements HudBoxProvider {

    @Override
    public int width() {
        return BreedTimerHud.measureBox()[0];
    }

    @Override
    public int height() {
        return BreedTimerHud.measureBox()[1];
    }

    @Override
    public float scale() {
        return 1f; // BreedTimer hat keine Skalierung
    }

    @Override
    public void drawSample(GuiGraphicsExtractor graphics, int x, int y, float scale) {
        BreedTimerHud.drawPreview(graphics, x, y, scale);
    }
}
