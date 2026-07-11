# BreedTimer

A Minecraft mod for Fabric and NeoForge that displays floating timers above breedable animals, showing when they can breed again and how long baby animals take to grow up.

## Features

- Floating breed cooldown timers above animals
- Baby growth timer display
- Love mode and ready-to-breed indicators
- Distance-based fading and FOV culling
- Compact mode for minimal display
- Sound notifications when animals are ready
- Dedicated keybind category with toggle on/off (`N`) and compact mode (`B`)
- Full in-game config screen (YACL + ModMenu)
- Client-side only - no server required

## Compatibility

- **Minecraft**: 1.21.9–1.21.11 (Fabric) / 1.21.11 (NeoForge)
- **Mod Loader**: Fabric (0.17.0+, requires Fabric API) or NeoForge
- **Java**: 21+
- **YACL**: Optional (for config screen)
- **ModMenu** (Fabric only): Optional (for config screen)

## Download

Download the latest release from [Modrinth](https://modrinth.com/mod/breed-timer) or [GitHub Releases](https://github.com/DennisTheGamer/breed-timer/releases). Each release contains one JAR per mod loader (`breedtimer-fabric-…` and `breedtimer-neoforge-…`).

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) or [NeoForge](https://neoforged.net/)
2. On Fabric: download [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download BreedTimer (the JAR matching your loader)
4. Place the JAR file(s) in your `mods` folder
5. Launch Minecraft

## Configuration

Open the config screen via ModMenu (Fabric) or the mod list entry (NeoForge). Settings are organized in three tabs:

- **General** - Enable/disable the mod, baby timers, compact mode
- **Rendering** - Scan radius, fade distances, FOV angle, background opacity
- **Notifications** - Sound alerts when animals are ready

Keybinds are listed under **Controls > Breed Timer**:
- `N` - Toggle mod on/off
- `B` - Toggle compact mode

Config file is saved at `config/breedtimer.json5`.

## Building from Source

```bash
git clone https://github.com/DennisTheGamer/breed-timer.git
cd breed-timer
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- **Author**: DennisTheGamer
- **Built with**: Fabric, Fabric API, NeoForge, YACL

## Support

- Report bugs on [GitHub Issues](https://github.com/DennisTheGamer/breed-timer/issues)
- Visit the [Modrinth page](https://modrinth.com/mod/breed-timer) for more information
