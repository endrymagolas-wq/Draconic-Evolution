Draconic-Evolution
==================

## Unofficial Minecraft 1.19.2 port (branch `port/1.19.2`)
This branch is an **unofficial derived port** of Draconic Evolution 3.1.2 (Minecraft 1.20.1, upstream `origin/1.20` @ `4e7fc310`)
to Minecraft 1.19.2 / Forge 43.x. It is **not supported by brandon3055**; please do not report issues with it upstream.
It needs the matching unofficial BrandonsCore port (https://github.com/endrymagolas-wq/BrandonsCore/tree/port/1.19.2)
and stock CodeChickenLib 1.19.2 (4.3.2 or newer). The code stays under the Don't Be a Jerk License below: no
redistribution of the mod as a whole outside mod packs, and no public uploads without the owner's permission.
Deviations from 1.20.1 behaviour are listed in `PORT_NOTES.md`.

Build: JDK 17, ForgeGradle 5 (`gradlew build`). First build and publish the BrandonsCore port, then point this build at that
maven with `-Pport_maven=<url>` or the env var `ORG_GRADLE_PROJECT_port_maven` (default: a `maven` folder next to this project).
[![](http://cf.way2muchnoise.eu/short_draconic-evolution_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/draconic-evolution)
[![](http://cf.way2muchnoise.eu/versions/Available%20for_draconic-evolution_full.svg)](https://www.curseforge.com/minecraft/mc-mods/draconic-evolution/files)
[![](https://img.shields.io/discord/474753901451542531.svg?logo=discord&label=Discord&labelColor=2d2d2d&style=flat)](https://discord.gg/e2HBEtF)
[![](https://img.shields.io/badge/Nexus%20index-maven-blue?style=flat&labelColor=2d2d2d)](https://nexus.covers1624.net/#browse/browse:maven-hosted:com%2Fbrandon3055%2Fdraconicevolution%2FDraconic-Evolution)
[![](https://img.shields.io/badge/Patreon-brandon3055-red?style=flat&labelColor=2d2d2d&logo=patreon)](https://www.patreon.com/brandon3055)

Draconic Evolution adds powerful new tools, Items, weapons and Armor. The premise is the enigmatic ore - Draconium. As you progress you find it has many uses from the mundane such as machines and items, to the near-mystical at the top with Awakened Draconium to make extremely powerful armor and tools.

## License / Use in Modpacks
This mod is licensed under the [**Don't Be a Jerk License**](https://github.com/Draconic-Inc/Draconic-Evolution/blob/HEAD/LICENSE.md) created by CoFH.
I hereby grant permission to use this mod in any mod pack without the need to request permission from myself the owner (brandon3055).
