# SwizzSMP — in-game test checklist

The automated suite (`node .tools/bot/itest.js`) checks the mechanics with two bots on a
real Paper 26.3 server: damage, cooldowns, freezes, domes and restoration, rituals,
menus, energy and the special items.

This list covers what bots **can't** judge: how things **look and feel** on a real
client. You need two players (or two accounts). Give yourself everything with:

```
/giveswizz <type> <you>     /energy set <you> 5     /resetcooldown <you>
/swizzadmin upgrade <you> true|false
```

## Highest priority (risky features)

- [ ] **Gleam Flashbang**: the victim's screen turns white for 4 s, clears for 1 s, and
      repeats for 15 s. Their real helmet shows again afterwards. If the screen does
      **not** go white, set `gleam.ability2.mode: TITLE` in `abilities.yml` and test again.
- [ ] **Confusion Mirage Dome**: everyone inside has the caster's skin and armor
      (including trims). Nametags are hidden. Only the caster sees purple name labels. After
      10 s, everyone's skin, armor and nametag come back. Leaving the dome is blocked, and
      ender pearls out are cancelled.
- [ ] **Confusion upgraded dome**: enemies' W/A/S/D are reversed and it feels playable
      (not too jittery). Tune `invert-strength` if needed.
- [ ] **Frost powder snow**: walking onto powder snow keeps you on top. Sneaking lets
      you sink. Check it isn't too jumpy.
- [ ] **Fortune upgraded totem**: with low health and no totem, about 1 in 10 lethal
      hits pops a totem, and your potion effects are still there afterwards.

## Each swizz

- [ ] Aqua: Drown (bubbles, damage), Whirlpool (spiral, pull, works on someone in a
      cobweb), trident disables a shield, rain speeds up cooldowns, upgraded water bottle
      (absorption, refills after 7 s).
- [ ] Confusion: 4-hit combo → the victim's next hit whiffs; Scramble shuffles the
      hotbar (twice when upgraded); upgraded 5th hit gives levitation.
- [ ] Earth: Fault Line visuals and knockback; Root Grab pulls to you; upgraded Root
      Field drags people over about 6 s; 12/13 hearts.
- [ ] Fortune: Golden Circle ring follows you; a gapple inside gives the extra absorption
      hearts; Fortune Roll messages; triple ores (not with Silk Touch); triple mob drops.
- [ ] Frost: Icicles (3 throws by right-click, switching item cancels); Deep Freeze;
      upgraded icicles break shields.
- [ ] Gleam: Radiance glow and Haste V; combo lightning from the 4th hit; upgraded
      low-health hearts above players under 5 hearts.
- [ ] Sculk: Sonic Beam charge sound and beam; Sculk Dome (can't break or tunnel out,
      fully restored after 15 s); upgraded dome (ground turns to sculk, wardens only attack
      enemies, Darkness); eating sculk repairs armor; Swift Sneak speed.

## Cherry blossom trail

- [ ] As blinkypies, walk: pink petals drift down behind your feet and settle on the
      ground. Sprinting drops more. Jumping and landing from 2+ blocks puff out petals.
      Another player sees it too. Anyone else gets no trail, and `/trail` tells them so.
- [ ] Sneaking, an invisibility potion and `/trail off` all hide it. `/trail on` brings it back.
- [ ] Too much or too little? Tune `petal-trail` in `config.yml` and `/swizzadmin reload`.

## Jumpscare

- [ ] `/jumpscare <you>` in first person: the nun picture fills the middle of the screen
      for about 2.5 s with a very loud scream and the camera jolts, then everything is
      back to normal (your real helmet too).
- [ ] With the texture pack declined, you see the elder guardian ghost instead.
- [ ] `/jumpscare` with a friend online scares them but not you.

## Aura showcase

- [ ] `/aura` with a friend watching from ~10 blocks: the sigil lies flat and centred under
      you (not standing up, not offset), glows, and both rings spin. The 7 swizz rise with
      chimes, orbit faster, and stream their colours into you.
- [ ] At the climax: white flash, lightning, boom, shards flying out, rainbow border and
      wobble, your name in rainbow letters. You float down gently at the end.
- [ ] Nothing is left behind afterwards (no floating items or blocks), even if you log out
      in the middle.

## Matrix, singularity and timestop (`/cinematic <name>`)

- [ ] matrix: the green CRT and typing are readable; the black cat walks by twice; the
      ground and walls turn black with a lime grid; the rain overlay animates smoothly (no
      flicker); agents wear black suits; bullets stop in the air and drop; the chrome covers
      the screen; the world looks normal again after "Welcome to the real world".
- [ ] singularity: the black hole looks round from every side, the disk spins, blocks peel
      off and fly in; after the blast the ground is back exactly as it was (relog to be sure).
- [ ] timestop: the clock hands sit on the clock face; a friend is frozen (even mid-jump),
      mobs stop, a thrown snowball or arrow hangs; everything carries on afterwards.

## Finale (`/cinematic finale`, 75 s)

- [ ] Watch it once with a friend: six act cards, you are frozen until the clock shatters,
      the sunrise at the end plays over about 11 s and the sky returns to the real time,
      fireworks hurt nobody, and the ground is intact afterwards.

## Resource pack

- [ ] On join you are asked to load the SwizzSMP pack; after accepting, the seven swizz
      items in `/swizzbook` show the custom icons (Earth still shows the armadillo scute).
- [ ] Decline the pack: the items show the vanilla look-alikes instead, nothing is broken.
- [ ] A real Heart of the Sea / Echo Shard / Snowball / Emerald / Amethyst Shard /
      Glowstone Dust still looks normal with the pack on.
- [ ] Craft each swizz with the recipe shown in `/swizzbook` (Aqua needs Water Breathing
      8:00 potions).

## Systems

- [ ] HUD action bar looks right and updates (`/swizz hud` toggles it).
- [ ] Sneak+F and Sneak+Q fire abilities; `/swizz controls` turns them off.
- [ ] `/swizzbook`: every recipe page, every guide book reads well, and the numbers match
      `abilities.yml`.
- [ ] Upgrader can't go into: chest, ender chest, shulker box, barrel, hopper, bundle,
      item frame, armor stand, decorated pot, allay, donkey chest.
- [ ] Ritual: the broadcast hides X/Z, then leaks them at 5 and 10 minutes; the
      particle circle shows the 5-block radius; standing in it at 20 minutes upgrades you;
      being away drops the Upgrader.
- [ ] Swizz Sword: bind two elements; `/ability3` and `/ability4`; dropping the sword
      brings your own swizz back.
- [ ] Restart the server during a Sculk dome: the terrain is restored on the next start.
