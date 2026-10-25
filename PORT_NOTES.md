# PORT_NOTES — Draconic Evolution 1.19.2 (unofficial port)

Unofficial derivative port of Draconic Evolution 1.20.1 (`origin/1.20` @ `4e7fc310`) to Minecraft 1.19.2 / Forge 43.x.
Not supported by brandon3055. This file lists the intentional deviations from upstream 1.20.1 behaviour and the
1.19.2 implementation choices. Plan and specs live in the port workspace (`PORTING_PLAN.md`, `specs/`).

Build pairing: see `logs/bc-builds.md` in the workspace (DE commit <-> BrandonsCore build).

## Damage types (1.20 data-driven -> 1.19.2 DamageSource subclasses) - WP-22

1.19.2 has no `damage_type` registry. Each of the 14 removed `data/draconicevolution/damage_type/*.json` files (+ the 13
damage_type tag files) is mirrored by a `DEDamage.Type` constant (same field names as 1.20); sources are built by
`lib/DEDamageSources` (PlainSource / KillSource / AttackerSource / ProjectileSource / CausingOnlySource).
Call-site rule: `src.is(DEDamage.X)` -> `DEDamage.is(src, DEDamage.X)`; `src.is(DEDamage.Tags.X)` -> `DEDamage.hasTag(src, DEDamage.Tags.X)`.

`F` = `bypassArmor().bypassMagic()` + flag BYPASSES_COOLDOWN (LOWEST `LivingAttackEvent` listener
`DEDamage.applyCooldownBypass`, zeroes `invulnerableTime`). Every DE source overrides `getFoodExhaustion()` (type value;
1.19.2 `bypassArmor()`/`bypassMagic()` would set it to 0) and `scalesWithDifficulty()` (1.20 semantics: checks the causing
entity, not the arrow).

| 1.20 type (`draconicevolution:`) | message_id | scaling | exh. | effects | tags (DE / minecraft) | 1.19.2 class | flags | tech level |
|---|---|---|---|---|---|---|---|---|
| fusion_explosion | draconicevolution.fusion_explosion | always | 0.1 | - | chaotic / bypasses_{armor,shield,effects,resistance,cooldown}, is_explosion | PlainSource (shared) | F, setExplosion, setScalesWithDifficulty | CHAOTIC |
| crystal_move | draconicevolution.crystal_move | always | 0.1 | - | - / bypasses_* (5), bypasses_invulnerability | PlainSource (shared) | F, bypassInvul, setScalesWithDifficulty | - |
| chaos_implosion | draconicevolution.chaos_implosion | always | 0.1 | - | chaotic / bypasses_* (5), is_explosion | PlainSource (shared) | F, setExplosion, setScalesWithDifficulty | CHAOTIC |
| guardian | draconicevolution.guardian | always | 0.1 | - | chaotic / bypasses_* (5) | AttackerSource (PlainSource if attacker null) | F, setScalesWithDifficulty | CHAOTIC |
| guardian_projectile | draconicevolution.guardian_projectile | always | 0.1 | burning (*) | chaotic / bypasses_* (5), is_explosion, is_projectile | ProjectileSource (IndirectEntityDamageSource) | F, setExplosion, setProjectile, setScalesWithDifficulty | CHAOTIC |
| guardian_laser | draconicevolution.guardian_laser | always | 0.1 | burning (*) | chaotic / bypasses_* (5), is_explosion | AttackerSource (direct, NOT Indirect) | F, setExplosion, setScalesWithDifficulty | CHAOTIC |
| {draconium,wyvern,draconic,chaotic}_arrow | draconicevolution.<tier>_arrow | when_caused_by_living_non_player | 0.1 | - | <tier> / is_projectile | ProjectileSource | setProjectile | tier |
| {...}_arrow_spoof | draconicevolution.<tier>_arrow (shared with base) | when_caused_by_living_non_player | 0.1 | - | <tier>, proj_anti_dodge / - | CausingOnlySource (direct = null, no source position, not Indirect); PlainSource if owner null | none | tier |
| KILL (1.20 `minecraft:generic_kill`) | administrative.kill | never | 0.0 | - | - | KillSource (shared; death message always the base key) | bypassInvul, bypassArmor, bypassMagic | - |

(*) `effects: burning` only changes the player hurt sound in 1.20; no 1.19.2 equivalent -> accepted deviation.
Tag mapping: is_explosion -> setExplosion (+ no hurt-knockback, see "Hurt knockback" below); is_projectile -> setProjectile; bypasses_armor -> bypassArmor (also bypasses vanilla
shields in 1.19.2, which covers bypasses_shield); bypasses_effects + bypasses_resistance -> bypassMagic;
bypasses_invulnerability -> bypassInvul (weaker in 1.19.2: Entity.isInvulnerableTo only exempts OUT_OF_WORLD);
bypasses_cooldown -> DEDamage.applyCooldownBypass; DE tags -> IDraconicDamage.getTechLevel / DEDamage.hasTag;
proj_anti_dodge -> CausingOnlySource structure (no datapack tag in 1.19.2).
/kill: 1.19.2 `/kill` = `hurt(OUT_OF_WORLD, Float.MAX_VALUE)`; ModularArmorEventHandler converts it to DE's KILL source
(de-1.18 logic). Known limits: spawn invulnerability, `Invulnerable` NBT.

Hurt knockback (WP-30, review finding COMBAT-1): 1.20.1 `LivingEntity.hurt` knocks the victim back (0.4, away from the
causing entity) only if the source is not in `#minecraft:is_explosion` [I-1.20]; 1.19.2 `LivingEntity.hurt` does it for
every source with a causing entity (mc-1.19.2 `LivingEntity.java:1133-1142`). `mixin/LivingEntityMixin` (common side,
`@Redirect` of the `knockback(DDD)V` call in `hurt(DamageSource, float)`) skips that call when
`DEDamage.skipsHurtKnockback(source)` (a DE type with `Flag.IS_EXPLOSION`). Effect: the guardian laser (every
unobstructed beam tick, and the obstructed-beam explosion) and guardian projectile hits no longer push the target away
from the guardian, as in 1.20.1. `fusion_explosion` / `chaos_implosion` have no causing entity, so nothing changes for
them. Vanilla sources keep the vanilla 1.19.2 knockback, including the vanilla `level.explode(shooter, ...)` of the
guardian projectile and the D-3 staff explosion (`explosion.player`, see D-3). The explosion push itself
(`Explosion.explode`) is unchanged. If another mod redirects the same call first (Mixin priority >= 1000), Mixin skips
this redirect with a "@Redirect conflict" warning and the 1.19.2 knockback applies.

Decisions taken with the spec defaults (change only by CR to WP-22):
- D-1 new types nested in the locked files (`DEDamage.Type`, `DEDamageSources.IDEDamageSource`).
- D-2 `ShieldControlEntity.tryBlockDamage(LivingDamageEvent)`: no UNBLOCKABLE check (1.20.1 parity; there the check never matched).
- D-3 staff explosion (`DraconicProjectileEntity:234`): `DamageSource.explosion((LivingEntity) owner)` (= de-1.18), an
  `EntityDamageSource("explosion.player", owner)`, so `getEntity() == owner`. 1.20.1 `explosion(owner, null)` has
  direct = owner and no causing entity [I-1.20], so none of the following happens there. Consequences in the port
  (review finding COMBAT-2, WP-30; mc-1.19.2 sources): kill credit, XP and player-kill loot for the owner
  (`LivingEntity.hurt:1084-1091` sets `lastHurtByPlayer`); every mob hit gets `lastHurtByMob = owner`, so neutral mobs
  hit by the explosion turn on the owner; on a `pvp=false` server (or for team mates without friendly fire) other
  players take no damage from the explosion (`ServerPlayer.hurt:656-660` + `canHarmPlayer`; 1.20.1 damages them); each
  victim gets the vanilla 1.19.2 hurt-knockback of 0.4 away from the owner (`:1142`; not exempted by `LivingEntityMixin`,
  which only covers DE types); death message `death.attack.explosion.player` ("blown up by <owner>"; 1.20.1:
  `death.attack.explosion`). The owner itself is not hit in either version (`level.explode(owner, ...)` excludes it).
  Test P5 (not run: needs a client or a player GameTest). Parity alternative for a lead decision: see `logs\WP-30.md`.
- D-4 `TileReactorCore:914` explosion = `Explosion.BlockInteraction.DESTROY` (1.20.1 BLOCK parity, decay).
- D-5 missing death keys (draconic/wyvern/draconium arrow, fusion/chaos/crystal_move): allowlist (as 1.20.1).
- D-6 cooldown bypass: one LOWEST `LivingAttackEvent` listener, registered in `ModularArmorEventHandler.init()`.
Accepted deviations: Warden treats spoof arrows as non-indirect; `DraconicGuardianEntity` crystal explosion loses the
direct entity (crystal), no consumer reads it.

Explosion block interaction: 1.20 `Level.ExplosionInteraction.MOB` -> `ForgeEventFactory.getMobGriefingEvent(level, entity)
? DESTROY : NONE` when the explosion has a source entity (`DraconicProjectileEntity`), and the plain `mobGriefing`
gamerule when it has none (`LaserBeamPhase` obstructed-beam explosion; review finding COMBAT-4, WP-30). Forge 47
`getMobGriefingEvent(level, null)` returns the gamerule without posting an event [I-1.20]; Forge 43 would post an
`EntityMobGriefingEvent` with a null entity, so protection mods could allow or deny it, which 1.20.1 does not allow.

## Reactor explosion lighting (WP-22 spec row 44)

Deviation from 1.20.1: `lib/ExplosionHelper.RemovalProcess.finishChunks` re-lights every modified chunk with
`ThreadedLevelLightEngine.lightChunk(chunk, true)`, as de-1.18 does. When the relight has finished it sends
`ClientboundLightUpdatePacket(pos, light, null, null, false)` to the players tracking the chunk. 1.20.1 has this block
commented out and only sends `ClientboundLevelChunkWithLightPacket`; the port still sends that packet too (5-arg form,
`trustEdges = false`, as de-1.18). Reason: the 1.19.2 light engine supports it, and it avoids dark or unlit chunks after a
reactor explosion (test M4, client, not verified). Threading (review findings COMBAT-3 / TILES-1, WP-30): the
`lightChunk` future completes on the light-engine worker thread, so the send is handed back to the server thread with
`thenRunAsync(..., server)`; reading `ChunkMap`'s player map from the worker was a data race (de-1.18 used `thenRun`).

## Creative tabs and block materials (WP-23)

Source: `specs/WP-23-tabs-materials.md`. Code: `init/DECreativeTabs.java`, `init/DEContent.java`, `blocks/ChaosShardAtmos.java`.

### Creative tabs
- 1.19.2 has no tab registry. `DECreativeTabs.BLOCKS/ITEMS/MODULES` are `CreativeModeTab` subclasses (labels
  `draconicevolution.blocks|items|modules` → keys `itemGroup.draconicevolution.*`). Their `fillItemList` runs the
  1.20.1 registry walks over `ForgeRegistries.*.getKeys()` with the same filters. Expected contents: 45 / 62 / 71 stacks.
- Every item that is in a 1.20.1 tab also gets `.tab(...)` on its `Item.Properties` (85 sites; 2 of them in `api/` via
  CR WP-23-1). This puts it in the creative search tab and in JEI. `structure_block` has no tab (same as 1.20.1).
- The icon cycles every 1200 ms through the tab contents, as in 1.20.1.
- Known cosmetic differences: the 1.19.2 search tab and JEI list DE items in `Registry.ITEM` id order (1.20.1 uses
  tab order). Tab icons follow the order of the last `fillItemList`.

### Block materials (accepted 1.19.2 vs 1.20.1 deltas)
Material = de-1.18 choice; colour = 1.20 `mapColor` as `MaterialColor`; the rest of the 1.20 chain is unchanged.

| # | Delta | Blocks | 1.19.2 | 1.20.1 |
|---|---|---|---|---|
| B1 | note-block instrument under a note block | STONE: rain_sensor, potentiometer, infused_obsidian; GLASS: 9 crystals, portal, chaos_crystal(_part), placed_item | BASEDRUM / HAT | HARP |
| B2 | fluids can wash the block away | non-full-shape blocks (crystals, injectors, placed_item, portal, …) | never (material blocks motion) | possible for small/empty shapes |
| B3 | redstone conduction of full-cube GLASS blocks | none expected | false | true if full shape |
| B4 | machine/storage sound | 27 blocks | METAL (kept from 1.20) | METAL |
| B5 | ore strength/colour | 4 ores | copy(DIAMOND_ORE): 3/3, STONE | same |
| B6 | loot | structure_block, portal, chaos_crystal(_part), placed_item | empty loot tables, no noLootTable() | same |
| B7 | map colour | portal, chaos_crystal(_part), placed_item, comet_spawner | NONE | NONE |
| B8 | material solidity flags (`isSolid`/`blocksMotion`) read by vanilla (heightmaps, sign/item-frame support, pathing, …) | all non-full-shape DE blocks | always solid (METAL/STONE/GLASS) | from the collision shape |
| B9 | reaper enchanted book's creative tab | vanilla item | TAB_COMBAT + search | Ingredients + search |

B1, B2, B8: rain does not fall through a `portal` block, signs/item frames can hang on crystals, note-block sounds
differ. These are edge cases and cannot be set per block in 1.19.2 without overriding Forge hooks. `comet_spawner`
(new in 1.20, no de-1.18 value) uses METAL/NONE; the block removes itself on its first tick.

## Data and runtime (WP-30, Phase 3 headless boot)

- Loot tables: the 1.20 field `random_sequence` is removed from all 51 block loot tables (`src/main/generated`;
  1.19.2 loot tables have no random sequences, plan Phase 3). Drops are unchanged; the 1.20 per-table random sequence
  (deterministic loot rolls per world seed) does not exist in 1.19.2, so loot uses the context random as vanilla 1.19.2 does.
- `pack.mcmeta`: `pack_format 9`, `forge:resource_pack_format 9`, `forge:data_pack_format 10` (1.19.2 values).
- Dev runs (`runServer`) put BrandonsCore, CodeChickenLib, JEI (forge runtime), Curios, CC:Tweaked and EnderStorage on
  the classpath (`fg.deobf`); the dedicated dev server boots with all of them (WP-30 boot gate).
- 1.19.2 `RecipeManager` logs `Loaded N recipes` with N = number of recipe *types* (vanilla behaviour), not recipes;
  DE's 179 recipes are counted from the data (0 parse errors, 0 skipped), see the WP-30 handoff.

## GameTests and reference diff (WP-31, Phase 3)

- Build: a dev-only `gametest` source set and the FG run `gameTestServer` (Gradle task `runGameTestServer`, working dir
  `run/gametest`) live in one delimited block at the end of `build.gradle`. `jar` packs `sourceSets.main` only; the
  refdiff of the port jar shows no gametest class. The run sets `forceExit false`, so Gradle's exit code is the
  gametest verdict (FG's default `System.exit(0)` in the single-use daemon made Gradle report "daemon disappeared").
- Runtime evidence (headless, fresh flat world): 12/12 required tests pass (registry ids vs the 1.20.1 manifest,
  179 recipes incl. fusion catalysts, 51 block loot tables, 43 tags, 15 worldgen datapack entries, 8 multiblocks,
  object holders, energy core T1/T2, fusion craft, generator OP, reactor warm-up), plus 11 optional checks of the
  WP-22 damage facade and WP-23 tabs/block properties, plus the optional reactor run (G12). The runtime registry dump
  differs from 1.20.1 only by the allowlisted damage types (14), creative-mode-tab registry entries (3) and the
  client-only `shield_hud`. Reports: `logs\gametest-report.md`, `logs\WP-31.md`.
- No behaviour change to the mod. The death-message keys a DE source can produce and that have no translation are
  exactly the 16 on the WP-22 §4 allowlist (missing in 1.20.1 as well, decision D-5).

## Client GUI (mgui) deviations

- Fusion crafting core screen (`client/gui/FusionCraftingCoreGui.IngredRenderer`, review finding CL-1, WP-30): child
  z-stacking is disabled (`setZStacking(false)`). The 1.19.2 GUI projection (`GameRenderer` ortho near 1000 /
  `ForgeHooksClient.getGuiFarPlane()`) leaves a visible z range of about +-1000 (1.20.1 about +-10000, WP-10 C11). Each
  ingredient icon adds 32 z when stacked, so with about 24 or more filled injectors the tooltips (overlay at depth + 100),
  and later the ingredient icons and the craft button, fell behind the near plane and were not drawn. Now the ingredient
  area is 32 deep for any injector count. The icons do not overlap below 43 stacks, so the screen looks the same as
  1.20.1; with 43 or more, overlapping icons share one z band instead of stacking. Needs client verification.
