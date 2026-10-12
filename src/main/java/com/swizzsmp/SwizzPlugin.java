package com.swizzsmp;

import com.swizzsmp.ability.AbilityService;
import com.swizzsmp.ability.CooldownService;
import com.swizzsmp.combat.AbilityDamage;
import com.swizzsmp.combat.ComboTracker;
import com.swizzsmp.combat.FreezeService;
import com.swizzsmp.combat.TargetFilter;
import com.swizzsmp.command.Commands;
import com.swizzsmp.config.AbilityValues;
import com.swizzsmp.config.ConfigFile;
import com.swizzsmp.config.ConfigMigrations;
import com.swizzsmp.config.Messages;
import com.swizzsmp.config.Settings;
import com.swizzsmp.data.PlayerDataStore;
import com.swizzsmp.data.ServerState;
import com.swizzsmp.effects.BlockRestorer;
import com.swizzsmp.effects.ControlInverter;
import com.swizzsmp.effects.Flashbang;
import com.swizzsmp.effects.LowHealthEffect;
import com.swizzsmp.energy.EnergyListener;
import com.swizzsmp.energy.EnergyService;
import com.swizzsmp.gui.BookMenu;
import com.swizzsmp.gui.GuideBook;
import com.swizzsmp.gui.LoadoutMenu;
import com.swizzsmp.gui.MenuListener;
import com.swizzsmp.gui.RerollMenu;
import com.swizzsmp.hud.ActionBarHud;
import com.swizzsmp.items.CustomItems;
import com.swizzsmp.items.ItemGuardListener;
import com.swizzsmp.items.RecipeService;
import com.swizzsmp.items.TokenListener;
import com.swizzsmp.items.UpgraderGuardListener;
import com.swizzsmp.passive.PassiveService;
import com.swizzsmp.ritual.RitualService;
import com.swizzsmp.special.DragonEggService;
import com.swizzsmp.special.SwizzMaceListener;
import com.swizzsmp.special.SwizzSwordService;
import com.swizzsmp.special.LocatorService;
import com.swizzsmp.special.TabColorService;
import com.swizzsmp.swizz.SwizzHandler;
import com.swizzsmp.swizz.SwizzService;
import com.swizzsmp.swizz.SwizzType;
import com.swizzsmp.swizz.impl.AquaHandler;
import com.swizzsmp.swizz.impl.ConfusionHandler;
import com.swizzsmp.swizz.impl.EarthHandler;
import com.swizzsmp.swizz.impl.FortuneHandler;
import com.swizzsmp.swizz.impl.GleamHandler;
import com.swizzsmp.swizz.impl.SculkHandler;
import com.swizzsmp.swizz.impl.FrostHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SwizzPlugin extends JavaPlugin {

    private ConfigFile configFile;
    private ConfigFile abilitiesFile;
    private ConfigFile itemsFile;
    private ConfigFile messagesFile;
    private ConfigFile guideFile;
    private GuideBook guide;
    private Settings settings;
    private Messages messages;

    private PlayerDataStore store;
    private SwizzService swizz;
    private CooldownService cooldowns;
    private AbilityService abilities;
    private PassiveService passives;
    private EnergyService energy;
    private TargetFilter targets;
    private FreezeService freeze;
    private ComboTracker combos;
    private AbilityDamage damage;
    private BlockRestorer blocks;
    private Flashbang flashbang;
    private ControlInverter inverter;
    private LowHealthEffect lowHealth;
    private CustomItems items;
    private RecipeService recipes;
    private ActionBarHud hud;
    private RitualService rituals;
    private ServerState serverState;
    private SwizzSwordService swords;
    private DragonEggService dragonEgg;
    private TabColorService tabColors;
    private LocatorService locator;
    private BukkitTask autosave;

    @Override
    public void onEnable() {
        Banner.print(getComponentLogger());
        configFile = new ConfigFile(this, "config.yml");
        abilitiesFile = new ConfigFile(this, "abilities.yml");
        itemsFile = new ConfigFile(this, "items.yml");
        messagesFile = new ConfigFile(this, "messages.yml");
        guideFile = new ConfigFile(this, "guide.yml");
        messages = new Messages(messagesFile);
        loadConfigs();

        store = new PlayerDataStore(this, () -> settings);
        swizz = new SwizzService(store);
        targets = new TargetFilter(store);
        freeze = new FreezeService(this);
        combos = new ComboTracker(this);
        damage = new AbilityDamage(this);
        blocks = new BlockRestorer(this);
        blocks.recoverPending();
        flashbang = new Flashbang(this);
        inverter = new ControlInverter(this);
        lowHealth = new LowHealthEffect(this);
        cooldowns = new CooldownService(this);
        abilities = new AbilityService(this);
        passives = new PassiveService(this);
        energy = new EnergyService(this);
        items = new CustomItems(itemsFile, getLogger());
        recipes = new RecipeService(itemsFile, items, getLogger());
        hud = new ActionBarHud(this);
        rituals = new RitualService(this);
        serverState = new ServerState(this);
        swords = new SwizzSwordService(this);
        tabColors = new TabColorService(this);
        locator = new LocatorService(this, serverState);
        guide = new GuideBook(this, guideFile);

        registerHandlers();
        recipes.registerAll();

        listen(new PlayerListener(this));
        listen(new EnergyListener(this));
        listen(new ItemGuardListener(this));
        listen(new TokenListener(this));
        listen(new UpgraderGuardListener(this));
        listen(new MenuListener());
        listen(swords);
        dragonEgg = new DragonEggService(this, serverState);
        listen(dragonEgg);
        listen(new SwizzMaceListener(this));
        listen(freeze);
        listen(combos);
        listen(damage);
        listen(blocks);
        listen(flashbang);
        listen(inverter);
        listen(lowHealth);
        Commands.registerAll(this);

        freeze.start();
        combos.start();
        flashbang.start();
        inverter.start();
        lowHealth.start();
        cooldowns.start();
        passives.start();
        hud.start();
        rituals.start();
        swords.start();
        tabColors.start();
        locator.start();
        startAutosave();

        // Support /reload and late enables: load anyone already online.
        for (Player player : Bukkit.getOnlinePlayers()) {
            store.loadOnline(player);
            passives.onJoin(player);
        }
        getLogger().info("SwizzSMP enabled.");
    }

    @Override
    public void onDisable() {
        // Paper disables plugins before players are kicked, so no quit event runs on
        // shutdown: record health now (see PlayerListener#restoreHealth).
        if (store != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                store.get(player).setLastHealth(player.isDead() ? 0 : player.getHealth());
            }
        }
        if (autosave != null) {
            autosave.cancel();
        }
        if (rituals != null) {
            rituals.stop();
        }
        if (swords != null) {
            swords.stop();
        }
        if (tabColors != null) {
            tabColors.stop();
        }
        if (locator != null) {
            locator.stop();
        }
        if (swizz != null) {
            swizz.shutdown();
        }
        if (abilities != null) {
            abilities.shutdown();
        }
        if (blocks != null) {
            blocks.restoreAll();
        }
        if (flashbang != null) {
            flashbang.stop();
        }
        if (inverter != null) {
            inverter.stop();
        }
        if (lowHealth != null) {
            lowHealth.stop();
        }
        if (combos != null) {
            combos.stop();
        }
        if (freeze != null) {
            freeze.stop();
        }
        if (hud != null) {
            hud.stop();
        }
        if (cooldowns != null) {
            cooldowns.stop();
        }
        if (passives != null) {
            passives.stop();
        }
        if (recipes != null) {
            recipes.unregisterAll();
        }
        if (store != null) {
            store.shutdown();
        }
    }

    private void registerHandlers() {
        swizz.register(new EarthHandler(this));
        swizz.register(new FrostHandler(this));
        swizz.register(new AquaHandler(this));
        swizz.register(new FortuneHandler(this));
        swizz.register(new GleamHandler(this));
        swizz.register(new SculkHandler(this));
        swizz.register(new ConfusionHandler(this, inverter));
        for (SwizzType type : SwizzType.values()) {
            if (swizz.handler(type) == null) {
                throw new IllegalStateException("No handler registered for " + type);
            }
        }
        for (SwizzHandler handler : swizz.handlers()) {
            if (handler instanceof Listener listener) {
                listen(listener);
            }
            if (handler instanceof ComboTracker.ComboListener comboListener) {
                combos.addListener(comboListener);
            }
        }
    }

    private void listen(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void loadConfigs() {
        configFile.load();
        abilitiesFile.load();
        itemsFile.load();
        messagesFile.load();
        guideFile.load();
        ConfigMigrations.run(configFile, abilitiesFile, messagesFile, getLogger());
        settings = new Settings(configFile.yaml());
        messages.reload();
    }

    private void startAutosave() {
        if (autosave != null) {
            autosave.cancel();
        }
        long period = Math.round(settings.autosaveMinutes * 60 * 20);
        autosave = Bukkit.getScheduler().runTaskTimer(this, () -> {
            store.saveDirtyAsync();
            rituals.save();
        }, period, period);
    }

    /** /swizzadmin reload. */
    public void reloadEverything() {
        loadConfigs();
        recipes.registerAll();
        hud.stop();
        hud.start();
        startAutosave();
        for (Player player : Bukkit.getOnlinePlayers()) {
            recipes.discoverAll(player);
            onStateChanged(player);
        }
    }

    /** Call after anything that changes what a player has (swizz, energy, loadout, upgrade). */
    public void onStateChanged(Player player) {
        if (player != null && player.isOnline()) {
            passives.refresh(player);
        }
    }

    /**
     * Saves the player's Swizz data and vanilla player file right away. Used after any
     * swap between items and Swizz state, so a crash can't duplicate or lose either.
     */
    public void saveNow(Player player) {
        store.saveAsync(store.get(player));
        player.saveData();
    }

    /** Ends every temporary effect tied to a player (death, or quit when {@code quitting}). */
    public void cleanupPlayer(Player player, boolean quitting) {
        abilities.cleanup(player);
        swizz.cleanup(player, quitting);
    }

    public void openReroller(Player player, EquipmentSlot hand) {
        if (!store.get(player).hasSwizz()) {
            messages.send(player, "reroll.need-swizz");
            return;
        }
        new RerollMenu(this, player, hand).open(player);
    }

    public void openLoadout(Player player) {
        new LoadoutMenu(this, player).open(player);
    }

    public void openBook(Player player) {
        new BookMenu(this).open(player);
    }

    public AbilityValues abilityValues(SwizzType type, String part) {
        return new AbilityValues(abilitiesFile.section(type.id() + "." + part));
    }

    /** The whole abilities.yml section of one swizz (used by the guide book). */
    public org.bukkit.configuration.ConfigurationSection abilitiesSection(SwizzType type) {
        return abilitiesFile.section(type.id());
    }

    /** The raw config.yml (for sections read live, like tab-colors). */
    public org.bukkit.configuration.file.YamlConfiguration settingsFile() {
        return configFile.yaml();
    }

    public ConfigFile itemsConfig() {
        return itemsFile;
    }

    public LocatorService locator() {
        return locator;
    }

    public TabColorService tabColors() {
        return tabColors;
    }

    public DragonEggService dragonEgg() {
        return dragonEgg;
    }

    public GuideBook guide() {
        return guide;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public PlayerDataStore store() {
        return store;
    }

    public SwizzService swizz() {
        return swizz;
    }

    public CooldownService cooldowns() {
        return cooldowns;
    }

    public AbilityService abilities() {
        return abilities;
    }

    public PassiveService passives() {
        return passives;
    }

    public EnergyService energy() {
        return energy;
    }

    public TargetFilter targets() {
        return targets;
    }

    public FreezeService freeze() {
        return freeze;
    }

    public ComboTracker combos() {
        return combos;
    }

    public AbilityDamage damage() {
        return damage;
    }

    public BlockRestorer blocks() {
        return blocks;
    }

    public Flashbang flashbang() {
        return flashbang;
    }

    public CustomItems items() {
        return items;
    }

    public RecipeService recipes() {
        return recipes;
    }

    public ActionBarHud hud() {
        return hud;
    }

    public RitualService rituals() {
        return rituals;
    }

    /** Unregisters a listener (used by features that can be toggled). */
    public void unlisten(Listener listener) {
        HandlerList.unregisterAll(listener);
    }
}
