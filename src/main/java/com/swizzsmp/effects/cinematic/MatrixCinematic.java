package com.swizzsmp.effects.cinematic;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.config.Messages;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "matrix" (DESIGN.md §13.7): the player escapes the Matrix. Screens become a green CRT
 * that types "Wake up…", a black cat walks past twice (déjà vu), the world derezzes into a
 * black-and-green construct (fake blocks only the viewers see) under falling green code,
 * black-suited copies of the player multiply and fire bullets that stop in mid-air, then
 * liquid chrome swallows the screen and the player wakes up in "the real world".
 */
final class MatrixCinematic extends Cinematic {

    private static final int CAT_1 = 80;
    private static final int CAT_2 = 104;
    private static final int CAT_WALK = 20;
    private static final int KNOCK = 135;
    private static final int CONSTRUCT_START = 138;
    private static final int CONSTRUCT_TICKS = 34;
    private static final int RAIN_START = 140;
    private static final int[] AGENT_TICKS = {175, 181, 187, 193, 199};
    private static final int FIRE = 210;
    private static final int FLIGHT = 30;
    private static final int THE_ONE = 244;
    private static final int DROP = 252;
    private static final int ESCAPE = 262;
    private static final int WHITE = 279;
    private static final int BLACKOUT = 282;
    private static final int BLACKOUT_TICKS = 60;
    private static final int ESCAPED_LINE = 292;
    private static final int REAL_WORLD_LINE = 326;
    private static final int END = 370;
    /** Typed lines on the CRT: message key and the tick typing starts. */
    private static final String[] INTRO_KEYS = {"matrix.wake-up", "matrix.has-you", "matrix.rabbit", "matrix.knock"};
    private static final int[] INTRO_TICKS = {10, 42, 74, 106};

    private static final int CONSTRUCT_RADIUS = 16;
    private static final Vector3f BULLET = new Vector3f(0.07f, 0.07f, 0.24f);
    private static final TextColor GREEN = TextColor.color(0x00FF41);
    private static final TextColor DARK_GREEN = TextColor.color(0x0A5A1E);
    private static final TextColor PALE = TextColor.color(0xD4FFE0);
    private static final String GLYPHS = "ｦｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾜ0123456789Z:=*+<>¦";
    private static final String CRT = "swizz:misc/crt";
    private static final String BLACK = "swizz:misc/black";
    private static final String[] RAIN = frames("swizz:misc/rain_", 8);
    private static final String[] GLITCH = frames("swizz:misc/glitch_", 3);
    private static final String[] CHROME = frames("swizz:misc/chrome_", 3);

    private record Fake(Block block, BlockData data, double distance) {
    }

    private record Column(TextDisplay display, int dies) {
    }

    private record Bullet(BlockDisplay display, Location from, Vector3f stop, Vector3f direction) {
    }

    /** Everyone who got screen effects, so an early end can clear them. */
    private final Set<UUID> audience = new HashSet<>();
    private final List<Fake> construct = new ArrayList<>();
    private final Set<UUID> constructSentTo = new HashSet<>();
    private int constructSent;
    private boolean constructRestored;
    private final List<Column> columns = new ArrayList<>();
    private final List<Mannequin> agents = new ArrayList<>();
    private final List<Location> agentHomes = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private Cat cat;
    private Location catFrom;
    private Vector catPath;

    MatrixCinematic(SwizzPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected int length() {
        return END;
    }

    @Override
    protected void tick() {
        if (t == 0) {
            begin();
        }
        intro();
        if (t >= CAT_1 && t <= CAT_1 + CAT_WALK) {
            walkCat(CAT_1);
        }
        if (t >= CAT_2 && t <= CAT_2 + CAT_WALK) {
            walkCat(CAT_2);
        }
        if (t == CAT_2) {
            for (Player viewer : viewers()) {
                glitch(viewer, 3);
                jolt(viewer, 0);
            }
        }
        if (t == CAT_2 + 3) {
            for (Player viewer : viewers()) {
                overlay(viewer, CRT, KNOCK - t);
            }
        }
        if (t == KNOCK) {
            knock();
        }
        if (t > KNOCK && t < KNOCK + 12 && t % 2 == 0) {
            for (Player viewer : viewers()) {
                glitch(viewer, 2);
            }
        }
        if (t >= CONSTRUCT_START && t <= CONSTRUCT_START + CONSTRUCT_TICKS) {
            spreadConstruct();
        }
        if (t >= RAIN_START && t < ESCAPE) {
            rain();
        }
        for (int i = 0; i < AGENT_TICKS.length; i++) {
            if (t == AGENT_TICKS[i]) {
                multiplyAgents(1 << i);
            }
        }
        if (t > AGENT_TICKS[0] && t < ESCAPE) {
            jitterAgents();
        }
        if (t == FIRE) {
            fire();
        }
        if (t > FIRE && t <= FIRE + FLIGHT && t % 3 == 0) {
            ripples();
        }
        if (t == FIRE + FLIGHT) {
            sound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 2f);
            sound(Sound.BLOCK_BELL_RESONATE, 1.6f);
        }
        if (t == THE_ONE) {
            theOne();
        }
        if (t == DROP) {
            dropBullets();
        }
        if (t >= ESCAPE && t < BLACKOUT) {
            escape();
        }
        if (t == BLACKOUT) {
            blackout();
        }
        if (t == BLACKOUT + 1) {
            restoreConstruct();
            columns.forEach(c -> remove(c.display()));
            columns.clear();
            bullets.forEach(b -> remove(b.display()));
            bullets.clear();
        }
        if (t >= ESCAPED_LINE && t < REAL_WORLD_LINE) {
            typed("matrix.escaped", ESCAPED_LINE, GREEN);
        }
        if (t >= REAL_WORLD_LINE && t < BLACKOUT + BLACKOUT_TICKS + 14) {
            typed("matrix.real-world", REAL_WORLD_LINE, PALE);
        }
        if (t > BLACKOUT && t < BLACKOUT + BLACKOUT_TICKS && t % 20 == 8) {
            for (Player viewer : viewers()) {
                sound(viewer, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1f);
            }
        }
        if (t == BLACKOUT + 18) {
            for (Player viewer : viewers()) {
                sound(viewer, Sound.ENTITY_PLAYER_BREATH, 1f, 0.8f);
            }
        }
        if (t == BLACKOUT + BLACKOUT_TICKS) {
            wake();
        }
        if (t == BLACKOUT + BLACKOUT_TICKS + 14) {
            for (UUID id : audience) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer != null) {
                    viewer.clearTitle();
                }
            }
        }
    }

    // ── 1. Wake up ─────────────────────────────────────────────────────────────

    private void begin() {
        for (Player viewer : viewers()) {
            audience.add(viewer.getUniqueId());
            overlay(viewer, CRT, KNOCK);
            sound(viewer, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 0.5f);
        }
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1e-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        catFrom = player.getLocation().add(forward.clone().multiply(4.5)).subtract(right.clone().multiply(4));
        catPath = right.multiply(8);
    }

    private void intro() {
        for (int i = 0; i < INTRO_KEYS.length; i++) {
            int end = i + 1 < INTRO_TICKS.length ? INTRO_TICKS[i + 1] : KNOCK;
            if (t >= INTRO_TICKS[i] && t < end) {
                typed(INTRO_KEYS[i], INTRO_TICKS[i], GREEN);
            }
        }
    }

    /** Déjà vu: the same black cat walks the same path twice. */
    private void walkCat(int start) {
        double f = (t - start) / (double) CAT_WALK;
        Location at = floor(catFrom.clone().add(catPath.clone().multiply(f)).add(0, 1.5, 0));
        at.setDirection(catPath);
        at.setPitch(0);
        if (t == start) {
            cat = spawn(at, Cat.class, c -> {
                c.setCatType(Cat.Type.ALL_BLACK);
                c.setAI(false);
                c.setInvulnerable(true);
                c.setSilent(true);
                c.setCollidable(false);
            });
        } else if (cat != null) {
            cat.teleport(at);
        }
        if (t == start + CAT_WALK / 2) {
            world.playSound(at, Sound.ENTITY_CAT_AMBIENT, 1f, 1f);
        }
        if (t == start + CAT_WALK) {
            remove(cat);
            cat = null;
        }
    }

    // ── 2. Knock, knock: the world derezzes ────────────────────────────────────

    private void knock() {
        for (Player viewer : viewers()) {
            audience.add(viewer.getUniqueId());
            viewer.clearTitle();
            glitch(viewer, 3);
            jolt(viewer, 0, 2, 6, 10);
            sound(viewer, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
            sound(viewer, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);
            sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
        }
        planConstruct();
    }

    /** Every visible block within the radius, nearest first, with its construct look. */
    private void planConstruct() {
        int cx = ground.getBlockX();
        int cy = ground.getBlockY();
        int cz = ground.getBlockZ();
        for (int dx = -CONSTRUCT_RADIUS; dx <= CONSTRUCT_RADIUS; dx++) {
            for (int dz = -CONSTRUCT_RADIUS; dz <= CONSTRUCT_RADIUS; dz++) {
                if (dx * dx + dz * dz > CONSTRUCT_RADIUS * CONSTRUCT_RADIUS) {
                    continue;
                }
                for (int dy = -6; dy <= 12; dy++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    BlockData data = constructLook(block);
                    if (data != null) {
                        construct.add(new Fake(block, data, Math.sqrt(dx * dx + dz * dz + dy * dy * 0.5)));
                    }
                }
            }
        }
        construct.sort(Comparator.comparingDouble(Fake::distance));
    }

    /**
     * Full blocks become black with a lime grid on their visible faces; plants, torches and
     * other walk-through blocks vanish. Anything else (slabs, glass, leaves) is left alone,
     * so the client's idea of where it can stand never changes.
     */
    private static BlockData constructLook(Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) {
            return null;
        }
        if (block.isPassable()) {
            return block.getState(false) instanceof TileState ? null : Material.AIR.createBlockData();
        }
        if (!type.isOccluding()) {
            return null;
        }
        if (block.getState(false) instanceof TileState) {
            return null; // signs, chests, heads...: re-sent blocks would lose their contents on screen
        }
        boolean up =!block.getRelative(BlockFace.UP).getType().isOccluding();
        boolean northSouth = !block.getRelative(BlockFace.NORTH).getType().isOccluding() || !block.getRelative(BlockFace.SOUTH).getType().isOccluding();
        boolean eastWest = !block.getRelative(BlockFace.EAST).getType().isOccluding() || !block.getRelative(BlockFace.WEST).getType().isOccluding();
        if (!up && !northSouth && !eastWest) {
            return null;
        }
        boolean gx = Math.floorMod(block.getX(), 4) == 0;
        boolean gy = Math.floorMod(block.getY(), 4) == 0;
        boolean gz = Math.floorMod(block.getZ(), 4) == 0;
        boolean line = (up && (gx || gz)) || (northSouth && (gx || gy)) || (eastWest && (gz || gy));
        return (line ? Material.LIME_CONCRETE : Material.BLACK_CONCRETE).createBlockData();
    }

    /** The construct spreads out from the player as a ring. */
    private void spreadConstruct() {
        double radius = (t - CONSTRUCT_START) / (double) CONSTRUCT_TICKS * (CONSTRUCT_RADIUS + 2);
        List<BlockState> wave = new ArrayList<>();
        while (constructSent < construct.size() && construct.get(constructSent).distance() <= radius) {
            Fake fake = construct.get(constructSent++);
            BlockState state = fake.block().getState();
            state.setBlockData(fake.data());
            wave.add(state);
        }
        if (wave.isEmpty()) {
            return;
        }
        for (Player viewer : viewers()) {
            constructSentTo.add(viewer.getUniqueId());
            viewer.sendBlockChanges(wave);
        }
        if (t % 6 == 0) {
            sound(Sound.BLOCK_SCULK_SPREAD, 0.6f);
            sound(Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f + random().nextFloat());
        }
    }

    private void restoreConstruct() {
        if (constructRestored || constructSent == 0) {
            return;
        }
        constructRestored = true;
        List<BlockState> real = new ArrayList<>();
        for (int i = 0; i < constructSent; i++) {
            real.add(construct.get(i).block().getState());
        }
        for (UUID id : constructSentTo) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null && viewer.getWorld() == world) {
                viewer.sendBlockChanges(real);
            }
        }
    }

    // ── 3. Digital rain ────────────────────────────────────────────────────────

    private void rain() {
        if (t % 2 == 0) {
            for (Player viewer : viewers()) {
                audience.add(viewer.getUniqueId());
                overlay(viewer, RAIN[(t / 2) % RAIN.length], 4);
            }
        }
        for (int i = 0; i < 2; i++) {
            spawnColumn(false);
        }
        for (int i = 0; i < 5; i++) {
            double angle = random().nextDouble(Math.PI * 2);
            double distance = random().nextDouble(1.5, 14);
            Location bottom = ground.clone().add(Math.cos(angle) * distance, 0.2, Math.sin(angle) * distance);
            Location top = bottom.clone().add(0, random().nextDouble(9, 15), 0);
            Color color = random().nextInt(4) == 0 ? Color.fromRGB(0xD4FFE0) : Color.fromRGB(0x00FF41);
            world.spawnParticle(Particle.TRAIL, top, 1, 0, 0, 0, 0, new Particle.Trail(bottom, color, random().nextInt(16, 28)));
        }
        updateColumns();
        if (t % 40 == RAIN_START % 40) {
            sound(Sound.BLOCK_BEACON_AMBIENT, 0.5f);
        }
        if (t % 5 == 0) {
            int[] minor = {0, 3, 5, 7, 10, 12, 15, 17, 19, 22};
            sound(Sound.BLOCK_NOTE_BLOCK_BIT, pitch(minor[random().nextInt(minor.length)]));
        }
    }

    /** A falling (or, on escape, rising) column of glowing code. */
    private void spawnColumn(boolean rising) {
        double angle = random().nextDouble(Math.PI * 2);
        double distance = random().nextDouble(2.5, 14);
        double height = rising ? random().nextDouble(-1, 2) : random().nextDouble(3, 9);
        Location at = ground.clone().add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
        float scale = (float) random().nextDouble(0.9, 1.7);
        int life = rising ? 14 : random().nextInt(26, 38);
        TextDisplay column = spawn(at, TextDisplay.class, d -> {
            d.text(code(random().nextInt(8, 15)));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(false);
            d.setBrightness(FULL_BRIGHT);
            d.setLineWidth(40);
            d.setTransformation(scaled(scale));
        });
        float fall = rising ? random().nextFloat(14, 22) : -random().nextFloat(5, 10);
        Bukkit.getScheduler().runTaskLater(plugin, () -> animate(column, new Transformation(new Vector3f(0, fall, 0),
                new Quaternionf(), new Vector3f(scale), new Quaternionf()), life - 2), 2L);
        columns.add(new Column(column, t + life));
    }

    /** Glyphs keep changing; expired columns go. */
    private void updateColumns() {
        columns.removeIf(c -> {
            if (t >= c.dies()) {
                remove(c.display());
                return true;
            }
            if ((t + c.dies()) % 4 == 0) {
                c.display().text(code(random().nextInt(8, 15)));
            }
            return false;
        });
    }

    /** A vertical string of katakana, dim at the top and white-hot at the falling head. */
    private static Component code(int length) {
        Component text = Component.empty();
        for (int i = 0; i < length; i++) {
            String glyph = String.valueOf(GLYPHS.charAt(random().nextInt(GLYPHS.length())));
            TextColor color = i == length - 1 ? PALE : TextColor.lerp((float) i / length, DARK_GREEN, GREEN);
            text = text.append(Component.text(glyph, color));
            if (i < length - 1) {
                text = text.append(Component.newline());
            }
        }
        return text;
    }

    // ── 4. Agents ──────────────────────────────────────────────────────────────

    /** Black-suited copies of the player, doubling each time. */
    private void multiplyAgents(int total) {
        ResolvableProfile profile = ResolvableProfile.resolvableProfile(player.getPlayerProfile());
        while (agents.size() < total) {
            int k = agents.size();
            double angle = k * Math.PI * (3 - Math.sqrt(5));
            double radius = 6 + (k % 3) * 1.6;
            Location home = floor(ground.clone().add(Math.cos(angle) * radius, 2, Math.sin(angle) * radius));
            face(home);
            Mannequin agent = spawn(home, Mannequin.class, m -> {
                m.setProfile(profile);
                m.setImmovable(true);
                m.setInvulnerable(true);
                m.setSilent(true);
                m.setCollidable(false);
                m.setDescription(Component.empty());
                EntityEquipment gear = m.getEquipment();
                gear.setChestplate(suit(Material.LEATHER_CHESTPLATE));
                gear.setLeggings(suit(Material.LEATHER_LEGGINGS));
                gear.setBoots(suit(Material.LEATHER_BOOTS));
            });
            agents.add(agent);
            agentHomes.add(home);
            world.spawnParticle(Particle.DUST, home.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0x00FF41), 1.3f));
        }
        sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f);
        sound(Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f);
    }

    private static ItemStack suit(Material piece) {
        ItemStack item = new ItemStack(piece);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(Color.fromRGB(0x141414));
        item.setItemMeta(meta);
        return item;
    }

    /** Agents stare at the player and glitch in place. */
    private void jitterAgents() {
        for (int i = 0; i < agents.size(); i++) {
            Location home = agentHomes.get(i);
            face(home);
            Location at = home.clone();
            if (random().nextInt(14) == 0) {
                at.add(random().nextDouble(-0.35, 0.35), 0, random().nextDouble(-0.35, 0.35));
            }
            agents.get(i).teleport(at);
        }
    }

    private void face(Location at) {
        Vector look = player.getLocation().toVector().subtract(at.toVector()).setY(0);
        if (look.lengthSquared() > 1e-4) {
            at.setDirection(look);
        }
        at.setPitch(0);
    }

    // ── 5. Bullet time ─────────────────────────────────────────────────────────

    private void fire() {
        Location target = chest();
        for (int i = 0; i < Math.min(12, agents.size()); i++) {
            Location from = agents.get(i).getLocation().add(0, 1.4, 0);
            flat(from);
            Vector direction = target.toVector().subtract(from.toVector()).normalize();
            Vector stopAt = target.toVector().subtract(direction.clone().multiply(1.5))
                    .add(new Vector(random().nextDouble(-0.4, 0.4), random().nextDouble(-0.4, 0.4), random().nextDouble(-0.4, 0.4)));
            Vector3f stop = stopAt.subtract(from.toVector()).toVector3f();
            Vector3f dir = direction.toVector3f();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir);
            BlockDisplay bullet = spawn(from, BlockDisplay.class, d -> {
                d.setBlock(Material.GOLD_BLOCK.createBlockData());
                d.setBrightness(FULL_BRIGHT);
                d.setTransformation(box(new Vector3f(), rotation, BULLET));
            });
            Bukkit.getScheduler().runTaskLater(plugin, () -> animate(bullet, box(stop, rotation, BULLET), FLIGHT - 2), 2L);
            bullets.add(new Bullet(bullet, from, stop, dir));
            world.spawnParticle(Particle.FLASH, from, 1, 0, 0, 0, 0, Color.fromRGB(0xFFE0A0));
        }
        for (Player viewer : viewers()) {
            for (int i = 0; i < 3; i++) {
                sound(viewer, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
            }
            sound(viewer, Sound.ENTITY_ARROW_SHOOT, 1f, 0.5f);
            sound(viewer, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.5f);
            effect(viewer, PotionEffectType.SLOWNESS, FLIGHT + 10, 1);
        }
    }

    /** Slow-motion air ripples trailing each bullet. */
    private void ripples() {
        double f = clamp((t - FIRE - 2) / (double) (FLIGHT - 2));
        for (Bullet bullet : bullets) {
            Vector3f d = bullet.direction();
            Vector3f u = new Vector3f(d).cross(0, 1, 0);
            if (u.lengthSquared() < 1e-4) {
                u.set(1, 0, 0);
            }
            u.normalize();
            Vector3f v = new Vector3f(d).cross(u).normalize();
            Location at = bullet.from().clone().add(bullet.stop().x * f, bullet.stop().y * f, bullet.stop().z * f);
            for (int i = 0; i < 12; i++) {
                double a = 2 * Math.PI * i / 12;
                double r = 0.32;
                world.spawnParticle(Particle.DUST, at.clone().add(
                        (u.x * Math.cos(a) + v.x * Math.sin(a)) * r,
                        (u.y * Math.cos(a) + v.y * Math.sin(a)) * r,
                        (u.z * Math.cos(a) + v.z * Math.sin(a)) * r), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0xE8FFF0), 0.5f));
            }
        }
    }

    private void theOne() {
        Title title = Title.title(Component.text(line("matrix.the-one"), GREEN).decorate(TextDecoration.BOLD), Component.empty(),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(400)));
        for (Player viewer : viewers()) {
            audience.add(viewer.getUniqueId());
            viewer.showTitle(title);
        }
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0.7f);
    }

    /** The stopped bullets fall and clink on the floor. */
    private void dropBullets() {
        for (Bullet bullet : bullets) {
            Vector3f landed = new Vector3f(bullet.stop().x, (float) (ground.getY() + 0.04 - bullet.from().getY()), bullet.stop().z);
            Quaternionf lying = new Quaternionf().rotateY((float) random().nextDouble(Math.PI * 2)).rotateX((float) (Math.PI / 2));
            animate(bullet.display(), box(landed, lying, BULLET), 10 + random().nextInt(5));
        }
        for (int i = 0; i < 4; i++) {
            int delay = 10 + i * 2;
            Bukkit.getScheduler().runTaskLater(plugin, () -> sound(Sound.BLOCK_CHAIN_FALL, 1.8f + random().nextFloat() * 0.2f), delay);
        }
    }

    // ── 6. Escape ──────────────────────────────────────────────────────────────

    private void escape() {
        int k = t - ESCAPE;
        if (k == 0) {
            for (Player viewer : viewers()) {
                audience.add(viewer.getUniqueId());
                sound(viewer, Sound.BLOCK_PORTAL_TRAVEL, 0.9f, 1f);
                sound(viewer, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                effect(viewer, PotionEffectType.NAUSEA, 60, 0);
            }
            columns.forEach(c -> animate(c.display(), new Transformation(new Vector3f(0, 18, 0), new Quaternionf(),
                    c.display().getTransformation().getScale(), new Quaternionf()), 10));
        }
        if (k == 2) {
            for (Player viewer : viewers()) {
                sound(viewer, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.5f);
                sound(viewer, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.6f);
            }
        }
        // Agents dissolve two at a time into rising green code.
        for (int i = 0; i < 2 && !agents.isEmpty(); i++) {
            Mannequin agent = agents.remove(agents.size() - 1);
            agentHomes.remove(agentHomes.size() - 1);
            Location at = agent.getLocation().add(0, 1, 0);
            world.spawnParticle(Particle.DUST, at, 60, 0.3, 0.9, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0x00FF41), 1.4f));
            world.spawnParticle(Particle.TRAIL, at, 8, 0.3, 0.6, 0.3, 0, new Particle.Trail(at.clone().add(0, 8, 0), Color.fromRGB(0x00FF41), 20));
            remove(agent);
        }
        spawnColumn(true);
        spawnColumn(true);
        updateColumns();
        // Liquid chrome creeps in from the edges and swallows the screen.
        int chrome = k < 2 ? -1 : Math.min(CHROME.length - 1, (k - 2) / 5);
        for (Player viewer : viewers()) {
            if (chrome < 0) {
                glitch(viewer, 2);
            } else if (t < WHITE && (k - 2) % 5 == 0) {
                overlay(viewer, CHROME[chrome], 8);
            }
            if (k % 2 == 0) {
                jolt(viewer, 0);
                sound(viewer, Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, 0.5f + random().nextFloat() * 1.5f);
            }
            if (t == WHITE) {
                whiteFlash(viewer, 3);
            }
        }
    }

    private void blackout() {
        for (Player viewer : viewers()) {
            audience.add(viewer.getUniqueId());
            viewer.stopAllSounds();
            if (!overlay(viewer, BLACK, BLACKOUT_TICKS)) {
                effect(viewer, PotionEffectType.BLINDNESS, BLACKOUT_TICKS, 0);
            }
        }
    }

    // ── 7. The real world ──────────────────────────────────────────────────────

    private void wake() {
        for (Player viewer : viewers()) {
            whiteFlash(viewer, 4);
            sound(viewer, Sound.ENTITY_PLAYER_BREATH, 1f, 1f);
            sound(viewer, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
            sound(viewer, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.7f);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** A message typed out one letter per tick under a blinking cursor, with key clicks. */
    private void typed(String key, int start, TextColor color) {
        String text = line(key);
        int shown = Math.min(text.length(), t - start + 1);
        boolean typing = shown < text.length() || t == start + text.length() - 1;
        boolean cursor = shown < text.length() || (t / 6) % 2 == 0;
        Component sub = Component.text(text.substring(0, shown), color).append(Component.text(cursor ? "█" : " ", color));
        Title title = Title.title(Component.empty(), sub, Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO));
        boolean click = typing && shown > 0 && text.charAt(shown - 1) != ' ';
        for (Player viewer : viewers()) {
            audience.add(viewer.getUniqueId());
            viewer.showTitle(title);
            if (click) {
                sound(viewer, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.35f, 1.6f + random().nextFloat() * 0.4f);
            }
        }
    }

    private String line(String key) {
        return PlainTextComponentSerializer.plainText().serialize(plugin.messages().get(key, Messages.text("name", player.getName())));
    }

    private void glitch(Player viewer, int ticks) {
        overlay(viewer, GLITCH[random().nextInt(GLITCH.length)], ticks);
        sound(viewer, Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 0.5f + random().nextFloat() * 1.5f);
    }

    private static String[] frames(String prefix, int count) {
        String[] frames = new String[count];
        for (int i = 0; i < count; i++) {
            frames[i] = prefix + i;
        }
        return frames;
    }

    @Override
    protected void cleanup() {
        restoreConstruct();
        if (t < END) {
            for (UUID id : audience) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer != null) {
                    clearOverlay(viewer);
                    viewer.clearTitle();
                    clearEffect(viewer, PotionEffectType.BLINDNESS);
                    clearEffect(viewer, PotionEffectType.NAUSEA);
                    clearEffect(viewer, PotionEffectType.SLOWNESS);
                }
            }
        }
    }
}
