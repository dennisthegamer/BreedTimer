# BreedTimer

**Always know when your animals and villagers are ready to breed!**

This lightweight client-side Fabric mod displays floating timers above breedable animals and villagers, showing exactly when they can breed again and how long babies take to grow up.

## Features

### Breed Cooldown Timers
- **Floating labels** — Clear countdown timers displayed directly above each animal or villager
- **Baby growth timers** — See exactly when baby animals and villagers will grow up
- **Love mode indicator** — Visual feedback when animals are in love mode
- **Ready indicator** — Instantly see which animals and villagers are ready to breed

### Villager Support
- **Adult villagers** show `✓ Ready` by default
- **Breed cooldown** starts after villagers stop showing heart particles — 5-minute timer identical to animals
- **Baby villager** growth countdown — 20 minutes until adult
- **Compact HUD** shows a dedicated villager line: `Villagers  X ready  Y cooldown  Z babies`

### Smart Rendering
- **Distance-based fading** — Timers fade out smoothly at longer distances
- **FOV culling** — Only renders timers for entities within your field of view
- **Line-of-sight check** — No timers through walls
- **Configurable scan radius** — Control how far the mod looks for animals and villagers
- **Background opacity** — Adjustable background for better readability

### Player-Friendly
- **Compact mode** — Minimal two-line HUD display (Animals / Villagers) for less screen clutter
- **Sound notifications** — Optional audio alert when an animal or villager becomes ready to breed again
- **Separate toggles** — Enable or disable animals and villagers independently
- **Timer sync** — Timers speed up correctly when using `/tick sprint`
- **In-game config** — Full YACL config screen via ModMenu
- **Keybinds** — Dedicated "Breed Timer" category in controls
- **Client-side only** — No server installation required

## Supported Animals

All breedable animals in MC 26.1 are covered:

Cow, Mooshroom, Sheep, Pig, Chicken, Rabbit, Horse, Donkey, Mule, Llama, Wolf, Cat, Ocelot, Fox, Panda, Goat, Camel, Sniffer, Bee, Turtle, Axolotl, Frog, Strider, Hoglin, Armadillo, Nautilus

Plus **Villagers** with full breed cooldown and baby tracking.

## Customization

Configure everything in-game via ModMenu:

**General**
- Toggle mod on/off
- Show Animals (toggle independently)
- Show Villagers (toggle independently)
- Show/hide baby growth timers
- Enable compact mode

**Keybinds** (Controls > Breed Timer)
- `N` — Toggle mod on/off
- `B` — Toggle compact mode

**Rendering**
- Scan radius (4–32 blocks)
- Fade start/end distance
- Field of view angle
- Background opacity

**Notifications**
- Play sound when an animal or villager becomes ready to breed

## Requirements

- **Minecraft**: 26.1+
- **Fabric Loader**: 0.18.4 or higher
- **Fabric API**: Required
- **Java**: 25 or higher
- **ModMenu** + **YACL**: Optional (for in-game config screen)

## Perfect For

- **Breeders** — Efficiently manage large animal farms and villager trading halls
- **Farmers** — Track breeding cooldowns across multiple pens at a glance
- **Speedrunners** — Know exactly when animals are ready without guessing
- **Everyone** — Anyone who breeds animals or villagers in Minecraft!

## Links

- **Issues & Bugs**: [GitHub Issues](https://github.com/DennisTheGamer/breed-timer/issues)
- **Source Code**: [GitHub Repository](https://github.com/DennisTheGamer/breed-timer)

## License

This mod is open-source and licensed under the MIT License.

---

**Made with love by DennisTheGamer**
