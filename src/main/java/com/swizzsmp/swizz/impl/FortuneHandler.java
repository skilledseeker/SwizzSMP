package com.swizzsmp.swizz.impl;

import com.swizzsmp.SwizzPlugin;
import com.swizzsmp.ability.AbilityContext;
import com.swizzsmp.ability.AbilityResult;
import com.swizzsmp.ability.Perk;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.config.Messages;
import com.swizzsmp.effects.Particles;
import com.swizzsmp.items.CustomItems;
import com.swizzsmp.passive.BuffSet;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.util.Attrs;
import com.swizzsmp.util.Keys;
import com.swizzsmp.util.Time;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.EntityEffect;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.block.Container;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** FORTUNE (DESIGN.md §7.3). */
public final class FortuneHandler extends BaseHandler implements Listener {

    private static final Color GREEN = Color.fromRGB(0x2ECC71);
    private static final Color GOLD = Color.fromRGB(0xF1C40F);

    private static final class Circle {
        final UUID caster;
        final double radius;
        final int duration;
        final boolean upgraded;
        final AbilityValues values;
        BukkitTask task;
        int age;

        Circle(UUID caster, AbilityValues values, boolean upgraded) {
            this.caster = caster;
            this.values = values;
            this.upgraded = upgraded;
            this.radius = values.num("radius");
            this.duration = Time.secondsToTicks(values.num("duration"));
        }
    }

    private final Map<UUID, Circle> circles = new HashMap<>();
    private final Map<UUID, BukkitTask> rollHealth = new HashMap<>();
    private final Map<UUID, BukkitTask> rollScale = new HashMap<>();

    public FortuneHandler(SwizzPlugin plugin) {
        super(plugin, SwizzType.FORTUNE);
    }

    // ── Passive ───────────────────────────────────────────────────────────────

    @Override
    public void passiveBuffs(Player player, boolean upgraded, BuffSet out) {
        out.effect(PotionEffectType.HERO_OF_THE_VILLAGE, passiveValues().integer("hero-of-the-village-level"));
    }

    private static boolean listContains(List<String> list, String name) {
        for (String entry : list) {
            if (entry.trim().toUpperCase(Locale.ROOT).equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Triple loot from mobs. Only real loot is multiplied: anything the mob was wearing,
     * holding or carrying (picked-up items, donkey chests) is skipped, and so are containers
     * and bundles, so nothing can be duplicated this way.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player || !hasPassive(killer)) {
            return;
        }
        AbilityValues v = passiveValues();
        if (listContains(v.list("excluded-mobs"), dead.getType().name())) {
            return;
        }
        List<ItemStack> owned = ownedItems(dead);
        int multiplier = Math.max(1, v.integer("loot-multiplier"));
        List<String> excludedItems = v.list("excluded-items");
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.isEmpty() || CustomItems.isCustom(drop)
                    || listContains(excludedItems, drop.getType().name()) || holdsItems(drop)) {
                continue;
            }
            if (owned.stream().anyMatch(o -> o.isSimilar(drop))) {
                continue;
            }
            for (int i = 1; i < multiplier; i++) {
                extra.add(drop.clone());
            }
        }
        if (!extra.isEmpty()) {
            event.getDrops().addAll(extra);
            lootSparkle(killer, dead.getLocation().add(0, 0.5, 0));
        }
    }

    /** A little burst so players can see the triple loot happen. */
    private static void lootSparkle(Player player, Location at) {
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 12, 0.35, 0.35, 0.35, 0);
        at.getWorld().spawnParticle(Particle.DUST, at, 8, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(GOLD, 1.2f));
        player.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.8f);
    }

    /** Everything a mob wears, holds or carries in an inventory. */
    private static List<ItemStack> ownedItems(LivingEntity entity) {
        List<ItemStack> owned = new ArrayList<>();
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                try {
                    ItemStack item = equipment.getItem(slot);
                    if (item != null && !item.isEmpty()) {
                        owned.add(item);
                    }
                } catch (IllegalArgumentException ignored) {
                    // slot not supported by this mob
                }
            }
        }
        if (entity instanceof InventoryHolder holder) {
            for (ItemStack item : holder.getInventory().getContents()) {
                if (item != null && !item.isEmpty()) {
                    owned.add(item);
                }
            }
        }
        return owned;
    }

    /** Shulker boxes, bundles and other items that carry items inside them. */
    private static boolean holdsItems(ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }
        var meta = item.getItemMeta();
        return meta instanceof BundleMeta
                || (meta instanceof BlockStateMeta state && state.hasBlockState() && state.getBlockState() instanceof Container);
    }

    /** Triple loot from ores (never for Silk Touch, where the drop is the ore itself). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOreDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Material ore = event.getBlockState().getType();
        AbilityValues v = passiveValues();
        if (!listContains(v.list("ores"), ore.name()) || !hasPassive(player)) {
            return;
        }
        int multiplier = Math.max(1, v.integer("loot-multiplier"));
        Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        boolean multiplied = false;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack.getType() == ore || CustomItems.isCustom(stack)) {
                continue;
            }
            int total = stack.getAmount() * multiplier;
            int max = stack.getMaxStackSize();
            stack.setAmount(Math.min(max, total));
            item.setItemStack(stack);
            multiplied = true;
            for (int left = total - max; left > 0; left -= max) {
                ItemStack overflow = stack.clone();
                overflow.setAmount(Math.min(max, left));
                at.getWorld().dropItemNaturally(at, overflow);
            }
        }
        if (multiplied) {
            lootSparkle(player, at);
        }
    }

    /**
     * Upgraded: a chance to survive a lethal hit like a totem, keeping your effects. Decided
     * on the lethal hit itself: the hit is cancelled, you drop to 1 HP and get the totem's
     * buffs on top of your own effects. A real totem in hand is used normally instead.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalHit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !hasPassive(player) || !upgraded(player)
                || event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.KILL
                || event.getFinalDamage() < player.getHealth() + player.getAbsorptionAmount()
                || player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= passiveValues().upgradedNum("totem-chance")) {
            return;
        }
        event.setCancelled(true);
        player.setHealth(Math.min(1.0, player.getHealth()));
        player.setAbsorptionAmount(0);
        player.setFireTicks(0);
        for (PotionEffect totem : List.of(new PotionEffect(PotionEffectType.REGENERATION, 900, 1),
                new PotionEffect(PotionEffectType.ABSORPTION, 100, 1), new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0))) {
            PotionEffect now = player.getPotionEffect(totem.getType());
            if (now == null || now.getAmplifier() < totem.getAmplifier()
                    || (now.getAmplifier() == totem.getAmplifier() && now.getDuration() < totem.getDuration())) {
                player.addPotionEffect(totem);
            }
        }
        player.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        plugin.messages().send(player, "fortune.totem");
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 40, 0.6, 0.9, 0.6, 0);
    }

    @Override
    public AbilityResult activate(AbilityContext ctx) {
        return ctx.perk() == Perk.ABILITY_1 ? goldenCircle(ctx) : fortuneRoll(ctx);
    }

    // ── Ability 1: Golden Circle ──────────────────────────────────────────────

    private AbilityResult goldenCircle(AbilityContext ctx) {
        Player caster = ctx.player();
        Circle previous = circles.remove(caster.getUniqueId());
        if (previous != null) {
            previous.task.cancel();
        }
        Circle circle = new Circle(caster.getUniqueId(), ctx.values(), ctx.upgraded());
        circles.put(caster.getUniqueId(), circle);
        circle.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickCircle(circle), 0L, 1L);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        plugin.messages().send(caster, "fortune.circle-start",
                Messages.text("time", Time.format(Time.secondsToMillis(ctx.values().num("duration")))));
        return AbilityResult.success();
    }

    private void tickCircle(Circle circle) {
        Player caster = Bukkit.getPlayer(circle.caster);
        if (caster == null || caster.isDead() || circle.age++ >= circle.duration) {
            endCircle(circle);
            return;
        }
        Location center = caster.getLocation();
        if (circle.age % 4 == 0) {
            double spin = circle.age * 0.05;
            Particles.circle(center.clone().add(0, 0.15, 0), circle.radius, 48, spin, at -> {
                Color color = ((int) (at.getX() + at.getZ()) & 1) == 0 ? GREEN : GOLD;
                at.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.4f));
            });
        }
        if (circle.age % 20 != 0) {
            return;
        }
        int hungerAmp = Math.max(0, circle.values.integer("hunger-level") - 1);
        int repair = circle.upgraded ? circle.values.upgradedInt("armor-repair-per-second") : 0;
        for (Player other : caster.getWorld().getPlayers()) {
            if (other.isDead() || other.getLocation().distanceSquared(center) > circle.radius * circle.radius) {
                continue;
            }
            boolean friendly = other.equals(caster) || plugin.targets().trusts(caster, other);
            if (friendly) {
                if (repair > 0) {
                    repairArmor(other, repair);
                }
            } else if (canHarm(caster, other, TargetFilter.Mode.PLAYERS)) {
                other.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 40, hungerAmp, false, true, true));
            }
        }
    }

    private static void repairArmor(Player player, int amount) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = player.getInventory().getItem(slot);
            if (piece == null || piece.isEmpty() || !(piece.getItemMeta() instanceof Damageable meta) || meta.getDamage() <= 0) {
                continue;
            }
            meta.setDamage(Math.max(0, meta.getDamage() - amount));
            piece.setItemMeta(meta);
            player.getInventory().setItem(slot, piece);
        }
    }

    private void endCircle(Circle circle) {
        circle.task.cancel();
        circles.remove(circle.caster, circle);
    }

    /** Golden apples eaten inside a friendly Golden Circle are enhanced. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGapple(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.GOLDEN_APPLE || circles.isEmpty()) {
            return;
        }
        Player eater = event.getPlayer();
        Circle circle = friendlyCircleAt(eater);
        if (circle == null) {
            return;
        }
        int extraLevels = circle.values.integer("extra-absorption-levels");
        double extraSaturation = circle.values.num("extra-saturation");
        later(1L, () -> {
            if (!eater.isOnline()) {
                return;
            }
            PotionEffect absorption = eater.getPotionEffect(PotionEffectType.ABSORPTION);
            int base = absorption == null ? 0 : absorption.getAmplifier();
            eater.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, base + extraLevels));
            eater.setSaturation((float) Math.min(20.0, eater.getSaturation() + extraSaturation));
            eater.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, eater.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0);
            eater.playSound(eater.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1.4f);
        });
    }

    private Circle friendlyCircleAt(Player player) {
        for (Circle circle : circles.values()) {
            Player caster = Bukkit.getPlayer(circle.caster);
            if (caster == null || !caster.getWorld().equals(player.getWorld())) {
                continue;
            }
            boolean friendly = caster.equals(player) || plugin.targets().trusts(caster, player);
            if (friendly && caster.getLocation().distanceSquared(player.getLocation()) <= circle.radius * circle.radius) {
                return circle;
            }
        }
        return null;
    }

    // ── Ability 2: Fortune Roll ───────────────────────────────────────────────

    private record Outcome(String type, int level, double amount, double seconds) {
        String describe() {
            return switch (type) {
                case "MAX_HEALTH" -> "<red>+" + trim(amount / 2) + " Hearts";
                case "SMALL" -> "<aqua>Tiny size (harder to hit)";
                case "STRENGTH" -> "<dark_red>Strength " + roman(level);
                case "RESISTANCE" -> "<gray>Resistance " + roman(level);
                default -> "<light_purple>Regeneration " + roman(level);
            };
        }

        private static String trim(double v) {
            return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
        }

        private static String roman(int level) {
            return switch (level) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                case 5 -> "V";
                default -> String.valueOf(level);
            };
        }
    }

    private Outcome roll(AbilityValues v) {
        ConfigurationSection outcomes = v.raw().getConfigurationSection("outcomes");
        List<Outcome> options = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        if (outcomes != null) {
            for (String key : outcomes.getKeys(false)) {
                ConfigurationSection o = outcomes.getConfigurationSection(key);
                if (o == null || o.getDouble("weight") <= 0) {
                    continue;
                }
                String type = o.getString("type", "REGENERATION").toUpperCase(Locale.ROOT);
                double amount = type.equals("SMALL") ? o.getDouble("scale", 0.5) : o.getDouble("amount");
                options.add(new Outcome(type, Math.max(1, o.getInt("level", 1)), amount, o.getDouble("seconds", v.num("duration"))));
                weights.add(o.getDouble("weight"));
                total += o.getDouble("weight");
            }
        }
        if (options.isEmpty()) {
            return new Outcome("REGENERATION", 1, 0, v.num("duration"));
        }
        double pick = ThreadLocalRandom.current().nextDouble(total);
        for (int i = 0; i < options.size(); i++) {
            pick -= weights.get(i);
            if (pick < 0) {
                return options.get(i);
            }
        }
        return options.getLast();
    }

    private AbilityResult fortuneRoll(AbilityContext ctx) {
        Player caster = ctx.player();
        AbilityValues v = ctx.values();
        Outcome outcome = roll(v);
        apply(caster, outcome);
        String time = Time.format(Time.secondsToMillis(outcome.seconds()));
        plugin.messages().send(caster, "fortune.rolled", Messages.parsed("result", outcome.describe()), Messages.text("time", time));
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.4f);
        caster.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, caster.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0);
        dust2(caster.getLocation().add(0, 1.2, 0), 25, 0.6, 1.3f);
        if (ctx.upgraded()) {
            int share = v.upgradedInt("share-with-trusted");
            List<Player> friends = plugin.targets().trustedNearby(caster, v.upgradedNum("share-radius"));
            for (int i = 0; i < Math.min(share, friends.size()); i++) {
                Player friend = friends.get(i);
                apply(friend, outcome);
                plugin.messages().send(friend, "fortune.shared", Messages.text("name", caster.getName()),
                        Messages.parsed("result", outcome.describe()), Messages.text("time", time));
                friend.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, friend.getLocation().add(0, 1, 0), 20, 0.4, 0.7, 0.4, 0);
            }
        }
        return AbilityResult.success();
    }

    private void apply(Player player, Outcome outcome) {
        int ticks = Time.secondsToTicks(outcome.seconds());
        switch (outcome.type()) {
            case "MAX_HEALTH" -> {
                Attrs.apply(player, Attribute.MAX_HEALTH, Keys.MOD_ROLL_HEALTH, outcome.amount(), AttributeModifier.Operation.ADD_NUMBER);
                double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(Math.min(max, player.getHealth() + outcome.amount()));
                BukkitTask previous = rollHealth.put(player.getUniqueId(), later(ticks, () -> removeRollHealth(player)));
                if (previous != null) {
                    previous.cancel();
                }
            }
            case "SMALL" -> {
                // Scale is 1.0 normally; e.g. scale 0.5 = half size.
                Attrs.apply(player, Attribute.SCALE, Keys.MOD_ROLL_SCALE, outcome.amount() - 1.0, AttributeModifier.Operation.ADD_NUMBER);
                player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.02);
                BukkitTask previous = rollScale.put(player.getUniqueId(), later(ticks, () -> removeRollScale(player)));
                if (previous != null) {
                    previous.cancel();
                }
            }
            case "STRENGTH" -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, outcome.level() - 1));
            case "RESISTANCE" -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, outcome.level() - 1));
            default -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, outcome.level() - 1));
        }
    }

    private void removeRollHealth(Player player) {
        BukkitTask task = rollHealth.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        if (player.isOnline()) {
            Attrs.remove(player, Attribute.MAX_HEALTH, Keys.MOD_ROLL_HEALTH);
            Attrs.clampHealth(player);
        }
    }

    private void removeRollScale(Player player) {
        BukkitTask task = rollScale.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        if (player.isOnline()) {
            Attrs.remove(player, Attribute.SCALE, Keys.MOD_ROLL_SCALE);
        }
    }

    @Override
    public void cleanup(Player player) {
        Circle circle = circles.get(player.getUniqueId());
        if (circle != null) {
            endCircle(circle);
        }
        if (rollHealth.containsKey(player.getUniqueId())) {
            removeRollHealth(player);
        }
        if (rollScale.containsKey(player.getUniqueId())) {
            removeRollScale(player);
        }
    }

    @Override
    public void shutdown() {
        for (Circle circle : new ArrayList<>(circles.values())) {
            endCircle(circle);
        }
        for (UUID id : new ArrayList<>(rollHealth.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                removeRollHealth(player);
            } else {
                rollHealth.remove(id).cancel();
            }
        }
        for (UUID id : new ArrayList<>(rollScale.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                removeRollScale(player);
            } else {
                rollScale.remove(id).cancel();
            }
        }
    }
}
