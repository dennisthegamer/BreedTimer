# BreedTimer

**Always know when your animals and villagers are ready to breed!**

This lightweight client-side mod for Fabric and NeoForge displays floating timers above breedable animals and villagers, showing exactly when they can breed again, how long babies take to grow up — and, when they can't breed right now, why.

## Features

### Breed Cooldown Timers
- **Floating labels** — Clear countdown timers displayed directly above each animal or villager
- **Baby growth timers** — See exactly when baby animals, baby villagers and tadpoles will grow up
- **Love mode indicator** — Visual feedback when animals are in love mode
- **Ready indicator** — Instantly see which animals and villagers are ready to breed
- **Blocked reasons** — An animal or villager that can't breed right now says why instead of
  falsely reading "✓ Ready": untamed, hurt, ridden, sitting, asleep, busy, scared during a
  thunderstorm, repelled near a warped fungus, or unable to breed at all (a mule, for example)
- **Optional food hints** — Turn on "Show Breeding Food" to add a line under each label naming
  what the animal or villager needs to eat next

### Villager Support
- **Adult villagers** show `✓ Ready` by default
- **Breed cooldown** starts after villagers stop showing heart particles — 5-minute timer identical to animals
- **Baby villager** growth countdown — 20 minutes until adult
- **Sleeping villagers** are marked "Asleep" instead of a false "Ready", and courting with no free
  bed nearby no longer starts a fake cooldown
- **Food hints** name what a villager still needs to eat before it will breed, when the setting is on
- **Compact HUD** shows a dedicated villager line, including how many are asleep:
  `Villagers  X ready  Y cooldown  Z babies  W asleep`

### Egg & Block Tracking
- **Turtle eggs** — Floating labels above turtle egg blocks showing the current hatch stage — Fresh (cyan), Cracking (orange), Hatching! (green)
- **Sniffer eggs** — The same three-stage label; an egg placed on a moss block hatches in half the time, and the label says "(boosted)" while that applies
- **Beehives** — Honey level 0–5 on the label, plus whether a lit campfire underneath makes harvesting safe right now
- **Torchflower and pitcher crops** — A growth-stage label on both, the pitcher plant turning green once ripe
- **Chicken egg & armadillo scute windows** *(optional, off by default)* — After hearing the drop sound once, a narrowing time window for the next one — never an exact countdown, since the mod only ever hears a sound, not a synced timer
- **Compact HUD** shows a dedicated line for each kind of egg or block that's actually nearby

### Allays
- **Duplication tracking** — An allay duplicates instead of breeding, on a flat five-minute cooldown the mod can't see the start of on its own. It reads "Not ready" until the mod has watched it duplicate once, then carries an exact countdown like any other timer
- **"Needs a jukebox"** — A ready allay that isn't currently dancing near a jukebox says so, instead of a "✓ Ready" it can't act on

### Bucket Ages
- **Exact age from a bucket** — Bucketing a baby axolotl or tadpole shows its exact remaining growth time as a tooltip on the bucket, carried into the label when released

### Smart Rendering
- **Distance-based fading** — Timers fade out smoothly at longer distances
- **FOV culling** — Only renders timers for entities within your field of view
- **See-through labels** — Labels stay readable through terrain, so a whole pen reads at a glance
- **Labels through walls — optional** — On by default, matching how the mod has always drawn. Switch it off and a
  label is hidden while terrain stands between you and the animal
- **Look-at only** — Optional: label just the animal under your crosshair instead of the whole pen
- **State filter** — Show or hide Ready, Cooldown, Babies, In love and Blocked labels independently,
  for both floating labels and the compact HUD counts, plus a master switch for every block label
- **Configurable scan radius** — Sets how far the compact HUD, ready chime and tracked eggs/blocks
  look; the mod always scans at least as far as your fade-end distance too, and floating labels
  themselves follow the fade distances below rather than this slider
- **Split label/HUD opacity, and a HUD text scale** — Independent background opacity for floating
  labels and the compact HUD panel, plus an adjustable HUD text scale
- **Optional text outline** — A per-glyph outline for labels, for the cases a low background
  opacity would otherwise make hard to read
- **Colour presets** — Default, colour-blind (red–green) or high-contrast, covering every label and
  HUD colour at once; the `✓ ❤ 🔒 ✖` icons carry state without colour either way
- **Freely positionable HUD** — Drag the timer overlay anywhere on screen in the built-in editor
  and save named position presets
- **Count only what is in view** — Optional setting for the compact HUD; restores the old behaviour
  of counting only what is inside the view cone and fade range, instead of everything loaded
  nearby (the compact HUD counts everything loaded by default, even off-screen)
- **"Next ready in m:ss"** — The compact HUD shows the soonest cooldown across everything tracked

### Player-Friendly
- **Compact mode** — A minimal HUD with lines shown only when relevant: Animals, Allays,
  Turtle Eggs, Sniffer Eggs, Beehives, Crops, Villagers and Next Ready
- **Sound notifications** — Optional audio alert that fires once, right when an animal or villager
  actually becomes ready — not every time you look at it again — with adjustable volume and pitch
- **Action-bar feedback** — Both keybinds show a brief confirmation of what they just did
- **Separate toggles** — Enable or disable animals and villagers independently
- **Timer sync** — Timers speed up correctly when using `/tick sprint`
- **In-game config** — Full YACL config screen via ModMenu, with a description on hover for every
  option
- **Keybinds** — Dedicated "Breed Timer" category in controls
- **Client-side only** — No server installation required

## Supported Animals

All breedable animals in MC 1.21–1.21.1 are covered:

Cow, Mooshroom, Sheep, Pig, Chicken, Rabbit, Horse, Donkey, Mule, Llama, Wolf, Cat, Ocelot, Fox, Panda, Goat, Camel, Sniffer, Bee, Turtle, Axolotl, Frog, Strider, Hoglin, Armadillo

Plus **Villagers** with full breed cooldown and baby tracking, **Tadpoles** with a growth timer, and **Allays** with duplication tracking.

## Customization

Configure everything in-game via ModMenu — every option shows a description on hover:

**General**
- Toggle mod on/off
- Show Animals (toggle independently)
- Show Villagers (toggle independently)
- Show Breeding Food — adds a food-hint line under labels
- Enable compact mode
- Count only what is in view (compact HUD)
- HUD Background Opacity, HUD Text Scale
- Edit HUD position… — opens the drag-and-drop HUD editor with position presets

**Filter**
- Show Ready / Cooldown / Babies / In love / Blocked labels — each independently, for both
  floating labels and the compact HUD
- Show egg & scute timers — the chicken/armadillo drop windows (opt-in, off by default)
- Show block labels — master switch for turtle eggs, sniffer eggs, beehives and crops

**Keybinds** (Controls > Breed Timer)
- `N` — Toggle mod on/off (with action-bar confirmation)
- `B` — Toggle compact mode (with action-bar confirmation)

**Rendering**
- Scan radius (4–32 blocks)
- Fade start/end distance
- Label cone angle
- Label only what you look at (optional)
- Labels through walls (on by default)
- Colour Preset — Default, Colour-blind, High contrast
- Label Background Opacity
- Text outline (optional)

**Notifications**
- Play sound when an animal or villager becomes ready to breed
- Sound Volume, Sound Pitch

## Requirements

**This build**: Minecraft 1.21–1.21.1 (Fabric and NeoForge), Java 21+ — the oldest of the mod's
six supported builds.

BreedTimer 1.6.0 is available for Minecraft 1.21 through 26.2, across six version-matched builds:

| | `mc26.2` | `mc26.1` | `mc1.21.11` | `mc1.21.6-1.21.8` | `mc1.21.2-1.21.5` | `mc1.21-1.21.1` |
|---|---|---|---|---|---|---|
| Minecraft | 26.2 | 26.1–26.1.2 | 1.21.9–1.21.11 | 1.21.6–1.21.8 | 1.21.2–1.21.5 | 1.21–1.21.1 |
| Java | 25+ | 25+ | 21+ | 21+ | 21+ | 21+ |

Some features are version-gated: the age lock (Golden Dandelion), sulfur cube and nautilus do not
exist on this build at all — the mobs and gates they add are absent here; camel husk, dried ghast
and happy ghast are `mc1.21.6-1.21.8` and up only, so none of them are on this build either. A
growth countdown for a baby dolphin is also not on this build: at 1.21/1.21.1 it is a `WaterAnimal`,
not the ageable superclass the label mixin needs to hook, so it never becomes a candidate in the
first place. Exact bucket-release ages (a bucketed baby axolotl or tadpole's exact remaining growth
time, both as a bucket tooltip and carried into its label once released) **are** on this build,
unlike on `mc1.21.2-1.21.5` — that build's own range straddles two independent 1.21.5 API breaks
that block a single implementation there; this build's whole range sits below both, so one
implementation covers it with no gap. See the GitHub repository's `docs/PORTING.md` for the full
table.

- **Mod Loader**: Fabric (0.17.0+, requires Fabric API) or NeoForge
- **YACL**: Optional (for in-game config screen)
- **ModMenu** (Fabric only): Optional (for in-game config screen)

## Perfect For

- **Breeders** — Efficiently manage large animal farms and villager trading halls
- **Farmers** — Track breeding cooldowns across multiple pens at a glance
- **Speedrunners** — Know exactly when animals are ready without guessing
- **Everyone** — Anyone who breeds animals or villagers in Minecraft!

## Links

- **Issues & Bugs**: [GitHub Issues](https://github.com/DennisTheGamer/BreedTimer/issues)
- **Source Code**: [GitHub Repository](https://github.com/DennisTheGamer/BreedTimer)

## License

This mod is open-source and licensed under the MIT License.

---

**Made with love by Dennis_thegamer**
