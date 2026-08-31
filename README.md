# BreedTimer

A Minecraft mod for Fabric and NeoForge that displays floating timers above breedable animals and villagers, showing when they can breed again, how long babies take to grow up, and — when they can't breed right now — why.

## Features

- Floating breed cooldown timers above animals, villagers and now also dolphins (baby only)
- Baby growth timer display, including tadpoles
- Blocked-reason labels: untamed, hurt, needing to dismount, sitting, asleep, busy, scared during a
  thunderstorm (pandas), repelled (hoglins near a warped fungus), missing a jukebox (allays), or
  simply unable to breed at all — the label names the reason instead of a false "✓ Ready"
- Love mode and ready-to-breed indicators
- Optional food hints: an extra line naming what an animal or villager needs to eat before it will
  breed (off by default)
- Turtle egg, sniffer egg and dried ghast tracking, each with its own floating hatch-stage label
- Beehive honey level (0–5) with a campfire-safe-to-harvest marker
- Torchflower and pitcher crop growth stages
- Optional chicken egg and armadillo scute drop windows, inferred from the sound and shown as a
  narrowing window, never a countdown (off by default)
- Exact remaining growth time on a bucketed baby axolotl or tadpole, shown as a tooltip on the
  bucket and carried into the label when released
- Allay duplication tracking, with an exact five-minute cooldown once a duplication is observed
- A state filter (Ready / Cooldown / Babies / In love / Blocked) plus a block-label master switch,
  so any combination of labels and HUD counts can be switched off
- "Next ready in m:ss" — the compact HUD's soonest cooldown across everything tracked
- Optional look-at-only label mode, and a toggle to hide labels through walls (labels are shown
  through walls by default, as the mod has always drawn them)
- Panda cub personality-odds prediction from both parents' genes, shown once a non-default outcome
  is at least 1% likely
- Distance-based fading and FOV culling
- Compact mode for minimal display
- Sound notification when an animal or villager becomes ready — fires once, at the moment it
  actually becomes ready, with adjustable volume and pitch
- Action-bar confirmation when either keybind is pressed
- Optional per-glyph text outline for labels, and a colour-blind (red–green) or high-contrast
  colour preset for every label and HUD colour
- Freely positionable HUD with named, saveable position presets, independent label/HUD background
  opacity and an adjustable HUD text scale
- Dedicated keybind category with toggle on/off (`N`) and compact mode (`B`)
- Full in-game config screen (YACL + ModMenu), with a description on hover for every option
- Client-side only - no server required

## Compatibility

**This branch (`mc1.21.6-1.21.8`)**: Minecraft 1.21.6–1.21.8, Fabric and NeoForge.

| | `mc26.2` | `mc26.1` | `mc1.21.11` | `mc1.21.6-1.21.8` | `mc1.21.2-1.21.5` | `mc1.21-1.21.1` |
|---|---|---|---|---|---|---|
| Minecraft | 26.2 | 26.1–26.1.2 | 1.21.9–1.21.11 | 1.21.6–1.21.8 | 1.21.2–1.21.5 | 1.21–1.21.1 |

See `docs/PORTING.md` for the full per-branch feature-availability table. Notably: the age lock
(Golden Dandelion), sulfur cube and nautilus do not exist on this branch — none of the three animals
or gates they add are present here; camel husk is `mc26.1`, `mc26.2` and `mc1.21.11` only; dolphin is
not on `mc1.21-1.21.1`; bucket ages are not on `mc1.21.2-1.21.5`; dried ghast and happy ghast are
1.21.6 and up, so this branch has both.

- **Mod Loader**: Fabric (0.17.0+, requires Fabric API) or NeoForge
- **Java**: 21+
- **YACL**: Optional (for config screen)
- **ModMenu** (Fabric only): Optional (for config screen)

## Download

Download the latest release from [Modrinth](https://modrinth.com/mod/breedtimer) or [GitHub Releases](https://github.com/DennisTheGamer/BreedTimer/releases). Each release contains one JAR per mod loader (`breedtimer-fabric-1.6.1+mc1.21.6-1.21.8.jar` and `breedtimer-neoforge-1.6.1+mc1.21.6-1.21.8.jar`).

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) or [NeoForge](https://neoforged.net/)
2. On Fabric: download [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download BreedTimer (the JAR matching your loader)
4. Place the JAR file(s) in your `mods` folder
5. Launch Minecraft

## Configuration

Open the config screen via ModMenu (Fabric) or the mod list entry (NeoForge). Every option shows a
description on hover. Settings are organized in four tabs:

- **General** - Enable/disable the mod, show animals and villagers independently, the "Show
  Breeding Food" hint, compact mode, whether the compact HUD counts only what is currently in view,
  HUD background opacity and HUD text scale. This tab also holds an **Edit HUD position…** button —
  not a separate tab — that opens an editor where you drag the HUD anywhere on screen and save
  named position presets
- **Filter** - Five independent state checkboxes (Ready, Cooldown, Babies, In love, Blocked) that
  control both floating labels and the compact HUD counts, plus "Show egg & scute timers" (the
  chicken/armadillo drop windows, opt-in) and "Show block labels" — a master switch for turtle
  eggs, sniffer eggs, dried ghasts, beehives and crops together
- **Rendering** - Scan radius, fade distances, label cone angle, an optional look-at-only label
  mode, an optional through-walls toggle (on by default, matching how the mod has always drawn), a
  colour preset (Default / Colour-blind / High contrast), label background opacity, and an optional
  text outline. The mod always scans at least as far as the fade-end distance, even if the scan
  radius slider is set lower
- **Notifications** - Sound alert when an animal or villager becomes ready, plus its volume and
  pitch

Both keybinds now show a brief action-bar confirmation when pressed. Keybinds are listed under
**Controls > Breed Timer**:
- `N` - Toggle mod on/off
- `B` - Toggle compact mode

Config file is saved at `config/breedtimer.json`. An older `breedtimer.json5` from a
previous version is read once and migrated automatically.

## Building from Source

```bash
git clone https://github.com/DennisTheGamer/BreedTimer.git
cd BreedTimer
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- **Author**: Dennis_thegamer
- **Built with**: Fabric, Fabric API, NeoForge, YACL

## Support

- Report bugs on [GitHub Issues](https://github.com/DennisTheGamer/BreedTimer/issues)
- Visit the [Modrinth page](https://modrinth.com/mod/breedtimer) for more information
