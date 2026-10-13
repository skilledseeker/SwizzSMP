# SwizzSMP Season 5 — Design Doc

This is the working blueprint for the plugin. It turns the owner's spec into exact,
buildable rules. Every unclear point in the spec has a numbered **decision (D-xx)**,
and every one of them is a config value so the owner can change it later.

**Rule for building:** follow this doc. If code and doc disagree, fix one of them so
they agree again. Tick the checklists in §15 as work is finished.

---

## 1. Platform

| Item | Choice |
|---|---|
| Server | **Paper 26.3** (the current supported version; 1.21.11 support ended June 2026) |
| Java | **25** (Paper 26.3's minimum) |
| Build | Gradle 9.8.1 (wrapper), Kotlin DSL |
| API | `io.papermc.paper:paper-api:26.3.build.175-stable` (`compileOnly`) |
| Dependencies | **None.** Only the Paper API. No NMS, ProtocolLib or PacketEvents. |
| Text | Adventure components + MiniMessage strings in `messages.yml` |
| Threading | Main thread only, except for writing data files (async). Folia is not supported. |

We need no packet library because the Paper API already covers every hard feature:
- per-viewer fake equipment: `Player#sendEquipmentChange`
- skin swap: `Player#setPlayerProfile`
- reading WASD input: `PlayerInputEvent` / `Player#getCurrentInput`
- per-viewer particles: `Player#spawnParticle`
- per-viewer entities: `Entity#setVisibleByDefault(false)` + `Player#showEntity`

Tools live in `.tools/` (portable JDK 25, Gradle, a local test server). They are gitignored.

---

## 2. Glossary

- **Swizz**: one of 7 elements: `AQUA, CONFUSION, EARTH, FORTUNE, FROST, GLEAM, SCULK`.
  The spec says both "Aqua" and "Water"; **D-01: the id is `AQUA`, shown as "Aqua"**.
- **Energy**: an integer from 1 to 5. It decides what is unlocked.
- **Perk**: one of `ABILITY_1`, `ABILITY_2`, `PASSIVE`.
- **Loadout**: the order of the 3 perks across the energy tiers (see §5).
- **Upgraded**: a per-player flag set by finishing an upgrader ritual.
- **Effective swizz**: the swizz(es) actually active right now, after the mace, the
  dragon egg and the Swizz sword are taken into account (§4.3).
- **Untrusted target**: a valid target of a harmful effect (§6.2).
- **Reload time**: the cooldown of a combo passive (Confusion, Gleam) after it fires.

---

## 3. Data model

### 3.1 PlayerData (one YAML file per player: `plugins/SwizzSMP/players/<uuid>.yml`)
```
uuid, lastKnownName
swizz:            SwizzType | null    # null = never obtained one
energy:           int (default 2)
upgraded:         bool
loadout:          [Perk, Perk, Perk]  # default [ABILITY_1, ABILITY_2, PASSIVE]
loadoutChangedAt: epochMillis         # for the switch cooldown
trusted:          Set<UUID>
cooldowns:        Map<String, long remainingMillis>  # keys: "A1","A2","B1","B2"
cooldownsSavedAt: epochMillis          # offline time is subtracted on load
override:         { swizz, untilEpochMillis } | null    # dragon egg
disabledUntil:    epochMillis (0 = none)                # swizz mace
hudEnabled:       bool (default true)
controlsEnabled:  bool (default true)
trailEnabled:     bool (default true)   # /trail (only blinkypies has the trail, §13.5)
```
- Loaded on join (sync; the file is tiny) and kept in memory while online.
- Saved async on quit, every 5 minutes (only if changed), and sync on disable.
- Admin commands on offline players load the file, change it and save it straight away.

### 3.2 Server state files
- `rituals.yml`: active rituals (anchor, owner, elapsed seconds, leaked coordinates).
- `restore.yml`: pending block restorations (sculk domes etc.), so a crash mid-dome
  still restores the terrain on the next start.
- `server-state.yml`: the dragon egg cooldown (one for the whole server).

### 3.3 Item identity
All custom items carry PDC key `swizz:item` = the item id, plus extra keys where needed.
They are never identified by name or material. Item ids:
`swizz_aqua … swizz_sculk`, `energy`, `reroller`, `upgrader`, `swizz_sword`, `swizz_mace`.

---

## 4. Getting and changing a swizz

### 4.1 Swizz items (crafted)
- Each swizz has a crafted item. **Right-clicking it** gives you that swizz **only if you
  have none yet**. Otherwise you get: "You already have a swizz — use a Reroller."
- **D-02:** anyone can craft a swizz item (they can be traded). Config:
  `swizz-items.craft-requires-no-swizz: false`.
- **D-03:** players don't get a swizz on first join. Config: `assign-random-on-first-join: false`.
- **D-04:** a new player's stored energy starts at 2 (`energy.start`).

### 4.2 Reroller
- Crafting it **requires the crafter to have a swizz** (the result is hidden in
  `PrepareItemCraftEvent` otherwise; checked again in `CraftItemEvent`).
- Right-click opens a GUI with the 6 other swizz. Picking one consumes the reroller and
  switches the swizz. Closing the GUI consumes nothing.
- Energy is kept. **D-05:** the upgraded flag is kept too (`reroller.keeps-upgrade: true`).
- **D-06:** cooldowns are tracked per slot (`A1`, `A2`), not per swizz, and **carry over**
  a reroll. Otherwise a reroll would give you a fresh ability straight away.
- Passive state is cleared and the new swizz's passive is applied (§7).

### 4.3 Resolving the effective swizz (in priority order)
1. `disabledUntil > now` (hit by the Swizz Mace) → **no swizz**: no abilities, no passives.
2. The player has a **Swizz Sword** with 2 bound elements in their inventory → elements
   `[s1, s2]` (§13.1).
3. Dragon egg `override` is active → `[override.swizz]`.
4. Otherwise → `[swizz]` (or none).

---

## 5. Energy and loadout

### 5.1 Energy rules
| Event | Result |
|---|---|
| Range | `min 1`, `max 5` (config) |
| Killed by a player (`getKiller() != null`) | Victim −1 (never below 1). The killer gains +1 **only if the victim actually lost one** (**D-07**, stops alt farming at 1 energy). If the killer is already at 5, the energy **drops as an item** at the victim's location. |
| Any other death ("natural causes") | Victim −1 (never below 1), and that energy **drops as an item** at the death location. |
| Victim already at 1 | Nothing is lost or dropped, and the killer gains nothing. |
| `/withdraw <n>` | Requires `energy - n >= 1`. Gives `n` energy items; anything that doesn't fit drops at your feet. |
| Right-click an energy item | +1 if below max (uses 1 item). At max: "You are at max energy." |
| Reroll | Energy is unchanged. |

Kill credit uses Bukkit's `getKiller()`. That includes projectiles and players who hit
you in the last ~5 seconds before a fall or void death.

### 5.2 Unlock tiers (the slot model) — **D-08**
The spec says: energy 1 → ability 1; 2 → ability 1 cooldown −25%; 3 → ability 2;
4 → ability 2 cooldown −25%; 5 → passive. It also says you can switch which perk you
use, the cooldown bonus follows the swap, and putting the passive first wastes the
cooldown point.

That gives this model. There are 5 tiers in a fixed order, and the loadout decides
which perk sits in each:

| Energy ≥ | Tier | Effect |
|---|---|---|
| 1 | Slot 1 | unlocks `loadout[0]` |
| 2 | Slot 1 bonus | `loadout[0]` gets −25% cooldown **if it is an ability** (wasted on the passive) |
| 3 | Slot 2 | unlocks `loadout[1]` |
| 4 | Slot 2 bonus | `loadout[1]` gets −25% cooldown if it is an ability |
| 5 | Slot 3 | unlocks `loadout[2]` (no bonus) |

The default loadout `[A1, A2, P]` matches the spec exactly. Examples: 2 energy with
`[A2, A1, P]` → ability 2 with −25%. 2 energy with `[P, A1, A2]` → only the passive (one
point wasted, as the spec says).

- Commands: `/loadout` (GUI) and `/loadout <perk> <perk> <perk>`.
- **D-09:** the loadout can be changed once every **30 s** (`loadout.switch-cooldown-seconds`).
  Ability cooldowns are never reset by switching.

---

## 6. Abilities: shared rules

### 6.1 Activation
- `/ability1`, `/ability2` (and `/ability3`, `/ability4` for the second element of a
  Swizz Sword).
- **Key combos (D-10, per player via `/swizz controls`):** `Sneak + F` (swap hands) =
  ability 1, `Sneak + Q` (drop) = ability 2. The vanilla swap/drop is cancelled only when
  sneaking with controls on. Sneak + Q needs an item in hand: the client sends no drop
  action for an empty hand.
- Order of checks:
  1. Has an effective swizz.
  2. Not disabled.
  3. Not frozen or stunned.
  4. World not in `disabled-worlds`.
  5. Perk unlocked.
  6. Cooldown ready.
- Then the swizz implementation returns one of:
  - `SUCCESS`: start the cooldown now.
  - `ARMED`: "next hit" abilities; the cooldown starts when the hit lands. The arm lasts
    `arm-window` (30 s). If it runs out, there is no cooldown.
  - `FAIL(reason)`: no cooldown; tell the player why (e.g. "No block in range").

### 6.2 Targeting (`TargetFilter`)
- **Never affected:** the caster, players on the caster's trust list, players in
  creative/spectator, dead entities, armor stands, display entities, and pets owned by
  the caster or a trusted player.
- **PLAYERS mode:** untrusted players only. Used by: hotbar shuffle, disguise dome,
  flashbang, glowing, drowning, roots, freeze.
- **ENEMIES mode:** untrusted players plus hostile mobs (`org.bukkit.entity.Enemy`).
  Used by damage and knockback: sculk beam, earthquake, icicles, whirlpool, lightning.
- Trust is one-way (spec): `/trust X` stops **your** harmful abilities from hitting X.
- **Ability damage (D-29, D-30):** credited to the caster, so kills count. With
  `abilities.true-damage: true` (the default), armor and Protection don't reduce it, so
  "3 hearts" means 3 hearts; Resistance and absorption still do. Shields never block
  ability damage, except Frost's icicles, which check shields themselves.

### 6.3 Cooldown maths — **D-11**
```
startCooldown = base
              × (slotBonus  ? 0.75 : 1)   # energy tier bonus
              × (upgraded   ? 0.75 : 1)   # the upgrade's −25%
              → never below cooldown.min-seconds (10 s)
```
The countdown then runs every tick: `remaining -= 50ms × rate`. The rate is set by the
environment-based passives (only while that passive is unlocked):

| Passive | Condition | Rate |
|---|---|---|
| Earth | in a NORMAL (overworld) world | 1/0.75 = **1.333** ("25% shorter") |
| Aqua | the world is raining or thundering | **1.333** |
| Frost | in a cold biome | **1.25** ("go down 25% faster") |

If more than one applies (Swizz Sword), the **highest** rate wins. Cooldowns also run
while you are offline, at rate 1.

**Cold biome (D-12):** `block.getTemperature() < 0.15` at the player's feet. That is the
same check vanilla uses for snowfall, height included.

### 6.4 Combo tracker (Confusion and Gleam passives)
- Counts melee hits from an attacker on **one** victim. A hit only counts if the damage
  event wasn't cancelled (read at MONITOR).
- The combo resets if:
  - the victim damages the attacker (melee or projectile), **or**
  - the attacker hits a different entity, **or**
  - more than `combo.timeout` (2.5 s) passes between hits.
- A passive in its reload doesn't count hits towards a new trigger.

### 6.5 Status effects (shared engine)
- **Freeze:** transient attribute modifiers set `MOVEMENT_SPEED` ×0 and
  `JUMP_STRENGTH` to 0. Horizontal moves in `PlayerMoveEvent` are cancelled (looking
  around is allowed; falling is allowed so it can't be used to fly). Melee hits, block
  placing and escape items (ender pearls, chorus fruit, wind charges, rockets, tridents,
  bows, crossbows) are blocked. **Eating (golden apples), potions (drunk or thrown) and
  shields still work** (owner change). Freeze ticks stay at maximum for the frost screen
  overlay; freeze damage is cancelled. Removed on expiry, quit and death.
- **Ability lock:** frozen players can't use abilities.
- All attribute changes are **transient modifiers** with namespaced keys. They are never
  saved to player.dat, so removing the plugin leaves nothing behind.

### 6.6 Passive buffs (`PassiveService`, runs every 20 ticks and on any state change)
- Builds the set of buffs a player should have (effects and attribute modifiers) and
  applies the difference.
- Effects use `INFINITE_DURATION`, are ambient and hide particles. A buff is only
  removed if the current effect is infinite **and** has our amplifier, so a potion the
  player drank themselves is never removed.

---

## 7. The seven swizz (base + upgraded)

All numbers below are defaults in `abilities.yml`. HP = half-hearts (1 heart = 2 HP).
**D-13: upgraded passives always keep the old passive** (the spec says so for 4 of the
7; we do the same for all of them). Upgraded ability cooldowns are ×0.75 (§6.3).

### 7.1 CONFUSION — purple (DUST #8E44AD, WITCH, PORTAL)
**Passive.** A 4-hit combo on a player → that player's **next melee hit on you misses**
(cancelled; plays a "whiff" sound and particles). The miss lasts up to 5 s. Then a
10 s reload. *(D-14: 1 missed hit — `miss-count: 1`.)*
- *Upgraded:* also, the **5th** hit of the same combo gives the victim Levitation I for 3 s.

**Ability 1 — Scramble** (cd 240 s). Arms for 30 s. Your next melee hit on an untrusted
player shuffles their hotbar slots 0–8 (a random order that is guaranteed to change
something).
- *Upgraded:* shuffles again 30 s later (if they are still online).

**Ability 2 — Mirage Dome** (cd 120 s, lasts 10 s, radius 10 — **D-15** radius not given
in spec).
- There are no blocks. The edge is drawn with purple particles on a sphere.
- **Sealed:** everyone inside at the start is a participant and can't leave. Moving out
  is reverted, and ender pearl / chorus teleports out are cancelled. Outsiders can't get
  in (D-16: sealed both ways, `block-entry: true`).
- **Disguise:** every participant (the caster included) gets the caster's skin
  (`setPlayerProfile` with the caster's textures; name and UUID are unchanged). For
  every viewer **except the caster**, all participants are shown wearing the caster's
  armor and trims (`sendEquipmentChange`, re-sent every 2 ticks). Nametags are hidden
  through a scoreboard team. The caster sees each participant's real name floating above
  them (a TextDisplay only the caster can see).
- At the end: profiles, equipment, teams and labels are restored. The same clean-up
  runs if someone quits or the plugin is disabled.
- *Upgraded:* untrusted participants also get **reversed WASD**. Their movement speed is
  set to 0, and every tick we read `getCurrentInput()` and push them the opposite way
  (velocity). Jumping is unchanged.

### 7.2 SCULK — dark blue (DUST #0B3D5C, SCULK_SOUL, SCULK_CHARGE_POP, SONIC_BOOM)
**Passive.**
- **Swift Sneak III** without touching any item: a transient `SNEAKING_SPEED`
  modifier of +0.45, giving the same 0.75 total as the enchantment. *(D-17: we don't
  enchant real leggings, which would be a dupe/trade exploit.)*
- **Eat sculk:** right-click with a `SCULK` block in hand → use 1 and repair **each worn
  armor piece by 3 durability**. This only happens if at least one piece is damaged;
  otherwise the block places normally. 4-tick delay between uses.
- *Upgraded:* immune to wardens (they can't target you and their damage to you is
  cancelled) and to Darkness (the effect is blocked).

**Ability 1 — Sonic Beam** (cd 150 s).
- Plays the warden's sonic charge sound and shows charge particles for 1.7 s (34 ticks).
- Then it fires along **where you are looking at that moment**.
- Range 20, hit radius 0.5. Stops at solid blocks (D-18:
  `pass-through-blocks: false`).
- Hits every ENEMIES target on the line for **6 HP (3 hearts)**, using the `SONIC_BOOM`
  damage type, so armor doesn't reduce it (like the warden).
- *Upgraded:* hit radius 1.0 (twice as wide), **8 HP (4 hearts)**.

**Ability 2 — Sculk Dome** (cd 120 s, lasts 15 s, radius **10**; owner change from 15).
- A sphere shell of `SCULK` blocks. It **replaces whatever it passes through** (air,
  plants, liquids and solid terrain) and everything is put back afterwards. Containers
  and other tile entities, unbreakable blocks, beds, doors and pistons are skipped.
- **No escaping:** ender pearl and chorus teleports out of the dome are cancelled, and
  chorus fruit can't be eaten inside it.
- During the dome, nobody can break a block in the shell zone (distance ≥ r−1.5), so
  you can't tunnel out. Explosions, pistons, fire and endermen are blocked there too.
- Spots where a player is standing are skipped, then filled once they are clear
  (checked every second).
- All changes are recorded in `BlockRestorer` and restored at the end (and on crash
  recovery).
- *Upgraded:*
  - Radius **25** (owner change from 30).
  - **Darkness** for untrusted players inside.
  - **2 wardens** spawn (40 HP, max 6 damage per hit). They only target untrusted
    players. They drop nothing and are removed at the end.
  - The **ground inside turns to sculk.** For each column, the top solid block inside
    the dome is replaced with SCULK, and the plant/snow above it is cleared. Vines
    become SCULK_VEIN with the same faces.
  - Containers, tile entities, bedrock and multi-block structures are skipped.
  - Converted blocks can't be broken (no free sculk XP) and are restored afterwards.

### 7.3 FORTUNE — green + yellow (HAPPY_VILLAGER, DUST #F1C40F, DUST #2ECC71)
**Passive.** Hero of the Village V (amplifier 4) plus **×3 loot**:
- **Mobs** you kill: the drops list ×3.
  - Excluded mobs: `PLAYER`, `WITHER`, `ENDER_DRAGON`.
  - Excluded items: `NETHER_STAR`, `DRAGON_EGG`, `ELYTRA`, `TOTEM_OF_UNDYING`,
    `HEAVY_CORE`, and all custom items.
  - XP is not multiplied.
- **Ores** you break (`BlockDropItemEvent`): drop amounts ×3.
  - Skipped when the drop is the block itself (Silk Touch). **D-19:** this stops ore dupes.
  - The ore list is in config; all vanilla ores are included. **`ANCIENT_DEBRIS` is off
    by default** for balance.
- *Upgraded:* when you would die, a **10% chance** of a totem pop that **keeps your effects**:
  - We un-cancel `EntityResurrectEvent` when you have no real totem.
  - Your effects are saved, and put back next tick on top of the totem's buffs.
  - Not for `VOID` or `KILL` deaths.

**Ability 1 — Golden Circle** (cd 150 s, lasts 30 s, radius 6, follows you).
- For you and trusted players inside, golden apples give +1 Absorption level (2 extra
  hearts) and +4.8 extra saturation.
- Untrusted players inside get Hunger I (refreshed while they're inside).
- *Upgraded:* you and trusted players inside repair 1 durability per armor piece per second.

**Ability 2 — Fortune Roll** (cd 180 s). One weighted roll:

| Outcome | Weight | Duration |
|---|---|---|
| Regeneration I | 25 | 15 s |
| Regeneration II | 5 | 15 s |
| Resistance I | 25 | 15 s |
| Resistance II | 5 | 15 s |
| +5 max hearts (+10 HP, healed on apply) | 25 | 15 s |
| Strength II (rare) | 5 | 10 s |
| **Tiny size** (scale 0.5, harder to hit) | 15 | 15 s |

- *Upgraded:* the same result is also given to the **2 nearest trusted players within 25 blocks**.

### 7.4 GLEAM — yellow + white (DUST #FFF59D, END_ROD, WAX_OFF, FIREWORK)
**Passive.** Permanent Haste I.
- **3-hit combo:** every extra hit in the same combo (4th, 5th, …) calls lightning on
  the victim and deals ×1.15 damage. The lightning is visual only
  (`strikeLightningEffect`) plus 2 HP, so it causes no fire, no mob conversion, and no
  self-damage.
- This lasts until the victim hits you or the combo breaks, then a 10 s reload.
- *Upgraded:* every 10 ticks you see red heart particles (only you) above untrusted
  players within 20 blocks who are **under 5 hearts** (10 HP).

**Ability 1 — Radiance** (cd 150 s). Untrusted players within **30** blocks get Glowing
for **15 s**, and you get **Haste V** for 15 s.
- *Upgraded:* radius **50**, both last **30 s**.

**Ability 2 — Flashbang** (cd 180 s, lasts 15 s, radius 10). Hits untrusted players.
- No potion effects are used.
- Their screen goes **white**: we send them a fake head item whose `equippable`
  component has `camera_overlay = minecraft:block/snow`, and only they see it.
- Cycle: **4 s white, then 1 s clear**, repeating (D-20). This matches "every 5 seconds
  you see normally just for it to turn back".
- At the end their real helmet is re-sent.
- Fallback (config): a big white title.
- *Upgraded:* also a lightning strike on the **nearest** untrusted target in range for
  **5 HP (2.5 hearts)**.

### 7.5 FROST — light blue + white (SNOWFLAKE, DUST #AEE6FF, ITEM_SNOWBALL)
**Passive.**
- Cooldowns run 1.25× faster in cold biomes.
- **Speed I in cold biomes.**
- **Can't sink in powder snow** unless sneaking. Powder snow within 2 blocks under the
  player's feet is shown **to that player only** as solid snow blocks (fake block
  changes, refreshed every 2 ticks and re-sent every second), so their own client lets
  them walk on it smoothly. Sneaking shows the real powder snow so they sink through on
  purpose. If they are already inside powder snow they are lifted to the top once.
- *Upgraded:* **+3 hearts** (+6 HP max) in cold biomes.

**Ability 1 — Icicles** (cd 180 s).
- Starts a dagger session: **3 daggers**, at least **1.5 s** apart, and the session
  times out after 15 s.
- Throw each with **right-click** or by running `/ability1` again.
- Each dagger is a no-gravity snowball shown as ICE, speed 2.0, range about 40.
- A hit deals **3 HP (1.5 hearts)**.
- Changing your held slot, swapping hands or dropping the item **ends the session**.
- A blocking shield in front of the target stops the dagger.
- *Upgraded:* hits also **stun-freeze** for 1 s and **disable shields** for 5 s (the
  hit goes through).

**Ability 2 — Deep Freeze** (cd 210 s). Arms for 30 s. The next player you melee is
**frozen for 6 s** (§6.5).
- *Upgraded:* freezes every untrusted player within **5 blocks straight away**; no arming.

### 7.6 EARTH — brown (DUST #7B4A2A, BLOCK_CRUMBLE of the ground block, FALLING_DUST dirt)
**Passive.**
- Cooldowns run 1.333× faster (25% shorter) in the overworld.
- **12 hearts in the overworld** (+4 HP max) and **30% knockback resistance** there
  (owner buff).
- *Upgraded:* **13 hearts everywhere** (+6 HP, replacing the +4), **50% knockback
  resistance everywhere** and permanent **Weaving**.

**Ability 1 — Fault Line** (cd **120 s**, buffed from 150).
- A quake **18** blocks long and 3 wide in your horizontal look direction. It moves
  forward 1 block per tick with rising ground particles and a rumble.
- Each ENEMIES target in it is hit once: **6 HP (3 hearts)**, pushed along the line
  (horizontal 1.4, up 0.5) and given **Slowness II for 3 s**.
- *Upgraded:* **8 HP (4 hearts)**, and cobwebs in the path (3 wide, 3 tall) are broken.

**Ability 2 — Root Grab** (cd **100 s**, buffed from 120). The roots deal **3 HP (1.5
hearts)** when they grab, and after the pull the victims stay **rooted next to you for
1.5 s**.
- Untrusted players within **30** blocks **that the caster can see** (line of sight;
  roots don't reach through walls) are held by roots for 0.5 s (root particles, can't move).
- Then they are **pulled to you**: teleported to safe spots in a ring 1.5–2.5 blocks
  around you, facing you.
- *Upgraded:* **Root Field**, radius **50**.
  - Every untrusted player in range that you can see is dragged towards the centre
    (your position when you cast).
  - Speed = radius ÷ 6 s, so about 6 s from the edge, as the spec says.
  - Their own movement is disabled while dragged; only velocity pulls them, so a wall
    in the way stops the drag (never pulled through blocks).
  - They take **2 HP** of `THORNS` damage per second until they reach the middle (within
    2 blocks) or 8 s pass.
  - A ring of particles marks the edge.

### 7.7 AQUA — blue + dark blue (DUST #3498DB, DUST #1A3C8C, BUBBLE_POP, SPLASH, DRIPPING_WATER)
**Passive.**
- Permanent Water Breathing and Dolphin's Grace.
- **Tridents break shields:** a melee trident hit on a blocking player disables their
  shield for 5 s, like an axe.
- **Trident melee = diamond sword:** while holding a trident, attack speed 1.6 and attack
  damage 7 (vanilla trident: 1.1 and 9), via transient attribute modifiers
  (`trident-attack-speed`, `trident-attack-damage`). Thrown tridents are unchanged.
- Cooldowns run 1.333× faster while it rains or thunders in your world.
- *Upgraded:*
  - **Immune to water mobs** (drowned, guardian, elder guardian, pufferfish): they can't
    target you, their damage is cancelled, and you can't get Mining Fatigue from elder
    guardians.
  - **Water bottle = enhanced golden apple:** Absorption II (4 hearts, "2 more than a
    gap") plus Regeneration II for 5 s.
  - The emptied bottle turns back into a water bottle after 7 s. There is a 7 s internal
    cooldown, so you can't drink a whole stack of bottles.

**Ability 1 — Drown** (cd 150 s).
- Untrusted players within **8** blocks start drowning for **8 s**: 1 HP (half a heart)
  every 20 ticks, using `DROWN` damage.
- Their air bubbles empty and bubble particles show at their head.
- Water Breathing does **not** protect (D-21).
- *Upgraded:* damage every **10 ticks** (twice the DPS), radius **12**.

**Ability 2 — Whirlpool** (cd 120 s, lasts 10 s).
- Aim at a block within **50** blocks (ray trace). A swirl of radius **5** forms there.
- Every ENEMIES target inside is pulled towards the centre (0.35 blocks per tick) with a
  sideways swirl.
- Cobwebs don't stop it: anyone in a web is moved by small teleports instead of velocity.
- *Upgraded:* radius **10**, lasts **20 s**.

---

## 8. Upgrader and ritual

- **Start:** right-click a block with an upgrader. A confirm step (D-22) follows: "Right-click
  again within 5 s to start. Your coordinates will be broadcast." The ritual's anchor is
  the top face of the clicked block.
- **Requirements:**
  - You have a swizz and you aren't upgraded.
  - You have no ritual of your own running.
  - The anchor is within **1,500 blocks of that world's spawn** on x and z, in every
    dimension (**D-23**, owner change: was ±750 around 0,0; config
    `ritual.max-distance-from-spawn: 1500`).
- **On start:** the upgrader is consumed into the ritual. A broadcast follows (the world
  name is written as Overworld / Nether / End):
  `<name> has started a ritual for an Upgrader at x: §k####§r y: 64 z: §k####§r in the Overworld`
- **Leak schedule (20 min total):**
  - t=0: Y.
  - t=5 min: X.
  - t=10 min: Z.
  - t=15 min: a "5 minutes left" broadcast with the full coordinates.
  - t=20 min: the outcome.
  - D-24: X before Z (`ritual.leak-order: [X, Z]`).
- **Visuals:** a particle circle of radius 5 around the anchor, every second (enchant +
  end rod). It is the only marker; there is no beacon beam.
- **At 20 min:**
  - If the owner is online, in the same world and within **5 blocks** (3D) of the anchor
    → `upgraded = true`, a success broadcast, sound and particles.
  - **Otherwise** the upgrader item **drops at the anchor**, and a failure broadcast follows.
- **Persistence:** saved to `rituals.yml`. Server downtime **pauses** the ritual (it
  stores elapsed seconds, not a clock time).
- **Not stashable** (spec). An upgrader can only be in a player inventory, on the
  ground, or inside a ritual. Blocked:
  - **Containers:** moving it into any non-player inventory, by click, shift-click,
    hotbar-number swap, drag, or collect-to-cursor into the top inventory.
  - **Hoppers and mobs:** hopper/minecart pickup (`InventoryPickupItemEvent`),
    `InventoryMoveItemEvent`, and pickup by any non-player entity (foxes, allays,
    piglins).
  - **Other holders:** bundles (either direction), item frames, armor stands, allays,
    decorated pots and shelves (any block right-click with it starts the ritual flow
    instead, so vanilla never runs).
  - **The ground:** dropped upgraders never despawn and are invulnerable to
    fire/lava/cactus/explosions.

---

## 9. Items, recipes and the item guard

### 9.1 Item looks (D-25, D-31)
- Every "token" item uses a **neutral base material** (`ECHO_SHARD` by default) with an
  **`item_model`** that points at a vanilla model (the look players get *without* the
  resource pack).
- That way they never trigger vanilla behaviour (eating, placing, throwing, beacons).
- **D-31 — custom textures with graceful fallback.** Every custom item also carries
  `custom_model_data` strings `[<item id>]`. The resource pack (`resourcepack/src`)
  overrides the vanilla item definitions of the fallback models
  (`assets/minecraft/items/<vanilla>.json`) with a `minecraft:select` on
  `custom_model_data`: our id → `swizz:item/<id>` (32×32 texture), anything else → the
  vanilla model, so real hearts of the sea etc. are unchanged and players without the
  pack see the fallback. `ResourcePackService` downloads the zip at startup to compute
  its SHA-1 and sends it to joining players (`config.yml → resource-pack`; not required
  by default). The pack is hosted as a raw file of the public GitHub repo. Earth has no
  custom texture yet (owner provided none).

| Item | Default model | Name color |
|---|---|---|
| Aqua swizz | `minecraft:heart_of_the_sea` | blue |
| Confusion swizz | `minecraft:amethyst_shard` | purple |
| Earth swizz | `minecraft:armadillo_scute` | brown |
| Fortune swizz | `minecraft:emerald` | green |
| Frost swizz | `minecraft:snowball` | aqua |
| Gleam swizz | `minecraft:glowstone_dust` | yellow |
| Sculk swizz | `minecraft:echo_shard` | dark aqua |
| Energy | `minecraft:nether_star` | light purple |
| Reroller | `minecraft:recovery_compass` | gold |
| Upgrader | `minecraft:beacon` (max stack size 1) | red |
| Swizz Sword | real `NETHERITE_SWORD`, unbreakable | |
| Swizz Mace | real `MACE`, unbreakable | |

All of them have the enchantment glint and lore explaining their use.

### 9.2 Recipes (owner's recipes from the Season 5 screenshots; all in `items.yml` → `recipes`)
Rows are top → bottom; `corners / edges-vertical / edges-horizontal / centre`.

| Result | Corners | Top & bottom middle | Left & right middle | Centre |
|---|---|---|---|---|
| Aqua | POTION of Water Breathing 8:00 (`POTION:LONG_WATER_BREATHING`) | PRISMARINE_SHARD | TURTLE_HELMET | HEART_OF_THE_SEA |
| Confusion | END_CRYSTAL | FLOW_ARMOR_TRIM_SMITHING_TEMPLATE | GOAT_HORN (any) | NETHER_STAR |
| Earth (placeholder, no screenshot) | DIAMOND | ROOTED_DIRT | ROOTED_DIRT | DIAMOND_BLOCK |
| Fortune | TOTEM_OF_UNDYING | ENDER_EYE | GOLDEN_APPLE | RABBIT_FOOT |
| Frost | TIDE_ARMOR_TRIM_SMITHING_TEMPLATE | VERDANT_FROGLIGHT | VERDANT_FROGLIGHT | BEACON |
| Gleam | SEA_LANTERN | OXIDIZED_LIGHTNING_ROD (unwaxed) | TRIDENT | NETHER_STAR |
| Sculk | SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE | ECHO_SHARD | CALIBRATED_SCULK_SENSOR | RECOVERY_COMPASS |
| Reroller | DIAMOND | AMETHYST_SHARD | AMETHYST_SHARD | TOTEM_OF_UNDYING |
| Upgrader | NETHERITE_SCRAP | DIAMOND_BLOCK | DIAMOND_BLOCK | NETHER_STAR |

Ingredient syntax: `MATERIAL`, `MATERIAL,MATERIAL` (any of), or `POTION:TYPE` (a
`PredicateChoice` matching the potion type, so renamed potions still work).
`/swizzadmin craftcheck <item>` verifies a recipe crafts from its own ingredients.
Config migration v4 replaces the 1.0 placeholder recipes on existing servers unless they
were edited.

Energy is never crafted; it only comes from kills and `/withdraw`.

### 9.3 Item guard (one listener for all custom items)
Custom items may **not**:
- be used in any recipe except ours: crafting table, crafter, smithing, furnace/smoker/
  blast furnace, brewing stand, stonecutter, loom or villager trades (the trade is
  cancelled);
- be placed, eaten or thrown (vanilla interact is cancelled; our own handler runs instead);
- be given to allays or put in item frames (upgraders only — other custom items may go in
  frames).

---

## 10. Commands and permissions

| Command | Permission | Notes |
|---|---|---|
| `/ability1`, `/ability2` | `swizz.use` (default: everyone) | `/ability3`, `/ability4` for the second element of a Swizz Sword |
| `/trust <player>` | `swizz.use` | toggles trust; max 20 |
| `/trust list` | `swizz.use` | |
| `/withdraw <amount>` | `swizz.use` | |
| `/swizzbook` | `swizz.use` | GUI: recipes, lore for every swizz, reroller and upgrader |
| `/loadout [p1 p2 p3]` | `swizz.use` | |
| `/swizz [info\|hud\|controls]` | `swizz.use` | your status; turn the HUD or key combos on/off |
| `/energy set\|add\|remove\|view <player> [n]` | `swizz.admin` (default: op) | clamped to [min, max] |
| `/giveenergy <player> [n]` | `swizz.admin` | energy item(s) |
| `/givereroller <player> [n]` | `swizz.admin` | |
| `/giveupgrader <player> [n]` | `swizz.admin` | |
| `/giveswizz <swizz> <player>` | `swizz.admin` | **sets** the swizz straight away (D-27). Use `/swizzadmin give` for the item. |
| `/resetcooldown <player>` | `swizz.admin` | ability cooldowns, passive reloads, loadout-switch cooldown |
| `/swizzadmin reload` | `swizz.admin` | reloads config, abilities, messages, recipes |
| `/swizzadmin give <itemId> <player> [n]` | `swizz.admin` | any custom item, including the sword and mace |
| `/swizzadmin upgrade <player> <true\|false>` | `swizz.admin` | |
| `/swizzadmin info <player>` | `swizz.admin` | full data dump |
| `/swizzadmin ritual list\|start <player>\|cancel <player>` | `swizz.admin` | `start` begins a ritual where the player stands (no item) |
| `/swizzadmin dragonegg reset` | `swizz.admin` | clears the server-wide dragon egg cooldown |
| `/swizzadmin recipe <item>` | `swizz.admin` | in-game recipe editor; saves to items.yml and reloads |
| `/giveupgraded <player>` | `swizz.admin` | upgrades the player's current swizz without a ritual |
| `/trail [on\|off]` | `swizz.use` | turns the cherry blossom trail off and on; only for blinkypies (§13.5) |
| `/jumpscare [player\|all]` | `swizz.admin` | horror picture + scream (§13.6). No argument: everyone except you; `all`: you too |
| `/cinematic <aura\|matrix\|singularity\|timestop> [player]` | `swizz.admin` | a showcase cinematic around a player (§13.7); no player: yourself |
| `/aura [player]` | `swizz.admin` | shortcut for `/cinematic aura` |

---

## 11. HUD and feedback

- **Action bar** every 10 ticks (when on). Default `hud.mode: cooldowns` (owner request)
  shows only `Ability 1 » 1:42   Ability 2 » ✔` for unlocked abilities, plus a passive
  reload while it counts down. `hud.mode: full` shows the line below:
  `◆ Frost ●●●○○  A1 ✔  A2 1:42  P ⟳8s`. Shows locked perks as `✖`, and `UPG` when upgraded.
- **Sounds:** a unique sound for each ability, a "not ready" click, and an ability-ready ding.
- **Messages** use a MiniMessage prefix (`messages.yml`).

---

## 12. /swizzbook (GUI)

- **Main page:** the 7 swizz items, the reroller, the upgrader and an "Energy & Rules" book.
- **Click a swizz** → recipe view (3×3 grid + result) plus a "Read" button that opens a
  written book with its passive, abilities and upgraded versions. The numbers are pulled
  live from config.

---

## 13. Special items

### 13.1 Swizz Sword
- Admin-given. On first right-click (while it has no elements bound) a GUI asks you to
  pick **2 elements**; they are stored on the item.
- While it is anywhere in your inventory (checked every second and on inventory events):
  - Your effective swizz = those 2 elements.
  - `/ability1`, `/ability2` = element 1. `/ability3`, `/ability4` = element 2
    (cooldown keys `B1`, `B2`).
  - Energy, loadout and upgrade apply to both. Both passives apply if unlocked (§6.3
    for rates).
- Lose it (dropped, stored, killed) → you go back to your own swizz straight away.
- Unbreakable.

### 13.2 Dragon Egg (the vanilla `DRAGON_EGG` item)
- **Right-click air** while holding it.
- Every online player **except the user** (D-28) gets a random swizz for **5 min**
  (`override` with an expiry time).
- **30 min cooldown for the whole server** (`server-state.yml`).
- Logging out and back in is handled through the expiry time.

**Locator bar tracker (owner request):** **Sneak + right-click air** with the egg. For
**30 minutes** only that player sees the locator bar (every player shown on the XP
bar); everyone else's locator bar is hidden all the time (waypoint receive range set
to 0 with a transient modifier). **12 hour** server-wide cooldown. User, end time and
cooldown are kept in `server-state.yml`. Config: `dragon-egg.locator`.

### 13.3 Tab-list colours
Name colour in the tab list by carried item (priority order): dragon egg **purple**,
Swizz Sword **yellow**, Swizz Mace **dark red**, upgrader **white**, nothing **gray**.
Checked every second. Colours in `config.yml → tab-colors`.

### 13.4 Swizz Mace
- A real mace. A melee hit on a player, when ready, **disables their swizz for 60 s**:
  no abilities and no passives (buffs are removed). One target per hit.
- **180 s cooldown** for the attacker.

### 13.5 Cherry blossom trail (owner request, cosmetic)
- Pink petals drift down behind the player's feet while they move: Minecraft's
  `CHERRY_LEAVES`, `TINTED_LEAVES` in a random pink from `petal-trail.colors`, and pink
  `DUST_COLOR_TRANSITION` glitter fading to white. Sprinting or gliding drops ×1.5.
  Petals are scattered along the path since the last drop (every 2 ticks), so fast moves
  don't leave gaps. Teleports and respawns don't draw a line.
- A puff of petals on every jump and on landing from a fall of 2+ blocks.
- **D-33:** only the player named **blinkypies** has it (owner request; hardcoded as
  `PetalTrail.OWNER`, matched by name, case-insensitive). It is on by default and
  `/trail` turns it off and on. Anyone else gets "one-of-a-kind cosmetic".
- **D-32:** the trail is hidden whenever it could give the player away: sneaking
  (`hide-while-sneaking`), invisible, spectator, dead, vanished (`vanished` metadata) or
  inside a Mirage dome. Particles are sent per viewer, only to players within 32 blocks
  who can see the player.
- Particles only, so nothing needs cleaning up. All numbers in `config.yml → petal-trail`.

### 13.6 Jumpscare (owner request, admin command)
- `/jumpscare` shows a horror picture in the middle of the screen for **2.5 s**
  (`duration-ticks: 50`) with a huge scream and three camera jolts (`sendHurtAnimation`).
- **Picture:** the owner's nun photo, centred on a black 16:9 canvas with feathered
  edges: `resourcepack/src/assets/swizz/textures/misc/jumpscare.png` (`blur` mcmeta for
  smooth scaling). It is a camera overlay on a fake helmet sent only to the victim, reusing
  Flashbang's code, so it has the same cleanup (expiry, quit, death, disable) and the same
  risk R1. Only drawn in first person.
- **D-34:** players without the texture pack loaded (tracked from resource pack status
  events) would see a missing texture, so they get the elder guardian ghost instead.
- **Sound:** a list of vanilla sounds in `jumpscare.sounds`, each played `repeat` (3)
  times at once on the MASTER channel; stacking copies is what makes it louder, since one
  sound can't go above full volume.

### 13.7 Cinematics (owner request, admin command)
`/cinematic <aura|matrix|singularity|timestop> [player]` (and `/aura` as a shortcut) plays a
showcase around a player. `effects/cinematic/`: `Cinematic` is the base class (tick clock,
spawned-entity tracking, cleanup, helpers for overlays, flashes, jolts, sounds, hovering and
display animation); `Cinematics` starts them by name, one at a time per player, and ends them
on quit and death. Shared rules:

- **Who sees what:** players within `cinematics.screen-radius` (30) get the screen effects,
  sounds and titles. Pack pictures (overlays, flat planes) need the pack; plane displays are
  only shown to players whose client loaded it, and overlays fall back to a white title or
  Blindness. Everything else works without the pack.
- **Nothing real changes:** world changes are fake blocks sent with `sendBlockChanges` to the
  viewers only (never blocks with a block entity, never under someone's feet when the
  collision would change) and are re-sent from the real world when the show ends.
- **Cleanup:** every spawned entity is non-persistent, tagged `swizz:spawned`, and removed when
  the show ends, the player quits, dies or changes world, and on disable. Modifiers
  (`swizz:cinematic_hover`, `swizz:timestop_*`) are transient; AI, gravity, velocities, the
  player time and fake blocks are restored in `cleanup()`. Early ends also clear overlays,
  titles and our own (ambient, particle-less) potion effects.
- Overlays that replace an overlay swap without a gap (Flashbang), so they can be animated.
- **D-35:** no core shaders. They can't be tested without a real client, they change a lot
  between versions, and a broken shader would break the whole pack (item textures, jumpscare)
  for everyone. Display entities, overlays and particles get the same look safely.

#### aura (14 s)
| Tick | What happens |
|---|---|
| 0 | Darkness on nearby screens, heartbeat starts, the player rises (Levitation II, 1.5 s) |
| 10–50 | A 16-block glowing sigil (7-point star, one node per swizz) unfolds on the floor; a cyan inner ring spins the other way |
| 30 | Hover: transient `GRAVITY` ×0 modifier (`swizz:cinematic_hover`) and velocity 0. Zero gravity also stops the vanilla "flying" kick |
| 50–106 | The 7 swizz items rise one by one with a rising pentatonic chime, glowing in their colour |
| 50–180 | They orbit faster and tighter; from 95 they stream `TRAIL` particles of their colour into the player; portal and conduit build-up sounds |
| 180 | Convergence: white flash (overlay, or a white title without the pack), lightning (visual only), sonic boom, totem, A-major chord, 3 camera jolts |
| 182–220 | 42 element blocks (one per swizz, 6 each) fly out spinning and shrinking; a rainbow ring races along the floor |
| 187 | Rainbow energy border overlay (pack) and Nausea on nearby screens; rainbow name title at 189 |
| 200–280 | A small spinning halo above the player's head |
| 225–260 | The sigils shrink away; at 232 gravity returns, with Slow Falling for a soft landing |

#### matrix (18.5 s): "escape the Matrix"
| Tick | What happens |
|---|---|
| 0–135 | Screens become a dim green CRT (`misc/crt`); "Wake up, *name*…", "The Matrix has you…", "Follow the white rabbit.", "Knock, knock, *name*." type out letter by letter in the subtitle with key clicks |
| 80, 104 | Déjà vu: the same black cat (no AI) walks the same path in front of the player twice; a glitch frame on the second pass |
| 135 | Hard glitch burst (`misc/glitch_0..2`), jolts, sonic boom |
| 138–172 | The construct: every visible block within 16 turns black with a lime 4-block grid on its visible faces, walk-through blocks vanish (fake blocks), spreading out as a ring |
| 140–262 | Digital rain: the 8-frame `misc/rain_*` overlay loops; falling katakana text-display columns and green `TRAIL` streaks in the world |
| 175–199 | Agents: mannequins with the player's skin in black suits, doubling to 16, staring and twitching |
| 210–252 | Bullet time: 12 gold bullets fly from the agents in slow motion with air ripples, stop 1.5 blocks short, "You are the One.", then drop and clink |
| 262–282 | Escape: agents dissolve, the code rises, liquid chrome (`misc/chrome_0..2`) swallows the screen, portal-travel sound, glitches, white flash |
| 282–342 | Silent black screen (`misc/black`, Blindness without the pack); the construct is restored; "You have escaped the Matrix." and "Welcome to the real world." type out with a heartbeat and a breath |
| 342 | Waking up: white flash, breath, the world is back |

#### singularity (12.5 s)
A black hole 7.5 blocks above the player: a billboard black disc (`bh_core`), a photon ring
(`bh_ring`) and two tilted spinning accretion disks (`bh_disk`). The player rises and hovers,
screens get a purple void border (`misc/void`) and a night sky (`setPlayerTime`, reset after).
From 45 to 165 surface blocks within 3.5–14 are hidden with fake air while copies of them
lift off and spiral in; matter streams in as `TRAIL` particles. At 165 it collapses, at 174
all sound stops, at 180 it detonates (flash, lightning, explosions, an expanding shell, 40
blocks thrown out) and the ground rebuilds ring by ring from the centre.

#### timestop (11.5 s)
A golden clock (`clock_face`, `clock_hour`, `clock_minute`, camera-facing) floats over the
player; its hands race, then stop. A sphere sweeps out to 24 blocks: players are frozen with
transient ×0 speed, jump and gravity modifiers (they can look around, even hang mid-jump),
mobs lose their AI, and items, arrows and other moving things keep their velocity and gravity
on hold. The world goes silent and blue (`misc/timestop`). The caster moves freely, leaves
mannequin afterimages and can throw things that stop after 2 blocks. At 118 the hands spin
back; at 140 the sphere collapses and everything carries on exactly where it stopped.

---

---

**Config migration:** `config.yml` has `config-version`. On start (and on reload), older
server copies are updated by `ConfigMigrations`: a value is only changed if it still has
the old default, so admins' own edits are kept. Any option missing from the server's
`config.yml`, `abilities.yml` or `messages.yml` is copied in from the bundled defaults
(never `items.yml`, so removed recipes stay removed).

## 14. Code architecture (`com.swizzsmp`)

```
SwizzPlugin                    main class; wires services; onEnable/onDisable order
config/   PluginConfig, AbilityConfig (typed view of abilities.yml), Messages
data/     PlayerData, PlayerDataStore (yaml io, async save), ServerState
energy/   EnergyService, EnergyListener (death/kill), EnergyItemListener
ability/  Perk, Loadout, CooldownService (tick task), AbilityService (activation),
          AbilityResult, AbilityContext, ControlsListener (sneak+F / sneak+Q)
swizz/    SwizzType (enum: id, display, colors, particles), SwizzRegistry,
          SwizzHandler (interface: ability1/2, passive hooks, cleanup)
swizz/impl/  Aqua..Sculk handlers (one class each + helper classes where large)
combat/   TargetFilter, ComboTracker, FreezeService, StatusListener
effects/  Particles (line, circle, sphere, spiral), BlockRestorer (persisted),
          DomeBuilder, Flashbang, Disguise, ControlInverter, LowHealthEffect, PetalTrail, Jumpscare
effects/cinematic/  Cinematic (base), Cinematics (registry), Aura/Matrix/Singularity/TimeStopCinematic
passive/  PassiveService (effects + attribute modifier reconciliation)
items/    ItemIds, CustomItems (factory + identify), RecipeService, ItemGuardListener,
          UpgraderGuardListener, SwizzItemListener (absorb on right-click)
ritual/   Ritual, RitualService (tick, leak, persist), RitualListener
gui/      Menu (InventoryHolder base), RerollMenu, LoadoutMenu, BookMenu, SwordBindMenu
special/  SwizzSwordService, DragonEggService, SwizzMaceListener
hud/      ActionBarHud
command/  one class per command family (Paper BasicCommand)
util/     Text (MiniMessage), Time, Locs, Attrs (transient modifiers), Keys
```

**Order of `onEnable`:**
1. Save the default configs, then load them.
2. Data store.
3. `BlockRestorer.recoverPending()`.
4. Services.
5. Recipes.
6. Listeners.
7. Commands.
8. Tick tasks.
9. Load online players (for `/reload`).
10. Resume rituals.

**`onDisable`:**
1. End all active ability effects (restore blocks, profiles, equipment, teams, remove
   wardens, displays and modifiers).
2. Save rituals.
3. Save all players (sync).

**Every temporary world change** (blocks, entities, profiles, teams, modifiers) has a
cleanup path that runs on **expiry, quit, death, world change and disable**.

---

## 15. Build phases and checklists

### Phase 0 — Tooling
- [x] Portable JDK 25 + Gradle in `.tools/`, Gradle wrapper, `build.gradle.kts`, `plugin.yml`
- [x] Local Paper 26.3 test server in `.tools/server` — boot test script
- [x] `.gitignore`, `CLAUDE.md`
- [x] Bot integration tests (`.tools/bot/itest.js`, mineflayer through ViaBackwards)

### Phase 1 — Core
- [x] Config files (config.yml, abilities.yml, items.yml, messages.yml, guide.yml) + typed loaders
- [x] PlayerData + store (load/save/autosave/offline edit)
- [x] SwizzType, Perk, Loadout (tier maths + unit tests)
- [x] CooldownService (rates, persistence, offline subtraction)
- [x] EnergyService + death/kill rules + energy item + /withdraw
- [x] Trust + TargetFilter
- [x] CustomItems + RecipeService + ItemGuard + swizz item absorb
- [x] AbilityService + /ability1-4 + key combos
- [x] PassiveService
- [x] HUD
- [x] All commands from §10 (admin + player)
- [x] Boot test on the server: no errors, commands registered, configs generated

### Phase 2 — Shared engines
- [x] Particles helpers
- [x] ComboTracker
- [x] FreezeService
- [x] BlockRestorer + Domes geometry
- [x] Flashbang
- [x] Disguise
- [x] ControlInverter

### Phase 3 — Swizz (base), each one complete with passive
- [x] Earth
- [x] Frost
- [x] Aqua
- [x] Fortune
- [x] Gleam
- [x] Sculk
- [x] Confusion

### Phase 4 — Upgrades
- [x] Ritual system (start, leaks, finish, drop, persistence, anti-stash)
- [x] Upgraded variants for all 7 swizz

### Phase 5 — GUIs
- [x] Reroller menu
- [x] Loadout menu
- [x] Swizzbook

### Phase 6 — Special items
- [x] Swizz Sword
- [x] Dragon Egg
- [x] Swizz Mace
- [x] Cherry blossom trail (§13.5)
- [x] Jumpscare (§13.6)
- [x] Cinematics: aura, matrix, singularity, timestop (§13.7)

### Phase 7 — Hardening
- [x] Audit every cleanup path (quit, death, world change, disable)
- [x] Dupe audit (items, blocks, loot)
- [x] Full boot test + bot integration suite (40/40)
- [x] `docs/TESTING.md`: an in-game test checklist for the owner
- [x] `README.md` for admins

---

## 16. Risks to check in-game (things we can't prove without a real client)

| # | Feature | Risk | Fallback |
|---|---|---|---|
| R1 | Flashbang `camera_overlay` sent to yourself | The client might not apply a self equipment packet to its own helmet slot | `flashbang.mode: TITLE` (white block-glyph title) |
| R2 | Disguise via `setPlayerProfile` | A brief re-spawn flicker for viewers; clashes with other nametag plugins (e.g. TAB) | Turn off nametag hiding in config |
| R3 | Reversed WASD | Jitter from the server pushing the player | Tune `invert.strength`; can be turned off |
| R4 | Powder-snow walking lift | Rubber-banding on big powder snow fields | Tune the lift |
| R5 | `EntityResurrectEvent` un-cancel without a totem | Paper's behaviour may differ | Fall back to a damage-event-based save |

---

## 17. Decision log (for the owner to review)

D-01 Aqua id/name · D-02 swizz items craftable by anyone · D-03 no auto-assign ·
D-04 start energy 2 · D-05 reroll keeps upgrade · D-06 cooldowns per slot, kept on reroll ·
D-07 kill gain only if victim lost energy · D-08 slot/tier loadout model ·
D-09 30 s loadout switch cooldown · D-10 Sneak+F / Sneak+Q key combos ·
D-11 multiplicative cooldowns + 10 s floor, environment as tick-rate ·
D-12 cold = temperature < 0.15 · D-13 upgraded passives keep old passive ·
D-14 Confusion miss = 1 hit · D-15 Confusion dome radius 10 · D-16 dome sealed both ways ·
D-17 Swift Sneak via attribute, not enchant · D-18 sculk beam blocked by walls ·
D-19 no triple on silk-touch drops; no ancient debris · D-20 flash 4 s on / 1 s off ·
D-21 drown ignores water breathing · D-22 ritual confirm click · D-23 ritual within 1500 of spawn ·
D-24 leak X then Z · D-25 neutral base item + item_model · D-26 placeholder recipes ·
D-27 /giveswizz sets the swizz directly · D-28 dragon egg excludes the user ·
D-29 ability damage ignores armor/Protection (`abilities.true-damage`) · D-30 shields don't block ability damage (icicles excepted) ·
D-31 custom textures via custom_model_data select with vanilla fallback; pack sent by the plugin ·
D-32 petal trail hidden while sneaking, invisible, vanished or disguised ·
D-33 petal trail only for blinkypies (hardcoded) ·
D-34 jumpscare falls back to the elder guardian without the pack ·
D-35 cinematics use displays, overlays, particles and fake blocks, no core shaders
