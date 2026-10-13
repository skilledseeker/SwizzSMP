package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.effects.Flashbang;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Attrs;
import com.swizzsmp.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * One showcase cinematic playing around one player (DESIGN.md §13.7). Subclasses script it
 * tick by tick in {@link #tick()}; this class runs the clock and owns the cleanup: every
 * entity made through {@link #spawn} is non-persistent and removed when the show ends, the
 * player quits, dies or changes world, or the plugin is disabled. Anything else a show
 * changes (fake blocks, AI, modifiers) is undone in {@link #cleanup()}.
 */
public abstract class Cinematic {

    protected static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);
    protected static final SwizzType[] TYPES = SwizzType.values();
    protected static final String FLASH_TEXTURE = "swizz:misc/flash";

    protected final SwizzPlugin plugin;
    protected final Player player;
    protected final World world;
    /** The floor under the player when the show started. */
    protected final Location ground;
    /** Ticks since the start. */
    protected int t;
    private final List<Entity> spawned = new ArrayList<>();
    private BukkitTask task;
    private Runnable onEnd = () -> { };
    private boolean ended;
    private boolean lifted;
    private boolean hovering;

    protected Cinematic(SwizzPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.world = player.getWorld();
        this.ground = floor(player.getLocation());
    }

    /** Total length in ticks. */
    protected abstract int length();

    /** Runs once per tick while {@link #t} counts up from 0 to {@link #length()}. */
    protected abstract void tick();

    /** Undoes everything that isn't a spawned entity (fake blocks, AI, modifiers). Runs once. */
    protected void cleanup() {
    }

    final void start(Runnable whenDone) {
        this.onEnd = whenDone;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::step, 0L, 1L);
    }

    private void step() {
        if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
            end();
            return;
        }
        tick();
        if (++t > length()) {
            end();
        }
    }

    /** Stops the show and removes everything it made. Safe to call more than once. */
    public final void end() {
        if (ended) {
            return;
        }
        ended = true;
        if (task != null) {
            task.cancel();
        }
        spawned.forEach(Entity::remove);
        spawned.clear();
        try {
            release();
            cleanup();
        } finally {
            onEnd.run();
        }
    }

    // ── Entities ────────────────────────────────────────────────────────────────

    protected <T extends Entity> T spawn(Location at, Class<T> type, Consumer<T> setup) {
        T entity = world.spawn(at.clone(), type, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(Keys.SPAWNED_BY_SWIZZ, PersistentDataType.BYTE, (byte) 1);
            setup.accept(e);
        });
        spawned.add(entity);
        return entity;
    }

    protected void remove(Entity entity) {
        if (entity != null) {
            entity.remove();
            spawned.remove(entity);
        }
    }

    /**
     * A flat textured square from the pack ({@code models/item/plane.json}). FIXED lies where
     * the transformation puts it; CENTER always faces the camera. Only shown to players whose
     * client has the pack, since anyone else would see a missing model.
     */
    protected ItemDisplay plane(Location at, String model, Display.Billboard billboard, Transformation start) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(model));
        ItemDisplay display = spawn(flat(at.clone()), ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(billboard);
            d.setBrightness(FULL_BRIGHT);
            d.setTeleportDuration(2);
            d.setTransformation(start);
            d.setVisibleByDefault(false);
        });
        for (Player viewer : world.getPlayers()) {
            if (hasPack(viewer)) {
                viewer.showEntity(plugin, display);
            }
        }
        return display;
    }

    /** Moves a display to a new transformation over {@code ticks} (set at least a tick after spawning). */
    protected static void animate(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    // ── The player at the centre ────────────────────────────────────────────────

    protected Location chest() {
        return flat(player.getLocation().add(0, 1.0, 0));
    }

    protected Location head() {
        return flat(player.getLocation().add(0, player.getHeight() + 0.35, 0));
    }

    /** Lifts the player for {@code ticks}; call {@link #hover()} when they should stop rising. */
    protected void lift(int ticks, int amplifier) {
        if (plugin.settings().cinematicLevitate) {
            effect(player, PotionEffectType.LEVITATION, ticks, amplifier);
            lifted = true;
        }
    }

    /**
     * Zero gravity and no momentum, so the player stays where the lift left them. A transient
     * modifier (never saved), and zero gravity also stops the vanilla "flying" kick.
     */
    protected void hover() {
        if (!plugin.settings().cinematicLevitate) {
            return;
        }
        player.removePotionEffect(PotionEffectType.LEVITATION);
        Attrs.apply(player, Attribute.GRAVITY, Keys.MOD_CINEMATIC_HOVER, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        player.setVelocity(new Vector());
        hovering = true;
    }

    /** Gravity back, with Slow Falling for a soft landing. Also runs when the show ends early. */
    protected void release() {
        if (!hovering && !lifted) {
            return;
        }
        hovering = false;
        lifted = false;
        player.removePotionEffect(PotionEffectType.LEVITATION);
        Attrs.remove(player, Attribute.GRAVITY, Keys.MOD_CINEMATIC_HOVER);
        effect(player, PotionEffectType.SLOW_FALLING, 120, 0);
    }

    // ── Everyone watching ───────────────────────────────────────────────────────

    /** Players close enough to get the screen effects. */
    protected List<Player> viewers() {
        double radius = plugin.settings().cinematicScreenRadius;
        List<Player> list = new ArrayList<>();
        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                list.add(viewer);
            }
        }
        return list;
    }

    protected boolean hasPack(Player viewer) {
        return plugin.resourcePack().hasPack(viewer);
    }

    /** A full-screen pack image for {@code ticks}. Shows nothing (and returns false) without the pack. */
    protected boolean overlay(Player viewer, String texture, int ticks) {
        if (!hasPack(viewer)) {
            return false;
        }
        plugin.flashbang().flash(viewer, ticks, ticks, 0, Flashbang.Mode.OVERLAY, texture);
        return true;
    }

    /** A white screen: the flash image, or a white title without the pack. */
    protected void whiteFlash(Player viewer, int ticks) {
        plugin.flashbang().flash(viewer, ticks, ticks, 0, hasPack(viewer) ? Flashbang.Mode.OVERLAY : Flashbang.Mode.TITLE, FLASH_TEXTURE);
    }

    protected void clearOverlay(Player viewer) {
        plugin.flashbang().clear(viewer);
    }

    protected static void effect(Player viewer, PotionEffectType type, int ticks, int amplifier) {
        viewer.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, false));
    }

    /** Removes an effect only if it is one of ours (ambient, no particles), never a real potion. */
    protected static void clearEffect(Player viewer, PotionEffectType type) {
        PotionEffect current = viewer.getPotionEffect(type);
        if (current != null && current.isAmbient() && !current.hasParticles()) {
            viewer.removePotionEffect(type);
        }
    }

    /** Camera jolts (as if hit) after each delay in ticks. */
    protected void jolt(Player viewer, long... delays) {
        for (long delay : delays) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (viewer.isOnline()) {
                    viewer.sendHurtAnimation(random().nextFloat() * 360f);
                }
            }, delay);
        }
    }

    /** A sound at the player, heard by everyone around. */
    protected void sound(Sound sound, float pitch) {
        world.playSound(player.getLocation(), sound, SoundCategory.MASTER, 3f, pitch);
    }

    /** A sound on the listener, so it is full volume wherever they stand. */
    protected static void sound(Player viewer, Sound sound, float volume, float pitch) {
        viewer.playSound(viewer, sound, SoundCategory.MASTER, volume, pitch);
    }

    // ── Geometry and maths ──────────────────────────────────────────────────────

    /** Lays a plane flat on the floor, turned {@code angle} around the vertical. */
    protected static Transformation flatTransform(double angle, float scale, float lift) {
        return new Transformation(new Vector3f(0, lift, 0),
                new Quaternionf().rotateY((float) angle).rotateX((float) (-Math.PI / 2)),
                new Vector3f(scale), new Quaternionf());
    }

    /** For camera-facing planes: turned {@code angle} in the screen plane, pushed {@code toCamera} forward. */
    protected static Transformation facing(double angle, float scale, float toCamera) {
        return new Transformation(new Vector3f(0, 0, toCamera), new Quaternionf().rotateZ((float) angle),
                new Vector3f(scale), new Quaternionf());
    }

    protected static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf());
    }

    /** A block model (0..1) of {@code size}, centred on {@code center} and turned around its middle. */
    protected static Transformation cube(Vector3f center, Quaternionf rotation, float size) {
        return box(center, rotation, new Vector3f(size));
    }

    /** A block model stretched to {@code size}, centred on {@code center} and turned around its middle. */
    protected static Transformation box(Vector3f center, Quaternionf rotation, Vector3f size) {
        Vector3f half = rotation.transform(new Vector3f(size).mul(0.5f));
        return new Transformation(new Vector3f(center).sub(half), new Quaternionf(rotation), new Vector3f(size), new Quaternionf());
    }

    protected static Quaternionf randomRotation(double max) {
        ThreadLocalRandom r = random();
        return new Quaternionf().rotateXYZ((float) r.nextDouble(-max, max), (float) r.nextDouble(-max, max), (float) r.nextDouble(-max, max));
    }

    /** The floor under a location: its own height when standing, else the first solid block below. */
    protected static Location floor(Location at) {
        double y = at.getY();
        for (int i = 0; i < 16 && !at.getWorld().getBlockAt(at.getBlockX(), (int) Math.floor(y - 0.05), at.getBlockZ()).isSolid(); i++) {
            y = Math.floor(y - 0.05);
        }
        return new Location(at.getWorld(), at.getX(), y, at.getZ());
    }

    /** The top solid block with air above it in this column, within 4 blocks of {@code nearY}. */
    protected Block surface(int x, int z, int nearY) {
        for (int y = nearY + 4; y >= nearY - 4; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isOccluding() && !block.getRelative(0, 1, 0).getType().isOccluding()) {
                return block;
            }
        }
        return null;
    }

    /** Displays turn with their entity yaw, so their locations always face straight ahead. */
    protected static Location flat(Location location) {
        location.setYaw(0);
        location.setPitch(0);
        return location;
    }

    /** Note-block pitch for {@code semitones} above F#3 (0..24). */
    protected static float pitch(int semitones) {
        return (float) Math.pow(2, (semitones - 12) / 12.0);
    }

    protected static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    protected static double smooth(double v) {
        double x = clamp(v);
        return x * x * (3 - 2 * x);
    }

    protected static double lerp(double a, double b, double f) {
        return a + (b - a) * clamp(f);
    }

    protected static ThreadLocalRandom random() {
        return ThreadLocalRandom.current();
    }
}
