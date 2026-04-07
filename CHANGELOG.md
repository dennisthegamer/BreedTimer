# Changelog

All notable changes to BreedTimer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
