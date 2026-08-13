# Changelog

All notable changes to BreedTimer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.6.0] - 2026-08-06

### Fixed

- **Feeding a baby now shortens its timer.** Feeding a baby speeds up its growth by 10 % of the
  remaining time, but the timer ignored this and kept counting down as if nothing had happened —
  so a well-fed animal grew up long before the timer said it would. Feeds are now picked up and
  applied with the game's own formula, rounding included, so the countdown follows the animal. 
  (Thanks for the report!)
- **…including horses, donkeys, mules, llamas and camels.** These are fed through a different path
  in the game than every other animal, so the first version of the fix above never saw them and
  their timers still ignored feeding. They are now covered too — and with their own numbers, which
  really do differ: a hay bale takes three minutes off a foal but only a minute and a half off a
  cria, and a camel gains a flat ten seconds no matter what you hand it.
- **The baby growth timer no longer jumps back to 20:00.** When the estimated growth time ran out
  while the animal was still a baby, the entry was discarded and immediately re-created with the
  full 20 minutes, so the countdown looped forever instead of ending. It now stops at the end of
  the estimate and reads "Almost grown" until the animal actually grows up. Affected baby animals
  and baby villagers alike.
- **Timer labels are readable again.** The text was drawn underneath its own background box,
  which at the default 50 % background opacity left it at exactly half brightness, and
  depending on the viewing angle the animal or the terrain could clip it. Labels are now drawn
  the way the game draws its own name tags — a see-through pass carrying the background, then a
  crisp pass on top — so they stay bright and no longer vanish behind geometry. Applies to
  animal, villager and turtle egg labels.
- **Animals the game refuses to breed no longer read "✓ Ready".** An untamed horse, a wolf you have
  not tamed, a mule — all of them were shown as ready to breed, because the mod only ever tracked
  the cooldown and never the conditions the game itself checks. They now say what is in the way
  instead of promising something that cannot happen.
- **Switching the display off no longer costs the timers time.** Animals keep growing and breeding
  while the labels are hidden, but the mod stopped counting along with them — so turning the
  display back on showed the values from the moment it was switched off, as if nothing had
  happened in between. The bookkeeping now runs regardless of what is on screen, and the same
  applies to the animal and villager categories individually. Timers still pause for animals that
  are out of range, because those genuinely stop on the server too.
- **Breeding a horse with a donkey now starts both cooldowns.** When two animals bred, the mod
  looked for the second parent among animals of the exact same kind — but a horse pairs with a
  donkey to make a mule, and a llama pairs with a trader llama. In those two cases the second
  parent was never found and kept reading "✓ Ready" for its whole five-minute cooldown.
- **Courting with no free bed nearby no longer starts a fake five-minute cooldown.** When a
  courting pair can't find a free bed, the game gives up and broadcasts only the "courtship
  failed" event — no child is born, so there is nothing to time. The mod used to treat any
  courtship event as a birth and started a cooldown anyway; it now only starts one when a birth
  actually happened.
- **Blocking conditions now outrank love mode.** Most of what stops Minecraft from breeding an
  animal is checked separately from falling in love, so an animal could be in love and still never
  breed — a tamed mule fed a golden carrot, an injured or ridden horse, a panda with no bamboo, a
  sitting wolf. These used to read "❤ In Love!" anyway, promising something that could not happen;
  they now show the actionable reason instead (hearts still appear above the animal's head — that
  part is vanilla). For the same reason, a cooldown sitting behind a gate is now reported after the
  gate: an untamed horse on cooldown shows "🔒 Tame first" rather than a countdown, since taming is
  the one the player can act on.
- **Turning "Play Sound When Ready" back on no longer replays a burst of chimes for animals that
  were already ready.** Readiness bookkeeping now runs regardless of the setting; previously it
  stopped while muted, so every animal that had become ready in the meantime looked newly ready the
  instant sound came back on.
- **The ready chime now fires once, right when an animal becomes ready, instead of every time you
  glance at it.** Readiness is tracked for every loaded animal, not only the ones on screen, so
  looking away and back no longer replays the chime for an animal that was already ready when you
  looked away. The sound still only plays for an animal you can actually see at the exact moment it
  turns ready.
- **Each singleplayer world now has its own save file, keyed so two same-named worlds no longer
  share a save file.** Breeding cooldowns are tracked per world, but a world's identity used to come from its
  display name — so two singleplayer worlds sharing a name shared one save file too. Worlds are now
  identified by their save-folder name instead, which the game itself never lets two worlds share.
  A world already tracked under the old scheme is picked up seamlessly: its existing save file is
  read once, the first time it is joined under the new scheme, and left in place from then on —
  every save after that goes to the new file.
- **The compact HUD no longer counts a drying-out dried ghast as ready.** A dried ghast that reached
  full hydration but has since left the water is ticking back down, not about to hatch — the
  floating label already read "Drying out" for this state, but the compact HUD's dried-ghast count
  still filed it under "ready". It is now counted alongside the still-soaking ones instead.
- **Turning the mod, or its animal/villager display, back on no longer replays a burst of chimes
  for everything that became ready while it was off.** The mute-burst fix above stopped the chime
  setting itself from causing this; the same burst could still happen by switching "Enabled", "Show
  Animals" or "Show Villagers" back on. Readiness bookkeeping is now primed instead of chimed for on
  each of those transitions too.

> **Note:** Feeding stops helping over the last ten seconds. The game works the speed-up out in
> whole seconds, and a tenth of nine seconds rounds down to zero — so from `0:09` on, feeding
> changes nothing and the timer simply runs out on its own. The mod deliberately copies that
> rounding instead of rounding in your favour, so the countdown and the animal stay in step.

### Added
- **Age-locked babies are marked as such.** A baby held at its current age with a Golden Dandelion
  never grows up. Rather than showing a countdown that could never finish, such animals now read
  "🔒 Age locked" and their timer is frozen until the lock is removed.
- **Some babies read "🔒 Never grows up" instead of a countdown.** A skeleton horse or zombie horse
  foal never becomes an adult — vanilla holds it at baby forever — so a ticking timer that could
  never reach zero is replaced with a label that says so outright.
- **Breeding conditions are shown on the label.** Where an animal is blocked, the label names the
  reason: "🔒 Tame first" for untamed horses, donkeys, llamas, camels, wolves, cats and nautiluses;
  "❤ Needs full health" for a hurt equine (the game demands full hearts, and this catches the
  frustrating case of a horse sitting one half-heart short); "Dismount first" for one being ridden;
  "Tell it to stand" for a tamed wolf that has been told to sit — it genuinely cannot be bred with
  while sitting; "Busy" for a turtle carrying an egg or an armadillo rolled up; "Needs bamboo
  nearby" for a panda with none in reach; and "✖ Cannot breed" in grey for every animal the game
  never breeds at all — mule, camel husk, happy ghast, polar bear, parrot, skeleton horse, zombie
  horse and zombie nautilus. The compact HUD gained a matching "n blocked" counter.
- **Sleeping villagers are marked as such.** A villager in bed cannot breed, but was still shown as
  ready. It now reads "Asleep", and the compact HUD counts it under "n asleep".
- **The compact HUD now counts everything within scan radius, not just what's on screen.**
  Previously a count dropped the instant the animal or villager left your view cone or crossed the
  fade distance, so the numbers flickered as you simply turned around even though nothing about the
  farm had changed. Counts are now ambient and stable, matching how the egg and dried-ghast lines
  already worked. A new "Count only what is in view" setting restores the old in-view-only counting
  for anyone who preferred it.
- **Optional: show what an animal eats.** A new "Show Breeding Food" setting — off by default —
  adds a grey line under each animal label naming the items that will feed it, e.g. "Feed: Wheat".
  Where an animal accepts a real choice the list wraps onto a second line rather than stopping
  short — a chicken shows all six kinds of seed — and only what still will not fit becomes a
  "+n" count. The list is not a hard-coded table: the mod offers every registered item to the
  animal's own food check, so a data pack that changes a breeding tag is reflected automatically,
  and an untamed nautilus correctly names its taming items rather than its breeding food. Villagers
  get the same line, listing what they need to eat before they will breed.
- **Sniffer eggs are tracked like turtle eggs.** A sniffer egg goes through the same three hatch
  stages, so it now gets the same floating label — "Sniffer Egg", "Cracking", "Hatching!" — and its
  own "Sniffer Eggs" line in the compact HUD when one is nearby. A sniffer egg placed on a moss
  block hatches in half the usual time, and the label now says so, appending "(boosted)" for as
  long as the boost applies.
- **Dried ghasts are tracked too.** A dried ghast block ripens through its hydration levels while
  waterlogged, then hatches into a baby happy ghast — so it now gets a floating label of its own:
  "Dried Ghast", "Soaking", "Ready to hatch!" — and a "Dried Ghasts" line in the compact HUD
  counting how many nearby are dry, soaking or ready. As with turtle and sniffer eggs, only the
  stage is shown, never a countdown: ripening depends on random ticks, so any time figure would be
  a lie.
- **Allays get their own duplication label.** An allay duplicates instead of breeding, on a flat
  five-minute cooldown the mod cannot see the start of on its own — so a newly-seen allay on
  cooldown reads "Not ready" with no time, and only once the mod has actually watched it duplicate
  does the label carry an exact countdown, then "✓ Ready" again. The compact HUD gained a matching
  "Allays" line.
- **Tadpoles show a growth timer.** Frog breeding produces frogspawn, which hatches into tadpoles,
  which turn into frogs — but only the frogs at either end were ever tracked. A tadpole now carries
  the same countdown as any other young animal, and reads "🔒 Age locked" if it has been held with
  a Golden Dandelion.
- **Dolphin growth timers.** A baby dolphin now gets the same growth countdown as any other young
  animal; an adult dolphin gets no label at all, since dolphins do not breed.
- **Beehive honey level.** A floating label over every beehive and bee nest reads the current honey
  level (0–5) and, once full, whether a lit campfire underneath makes it safe to harvest without
  angering the bees. Beehives get their own "Beehives" line in the compact HUD.
- **Turtle laying-egg countdown.** The vague "Busy" a laying turtle used to show is now an exact
  countdown to the egg appearing, timed from the moment the turtle starts laying — not an estimate.
  A turtle seen only after it started laying still reads "Laying egg", just with no number, since the
  start was never observed.
- **"❤ Needs full health" now also covers wolves, nautiluses and owned cats.** A hurt wolf or
  nautilus reads the same honest label an underfed horse already got; a cat only shows it to its own
  owner, since only the owner can feed a cat at all — a non-owner still sees "✓ Ready" and can,
  correctly, do nothing about it. Nautilus is available on `mc26.1` and `mc26.2` only — the Nautilus
  entity class does not exist on Minecraft 1.21.9/1.21.10, and the `mc1.21.11` branch ships one jar
  covering 1.21.9–1.21.11, so it cannot reference that class either.
- **Allay "Needs a jukebox".** A ready allay that is not currently dancing near a jukebox now says so
  instead of a plain "✓ Ready" it cannot act on. Once the mod has actually watched an allay
  duplicate, the new copy carries an exact five-minute cooldown seeded from that moment — both parent
  and child.
- **Sniffer seed countdown.** Once a sniffer starts digging, the label counts down the few seconds to
  the seed appearing instead of just saying "Busy". A sniffer seen only after it started digging
  still reads "Busy", since the mod never saw the start to count from.
- **Panda "Too scared".** A panda that will not eat during a thunderstorm now says so, instead of
  reading "✓ Ready" or a bamboo warning that does not apply while it is scared.
- **Hoglin "Repelled".** A hoglin near a warped fungus, nether portal, respawn anchor or other
  repellent now reads "Repelled" instead of falsely promising it will breed. This replays the same
  block scan the game's own gate is built on rather than reading the gate directly, so it can be up
  to ~10 seconds stale after the repellent is removed, and it does not re-check a hoglin that is
  actively fleeing a piglin — both are honest limitations of a client-only mod, not bugs.
- **Exact age from a bucketed animal.** Bucketing a baby axolotl or tadpole shows its exact remaining
  growth time as a tooltip on the bucket — the one moment this client-only mod can ever know an exact
  age. Releasing it carries that same number into its floating label instead of starting over at a
  fresh estimate.
- **State filter.** The config screen gained a **Filter** tab: Ready, Cooldown, Babies, In love and
  Blocked can each be switched off independently, for both floating labels and the compact HUD
  counts, plus a "Show block labels" master switch covering turtle eggs, sniffer eggs, dried ghasts,
  beehives and crops together.
- **"Next ready in m:ss".** The compact HUD gained a line showing the soonest cooldown across every
  tracked animal, villager and allay — the number a player actually wants when deciding whether to
  wait around.
- **Look-at-only labels, and a real through-walls toggle.** An optional mode labels only the animal
  under the crosshair instead of the whole pen; a separate, on-by-default "Labels through walls"
  toggle can now switch off the see-through rendering the mod has always used, for anyone who wants
  labels hidden through walls instead.
- **Chicken egg and armadillo scute windows.** Optional, off by default: after hearing a chicken lay
  an egg or an armadillo shed a scute, the label shows a narrowing time window for the next one. This
  is the mod's only inferred display — a window derived from a sound heard once, not a fact read off
  a synced field — so it is never shown as an exact countdown, and it is dropped the moment the
  animal leaves tracking range rather than risk describing a drop nobody saw.
- **Torchflower and pitcher crop stage labels.** Both crops now show a growth-stage label, the
  pitcher plant turning green once ripe, plus their own "Crops" line in the compact HUD.
- **Panda cub personality odds.** Standing two adult pandas near each other adds a sub-line under the
  nearest one's timer predicting the cub's personality odds from both parents' genes, using the
  game's own weighted mutation table — suppressed whenever every non-default outcome is under 1%, so
  an ordinary pair of normal pandas shows nothing extra.
- **Every config option now has a description**, shown on hover; the "Show Baby Growth Timer"
  tooltip now discloses the growth-estimate limitation in-game rather than only in this file. "Field
  of View Angle" is renamed "Label cone angle" to stop it being read as the game's own FOV setting.
- **Optional per-glyph text outline** for labels, off by default — mainly useful with the background
  opacity turned all the way down, where light text can otherwise vanish against snow or sand.
- **Separate label and HUD background opacity**, plus a new HUD text scale. The old single
  "Background Opacity" slider is gone; an existing config's value is carried into both new sliders
  automatically on first load, so nobody's labels change on upgrade.
- **Ready-chime volume and pitch** are now adjustable, instead of a fixed pling.
- **Action-bar feedback for both keybinds.** Pressing the on/off or compact-mode keybind now shows a
  brief confirmation ("BreedTimer: on"/"off", "BreedTimer: compact HUD"/"floating labels") instead of
  relying on the visual change alone.
- **Colour-blind (red–green) and high-contrast colour presets.** Every label and compact-HUD colour
  now comes from one selectable palette instead of a fixed set of colours; the icons (`✓ ❤ 🔒 ✖`)
  still carry state without relying on colour at all, in case a preset does not fit.

### Fixed (i18n)
- **Compact-HUD counts now read correctly at count 1, in both languages.** The German lines for
  asleep, hatching and soaking animals were grammatically wrong at a count of 1 ("1 schlafen"); so
  was the baby count in both English and German ("1 babies" / "1 Babys"). All four now read
  correctly at any count.

### Changed
- **The panda "Needs bamboo nearby" warning now comes from the game's own verdict, not a block
  scan.** The mod used to sweep the 15×3×15 volume around every loaded panda for bamboo itself,
  once a second — up to 675 block lookups per panda. It now reads the same synced counter the
  panda's own breeding goal sets when it fails to find bamboo, which is exact and far cheaper to
  check. The trade: the no-bamboo warning now appears when the panda actually tries to breed,
  instead of pre-emptively, so a lone adult panda in a bamboo-free pen that nobody has fed reads
  "✓ Ready" until someone does.
- The food-hint list's separator and its "+n" suffix are now translatable strings instead of
  hard-coded punctuation.
- **The text-outline tickbox is greyed out while the high-contrast colour preset is active.** That
  preset already forces the outline on by itself, so the tickbox could not actually change
  anything — it now looks the part instead of pretending to be toggleable.

### Performance
- **Labels no longer cost frame rate when you look at a herd.** To draw a label the mod first had
  to work out which animal it was drawing it above, and it did that by searching the world around
  the render position — once for every creature on screen, every frame. In a herd each of those
  searches walked the whole herd, so the cost grew with the square of how many animals were in
  view: exactly the situation where labels are wanted. The animal is now known outright instead of
  being searched for, which also means two animals standing inside one another can no longer swap
  labels. Looking away, hiding the labels or switching to the compact HUD now genuinely stops the
  work rather than doing it and discarding the result.
- **The nearby-egg scan got about a thousand times cheaper in the ordinary case.** It read every
  block in a 33×33×11 box around you twenty times a second — a quarter of a million block lookups
  per second — even with no egg anywhere near. It now asks each chunk section whether it could
  contain an egg at all and skips it entirely when it cannot, which is almost always.
- **The ready chime stopped scanning when there is nothing to announce.** Finding out whether a
  ready animal is actually in sight involves tracing a line to it, and that ran for every animal
  in range every tick even when none had just become ready. It now runs only in the moment one
  actually does.
- Smaller repeated work removed throughout: the field-of-view test no longer recomputes the
  camera angle for every animal, the species check is worked out once per kind of animal instead
  of on every frame, and timer text no longer goes through the general-purpose formatter.
- A polar bear cub with the food hint enabled no longer searches every item in the game, every
  frame, to conclude — correctly — that nothing feeds it.

### Known limitation
- Baby growth time remains an estimate. The server never tells the client how old an animal
  actually is, so the timer starts at 20:00 the first time a baby is seen — walk up to a
  half-grown baby and it will read too high. For the same reason only babies fed by *you* can be
  taken into account; on a server, feeding by other players stays invisible to the timer.
- The food line is left off where it would mislead: an animal that can never breed does not get
  one, and neither does a baby villager, which cannot be fed to grow up faster the way a baby
  animal can.
- One breeding condition is not shown. A villager needs twelve food points before it will breed at
  all; that state never reaches the client in a form the mod can read, so a villager shown as ready
  may still be short of food. (The hoglin and sniffer cases this note used to list are now shown —
  see "Repelled" and the sniffer seed countdown below.)
- A pair spotted mid-courtship can read "❤ In Love!" for up to 30 seconds while they are actually
  on cooldown. Vanilla sends the identical event for entering love mode and for breeding, and the
  one client-visible clue that tells them apart — a newborn standing nearby — does not exist yet
  when the event arrives: the game broadcasts it before the child is even added to the world. This
  was already the practical behaviour almost every time it mattered; the mod no longer runs a
  newborn search that could not tell the two cases apart to begin with.

## [1.5.1] - 2026-07-21

### Changed
- Version aligned across all branches; no functional change on this line.

## [1.5.0] - 2026-07-18

### Added
- **Freely positionable HUD**: the config screen has a new "Edit HUD Position..." button that
  opens an editor - drag the HUD anywhere on screen and confirm (the HUD is shown in compact
  mode with animals nearby)
- **Position presets**: save, apply, rename and delete HUD positions
- HudLib is bundled inside the jar (jar-in-jar); there is nothing extra to install

### Changed
- **The config file moved from `breedtimer.json5` to `breedtimer.json`.** An existing json5
  file is read once and carried over automatically, so settings such as `scanRadius` are kept;
  configuration no longer depends on YACL's config handler
- The HUD now starts at the standard margin instead of a hard-coded `(5,5)` - it sits about
  5 px further inside the screen corner. Existing setups are unaffected once a position is saved

## [1.4.0] - 2026-07-14

### Changed
- Unified the mod version to `1.4.0` across all loaders and supported Minecraft versions, so every build carries one consistent version.
- Standardized jar naming to `<modid>-<loader>-<version>+mc<range>` (e.g. `breedtimer-fabric-1.4.0+mc26.2.jar`).
- Corrected author and contact metadata — author credited as `Dennis_thegamer`; Modrinth and GitHub links now match the published jar metadata.

## [1.3.0] - 2026-07-11

### Added
- **NeoForge support** — BreedTimer now runs on both Fabric and NeoForge. Each release ships two jars built from one shared codebase: `breedtimer-fabric-…` and `breedtimer-neoforge-…`. On NeoForge, the config screen is reachable via the native mod list entry (no ModMenu needed).

### Changed
- Restructured the project into `common/` (shared code and mixins) plus `fabric/` and `neoforge/` loader subprojects. No gameplay changes.

## [1.2.0] - 2026-07-02

### Fixed
- **Timers no longer count down while entities are unloaded** — Breed cooldown and love-mode timers previously kept counting based on global game time, even when the animal or villager was unloaded (e.g. after walking away or switching dimensions). Since unloaded entities don't tick server-side, their real cooldowns pause — the mod now pauses its timers too whenever the entity is not loaded on the client, so timers stay accurate when you step away and come back. (Thanks for the report!)

### Changed
- **Baby growth timers now track all loaded entities** — Baby timers previously only counted down while the baby was within the configured scan radius; they now count down for every loaded baby animal/villager, matching actual server-side growth more closely.

> **Note:** The client can only see entities within the server's entity tracking range. If an entity ticks on the server while just outside that range, the displayed timer may show slightly more remaining time than the real cooldown — it will never falsely show "Ready" anymore, though.

## [1.1.2] - 2026-06-21
- **Support for Minecraft 26.2** — Updated mappings and dependencies for the latest Minecraft version. No functional changes.

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
