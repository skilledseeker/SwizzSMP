package com.swizzsmp.effects.cinematic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** A burst of small block displays that fly outward, spinning and shrinking away. */
final class Shards extends Layer {

    /** One block per swizz, in {@link com.swizzsmp.swizz.SwizzType} order. */
    static final Material[] ELEMENTS = {Material.LIGHT_BLUE_STAINED_GLASS, Material.AMETHYST_BLOCK,
            Material.PACKED_MUD, Material.EMERALD_BLOCK, Material.BLUE_ICE, Material.GOLD_BLOCK, Material.SCULK};
    static final Material[] COSMIC = {Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.END_STONE, Material.AMETHYST_BLOCK, Material.MAGMA_BLOCK};

    private final List<BlockDisplay> shards = new ArrayList<>();

    Shards(Cinematic host) {
        super(host);
    }

    /** {@code count} shards of the given blocks (cycling) packed into one point. */
    void spawn(Location at, Material[] blocks, int count, float size) {
        for (int i = 0; i < count; i++) {
            Material block = blocks[i % blocks.length];
            shards.add(host.spawn(Cinematic.flat(at.clone()), BlockDisplay.class, d -> {
                d.setBlock(block.createBlockData());
                d.setBrightness(Cinematic.FULL_BRIGHT);
                d.setTransformation(Cinematic.cube(new Vector3f(), new Quaternionf(), size));
            }));
        }
    }

    /**
     * Every shard flies out between the two distances. {@code sphere} throws them in every
     * direction (mostly up); otherwise they fan out level, between the two heights.
     */
    void launch(double minDistance, double maxDistance, boolean sphere, double yMin, double yMax) {
        for (int i = 0; i < shards.size(); i++) {
            Vector3f to;
            if (sphere) {
                double theta = Cinematic.random().nextDouble(Math.PI * 2);
                double phi = Math.acos(Cinematic.random().nextDouble(-0.6, 1));
                double r = Cinematic.random().nextDouble(minDistance, maxDistance);
                to = new Vector3f((float) (r * Math.sin(phi) * Math.cos(theta)), (float) (r * Math.cos(phi)), (float) (r * Math.sin(phi) * Math.sin(theta)));
            } else {
                double angle = 2 * Math.PI * i / shards.size() + Cinematic.random().nextDouble(-0.1, 0.1);
                double distance = Cinematic.random().nextDouble(minDistance, maxDistance);
                to = new Vector3f((float) (Math.cos(angle) * distance), (float) Cinematic.random().nextDouble(yMin, yMax), (float) (Math.sin(angle) * distance));
            }
            Cinematic.animate(shards.get(i), Cinematic.cube(to, Cinematic.randomRotation(3), 0.05f), 24 + Cinematic.random().nextInt(12));
        }
    }

    void removeAll() {
        shards.forEach(host::remove);
        shards.clear();
    }
}
