# Contributing

Thanks for your interest in SpellCraft – Magic World!

## Reporting bugs

Open an [issue](../../issues/new/choose) and include:

- Minecraft version and loader (Forge or Fabric) and the mod version
- What you expected to happen and what happened instead
- `latest.log` or the crash report (use a paste service for long logs)
- Other mods installed, if the problem might be a conflict

## Suggesting features

Open an issue describing the idea and how it fits the mod (spells, creatures, structures, progression).

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep changes focused. Bug fixes should note which Minecraft versions/ports are affected (`common/`, `fabric/`, or a `ports/` folder).
3. If you add or change a spell, update `spell_roster.json` as well.
4. Make sure the affected Gradle project builds before opening the PR.
5. Describe what the change does and how you tested it.

The repository is MIT licensed; by contributing you agree your contribution is released under the same license.
