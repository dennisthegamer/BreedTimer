# BreedTimer

A Minecraft mod for Fabric and NeoForge that displays floating timers above breedable animals and villagers, showing when they can breed again, how long babies take to grow up, and — when they can't breed right now — why.

## Features

- Floating breed cooldown timers above animals, villagers and now also dolphins (baby only) and,
  on `mc26.2`, sulfur cubes
- Baby growth timer display, including tadpoles
- Blocked-reason labels: untamed, hurt, needing to dismount, sitting, asleep, busy, scared during a
  thunderstorm (pandas), missing a jukebox (allays), or simply unable to breed at all — the label
  names the reason instead of a false "✓ Ready"
- Love mode and ready-to-breed indicators
- Optional food hints: an extra line naming what an animal or villager needs to eat before it will
  breed (off by default)
- Turtle egg, sniffer egg and dried ghast tracking, each with its own floating hatch-stage label
- Beehive honey level (0–5) with a campfire-safe-to-harvest marker
- Torchflower and pitcher crop growth stages
- Optional chicken egg and armadillo scute drop windows, inferred from the sound and shown as a
  narrowing window, never a countdown (off by default)
- Exact remaining growth time on a bucketed baby axolotl, tadpole or (`mc26.2`) sulfur cube, shown
  as a tooltip on the bucket and carried into the label when released
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
- Full in-game config screen (YACL), with a description on hover for every option
- Client-side only - no server required

## Compatibility

**This branch (`mc1.21.9-1.21.10`)**: Minecraft 1.21.9 and 1.21.10, **NeoForge only**.

| | `mc26.2` | `mc26.1` | `mc1.21.11` | `mc1.21.9-1.21.10` | `mc1.21.6-1.21.8` | `mc1.21.2-1.21.5` | `mc1.21-1.21.1` |
|---|---|---|---|---|---|---|---|
| Minecraft | 26.2 | 26.1–26.1.2 | 1.21.9–1.21.11 | 1.21.9–1.21.10 | 1.21.6–1.21.8 | 1.21.2–1.21.5 | 1.21–1.21.1 |
| Fabric | ✅ | ✅ | ✅ | — | ✅ | ✅ | ✅ |
| NeoForge | ✅ | ✅ | 1.21.11 only | ✅ | ✅ | ✅ | ✅ |

**On Fabric, do not use this branch.** The `mc1.21.11` Fabric jar already covers 1.21.9 through
1.21.11 in a single artifact, so this branch builds no Fabric jar at all — there is no Fabric
subproject in the tree.

### Why this branch exists

Minecraft moved two things at two different versions:

- **the render pipeline at 1.21.9** — `EntityRenderer.render` became
  `submit(…, SubmitNodeCollector, CameraRenderState)`
- **the entity packages at 1.21.11** — `animal.Cow` → `animal.cow.Cow`,
  `npc.Villager` → `npc.villager.Villager`, `resources.ResourceLocation` → `resources.Identifier`

Fabric is unaffected: its intermediary mappings stay stable across the whole band, which is why one
Fabric jar spans 1.21.9–1.21.11. NeoForge jars bake in Mojang mappings at compile time, so they
land on one side of each boundary. That leaves 1.21.9 and 1.21.10 between the two, matching neither
neighbour — `mc1.21.6-1.21.8` has the right class names but the old render pipeline, `mc1.21.11`
has the right render pipeline but the new class names. This branch is the `mc1.21.11` source
compiled against pre-1.21.11 class names, which satisfies both.

Neither range may be widened. This jar has 21 `net/minecraft` references that do not resolve on
1.21.11 — the exact mirror image of the 21 that the `mc1.21.11` NeoForge jar cannot resolve here.

### Feature availability

See `docs/PORTING.md` for the full per-branch table. Notably: the age lock (Golden Dandelion) is
`mc26.1`/`mc26.2` only; the nautilus, zombie nautilus and camel husk only exist from Minecraft
1.21.11, so they are **not** available on this branch; sulfur cube is `mc26.2` only; dried ghast and
happy ghast are present here (1.21.6 and up); dolphin growth and bucket ages are both present here.

- **Mod Loader**: NeoForge (see above for Fabric)
- **Java**: 21+ on this branch and the other 1.21.x branches, 25+ on `mc26.1`/`mc26.2`
- **YACL**: Optional (for config screen)

## Download

Download the latest release from [Modrinth](https://modrinth.com/mod/breedtimer) or [GitHub Releases](https://github.com/DennisTheGamer/BreedTimer/releases). This branch releases a single JAR:

- **NeoForge**: `breedtimer-neoforge-1.6.0+mc1.21.9-1.21.10.jar` — Minecraft 1.21.9 and 1.21.10.
- **Fabric**: not built here. Use `breedtimer-fabric-1.6.0+mc1.21.9-1.21.11.jar` from the
  `mc1.21.11` release — that one jar already covers 1.21.9 and 1.21.10.

## Installation

1. Install [NeoForge](https://neoforged.net/)
2. Download `breedtimer-neoforge-1.6.0+mc1.21.9-1.21.10.jar`
3. Place the JAR file in your `mods` folder
4. Launch Minecraft

## Configuration

Open the config screen via the NeoForge mod list entry. Every option shows a
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
- **Built with**: NeoForge, YACL (Architectury multiloader project; this branch builds the NeoForge target only)

## Support

- Report bugs on [GitHub Issues](https://github.com/DennisTheGamer/BreedTimer/issues)
- Visit the [Modrinth page](https://modrinth.com/mod/breedtimer) for more information
