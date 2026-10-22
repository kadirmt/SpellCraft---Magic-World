# SpellCraft – Magic World

A Harry Potter-inspired magic mod for Minecraft. Craft a wand, learn spells at the Spell Table, level up your wizard, duel other players live, brew potions, and raid a dementor-guarded prison to reach a Basilisk boss.

[![CurseForge](https://img.shields.io/badge/CurseForge-3.7K%2B%20downloads-orange)](https://www.curseforge.com/minecraft/mc-mods/spellcraft-magic-world)
[![Modrinth](https://img.shields.io/badge/Modrinth-1.8K%2B%20downloads-green)](https://modrinth.com/mod/spellcraft-magic-world)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Download:** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/spellcraft-magic-world) · [Modrinth](https://modrinth.com/mod/spellcraft-magic-world) — over 5,500 downloads combined.

## Features

- **5 wand tiers** – Arcanewood, Thunderbird, Phoenix, Troll and Elder, each with its own power and cast-time multipliers.
- **26+ spells** in four tiers (Basic → Advanced → Dark → Unforgivable), cast from a 12-slot drag-and-drop loadout with channel time, telegraph animation and cooldowns.
- **Wizard level and skill tree** (max level 15) – mana capacity, mana regeneration, cooldown reduction, spell power; respec is free.
- **Live wand duels** – two simultaneous casts lock into a Priori Incantatem beam duel decided by click speed, wand power and mana.
- **20+ original creatures** with GeckoLib animations (Trolls, Werewolves, Acromantulas, Dementors, Hippogriffs, Thestrals, Phoenixes, Thunderbirds, …) and wandering Wizard Trader NPCs.
- **Two custom biomes** (Gloomwood, Arcanewood Grove), a **Hogsmeade-style village**, an ocean **prison tower** and a **Basilisk boss lair** with its own boss bar and drops.
- **3 potions** and a summonable **Corporeal Patronus** (owl, wolf or stag as you level up).

## Supported versions

| Minecraft | Loaders |
|-----------|---------|
| 26.2      | Forge, Fabric |
| 26.1.2    | Forge, Fabric |
| 1.21.1    | Forge, Fabric |
| 1.20.1    | Forge, Fabric |

Pre-built jars are published on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/spellcraft-magic-world) and [Modrinth](https://modrinth.com/mod/spellcraft-magic-world). The latest release is 0.0.6.

## Repository layout

The mod id is `arcanum`.

| Path | Contents |
|------|----------|
| `common/`, `fabric/` | Main Gradle project for Minecraft 1.21.1 (Architectury common code + Fabric), see `gradle.properties` for the locked dependency versions |
| `ports/forge-1.21.1/` | Forge port for 1.21.1 |
| `ports/mc1.20.1/` | Common/Fabric/Forge sources for 1.20.1 |
| `ports/mc26.1.2/`, `ports/mc26.2/` | Ports for the 26.x versions (Fabric + Forge) |
| `spell_roster.json` | Data description of every spell: incantation, tier, targeting, mechanics, particles |

Dependencies: [GeckoLib](https://github.com/bernie-g/geckolib) for creature animation and TerraBlender for the custom biomes (see `gradle.properties` for exact versions).

## Admin / testing commands

Requires operator permission.

- `/arcanum spells unlock` – learn every spell
- `/arcanum spells lock` – forget all spells
- `/arcanum maxxp` – set your wizard level to max

## Status

The author does not plan further updates, so the mod has been released as open source. Bug reports, forks and pull requests are welcome – see [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits

- Designs from [kasu](https://kasuama.carrd.co/).
- Built with Architectury, Fabric, Forge, GeckoLib and TerraBlender.

## License

[MIT](LICENSE)
