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
Tag mapping: is_explosion -> setExplosion; is_projectile -> setProjectile; bypasses_armor -> bypassArmor (also bypasses vanilla
shields in 1.19.2, which covers bypasses_shield); bypasses_effects + bypasses_resistance -> bypassMagic;
bypasses_invulnerability -> bypassInvul (weaker in 1.19.2: Entity.isInvulnerableTo only exempts OUT_OF_WORLD);
bypasses_cooldown -> DEDamage.applyCooldownBypass; DE tags -> IDraconicDamage.getTechLevel / DEDamage.hasTag;
proj_anti_dodge -> CausingOnlySource structure (no datapack tag in 1.19.2).
/kill: 1.19.2 `/kill` = `hurt(OUT_OF_WORLD, Float.MAX_VALUE)`; ModularArmorEventHandler converts it to DE's KILL source
(de-1.18 logic). Known limits: spawn invulnerability, `Invulnerable` NBT.

Decisions taken with the spec defaults (change only by CR to WP-22):
- D-1 new types nested in the locked files (`DEDamage.Type`, `DEDamageSources.IDEDamageSource`).
- D-2 `ShieldControlEntity.tryBlockDamage(LivingDamageEvent)`: no UNBLOCKABLE check (1.20.1 parity; there the check never matched).
- D-3 staff explosion (`DraconicProjectileEntity`): `DamageSource.explosion((LivingEntity) owner)` (credit to owner, = de-1.18); test P5.
- D-4 `TileReactorCore:914` explosion = `Explosion.BlockInteraction.DESTROY` (1.20.1 BLOCK parity, decay).
- D-5 missing death keys (draconic/wyvern/draconium arrow, fusion/chaos/crystal_move): allowlist (as 1.20.1).
- D-6 cooldown bypass: one LOWEST `LivingAttackEvent` listener, registered in `ModularArmorEventHandler.init()`.
Accepted deviations: Warden treats spoof arrows as non-indirect; `DraconicGuardianEntity` crystal explosion loses the
direct entity (crystal), no consumer reads it.

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
