# SwizzSMP

The Season 5 plugin for SwizzSMP: seven elemental **swizz** (Aqua, Confusion, Earth,
Fortune, Frost, Gleam, Sculk), energy, abilities, upgrade rituals and special items.

## Requirements

- **Paper 26.3** (built and tested on build 175). It should also run on 26.1 and 26.2.
- **Java 25** on the server.
- No other plugins needed.

## Install

1. Put `SwizzSMP-1.0.jar` in `plugins/`.
2. Start the server once. It creates `plugins/SwizzSMP/` with the config files.
3. Edit the configs if you like, then run `/swizzadmin reload`.

## How players use it

| Action | How |
|---|---|
| Get a swizz | Craft one (see `/swizzbook`) and right-click it |
| Use abilities | `/ability1`, `/ability2`, or **Sneak + F** / **Sneak + Q** (Q needs an item in hand) |
| Change swizz | Craft a **Reroller** (you need a swizz first), right-click it, pick a swizz |
| Choose perks | `/loadout` (GUI) or `/loadout a2 a1 passive` |
| Protect friends | `/trust <player>`, `/trust list` |
| Energy to items | `/withdraw <amount>` (you always keep 1) |
| Status | `/swizz` · `/swizz hud`, `/swizz controls` and `/swizz heartbeat` toggle the action bar, the key combos and the low-health warning |
| Guide | `/swizzbook`: recipes and a book about every swizz |
| Petal trail | `/trail` turns the cherry blossom trail off and on (only blinkypies has it) |
| Upgrade | Right-click a block with an **Upgrader** twice to start a 20-minute ritual |

## Admin commands (`swizz.admin`, op by default)

| Command | What it does |
|---|---|
| `/energy set\|add\|remove\|view <player> [n]` | Change or view energy (works on offline players) |
| `/giveenergy <player> [n]` | Give energy items |
| `/givereroller <player> [n]` | Give rerollers |
| `/giveupgrader <player> [n]` | Give upgraders |
| `/giveswizz <swizz> <player> [upgraded]` | Set a player's swizz directly (add `upgraded` to give it already upgraded) |
| `/giveupgraded <player>` | Upgrade a player's current swizz without a ritual |
| `/resetcooldown <player>` | Clear all cooldowns (handy for testing) |
| `/swizzadmin reload` | Reload every config file |
| `/swizzadmin give <item> <player> [n]` | Give any custom item: `swizz_<type>`, `energy`, `reroller`, `upgrader`, `swizz_sword`, `swizz_mace` |
| `/swizzadmin upgrade <player> <true\|false>` | Set or remove the upgrade |
| `/swizzadmin info <player>` | Everything about a player |
| `/swizzadmin ritual list` · `start <player>` · `cancel <player>` | See rituals, start one where a player stands (no item needed, for events), or cancel one (returns the Upgrader) |
| `/swizzadmin dragonegg reset` | Clear the server-wide Dragon Egg cooldown |
| `/swizzadmin recipe <item>` | Edit an item's crafting recipe in game (saves to `items.yml`, works right away) |
| `/swizzadmin craftcheck <item>` | Verify that an item's recipe actually crafts with its own ingredients |
| `/jumpscare [player\|all]` | Horror picture + huge scream for 2.5 s. No name = everyone but you, `all` = you too, a name = just them. Players without the texture pack get the elder guardian ghost. Sounds and length in `config.yml → jumpscare` |
| `/cinematic <aura\|matrix\|singularity\|timestop> [player]` | Showcase animations around a player (no name = yourself). **aura**: glowing sigil, the 7 swizz orbit and pour into the player, flash and shockwave. **matrix**: "Wake up…" on a green CRT, déjà-vu cat, the world turns into code, agents, bullet time, chrome, "Welcome to the real world". **singularity**: a black hole tears up the ground and detonates. **timestop**: a golden clock and a sphere that freezes players, mobs and arrows. Nothing real is changed. `/aura` is a shortcut (`config.yml → cinematics`) |
The **Swizz Sword** and **Swizz Mace** are admin-given only (`/swizzadmin give`). The
**Dragon Egg** uses the real dragon egg: right-click air to scramble everyone's swizz
for 5 minutes, or **sneak + right-click air** to be the only player with a locator bar
for 30 minutes (12 hour cooldown).

## Config files

| File | What's in it |
|---|---|
| `config.yml` | Energy rules, cooldown rules, ritual settings, dragon egg, mace, HUD, key combos |
| `abilities.yml` | **Every balance number**: cooldowns, damage, radius, durations, combo hits, upgraded values |
| `items.yml` | Item names, lore, looks (`item_model`) and **all crafting recipes** (or edit them in game with `/swizzadmin recipe <item>`) |
| `messages.yml` | Every message ([MiniMessage](https://docs.advntr.dev/minimessage) format) |
| `guide.yml` | The `/swizzbook` book pages; numbers are filled in live from `abilities.yml` |

Damage and health are in HP (2 HP = 1 heart). Ability cooldowns in `abilities.yml` are
base values. The energy tier bonus and the upgrade bonus (−25% each, in `config.yml`)
apply on top, and no cooldown goes below `cooldowns.min-seconds`.

With `abilities.true-damage: true`, armor doesn't reduce ability damage, so "3 hearts"
means 3 hearts. Set it to `false` for vanilla armor reduction.

## Recipes

All recipes are in `items.yml` (or edit them in game with `/swizzadmin recipe <item>`).
An ingredient is a material (`DIAMOND`), several materials (`GOLDEN_APPLE,ENCHANTED_GOLDEN_APPLE`
= any of them), or a potion of an exact type (`POTION:LONG_WATER_BREATHING` = Water
Breathing 8:00). Run `/swizzadmin craftcheck <item>` after editing to make sure it crafts.

## Other plugins

PlugManX is installed for plugin management. SwizzSMP is reload-safe: after uploading a
new jar, `/plugman reload SwizzSMP` loads it without a restart (all effects are cleaned up
and re-created; players keep their data). For config changes alone, `/swizzadmin reload`
is enough.

## Data files

- `players/<uuid>.yml`: each player's swizz, energy, upgrade, loadout, trust list and cooldowns
- `rituals.yml`: running rituals (they survive restarts; downtime pauses them)
- `restore.yml`: blocks to restore if the server stopped in the middle of a Sculk dome
- `server-state.yml`: the dragon egg cooldown

## Resource pack (item textures)

The swizz items have their own textures. The pack lives in `resourcepack/` of this repo:
`resourcepack/src/` is the source and `resourcepack/SwizzSMP-pack.zip` the built pack
(`gradlew buildPack`, also done by `gradlew build`).

The plugin sends the pack to players itself (`config.yml` → `resource-pack`): at startup it
downloads the zip from `resource-pack.url` once to learn its SHA-1, then every joining
player is asked to load it. By default players may decline (`required: false`); they then
see the vanilla look-alike items (`model:` in `items.yml`). Set `required: true` to kick
players who decline.

**Updating a texture:** replace the PNG in `resourcepack/src/assets/swizz/textures/item/`
(32×32, same file name), run `gradlew buildPack`, commit and push both the PNG and the
zip, wait a minute for GitHub's cache, then `/swizzadmin reload` on the server. Earth has
no custom texture yet (it uses the armadillo scute); drop a `swizz_earth.png` in the same
folder and add the two JSON files like the other items to give it one.

## Building from source

```
build.cmd                      (Windows, uses .tools/jdk-25 if present)
./gradlew build                (needs JAVA_HOME pointing at a JDK 25)
```

The jar is written to `build/libs/`. See `docs/DESIGN.md` for how everything works,
and `docs/TESTING.md` for the in-game test checklist.
