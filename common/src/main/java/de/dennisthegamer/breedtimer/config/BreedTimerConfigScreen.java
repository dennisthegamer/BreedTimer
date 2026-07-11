package de.dennisthegamer.breedtimer.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BreedTimerConfigScreen {

    public static Screen createConfigScreen(Screen parent) {
        BreedTimerConfig def = new BreedTimerConfig();
        BreedTimerConfig cfg = BreedTimerConfig.get();

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
                                .name(Component.translatable("breedtimer.config.showBabyTimer"))
                                .binding(def.showBabyTimer, () -> cfg.showBabyTimer, v -> cfg.showBabyTimer = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.compactMode"))
                                .binding(def.compactMode, () -> cfg.compactMode, v -> cfg.compactMode = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())

                // Tab 2: Rendering
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
                                .binding(def.fadeStartDistance, () -> cfg.fadeStartDistance, v -> cfg.fadeStartDistance = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(4, 32).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("breedtimer.config.fadeEndDistance"))
                                .binding(def.fadeEndDistance, () -> cfg.fadeEndDistance, v -> cfg.fadeEndDistance = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(4, 48).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.translatable("breedtimer.config.fovAngle"))
                                .binding(def.fovAngle, () -> cfg.fovAngle, v -> cfg.fovAngle = v)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(30, 180).step(5))
                                .build())
                        .option(Option.<Float>createBuilder()
                                .name(Component.translatable("breedtimer.config.backgroundOpacity"))
                                .binding(def.backgroundOpacity, () -> cfg.backgroundOpacity, v -> cfg.backgroundOpacity = v)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.05f))
                                .build())
                        .build())

                // Tab 3: Notifications
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("breedtimer.config.category.notifications"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable("breedtimer.config.playSound"))
                                .binding(def.playSound, () -> cfg.playSound, v -> cfg.playSound = v)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())

                .build()
                .generateScreen(parent);
    }
}
