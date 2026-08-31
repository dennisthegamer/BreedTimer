package de.dennisthegamer.breedtimer;

import de.dennisthegamer.breedtimer.config.BreedTimerConfig;
import de.dennisthegamer.breedtimer.render.BlockLabelScanner;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper;
import de.dennisthegamer.breedtimer.util.LookTarget;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper.AnimalState;
import de.dennisthegamer.breedtimer.util.BreedCooldownHelper.AnimalTimerInfo;
import de.dennisthegamer.breedtimer.util.VillagerCooldownHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared client logic; loader-specific entrypoints (fabric/neoforge) call
 * {@link #init()} and wire these methods to their loader's events.
 */
public final class BreedTimerClient {

    public static final String MOD_ID = "breedtimer";

    private static final KeyMapping.Category KEYBIND_CATEGORY = KeyMapping.Category.register(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "breedtimer")
    );
    public static KeyMapping toggleEnabledKey;
    public static KeyMapping toggleCompactKey;

    private static final Set<UUID> previouslyReady   = new HashSet<>();
    private static final Set<UUID> previouslyWilling = new HashSet<>();

    /**
     * F-3: whether the animal/villager readiness bookkeeping below ran on the previous tick. Both
     * gates that can skip it -- the outer {@code !config.enabled} return and each category's own
     * {@code showAnimals}/{@code showVillagers} switch -- clear the matching flag the instant they
     * skip it, so a tick where the flag is false but the category is running again is exactly an
     * off->on transition: readiness must be primed from the current set instead of chimed for, the
     * same way the Task 17 fix already treats {@code playSound} muting.
     */
    private static boolean animalsBookkeepingActive = false;
    private static boolean villagersBookkeepingActive = false;

    private BreedTimerClient() {}

    public static void init() {
        BreedTimerConfig.load();

        toggleEnabledKey = new KeyMapping(
                "key.breedtimer.toggleEnabled",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KEYBIND_CATEGORY
        );

        toggleCompactKey = new KeyMapping(
                "key.breedtimer.toggleCompact",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KEYBIND_CATEGORY
        );
    }

    public static void onWorldJoin(ClientPacketListener handler) {
        WorldIdentity identity = resolveWorldIdentity(handler);
        BreedCooldownHelper.onWorldJoin(identity.id(), identity.legacyId(), identity.worldDir());
        VillagerCooldownHelper.onWorldJoin(identity.id(), identity.legacyId(), identity.worldDir());
    }

    public static void onWorldLeave() {
        BreedCooldownHelper.onWorldLeave();
        VillagerCooldownHelper.onWorldLeave();
        BlockLabelScanner.clear();
        LookTarget.clear();
        previouslyReady.clear();
        previouslyWilling.clear();
        animalsBookkeepingActive = false;
        villagersBookkeepingActive = false;
    }

    /**
     * A world's save-file identity, plus the identity it would have carried before F-1 (world
     * identity by save-folder name). {@code legacyId} is null unless it would resolve to a
     * different file than {@code id} -- multiplayer and the "unknown" fallback never differ from
     * their own id, so they always pass null, meaning "nothing to migrate".
     */
    private record WorldIdentity(String id, String legacyId, Path worldDir) {}

    private static WorldIdentity resolveWorldIdentity(ClientPacketListener handler) {
        var serverData = handler.getServerData();
        // Multiplayer: no world folder within reach, so the config directory keyed by server
        // address stays the only option.
        if (serverData != null) return new WorldIdentity(sanitize(serverData.ip), null, null);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            // The world's save-folder name. Unlike the display name below, two worlds can never
            // share this -- the game itself refuses to create a second save folder with the same
            // name -- so this is what actually identifies a save file, one-to-one. normalize()
            // first: getWorldPath's result can carry "." segments depending on how the save root
            // resolved, and only the final, resolved segment is the folder's own name.
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize();
            String id = sanitize(root.getFileName().toString());
            // The world's display name, not its save-folder id -- two worlds can share this even
            // though they can never share a save folder, which is exactly the collision F-1 fixes.
            // Kept only as the legacy id an existing save file might still be sitting under, for
            // BreedCooldownHelper/VillagerCooldownHelper's one-time migration on world join; for
            // most worlds the folder name and the level name are the same string, so this equals
            // id and no migration is needed.
            String legacyId = sanitize(mc.getSingleplayerServer().getWorldData().getLevelSettings().levelName());
            // The folder itself, not just its name: state written inside it follows the world through
            // copies, junctions and instance switches, and cannot be claimed by a same-named world in
            // another instance -- see WorldSaveMigration for the collision this replaces.
            return new WorldIdentity(id, legacyId.equals(id) ? null : legacyId, root);
        }
        return new WorldIdentity("unknown", null, null);
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * The action bar, not chat: this is transient state feedback, and a line per keypress in the
     * chat log would be noise. Written before the player/level null check below on purpose -- the
     * keybinds are consumed there too, and the overlay simply does not render without a world.
     *
     * <p>PORT WARNING (Task 45): {@code mc.gui.setOverlayMessage} is the spelling on this branch and
     * on every branch except mc26.2. 26.2 split {@code Gui} into {@code Gui}/{@code Hud}, so there
     * the call reads {@code mc.gui.hud.setOverlayMessage(...)} and {@code Gui} itself has no
     * {@code setOverlayMessage}.
     */
    private static void announce(Minecraft mc, String key) {
        // PORT WARNING (Task 45): mc26.2 spells this mc.gui.hud.setOverlayMessage. This branch and
        // the other four (mc26.1 and the three older 1.21.x) call mc.gui.setOverlayMessage, no .hud.
        mc.gui.setOverlayMessage(Component.translatable(key), false);
    }

    public static void onClientTick(Minecraft mc) {
        if (toggleEnabledKey.consumeClick()) {
            BreedTimerConfig config = BreedTimerConfig.get();
            config.enabled = !config.enabled;
            BreedTimerConfig.save();
            announce(mc, config.enabled ? "breedtimer.message.enabled" : "breedtimer.message.disabled");
        }

        if (toggleCompactKey.consumeClick()) {
            BreedTimerConfig config = BreedTimerConfig.get();
            config.compactMode = !config.compactMode;
            BreedTimerConfig.save();
            announce(mc, config.compactMode ? "breedtimer.message.compact_on"
                                            : "breedtimer.message.compact_off");
        }

        BreedTimerConfig config = BreedTimerConfig.get();

        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        // Collect all client-loaded entities so the helpers can pause timers
        // for entities that are currently unloaded
        List<Entity> loadedEntities = new ArrayList<>();
        if (level instanceof ClientLevel clientLevel) {
            for (Entity entity : clientLevel.entitiesForRendering()) {
                loadedEntities.add(entity);
            }
        }

        // Bookkeeping runs even while the display is switched off, and regardless of which
        // categories are shown. Animals on the server keep growing and breeding no matter what the
        // client draws, so standing the estimates still does not freeze anything -- it silently
        // makes them wrong by however long the mod was off. The one legitimate reason to pause a
        // timer is an unloaded entity, which the helpers detect themselves from loadedEntities.
        // Keeping these unconditional is also why neither helper needs its clock resynchronised:
        // no gap can build up in the first place, so there is none to discard or to apply in a
        // lump (the latter clamps to 6000 -- exactly one full breed cooldown).
        BreedCooldownHelper.tick(level, mc.isPaused(), loadedEntities);
        VillagerCooldownHelper.tick(level, mc.isPaused(), loadedEntities);

        if (!config.enabled) {
            // F-3: the readiness bookkeeping below does not run at all while disabled, so both
            // "active last tick" flags must drop to false right here, not just go unset -- otherwise
            // re-enabling would not look like a transition and the chime burst this exists to
            // prevent would fire anyway. See the flags' own javadoc.
            animalsBookkeepingActive = false;
            villagersBookkeepingActive = false;
            return;
        }

        // Both label gates resolved once here rather than per entity per frame; see LookTarget.
        LookTarget.update(player, level, config, loadedEntities);

        // Unlike the two above, this holds no running estimate: it is a render cache rebuilt from
        // live block states every tick, so it loses nothing by standing still -- and at a
        // 33x33x11 block sweep it is by far the most expensive thing here. showBlocks is the
        // master switch for all six block-kind labels (turtle/sniffer eggs, dried ghast, beehive,
        // and the crops Task 34 adds), gated the same way showAnimals already is.
        if (config.showAnimals && config.showBlocks) BlockLabelScanner.tick(player, level, config);

        if (config.showAnimals) {
            // The bookkeeping below runs unconditionally, even while playSound is off. The chime is
            // gated, the bookkeeping is not -- otherwise turning sound back on makes every visible
            // ready animal look newly ready in one tick.
            //
            // F-3: the same is true of reaching this block at all. If either enabled or showAnimals
            // was off one tick ago, previouslyReady is stale by however long that was, so priming it
            // must win over chiming on the very tick this block starts running again.
            boolean reactivatingAnimals = !animalsBookkeepingActive;
            animalsBookkeepingActive = true;

            Set<UUID> currentlyReady = new HashSet<>();
            boolean anyNewlyReady = false;
            for (Entity entity : loadedEntities) {
                if (!(entity instanceof Animal animal) || !BreedCooldownHelper.isSupportedAnimal(animal)) continue;
                if (BreedCooldownHelper.createTimerInfo(animal).state() == AnimalState.READY) {
                    UUID uuid = animal.getUUID();
                    currentlyReady.add(uuid);
                    if (!previouslyReady.contains(uuid)) anyNewlyReady = true;
                }
            }
            // Readiness is remembered for every loaded animal, not just the visible ones -- keying
            // it to visibility meant looking away and back re-chimed for each of them. The chime
            // itself still only fires for one you could actually see become ready.
            // Skipped outright unless sound is on and some animal is both ready and was not ready
            // last tick, because nothing else can make it play a sound. Not an approximation:
            // getVisibleAnimals returns a subset of the loaded animals just walked, and nothing
            // mutates between the two loops, so anything it reports READY is in currentlyReady.
            // With the difference to previouslyReady empty this loop provably cannot fire -- and
            // skipping it saves a scan box query plus a raycast per visible animal, every tick.
            //
            // reactivatingAnimals adds the F-3 case the above reasoning does not cover: on the tick
            // this block starts running again, anyNewlyReady can legitimately be true for animals
            // that only *look* newly ready because the bookkeeping itself was off, not because they
            // just became ready. previouslyReady is about to be primed with the full current set
            // below instead.
            if (config.playSound && config.showReady && anyNewlyReady && !reactivatingAnimals) {
                for (AnimalTimerInfo info : BreedCooldownHelper.getVisibleAnimals(player, level)) {
                    UUID uuid = info.animal().getUUID();
                    if (info.state() == AnimalState.READY && !previouslyReady.contains(uuid)) {
                        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), config.soundVolume, config.soundPitch);
                    }
                }
            }
            previouslyReady.clear();
            previouslyReady.addAll(currentlyReady);
        } else {
            animalsBookkeepingActive = false;
        }

        if (config.showVillagers) {
            // Same reasoning as the animal block above: bookkeeping is unconditional, the chime is
            // gated on config.playSound. F-3 mirrors the same reactivation guard, too.
            boolean reactivatingVillagers = !villagersBookkeepingActive;
            villagersBookkeepingActive = true;

            Set<UUID> currentlyVillagerReady = new HashSet<>();
            boolean anyNewlyWilling = false;
            for (Entity entity : loadedEntities) {
                if (!(entity instanceof Villager villager) || villager.isBaby()) continue;
                if (VillagerCooldownHelper.createTimerInfo(villager).state()
                        == VillagerCooldownHelper.VillagerState.READY) {
                    UUID uuid = villager.getUUID();
                    currentlyVillagerReady.add(uuid);
                    if (!previouslyWilling.contains(uuid)) anyNewlyWilling = true;
                }
            }
            // Same reasoning as the animal chime above, including the F-3 reactivation guard.
            if (config.playSound && config.showReady && anyNewlyWilling && !reactivatingVillagers) {
                for (VillagerCooldownHelper.VillagerTimerInfo info : VillagerCooldownHelper.getVisibleVillagers(player, level)) {
                    UUID uuid = info.villager().getUUID();
                    if (info.state() == VillagerCooldownHelper.VillagerState.READY
                            && !previouslyWilling.contains(uuid)) {
                        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), config.soundVolume, config.soundPitch);
                    }
                }
            }
            previouslyWilling.clear();
            previouslyWilling.addAll(currentlyVillagerReady);
        } else {
            villagersBookkeepingActive = false;
        }
    }
}
