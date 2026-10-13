# SwizzSMP — All Commands

## Player commands (everyone)

| Command | What it does |
|---|---|
| `/ability1` | Use ability 1 |
| `/ability2` | Use ability 2 |
| `/ability3` | Use ability 1 of your second element (Swizz Sword only) |
| `/ability4` | Use ability 2 of your second element (Swizz Sword only) |
| `/trust <player>` | Trust a player so your harmful abilities don't hit them. Run it again to untrust. |
| `/trust list` | Show who you trust |
| `/withdraw <amount>` | Turn energy into energy items. You always keep at least 1. |
| `/loadout` | Open the menu to choose which perk each energy slot unlocks |
| `/loadout <a1\|a2\|passive> <a1\|a2\|passive> <a1\|a2\|passive>` | Set the order directly, e.g. `/loadout a2 a1 passive` |
| `/swizz` | Show your swizz, energy, upgrade, loadout, cooldowns and trust count |
| `/swizz hud` | Turn the action bar HUD on/off |
| `/swizz controls` | Turn the Sneak+F / Sneak+Q shortcuts on/off |
| `/swizz heartbeat` | Turn the low-health red screen + heartbeat on/off |
| `/trail [on\|off]` | Turn the cherry blossom trail on/off (only blinkypies has it) |
| `/swizzbook` | Open the recipe book and guide for every swizz |

### Shortcuts

| Keys | What it does |
|---|---|
| **Sneak + F** | Ability 1 |
| **Sneak + Q** | Ability 2 (you need to be holding an item) |

## Admin commands (op only, permission `swizz.admin`)

| Command | What it does |
|---|---|
| `/giveswizz <swizz> <player>` | Set a player's swizz directly |
| `/giveswizz <swizz> <player> upgraded` | Set a player's swizz, already upgraded |
| `/giveupgraded <player>` | Upgrade a player's current swizz without a ritual |
| `/energy set <player> <amount>` | Set a player's energy |
| `/energy add <player> <amount>` | Add energy |
| `/energy remove <player> <amount>` | Remove energy |
| `/energy view <player>` | Check a player's energy |
| `/giveenergy <player> [amount]` | Give energy items |
| `/givereroller <player> [amount]` | Give rerollers |
| `/giveupgrader <player> [amount]` | Give upgraders |
| `/resetcooldown <player>` | Clear all of a player's cooldowns |
| `/swizzadmin reload` | Reload every config file |
| `/swizzadmin give <item> <player> [amount]` | Give any custom item (list below) |
| `/swizzadmin upgrade <player> true` | Upgrade a player's swizz |
| `/swizzadmin upgrade <player> false` | Remove a player's upgrade |
| `/swizzadmin info <player>` | Show everything about a player |
| `/swizzadmin ritual list` | List running rituals with their real coordinates |
| `/swizzadmin ritual start <player>` | Start a ritual where the player is standing (no upgrader needed) |
| `/swizzadmin ritual cancel <player>` | Cancel a player's ritual and give the upgrader back |
| `/swizzadmin dragonegg reset` | Reset the server-wide Dragon Egg cooldown |
| `/swizzadmin recipe <item>` | Edit an item's crafting recipe in game (click a grid slot while holding an item; Save). Potions keep their exact type |
| `/swizzadmin craftcheck <item>` | Check that an item's recipe crafts with its own ingredients |
| `/jumpscare [player\|all]` | Horror picture + huge scream for 2.5 s (no name = everyone but you, `all` = you too) |
| `/aura [player]` | 14-second showcase cinematic around a player (no name = yourself) |
## Names you can type

**Swizz types:** `aqua`, `confusion`, `earth`, `fortune`, `frost`, `gleam`, `sculk`
(`water` also works for aqua)

**Items for `/swizzadmin give`:**

| Item id | Item |
|---|---|
| `swizz_aqua` | Aqua swizz |
| `swizz_confusion` | Confusion swizz |
| `swizz_earth` | Earth swizz |
| `swizz_fortune` | Fortune swizz |
| `swizz_frost` | Frost swizz |
| `swizz_gleam` | Gleam swizz |
| `swizz_sculk` | Sculk swizz |
| `energy` | Energy |
| `reroller` | Reroller |
| `upgrader` | Upgrader |
| `swizz_sword` | Swizz Sword |
| `swizz_mace` | Swizz Mace |

## Using items (no command needed)

| Item | How to use it |
|---|---|
| Swizz item | Right-click to bind it (only works if you don't have a swizz yet) |
| Energy | Right-click for +1 energy |
| Reroller | Right-click, then pick a new swizz in the menu (you keep your energy) |
| Upgrader | Right-click a block twice to start a 20-minute ritual there |
| Swizz Sword | Right-click the first time to choose 2 elements. Keep it in your inventory to use both. |
| Swizz Mace | Hit a player to disable their swizz for 60 seconds (3 minute cooldown) |
| Dragon Egg (the real one) | Right-click air to scramble everyone else's swizz for 5 minutes (30 minute server cooldown). Sneak + right-click air: only you see the locator bar for 30 minutes (12 hour cooldown) |

## Quick test setup

```
/giveswizz frost <you>
/energy set <you> 5
/swizzadmin upgrade <you> true
/resetcooldown <you>
/swizzadmin give swizz_sword <you>
```
