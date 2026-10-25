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
guardian projectile. The D-3 staff explosion has no causing entity since decision D-3 (c), so it gets no hurt-knockback
in either version (see D-3). The explosion push itself
(`Explosion.explode`) is unchanged. If another mod redirects the same call first (Mixin priority >= 1000), Mixin skips
this redirect with a "@Redirect conflict" warning and the 1.19.2 knockback applies.

Decisions taken with the spec defaults (change only by CR to WP-22):
- D-1 new types nested in the locked files (`DEDamage.Type`, `DEDamageSources.IDEDamageSource`).
- D-2 `ShieldControlEntity.tryBlockDamage(LivingDamageEvent)`: no UNBLOCKABLE check (1.20.1 parity; there the check never matched).
- D-3 staff explosion (`DraconicProjectileEntity:234`): **lead decision (c)** (CR WP-30-2 §2, `DECISIONS.md`
  2026-10-25), closest parity with 1.20.1 `damageSources().explosion(owner, null)` (type `minecraft:explosion`,
  direct = owner, causing = null [I-1.20]). The port uses `DEDamageSources.explosion(owner)` =
  `DEDamageSources.DirectExplosionSource`, an `EntityDamageSource("explosion", owner)` with `setExplosion()`,
  `getEntity()` -> null, `getDirectEntity()` -> owner, `scalesWithDifficulty()` -> always true and a death message that
  never uses the `.item` variant. It is not an `IDEDamageSource` (vanilla type, no DE tags) and deliberately not an
  `IndirectEntityDamageSource` (1.19.2 `EnderMan.hurt:327-342` dodges every Indirect source; 1.20.1 dodges only
  `#is_projectile`). Effects, all as 1.20.1 (mc-1.19.2 sources): no kill credit, XP or player-kill loot
  (`LivingEntity.hurt:1084-1105` sets nothing, `dropAllDeathLoot:1337`/`dropExperience:1355` see
  `lastHurtByPlayerTime == 0`, loot context without `killer_entity`/`last_damage_player`, direct killer = owner); no
  `lastHurtByMob`, so no mob turns on the owner; no PvP gate (`ServerPlayer.hurt:656-660` reads `getEntity()`), so
  other players are damaged on `pvp=false` servers and team mates without friendly fire are damaged too; no
  hurt-knockback (`:1133-1145`, random `hurtDir`); difficulty scaling against players (`Player.hurt:848`, Easy/Hard;
  Peaceful: 0); shield blocking against the owner's position (`isDamageSourceBlocked:1241-1263` via
  `EntityDamageSource.getSourcePosition`) and the blocking knock-back on the owner (`blockUsingShield`); death message
  `death.attack.explosion` ("<victim> blew up"), also when the owner holds a renamed item; endermen take the damage and
  then teleport randomly (90 %, `EnderMan.hurt:344-347`, the 1.20.1 rule for a source without a living causing entity).
  The owner itself is not hit (`level.explode(owner, ...)` excludes it). Exhaustion 0.1 (as 1.20.1 `explosion`).
  Before the decision the port used the de-1.18 source `DamageSource.explosion((LivingEntity) owner)`
  (`explosion.player`, credited to the owner, PvP-gated, +0.4 knockback, never scaled for a player owner).
  Reachability (upstream, unchanged): the only code that creates a `DraconicProjectileEntity` (the staff shot,
  `ModularStaff`) is commented out in 1.20.1 and in the port, the `draconic_arrow` entity type builds
  `DraconicArrowEntity`, and the constructor itself throws `IllegalArgumentException: Unregistered serializer` because
  `lib/Serializers.OPT_MODULE_SERIALIZER` is never registered (registration commented out upstream; the
  `SynchedEntityData.define` check exists in both versions). So this path is not reachable in normal play; the change
  matters only if the projectile is revived. The call-site GameTest registers the serializer on the gametest server
  (test-only) to reach line 234.
  Test P5 / GameTests `d3staffexplosioncallsite`, `d3staffexplosionsourceeffects`, `d3staffexplosionshieldblocking`
  (`src/gametest/.../DamageReviewGameTests.java`, batch `de_damage_d3`): each effect above is asserted on the server,
  each with a control (the old de-1.18 source, an Indirect variant or the option-(b) source without position) that
  shows the difference.
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
- Later additions: WP-31b (35 required server tests, `logs\WP-31b.md`), the two WP-30 review-fix checks (COMBAT-1
  knockback, COMBAT-3 relight thread; CR WP-30-3) and the three D-3 tests, the last five in
  `src/gametest/.../DamageReviewGameTests.java`. A default run now has 63 tests: 52 required, plus the 11 optional
  WP-22/WP-23 checks.

## Client GUI (mgui) deviations

- Fusion crafting core screen (`client/gui/FusionCraftingCoreGui.IngredRenderer`, review finding CL-1, WP-30): child
  z-stacking is disabled (`setZStacking(false)`). The 1.19.2 GUI projection (`GameRenderer` ortho near 1000 /
  `ForgeHooksClient.getGuiFarPlane()`) leaves a visible z range of about +-1000 (1.20.1 about +-10000, WP-10 C11). Each
  ingredient icon adds 32 z when stacked, so with about 24 or more filled injectors the tooltips (overlay at depth + 100),
  and later the ingredient icons and the craft button, fell behind the near plane and were not drawn. Now the ingredient
  area is 32 deep for any injector count. The icons do not overlap below 43 stacks, so the screen looks the same as
  1.20.1; with 43 or more, overlapping icons share one z band instead of stacking. Needs client verification.

## Additions not in upstream 1.20.1: cosmetic armor

Not part of DE 1.20.1; added by the port so a player can wear a visually complete set together with the modular
chestpiece. The pieces have no gameplay function at all. Code is kept apart from upstream code:
`items/cosmetic/*` (items, material, tier, registration with its own `DeferredRegister`) and
`client/model/cosmetic/*` (rendering); the only hooks in upstream files are one line in `DraconicEvolution`
(`CosmeticArmor.init()`) and two lines in `init/DECreativeTabs.itemContents`.

- **Items (9):** `draconicevolution:{wyvern,draconic,chaotic}_{helmet,leggings,boots}` (`CosmeticArmorItem extends
  ArmorItem`, stack size 1, rarity of the tier). None of these ids exists in DE 1.20.1 or collides with another DE id.
- **No properties:** armor material with defense 0, toughness 0, knockback resistance 0, durability 0 (so the item is
  not damageable), enchantment value 0, no repair ingredient; `getDefaultAttributeModifiers` returns an empty map in every
  slot (no "When on head" tooltip block); not enchantable (table, anvil/book), not repairable, no glint, no effects. Only
  the diamond equip sound is kept. A tooltip says "Cosmetic only". The modular-armor logic (`ModularArmorEventHandler`,
  shield, undying, flight, attributes, capacitor charging) only looks at `IModularItem` / `IModularArmor` items, so it
  never sees these pieces; a full set + chestpiece gives exactly the attributes of the chestpiece alone (GameTest).
- **Recipes** (`data/draconicevolution/recipes/cosmetic/*.json`, vanilla armor shapes, shaped crafting table):
  helmet `XXX / X X` (5), leggings `XXX / X X / X X` (7), boots `X X / X X` (4), with X = Wyvern:
  `#forge:ingots/draconium` (draconium ingot), Draconic: `#forge:ingots/draconium_awakened` (awakened draconium
  ingot), Chaotic: `draconicevolution:medium_chaos_frag` (medium chaos fragment). No recipe advancements (the recipes
  are not auto-unlocked in the recipe book; JEI shows them).
- **Creative tab:** DE items tab, right after the chestpiece of the same tier (helmet, leggings, boots). The walk over the
  item registry skips the cosmetic items and inserts them after each chestpiece, so this holds for any registry order.
  Search tab / JEI: registry order.
- **Lang:** `en_us` (in `src/main/generated`, which the port ships as committed files; a datagen rerun would drop the
  keys), `uk_ua` and `ru_ru` (proper names). The other shipped locales fall back to `en_us`.
- **Rendering, fancy mode** (`DEConfig` client `fancyToolModels = true`, default): the 3D OBJ parts of DE 1.12.2
  (`models/armor/{wyvern,draconic}_{helmet,belt,left_leg,right_leg,left_boot,right_boot}.obj`, copied unchanged to
  `models/cosmetic_armor/`) with the 1.12.2 textures. `CosmeticArmorModel` (a `HumanoidModel` whose head / body / legs are
  BC `ExtendedModelPart`s with one CCL `CCModel` child per OBJ) is drawn by BrandonsCore's `EquippedItemModelLayer`, the
  chestpiece's path; the vanilla armor layer gets BC's empty `DummyHumanoidModel`. Helmet on the head, leggings = belt
  (body) + two leg pieces, boots = two boot pieces on the legs. Offsets and scales are the 1.12.2 values of
  `ModelWyvernArmor` / `ModelDraconicArmor` plus the `ModelRenderOBJ` rotation (180 deg about (-1, 0, 1)); the humanoid
  skeleton is the same in 1.12.2 and 1.19.2. One deviation: the Wyvern helmet is scaled by 1.01 because 104 of its faces
  are exactly coplanar with the vanilla head cube (z-fighting). The pose is copied from the wearer's model every frame
  (`ForgeHooksClient.copyModelProperties`, as the vanilla armor layer does), so sneaking, riding, swimming, babies and
  armor stand poses follow the vanilla model; 1.12.2's whole-model sneak translation is not ported (1.19.2 moves the
  bones instead). The parts are rendered every frame through `CCRenderState` into the vanilla
  `RenderType.entityCutoutNoCull(<part texture>)` buffer (OBJ triangles as degenerate quads, flat normals, V flipped back
  because the 1.12.2 OBJs use a top-down V and CCL's parser flips V). No DE shader and no VBO render type is used.
- **Rendering, non-fancy mode** (`fancyToolModels = false`, recommended with Oculus shader packs): the vanilla armor
  layer and model with the 1.12.2 2D armor textures (`getArmorTexture` ->
  `textures/models/cosmetic_armor/<tier>_armor_layer_{1,2}.png`, 256x128, layer 2 for leggings).
- **Chaotic tier:** 1.12.2 had no Chaotic armor. The Chaotic set uses the Draconic geometry; its textures (3D part
  textures, armor layers, item icons `textures/item/armor/chaotic_*.png`) are generated from the Draconic ones by
  `tools/cosmetic/recolor.py` in the port workspace: orange pixels become the dark, fully saturated red of DE's Chaotic
  chestpiece icon (brightness fitted on the Draconic vs Chaotic chestpiece icons), everything else is kept.
  `recolor.py --check` verifies the committed PNGs. Wyvern/Draconic item icons are the files already present in the
  1.20 assets (`textures/item/armor/`, identical to 1.12.2).
- **Licence / origin:** all models and textures are brandon3055's Draconic Evolution art (1.12.2 branch), reused and, for
  Chaotic, modified under DE's licence, CC BY-NC-SA 4.0 (non-commercial, attribution, share-alike), like the rest of
  this port.
- **Checks:** GameTests `CosmeticArmorGameTests` (batch `de_cosmetic`, required): registration, no properties
  (attributes, durability, enchanting, repair), recipes (load + real crafting grid + wrong material), tab placement,
  armor stands with chestpiece vs chestpiece + set (identical attributes, chestpiece still found, pieces never damaged).
  The upstream-parity tests (WP-23 T1-T5, G4) skip the 9 items / `cosmetic/` recipes; refdiff lines in
  `tools/refdiff/allow-port.txt` ("cosmetic armor"). Offline preview: `tools/cosmetic/preview.py`. In-game look
  (lighting, render order, shader packs) is not verified by any automated check.

## Client compatibility (Oculus / ImmediatelyFast)

Found in game (2026-10-25) with the target pack and in a clean dev client; fixed in DE 3.1.2.9003 + BrandonsCore
3.2.1.9008 (BC branch / DE branch `fix/vbo-bind`).

1. **Invisible GUI items / black geometry (VAO corruption).** Stock CodeChickenLib 4.3.2 `VBORenderType` uploads and
   draws its `VertexBuffer` without binding it, so on 1.19.2 the data goes into whatever vanilla VAO is bound. After a
   chestpiece or fancy tool model was drawn, items in GUIs could turn invisible and other geometry black. Fix: the
   chestpiece (`ModularChestpieceModel`), tool models (`ToolRenderBase` and subclasses) and BC contributor wings use
   BrandonsCore `com.brandon3055.brandonscore.mgui.render.BindingVBORenderType`, a copy of the CCL 4.3.2 class that
   binds/unbinds like CCL 1.20.1. CCL stays the stock 4.3.2.490 jar.
2. **Chestpiece not drawn on the body, fancy 3D tools invisible.** Oculus (even with shaders off) and ImmediatelyFast
   replace the buffer sources with batching sources that draw collected geometry themselves and never call
   `RenderType.end`, the only place a VBO render type draws. All VBO submits now call
   `BindingVBORenderType.WithCallbacks.drawImmediate()` instead of `buffers.getBuffer(type)`: `ToolRenderBase` (4
   sites), `RenderModularChestpiece` core gem (1), `ModularChestpieceModel` (3), BC `ContributorModel` (1). Under vanilla
   the result is identical (same RenderSystem matrices). Verified in the full pack with shaders off: chestpiece on the
   body and 3D tools render. (The `RenderModularChestpiece` core gem site was converted during integration, same
   pattern, not separately checked in game.)
3. **Fancy tools still missing in the HUD hotbar** with ImmediatelyFast: its `hud_batching` batches hotbar item
   rendering the same way. Pack-side fix (verified): `config/immediatelyfast.json` -> `"hud_batching": false`.
   With an Oculus **shader pack active**, DE custom-shader models stay invisible in the world (BC PORT_NOTES R2, not
   changed): use `Client.fancyToolModels=false` (2D tool sprites; the chestpiece is then not drawn on the body) and
   `Client.guardianShaders=false` in `config/brandon3055/DraconicEvolution.cfg`.

Recommended settings: no shader pack -> `fancyToolModels=true` works, set ImmediatelyFast `hud_batching=false`;
shader pack active -> `fancyToolModels=false`, `guardianShaders=false`. The cosmetic armor renderer does not use VBO
render types (plain `EquippedItemModelLayer` / armor layers), so it is not affected by items 1–2.
