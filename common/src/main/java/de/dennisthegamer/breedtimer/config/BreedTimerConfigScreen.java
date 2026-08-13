package de.dennisthegamer.breedtimer.config;

import de.dennisthegamer.breedtimer.hud.BreedTimerHudBox;
import de.dennisthegamer.breedtimer.hud.BreedTimerSlotStore;
import de.dennisthegamer.breedtimer.render.StatePalette;
import de.dennisthegamer.hudlib.ui.HudEditorScreen;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BreedTimerConfigScreen {

    public static Screen createConfigScreen(Screen parent) {
        BreedTimerConfig def = new BreedTimerConfig();
        BreedTimerConfig cfg = BreedTimerConfig.get();

        // F-4: while the active preset forces the outline on (StatePalette.forcesOutline; today
        // only HIGH_CONTRAST) it overrides textOutline's own value regardless (see
        // BreedTimerConfig.effectiveTextOutline), so the tickbox is greyed out then -- toggling it
        // would not change what actually renders. Built ahead of the two options below (rather
        // than inline) so textOutlineOption's initial availability can read cfg's live value, and
        // so colorPresetOption has something to notify when the dropdown changes.
        Option<StatePalette> colorPresetOption = Option.<StatePalette>createBuilder()
                .name(Component.translatable("breedtimer.config.colorPreset"))
                .description(val -> OptionDescription.of(
                        Component.translatable("breedtimer.config.colorPreset.desc")))
                .binding(def.colorPreset, () -> cfg.colorPreset, v -> cfg.colorPreset = v)
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(StatePalette.class)
                        .valueFormatter(v -> Component.translatable(
                                "breedtimer.palette." + v.name().toLowerCase(java.util.Locale.ROOT))))
                .build();

        Option<Boolean> textOutlineOption = Option.<Boolean>createBuilder()
                .name(Component.translatable("breedtimer.config.textOutline"))
                .description(val -> OptionDescription.of(
                        Component.translatable("breedtimer.config.textOutline.desc")))
                .binding(def.textOutline, () -> cfg.textOutline, v -> cfg.textOutline = v)
                .controller(TickBoxControllerBuilder::create)
                .available(!cfg.colorPreset.forcesOutline)
                .build();

        // YACL's own per-option event listener is enough here -- no general listener framework
        // needed. addEventListener (not the deprecated BiConsumer addListener) fires on every
        // pending change to the dropdown, including before Save, so availability tracks the preset
        // live while the screen is still open; recomputing from pendingValue() on every event is
        // cheap and idempotent, so there is no need to branch on which Event fired.
        colorPresetOption.addEventListener((opt, event) ->
                textOutlineOption.setAvailable(!opt.pendingValue().forcesOutline));

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("breedtimer.config.title"))
                .save(BreedTimerConfig::save)

                // Tab 1: General
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("breedtimer.config.category.general"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.enabled"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.enabled.desc")))
                                .binding(def.enabled, () -> cfg.enabled, v -> cfg.enabled = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.showAnimals"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.showAnimals.desc")))
                                .binding(def.showAnimals, () -> cfg.showAnimals, v -> cfg.showAnimals = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.showVillagers"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.showVillagers.desc")))
                                .binding(def.showVillagers, () -> cfg.showVillagers, v -> cfg.showVillagers = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.showFoodHint"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.showFoodHint.desc")))
                                .binding(def.showFoodHint, () -> cfg.showFoodHint, v -> cfg.showFoodHint = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.compactMode"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.compactMode.desc")))
                                .binding(def.compactMode, () -> cfg.compactMode, v -> cfg.compactMode = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.hudInViewOnly"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.hudInViewOnly.desc")))
                                .binding(def.hudInViewOnly, () -> cfg.hudInViewOnly, v -> cfg.hudInViewOnly = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Float>createBuilder()
                                .name(Component.translatable("breedtimer.config.hudOpacity"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.hudOpacity.desc")))
                                .binding(def.hudOpacity, () -> cfg.hudOpacity, v -> cfg.hudOpacity = v)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.05f))
                                .build())
                        .option(Option.<Float>createBuilder()
                                .name(Component.translatable("breedtimer.config.hudScale"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.hudScale.desc")))
                                .binding(def.hudScale, () -> cfg.hudScale, v -> cfg.hudScale = v)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.5f, 2.0f).step(0.05f))
                                .build())
                        .option(ButtonOption.createBuilder()
                                .name(Component.translatable("breedtimer.config.hud_edit"))
                                .description(OptionDescription.of(
                                        Component.translatable("breedtimer.config.hud_edit.desc")))
                                .action((yaclScreen, opt) -> {
                                    BreedTimerConfig liveConfig = BreedTimerConfig.get();
                                    Minecraft.getInstance().setScreen(new HudEditorScreen(
                                            yaclScreen, new BreedTimerHudBox(), new BreedTimerSlotStore(),
                                            () -> liveConfig.hudPlacement,
                                            p -> { liveConfig.hudPlacement = p; BreedTimerConfig.save(); }));
                                })
                                .build())
                        .build())

                // Tab 2: Filter. Order: Ready, Cooldown, Babies, In love, Blocked -- the same order
                // createTimerInfo resolves them in and the same order the HUD line prints them in, so
                // the screen reads like the thing it controls. showBabyTimer is moved here (not
                // duplicated) from General: it already was exactly this filter, just tested
                // independently in four places before Task 30 gave the state filter one admission
                // predicate.
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("breedtimer.config.category.filter"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.showReady"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.showReady.desc")))
                                .binding(def.showReady, () -> cfg.showReady, v -> cfg.showReady = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.showCooldown"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.showCooldown.desc")))
                                .binding(def.showCooldown, () -> cfg.showCooldown, v -> cfg.showCooldown = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.showBabyTimer"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.showBabyTimer.desc")))
                                .binding(def.showBabyTimer, () -> cfg.showBabyTimer, v -> cfg.showBabyTimer = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.showInLove"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.showInLove.desc")))
                                .binding(def.showInLove, () -> cfg.showInLove, v -> cfg.showInLove = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.showBlocked"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.showBlocked.desc")))
                                .binding(def.showBlocked, () -> cfg.showBlocked, v -> cfg.showBlocked = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        // Placed right after the five state filters, not with showBlocks below: this is
                        // opt-in and off by default, the mod's only inferred display rather than a sixth
                        // breeding state or a block-label master switch.
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.showDropWindows"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.showDropWindows.desc")))
                                .binding(def.showDropWindows, () -> cfg.showDropWindows, v -> cfg.showDropWindows = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        // Not a breeding state (see BreedTimerConfig.showBlocks): the master switch
                        // for turtle/sniffer eggs, beehive and crop labels, listed last because it
                        // controls block labels rather than one of the five states above it.
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.showBlocks"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.showBlocks.desc")))
                                .binding(def.showBlocks, () -> cfg.showBlocks, v -> cfg.showBlocks = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())

                // Tab 3: Rendering
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("breedtimer.config.category.rendering"))
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("breedtimer.config.scanRadius"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.scanRadius.desc")))
                                .binding(def.scanRadius, () -> cfg.scanRadius, v -> cfg.scanRadius = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(4, 32).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("breedtimer.config.fadeStartDistance"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.fadeStartDistance.desc")))
                                .binding(def.fadeStartDistance, () -> cfg.fadeStartDistance, v -> cfg.fadeStartDistance = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(4, 32).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("breedtimer.config.fadeEndDistance"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.fadeEndDistance.desc")))
                                .binding(def.fadeEndDistance, () -> cfg.fadeEndDistance, v -> cfg.fadeEndDistance = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(4, 48).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("breedtimer.config.fovAngle"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.fovAngle.desc")))
                                .binding(def.fovAngle, () -> cfg.fovAngle, v -> cfg.fovAngle = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(30, 180).step(5))
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.labelLookAtOnly"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.labelLookAtOnly.desc")))
                                .binding(def.labelLookAtOnly, () -> cfg.labelLookAtOnly, v -> cfg.labelLookAtOnly = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.labelThroughWalls"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.labelThroughWalls.desc")))
                                .binding(def.labelThroughWalls, () -> cfg.labelThroughWalls,
                                        v -> cfg.labelThroughWalls = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        // Above the two opacity sliders: it governs everything else the tab is about,
                        // and it decides what every colour below actually looks like.
                        .option(colorPresetOption)
                        .option(Option.<Float>createBuilder()
                                .name(Component.translatable("breedtimer.config.labelOpacity"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.labelOpacity.desc")))
                                .binding(def.labelOpacity, () -> cfg.labelOpacity, v -> cfg.labelOpacity = v)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.05f))
                                .build())
                        .option(textOutlineOption)
                        .build())

                // Tab 4: Notifications
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("breedtimer.config.category.notifications"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.playSound"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.playSound.desc")))
                                .binding(def.playSound, () -> cfg.playSound, v -> cfg.playSound = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Float>createBuilder()
                                .name(Component.translatable("breedtimer.config.soundVolume"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.soundVolume.desc")))
                                .binding(def.soundVolume, () -> cfg.soundVolume, v -> cfg.soundVolume = v)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.05f))
                                .build())
                        .option(Option.<Float>createBuilder()
                                .name(Component.translatable("breedtimer.config.soundPitch"))
                                .description(val -> OptionDescription.of(
                                        Component.translatable("breedtimer.config.soundPitch.desc")))
                                .binding(def.soundPitch, () -> cfg.soundPitch, v -> cfg.soundPitch = v)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.5f, 2.0f).step(0.05f))
                                .build())
                        .build())

                .build()
                .generateScreen(parent);
    }
}
