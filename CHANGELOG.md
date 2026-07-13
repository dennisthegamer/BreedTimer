# Changelog

All notable changes to BreedTimer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.1] - 2026-07-13

### Added
- **Backport to Minecraft 1.21.6–1.21.8** — same feature set as the 1.21.9–1.21.11 release, built against 1.21.8 with Fabric and NeoForge jars from one shared codebase.

## [1.3.0] - 2026-07-11

### Added
- **NeoForge support** — BreedTimer now runs on both Fabric and NeoForge. Each release ships two jars built from one shared codebase: `breedtimer-fabric-…` and `breedtimer-neoforge-…`. On NeoForge, the config screen is reachable via the native mod list entry (no ModMenu needed).

### Changed
- Restructured the project into `common/` (shared code and mixins) plus `fabric/` and `neoforge/` loader subprojects, and migrated the sources from Yarn to Mojang mappings. No gameplay changes.
- The NeoForge jar targets Minecraft 1.21.11 only; the Fabric jar continues to support 1.21.9–1.21.11.

## [1.1.1] - 2026-05-11

### Added
- **Turtle egg floating labels** — Labels now appear above turtle egg blocks showing the current hatch stage:
  - `Nx Egg` — freshly laid (cyan)
  - `Nx Cracking` — first cracks (orange)
  - `Nx Hatching!` — about to hatch (green)
- **Compact HUD: turtle eggs** — When turtle eggs are nearby, a dedicated line is shown in compact mode: `Turtle Eggs  X fresh  Y cracking  Z hatching`

> **Note:** Turtle egg hatch time is random-tick based and non-deterministic — the hatch stage (0–2 of 3) is displayed rather than an exact remaining time.

### Fixed
- **Villager tracking completely fixed** — `VillagerEventMixin` was never registered in the mixin config, meaning villager cooldowns and baby timers were never tracked. A previous fix attempt crashed on startup because it injected into `Villager.handleEntityEvent` (not declared in `AbstractVillager`). The mixin now correctly injects into `AbstractVillager.addParticlesAroundSelf` to reliably detect heart particles.

---

## [1.1.0] - 2026-05-10

### Added

**Villager support**
- Adult Villagers now show `✓ Ready` (green) by default
- After breeding (detected when Villager stops showing heart particles), a 5-minute breed cooldown timer is shown — identical behavior to animals
- Baby Villagers show `Growing: M:SS` countdown (20 minutes until adult)
- Compact mode shows a dedicated second HUD line: `Villagers  X ready  Y cooldown  Z babies`
- Sound alert (Pling) plays when a Villager comes back from cooldown, consistent with animal ready-sound
- Baby Villager and Villager cooldown timers persist across world reloads
- FOV culling, distance fade, and line-of-sight checks apply to Villager labels — identical to animal behavior
- WanderingTrader intentionally excluded — cannot breed

**New animal coverage**
- Added **Strider** (breeds with Warped Fungus)
- Added **Hoglin** (breeds with Crimson Fungus)
- Added **Armadillo** (breeds with Spider Eyes)
- Added **MushroomCow / Mooshroom** (was previously missed — extends `AbstractCow`, not `Cow`)
- Added **Nautilus** (breeds when tamed, new mob in MC 26.1)

**Separate enable toggles for Animals and Villagers**
- New config options: `Show Animals` and `Show Villagers` in the General tab
- Each can be disabled independently; the N-keybind still toggles the whole mod
- Compact HUD lines only appear for enabled entity types
- Disabling a type also stops tick-tracking and sound for that type

**Timer sync with `/tick sprint`**
- All cooldown and baby-growth timers now use server game time (`level.getGameTime()`) for delta calculation
- Timers speed up correctly when the server runs at an accelerated tick rate

### Changed
- Compact mode HUD now labels each line: `Animals  …` and `Villagers  …` for clear distinction

---

## [1.0.0] - 2026-04-06

### Added
- Initial release
- Floating breed cooldown timers above animals
- Baby growth timer display
- Love mode and ready-to-breed indicators
- Distance-based fade rendering with configurable start/end distances
- FOV-based culling to only show timers for visible animals
- Configurable scan radius (4-32 blocks)
- Adjustable background opacity for timer labels
- Compact mode for minimal display
- Sound notification when animals become ready to breed
- Full YACL config screen with tabbed layout:
  1. **General**: Enable/disable mod, baby timers, compact mode
  2. **Rendering**: Scan radius, fade distances, FOV angle, background opacity
  3. **Notifications**: Sound alerts
- ModMenu integration
- Dedicated "Breed Timer" keybind category in controls
- Keybind to toggle mod on/off (`N`)
- Keybind to toggle compact mode (`B`)
- Localization support (English & German)
- Client-side only - no server installation required
- Compatibility with Minecraft 26.1+
- Fabric Loader 0.18.4+ support
- Fabric API integration
