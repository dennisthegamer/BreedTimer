package de.dennisthegamer.breedtimer.hud;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.render.BreedTimerHud;
import de.dennisthegamer.hudlib.ui.HudBoxProvider;
import net.minecraft.client.gui.GuiGraphics;

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
        // HudLib divides the screen by this and resolves the placement in the scaled space, so it
        // has to be the same number drawPreview() and render() push onto the pose.
        return BreedTimerConfig.get().hudScale;
    }

    @Override
    public void drawSample(GuiGraphics graphics, int x, int y, float scale) {
        BreedTimerHud.drawPreview(graphics, x, y, scale);
    }
}
