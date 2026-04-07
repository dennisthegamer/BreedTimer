package com.example.breedtimer.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BreedTimerConfigScreen {

    public static Screen createConfigScreen(Screen parent) {
        return YetAnotherConfigLib.create(BreedTimerConfig.HANDLER, (defaults, config, builder) ->
                builder
                        .title(Component.translatable("breedtimer.config.title"))

                        // Tab 1: General
                        .category(ConfigCategory.createBuilder()
                                .name(Component.translatable("breedtimer.config.category.general"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("breedtimer.config.enabled"))
                                        .description(val -> dev.isxander.yacl3.api.OptionDescription.of(
                                                Component.translatable("breedtimer.config.enabled.desc")))
                                        .binding(defaults.enabled, () -> config.enabled, v -> config.enabled = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("breedtimer.config.showBabyTimer"))
                                        .binding(defaults.showBabyTimer, () -> config.showBabyTimer, v -> config.showBabyTimer = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("breedtimer.config.compactMode"))
                                        .binding(defaults.compactMode, () -> config.compactMode, v -> config.compactMode = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .build())

                        // Tab 2: Rendering
                        .category(ConfigCategory.createBuilder()
                                .name(Component.translatable("breedtimer.config.category.rendering"))
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("breedtimer.config.scanRadius"))
                                        .description(val -> dev.isxander.yacl3.api.OptionDescription.of(
                                                Component.translatable("breedtimer.config.scanRadius.desc")))
                                        .binding(defaults.scanRadius, () -> config.scanRadius, v -> config.scanRadius = v)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(4, 32).step(1))
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("breedtimer.config.fadeStartDistance"))
                                        .binding(defaults.fadeStartDistance, () -> config.fadeStartDistance, v -> config.fadeStartDistance = v)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(4, 32).step(1))
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("breedtimer.config.fadeEndDistance"))
                                        .binding(defaults.fadeEndDistance, () -> config.fadeEndDistance, v -> config.fadeEndDistance = v)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(4, 48).step(1))
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("breedtimer.config.fovAngle"))
                                        .binding(defaults.fovAngle, () -> config.fovAngle, v -> config.fovAngle = v)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(30, 180).step(5))
                                        .build())
                                .option(Option.<Float>createBuilder()
                                        .name(Component.translatable("breedtimer.config.backgroundOpacity"))
                                        .binding(defaults.backgroundOpacity, () -> config.backgroundOpacity, v -> config.backgroundOpacity = v)
                                        .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 1.0f).step(0.05f))
                                        .build())
                                .build())

                        // Tab 3: Notifications
                        .category(ConfigCategory.createBuilder()
                                .name(Component.translatable("breedtimer.config.category.notifications"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("breedtimer.config.playSound"))
                                        .binding(defaults.playSound, () -> config.playSound, v -> config.playSound = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .build())
        ).generateScreen(parent);
    }
}
