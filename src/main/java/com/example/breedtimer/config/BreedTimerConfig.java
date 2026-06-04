package com.example.breedtimer.config;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

public class BreedTimerConfig {

    public static final ConfigClassHandler<BreedTimerConfig> HANDLER =
            ConfigClassHandler.createBuilder(BreedTimerConfig.class)
                    .id(Identifier.of("breedtimer", "config"))
                    .serializer(config -> GsonConfigSerializerBuilder.create(config)
                            .setPath(FabricLoader.getInstance().getConfigDir()
                                    .resolve("breedtimer.json5"))
                            .setJson5(true)
                            .build())
                    .build();

    @SerialEntry public boolean enabled = true;
    @SerialEntry public boolean showAnimals  = true;
    @SerialEntry public boolean showVillagers = true;
    @SerialEntry public int scanRadius = 16;
    @SerialEntry public int fadeStartDistance = 16;
    @SerialEntry public int fadeEndDistance = 20;
    @SerialEntry public int fovAngle = 90;
    @SerialEntry public boolean showBabyTimer = true;
    @SerialEntry public float backgroundOpacity = 0.5f;
    @SerialEntry public boolean playSound = true;
    @SerialEntry public boolean compactMode = false;

    public static BreedTimerConfig get() {
        return HANDLER.instance();
    }
}
