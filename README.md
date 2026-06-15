# ClaySoldiers

[![CI](https://github.com/humankindmc/claysoldiers/actions/workflows/ci.yml/badge.svg)](https://github.com/humankindmc/claysoldiers/actions/workflows/ci.yml)

ClaySoldiers is a Paper Minecraft plugin for survival-friendly Clay Soldier battles.

Players can craft, customize, deploy, and command small clay armies that fight automatically using teams, roles, modifiers, formations, movement behavior, and configurable combat rules. This repository is only for the Clay Soldiers plugin.

## Overview

ClaySoldiers adds deployable soldier dolls that can be crafted in survival gameplay, assigned to teams, upgraded into roles, modified with special traits, and released into the world for automated battles.

The plugin is designed for Paper servers and keeps gameplay values configurable so server owners can tune recipes, stats, spawn limits, combat behavior, and display settings.

## Features

- Craftable and deployable Clay Soldier dolls
- Team colors for allied and enemy targeting
- Roles: Warrior, Guard, Spearman, Skirmisher, Slinger
- Modifiers: Reinforced, Swift, Fierce, Longshot, Explosive, Venom, Lifesteal
- AI combat between opposing teams
- Tactical formations
- Flanking and dodging behavior
- Ranged attacks for Slingers
- Nameplates with team, role, health, and formation display
- Configurable stats, recipes, spawn limits, drops, and behavior

## Installation

1. Build or download the ClaySoldiers jar.
2. Place the jar in your Paper server's `plugins` directory.
3. Start or restart the server.
4. Edit the generated `plugins/ClaySoldiers/config.yml` and `messages.yml` files as needed.
5. Restart the server or reload through your normal server workflow after configuration changes.

ClaySoldiers targets the Paper API version declared in `build.gradle.kts`.

## Commands

Main command:

```text
/claysoldiers
```

Alias:

```text
/csoldiers
```

Available subcommands:

```text
/claysoldiers give <team> [amount] [player]
/claysoldiers give <team> [role] [amount] [player]
/claysoldiers spawn <team> [amount] [role]
/claysoldiers clear [radius]
/claysoldiers teams
/claysoldiers roles
/claysoldiers count
```

## Permissions

```text
claysoldiers.claysoldiers
```

Default: `op`

This permission allows use of the ClaySoldiers admin command.

## Configuration

ClaySoldiers ships with editable YAML resources:

- `config.yml` controls recipes, soldier stats, spawn limits, movement, combat behavior, formations, nameplates, drops, and modifier values.
- `messages.yml` controls user-facing command, item, role, team, and modifier text.

Server owners should review both files before running large public battles.

## Building From Source

Requirements:

- Java toolchain compatible with the version in `build.gradle.kts`
- Git

Build:

```text
./gradlew build
```

On Windows:

```text
.\gradlew.bat build
```

The built jar is written to:

```text
build/libs/
```

## Contributing

Contributions are welcome when they improve the Clay Soldiers plugin. Please read `CONTRIBUTING.md` before opening a pull request.

This repository does not accept unrelated server features or non-Clay Soldiers gameplay systems.

## License

ClaySoldiers is licensed under the Apache License 2.0. See `LICENSE` for details.
