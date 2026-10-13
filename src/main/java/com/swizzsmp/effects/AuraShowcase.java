package com.swizzsmp.effects;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Attrs;
import com.swizzsmp.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The /aura showcase (DESIGN.md §13.7): a 14-second cinematic around one player. A glowing
 * sigil unfolds under them as they rise, the seven swizz orbit faster and faster while
 * streaming their colours into them, then collapse in a white flash, lightning and a
 * shockwave of element blocks, with a rainbow energy border and a wobble on nearby screens.
 * <p>
 * Every display is non-persistent (never saved) and removed when the show ends, the player
 * quits or dies, or the plugin is disabled. Hovering is a transient zero-gravity modifier,
 * which also keeps the server from kicking the player for flying.
 */
public final class AuraShowcase implements Listener {

    private static final int SIGIL_START = 10;
    private static final int GEMS_START = 50;
    private static final int GEM_GAP = 8;
    private static final int BEAMS_START = 95;
    private static final int CHARGE_START = 100;
    private static final int CONVERGE = 180;
    private static final int HALO = 200;
    private static final int FADE = 225;
    private static final int LAND = 232;
    private static final int END = 280;
    /** Sigils turn to their next angle every this many ticks (the client interpolates). */
    private static final int SPIN_STEP = 5;
    private static final double ORBIT_RADIUS = 5.5;
    private static final float OUTER_SIZE = 16f;
    private static final float INNER_SIZE = 8.5f;

    private static final String OUTER_MODEL = "swizz:sigil_outer";
    private static final String INNER_MODEL = "swizz:sigil_inner";
    private static final String FLASH_TEXTURE = "swizz:misc/flash";
    private static final String AURA_TEXTURE = "swizz:misc/aura";

    private static final SwizzType[] TYPES = SwizzType.values();
    /** Shockwave block for each swizz, in {@link SwizzType} order. */
    private static final Material[] SHARDS = {Material.LIGHT_BLUE_STAINED_GLASS, Material.AMETHYST_BLOCK,
            Material.PACKED_MUD, Material.EMERALD_BLOCK, Material.BLUE_ICE, Material.GOLD_BLOCK, Material.SCULK};
    /** A rising pentatonic run (note-block semitones above F#3), one note per swizz. */
    private static final int[] NOTES = {5, 7, 10, 12, 15, 17, 19};
    /** A major, played when the seven become one. */
    private static final int[] CHORD = {15, 19, 22};
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    private final SwizzPlugin plugin;
    private final Map<UUID, Run> runs = new HashMap<>();

    public AuraShowcase(SwizzPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return false if this player's aura is already playing */
    public boolean play(Player player) {
        if (runs.containsKey(player.getUniqueId())) {
            return false;
        }
        Run run = new Run(player);
        runs.put(player.getUniqueId(), run);
        run.task = Bukkit.getScheduler().runTaskTimer(plugin, run::tick, 0L, 1L);
        return true;
    }

    public void stop() {
        for (Run run : List.copyOf(runs.values())) {
            run.end();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Run run = runs.get(event.getPlayer().getUniqueId());
        if (run != null) {
            run.end();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Run run = runs.get(event.getPlayer().getUniqueId());
        if (run != null) {
            run.end();
        }
    }

    private final class Run {
        final Player player;
        final World world;
        final Location ground;
        final boolean levitate = plugin.settings().auraLevitate;
        final List<Entity> spawned = new ArrayList<>();
        final ItemDisplay[] gems = new ItemDisplay[TYPES.length];
        final List<BlockDisplay> shards = new ArrayList<>();
        ItemDisplay outer;
        ItemDisplay inner;
        ItemDisplay halo;
        BukkitTask task;
        int t;
        double orbitAngle;
        double outerAngle;
        double innerAngle;

        Run(Player player) {
            this.player = player;
            this.world = player.getWorld();
            this.ground = floor(player.getLocation());
        }

        void tick() {
            if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
                end();
                return;
            }
            Location chest = flat(player.getLocation().add(0, 1.0, 0));
            if (t == 0) {
                begin();
            }
            if (t == 30) {
                hover();
            }
            if (t % SPIN_STEP == 0 && t > 0) {
                spin();
            }
            if (t == SIGIL_START) {
                sound(Sound.BLOCK_END_PORTAL_SPAWN, 0.8f);
            }
            if (t >= 40 && t < CONVERGE && t % 4 == 0) {
                groundRing();
            }
            if (t < CONVERGE && t % (t < 140 ? 20 : 10) == 0) {
                sound(Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f + t / 400f);
            }
            if (t >= GEMS_START && t < GEMS_START + TYPES.length * GEM_GAP && (t - GEMS_START) % GEM_GAP == 0) {
                raiseGem((t - GEMS_START) / GEM_GAP);
            }
            if (t >= GEMS_START && t < CONVERGE) {
                moveGems(chest);
            }
            if (t >= BEAMS_START && t < CONVERGE && t % 2 == 0) {
                beams(chest);
            }
            if (t == CHARGE_START) {
                sound(Sound.BLOCK_PORTAL_TRIGGER, 1.2f);
            }
            if (t == 140) {
                sound(Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f);
            }
            if (t == 172) {
                sound(Sound.ITEM_TRIDENT_RIPTIDE_3, 0.7f);
            }
            if (t >= 120 && t < CONVERGE && t % 2 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, chest, 4 + (t - 120) / 4, 0.5, 0.8, 0.5, 0.15);
            }
            if (t == CONVERGE) {
                converge(chest);
            }
            if (t == CONVERGE + 2) {
                launchShards();
            }
            if (t > CONVERGE && t <= CONVERGE + 20) {
                shockwave(t - CONVERGE);
            }
            if (t == CONVERGE + 7) {
                auraScreens();
            }
            if (t == CONVERGE + 9) {
                titles();
            }
            if (t == HALO) {
                halo = sigil(head(), INNER_MODEL);
            }
            if (halo != null) {
                halo.teleport(head());
            }
            if (t == CONVERGE + 40) {
                shards.forEach(Entity::remove);
                shards.clear();
            }
            if (t == FADE) {
                sound(Sound.BLOCK_BEACON_DEACTIVATE, 0.8f);
            }
            if (t == LAND) {
                land();
            }
            if (t == 245) {
                for (int note : CHORD) {
                    sound(Sound.BLOCK_NOTE_BLOCK_CHIME, pitch(note));
                }
            }
            if (++t > END) {
                end();
            }
        }

        // ── Phases ──────────────────────────────────────────────────────────────

        void begin() {
            for (Player viewer : viewers()) {
                viewer.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 90, 0, true, false, false));
            }
            sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.5f);
            if (levitate) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 1, true, false, false));
            }
            outer = sigil(ground, OUTER_MODEL);
            inner = sigil(ground, INNER_MODEL);
        }

        /** Zero gravity and no momentum: the player stays where the lift left them. */
        void hover() {
            if (!levitate) {
                return;
            }
            player.removePotionEffect(PotionEffectType.LEVITATION);
            Attrs.apply(player, Attribute.GRAVITY, Keys.MOD_AURA_HOVER, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            player.setVelocity(new Vector());
        }

        void land() {
            Attrs.remove(player, Attribute.GRAVITY, Keys.MOD_AURA_HOVER);
            if (levitate) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, true, false, false));
            }
        }

        void spin() {
            double charge = charge();
            outerAngle += Math.toRadians(SPIN_STEP * (1.2 + 4 * charge));
            innerAngle -= Math.toRadians(SPIN_STEP * (2.4 + 7 * charge));
            int next = t + SPIN_STEP;
            animate(outer, flatTransform(outerAngle, outerScale(next), 0.03f), SPIN_STEP);
            animate(inner, flatTransform(innerAngle, innerScale(next), 0.06f), SPIN_STEP);
            animate(halo, flatTransform(innerAngle * 1.5, haloScale(next), 0f), SPIN_STEP);
        }

        void groundRing() {
            Location ring = ground.clone().add(0, 0.2, 0);
            Particles.circle(ring, 7.4, 28, t * 0.05, p -> world.spawnParticle(Particle.REVERSE_PORTAL, p, 1, 0, 0.1, 0, 0.02));
            world.spawnParticle(Particle.ENCHANT, ground.clone().add(0, 1.5, 0), 20, 3, 0.5, 3, 1.0);
        }

        void raiseGem(int i) {
            SwizzType type = TYPES[i];
            double angle = orbitAngle + i * 2 * Math.PI / TYPES.length;
            Location at = new Location(world, ground.getX() + Math.cos(angle) * ORBIT_RADIUS, ground.getY() + 0.3,
                    ground.getZ() + Math.sin(angle) * ORBIT_RADIUS);
            ItemDisplay gem = world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(plugin.items().swizzItem(type));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(FULL_BRIGHT);
                d.setGlowing(true);
                d.setGlowColorOverride(type.primaryColor());
                d.setTeleportDuration(2);
                d.setTransformation(scaled(0.01f));
                prepare(d);
            });
            gems[i] = gem;
            Bukkit.getScheduler().runTaskLater(plugin, () -> animate(gem, scaled(1.4f), 8), 2L);
            world.spawnParticle(Particle.DUST, at, 30, 0.3, 0.6, 0.3, 0, new Particle.DustOptions(type.primaryColor(), 1.6f));
            sound(Sound.BLOCK_NOTE_BLOCK_BELL, pitch(NOTES[i]));
            sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, pitch(NOTES[i]));
        }

        /** Orbit the player, faster and tighter as the charge builds, then collapse into them. */
        void moveGems(Location chest) {
            double charge = charge();
            orbitAngle += Math.toRadians(1.5 + 16 * charge * charge);
            double radius = t < 130 ? ORBIT_RADIUS
                    : t < 170 ? lerp(ORBIT_RADIUS, 3, (t - 130) / 40.0)
                    : lerp(3, 0, (t - 170) / 10.0);
            if (t == 172) {
                for (ItemDisplay gem : gems) {
                    animate(gem, scaled(0.3f), 8);
                }
            }
            for (int i = 0; i < gems.length; i++) {
                if (gems[i] == null) {
                    continue;
                }
                int born = GEMS_START + i * GEM_GAP;
                double rise = smooth(clamp((t - born) / 20.0));
                double angle = orbitAngle + i * 2 * Math.PI / gems.length;
                double y = lerp(ground.getY() + 0.3, chest.getY() + Math.sin(t / 6.0 + i) * 0.25, rise);
                gems[i].teleport(new Location(world, chest.getX() + Math.cos(angle) * radius, y, chest.getZ() + Math.sin(angle) * radius));
            }
        }

        void beams(Location chest) {
            for (int i = 0; i < gems.length; i++) {
                if (gems[i] == null || t < GEMS_START + i * GEM_GAP + 20) {
                    continue;
                }
                Location from = gems[i].getLocation();
                Color color = TYPES[i].primaryColor();
                world.spawnParticle(Particle.TRAIL, from, 2, 0.08, 0.08, 0.08, 0, new Particle.Trail(chest, color, 12));
                world.spawnParticle(Particle.DUST, from, 2, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(color, 1.4f));
            }
        }

        void converge(Location chest) {
            for (int i = 0; i < gems.length; i++) {
                if (gems[i] != null) {
                    gems[i].remove();
                    gems[i] = null;
                }
            }
            world.strikeLightningEffect(player.getLocation());
            world.spawnParticle(Particle.FLASH, chest, 1, 0, 0, 0, 0, Color.WHITE);
            world.spawnParticle(Particle.SONIC_BOOM, chest, 1);
            world.spawnParticle(Particle.END_ROD, chest, 150, 0.3, 0.3, 0.3, 0.6);
            world.spawnParticle(Particle.FIREWORK, chest, 100, 0.3, 0.3, 0.3, 0.45);
            for (SwizzType type : TYPES) {
                world.spawnParticle(Particle.DUST, chest, 25, 1.2, 1.2, 1.2, 0, new Particle.DustOptions(type.primaryColor(), 2.2f));
            }
            for (Player viewer : viewers()) {
                boolean pack = plugin.resourcePack().hasPack(viewer);
                plugin.flashbang().flash(viewer, 7, 7, 0, pack ? Flashbang.Mode.OVERLAY : Flashbang.Mode.TITLE, FLASH_TEXTURE);
                // Played on the listener, not at the centre, so the hit is full volume for everyone.
                viewer.playSound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.MASTER, 1f, 0.8f);
                viewer.playSound(viewer, Sound.ITEM_TOTEM_USE, SoundCategory.MASTER, 1f, 1f);
                viewer.playSound(viewer, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 1f, 0.6f);
                viewer.playSound(viewer, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1f, 1.4f);
                for (int note : CHORD) {
                    viewer.playSound(viewer, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 1f, pitch(note));
                }
                for (long delay : new long[]{0, 3, 7}) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (viewer.isOnline()) {
                            viewer.sendHurtAnimation(ThreadLocalRandom.current().nextFloat() * 360f);
                        }
                    }, delay);
                }
            }
            spawnShards(chest);
        }

        void spawnShards(Location chest) {
            for (int i = 0; i < TYPES.length * 6; i++) {
                Material block = SHARDS[i % TYPES.length];
                BlockDisplay shard = world.spawn(chest, BlockDisplay.class, d -> {
                    d.setBlock(block.createBlockData());
                    d.setBrightness(FULL_BRIGHT);
                    d.setTransformation(cube(new Vector3f(), new Quaternionf(), 0.5f));
                    prepare(d);
                });
                shards.add(shard);
            }
        }

        /** Each shard flies outward, spinning and shrinking away. */
        void launchShards() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < shards.size(); i++) {
                double angle = 2 * Math.PI * i / shards.size() + random.nextDouble(-0.1, 0.1);
                double distance = random.nextDouble(9, 15);
                Vector3f to = new Vector3f((float) (Math.cos(angle) * distance), (float) random.nextDouble(-0.5, 3.5),
                        (float) (Math.sin(angle) * distance));
                Quaternionf spin = new Quaternionf().rotateXYZ((float) random.nextDouble(-3, 3),
                        (float) random.nextDouble(-3, 3), (float) random.nextDouble(-3, 3));
                animate(shards.get(i), cube(to, spin, 0.06f), 26 + random.nextInt(10));
            }
        }

        /** A ring of every swizz colour racing out along the floor. */
        void shockwave(int k) {
            double radius = k * 0.85;
            int[] next = {k};
            Particles.circle(ground.clone().add(0, 0.3, 0), radius, 10 + (int) (radius * 7), 0, p -> world.spawnParticle(Particle.DUST, p, 1,
                    0.05, 0.05, 0.05, 0, new Particle.DustOptions(TYPES[next[0]++ % TYPES.length].primaryColor(), 1.8f)));
            if (k % 2 == 0) {
                Particles.circle(ground.clone().add(0, 0.5, 0), radius, 6 + (int) (radius * 2), 0.3,
                        p -> world.spawnParticle(Particle.FIREWORK, p, 1, 0, 0.05, 0, 0.02));
            }
        }

        void auraScreens() {
            for (Player viewer : viewers()) {
                if (plugin.resourcePack().hasPack(viewer)) {
                    plugin.flashbang().flash(viewer, 70, 70, 0, Flashbang.Mode.OVERLAY, AURA_TEXTURE);
                }
                viewer.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 90, 0, true, false, false));
            }
            sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f);
        }

        void titles() {
            Messages msg = plugin.messages();
            Title title = Title.title(msg.get("aura.title", Messages.text("name", player.getName())),
                    msg.get("aura.subtitle", Messages.text("name", player.getName())),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2600), Duration.ofMillis(900)));
            for (Player viewer : viewers()) {
                viewer.showTitle(title);
            }
        }

        void end() {
            if (task != null) {
                task.cancel();
            }
            runs.remove(player.getUniqueId());
            spawned.forEach(Entity::remove);
            spawned.clear();
            if (Attrs.has(player, Attribute.GRAVITY, Keys.MOD_AURA_HOVER) || (levitate && t < 30)) {
                player.removePotionEffect(PotionEffectType.LEVITATION);
                land();
            }
        }

        // ── Helpers ─────────────────────────────────────────────────────────────

        /** A flat glowing sigil (pack model). Hidden from players without the pack, who'd see a missing model. */
        ItemDisplay sigil(Location at, String model) {
            ItemStack stack = new ItemStack(Material.PAPER);
            stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(model));
            ItemDisplay display = world.spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(stack);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBrightness(FULL_BRIGHT);
                d.setTeleportDuration(2);
                d.setTransformation(flatTransform(0, 0.01f, 0f));
                d.setVisibleByDefault(false);
                prepare(d);
            });
            for (Player viewer : world.getPlayers()) {
                if (plugin.resourcePack().hasPack(viewer)) {
                    viewer.showEntity(plugin, display);
                }
            }
            return display;
        }

        void prepare(Entity entity) {
            entity.setPersistent(false);
            entity.getPersistentDataContainer().set(Keys.SPAWNED_BY_SWIZZ, PersistentDataType.BYTE, (byte) 1);
            spawned.add(entity);
        }

        List<Player> viewers() {
            double radius = plugin.settings().auraScreenRadius;
            List<Player> list = new ArrayList<>();
            for (Player viewer : world.getPlayers()) {
                if (viewer.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                    list.add(viewer);
                }
            }
            return list;
        }

        void sound(Sound sound, float pitch) {
            world.playSound(player.getLocation(), sound, SoundCategory.MASTER, 3f, pitch);
        }

        Location head() {
            return flat(player.getLocation().add(0, player.getHeight() + 0.35, 0));
        }

        /** 0 → 1 while the seven build up, falling back to 0 after the convergence. */
        double charge() {
            if (t <= CONVERGE) {
                return clamp((t - CHARGE_START) / (double) (CONVERGE - CHARGE_START));
            }
            return clamp(1 - (t - CONVERGE) / 40.0);
        }

        float outerScale(int at) {
            if (at < SIGIL_START) {
                return 0.01f;
            }
            if (at < 50) {
                return (float) Math.max(0.01, OUTER_SIZE * smooth((at - SIGIL_START) / 40.0));
            }
            if (at >= CONVERGE && at < CONVERGE + 8) {
                return OUTER_SIZE * 1.2f;
            }
            if (at >= FADE) {
                return (float) Math.max(0.01, OUTER_SIZE * (1 - smooth((at - FADE) / 35.0)));
            }
            return OUTER_SIZE + (float) Math.sin(at / 8.0) * 0.4f;
        }

        float innerScale(int at) {
            if (at < SIGIL_START + 10) {
                return 0.01f;
            }
            if (at < 50) {
                return (float) Math.max(0.01, INNER_SIZE * smooth((at - SIGIL_START - 10) / 30.0));
            }
            if (at >= FADE - 5) {
                return (float) Math.max(0.01, INNER_SIZE * (1 - smooth((at - FADE + 5) / 35.0)));
            }
            return INNER_SIZE;
        }

        float haloScale(int at) {
            if (at < HALO + 5 || at >= END - 8) {
                return 0.01f;
            }
            return 1.1f;
        }
    }

    // ── Static helpers ──────────────────────────────────────────────────────────

    private static void animate(Display display, Transformation transformation, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation);
    }

    /** Lies a plane model flat on the floor, turned {@code angle} around the vertical. */
    private static Transformation flatTransform(double angle, float scale, float lift) {
        return new Transformation(new Vector3f(0, lift, 0),
                new Quaternionf().rotateY((float) angle).rotateX((float) (-Math.PI / 2)),
                new Vector3f(scale), new Quaternionf());
    }

    private static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf());
    }

    /** A block model (0..1) of {@code size}, centred on {@code center} and turned around its middle. */
    private static Transformation cube(Vector3f center, Quaternionf rotation, float size) {
        Vector3f half = rotation.transform(new Vector3f(size / 2));
        return new Transformation(new Vector3f(center).sub(half), rotation, new Vector3f(size), new Quaternionf());
    }

    /** The floor under a location: its own height when standing, else the first solid block below. */
    private static Location floor(Location at) {
        double y = at.getY();
        for (int i = 0; i < 16 && !at.getWorld().getBlockAt(at.getBlockX(), (int) Math.floor(y - 0.05), at.getBlockZ()).isSolid(); i++) {
            y = Math.floor(y - 0.05);
        }
        return new Location(at.getWorld(), at.getX(), y, at.getZ());
    }

    /** Displays turn with their entity yaw, so their locations always face straight ahead. */
    private static Location flat(Location location) {
        location.setYaw(0);
        location.setPitch(0);
        return location;
    }

    private static float pitch(int semitones) {
        return (float) Math.pow(2, (semitones - 12) / 12.0);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double smooth(double v) {
        double x = clamp(v);
        return x * x * (3 - 2 * x);
    }

    private static double lerp(double a, double b, double f) {
        return a + (b - a) * clamp(f);
    }
}
