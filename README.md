# ClaySoldiers

**ClaySoldiers** is a Paper Minecraft plugin for Clay Soldiers-inspired battles, allowing players to craft, customize, deploy, and command tiny clay armies in survival Minecraft.

Players can create soldiers, assign them to teams, upgrade their roles, add modifiers, and watch them fight enemy teams with custom movement, attacks, dodging, flanking, formations, particles, sounds, and health displays.

---

## Current Version

```text
ClaySoldiers 0.4.5
Paper 26.1.2
```

---

## Features

- Survival-craftable Clay Soldier dolls
- Multiple soldier teams/colors
- Role upgrades:
  - Warrior
  - Guard
  - Spearman
  - Skirmisher
  - Slinger
- Soldier modifiers:
  - Reinforced
  - Swift
  - Fierce
  - Longshot
  - Explosive
  - Venom
  - Lifesteal
- Automatic soldier combat
- Team-based targeting
- Custom movement and pathfinding
- Dodging and flanking behavior
- Tactical formations
- Shield walls and spear walls
- Ranged slinger attacks
- Nameplates with health display
- Configurable recipes, stats, limits, and behavior
- Admin/player commands for giving, spawning, clearing, and listing soldiers

---

# Clay Soldiers Guide

## What Are Clay Soldiers?

Clay Soldiers are small deployable units that fight for their team.

Each soldier has:

- A team
- A role
- Health
- Attack damage
- Movement behavior
- Optional modifiers
- Custom equipment and appearance

Soldiers from the same team are allies.

Soldiers from different teams are enemies and will automatically fight each other when nearby.

---

## Crafting Clay Soldiers

By default, players can craft basic Clay Soldier Dolls using:

```text
Clay Ball + Soul Sand = 4 Clay Soldier Dolls
```

Depending on the server recipe setup, this may work as either a shaped or shapeless recipe.

---

## Deploying Soldiers

To deploy a Clay Soldier:

```text
Right-click while holding a Clay Soldier Doll
```

Behavior:

| Action | Result |
|---|---|
| Right-click | Deploys soldiers from the stack |
| Sneak + right-click | Deploys only one soldier |
| Creative mode | Does not consume dolls |
| Survival/adventure mode | Consumes deployed dolls |

If the player right-clicks a block, the soldier spawns on the clicked block face.

If the player right-clicks the air, the plugin attempts to spawn the soldier near the player's target block or in front of the player.

---

## Teams

Teams determine who soldiers fight.

Same-team soldiers will not attack each other.  
Different-team soldiers will automatically fight when nearby.

Available teams:

| Team Key | Display Name | Recolor Ingredient |
|---|---|---|
| `clay` | Clay | Clay Ball |
| `red` | Red | Red Dye |
| `yellow` | Yellow | Yellow Dye |
| `green` | Green | Green Dye |
| `blue` | Blue | Blue Dye |
| `orange` | Orange | Orange Dye |
| `magenta` | Magenta | Magenta Dye |
| `lightblue` | Light Blue | Light Blue Dye |
| `lime` | Lime | Lime Dye |
| `pink` | Pink | Pink Dye |
| `cyan` | Cyan | Cyan Dye |
| `purple` | Purple | Purple Dye |
| `brown` | Brown | Brown Dye |
| `black` | Black | Black Dye |
| `gray` | Gray | Gray Dye |
| `white` | White | White Dye |
| `melon` | Melon | Melon Slice |
| `pumpkin` | Pumpkin | Pumpkin |
| `redstone` | Redstone | Redstone |
| `coal` | Coal | Coal |
| `carrot` | Carrot | Carrot |
| `potato` | Potato | Potato |
| `beetroot` | Beetroot | Beetroot |

---

## Recoloring Soldiers

To recolor a Clay Soldier Doll, craft the doll with a team ingredient.

Examples:

```text
Clay Soldier Doll + Red Dye = Red Soldier Doll
Clay Soldier Doll + Blue Dye = Blue Soldier Doll
Clay Soldier Doll + Pumpkin = Pumpkin Soldier Doll
Clay Soldier Doll + Redstone = Redstone Soldier Doll
```

Recoloring preserves the doll's current role and modifiers.

---

# Soldier Roles

Roles change how a soldier behaves in combat.

Each role has different stats, equipment, movement behavior, attack style, and formation behavior.

## Warrior

The default role.

Warriors are balanced front-line fighters.

They are useful for simple armies, early fights, and cheap mass deployment.

Default weapon:

```text
Stick
```

---

## Guard

Guards are slow, defensive shield units.

They have higher health, lower speed, and can perform shield bash attacks.

Upgrade ingredient:

```text
Iron Ingot
```

Crafting:

```text
Warrior Doll + Iron Ingot = Guard Doll
```

Best used as a front line.

---

## Spearman

Spearmen are long-reach fighters designed to attack from the second line.

Upgrade ingredient:

```text
Bamboo
```

Crafting:

```text
Warrior Doll + Bamboo = Spearman Doll
```

Best used behind Warriors or Guards.

---

## Skirmisher

Skirmishers are fast flankers with high dodge chance.

Upgrade ingredient:

```text
Feather
```

Crafting:

```text
Warrior Doll + Feather = Skirmisher Doll
```

Best used to harass enemies and attack from the sides.

---

## Slinger

Slingers are ranged support units that throw clay pellets.

Upgrade ingredient:

```text
Snowball
```

Crafting:

```text
Warrior Doll + Snowball = Slinger Doll
```

Best used behind a defensive front line.

---

# Modifiers

Modifiers add special traits to a Clay Soldier Doll.

Modifiers can be added by crafting a doll with the modifier ingredient.

A doll can have multiple modifiers, but each modifier can only be applied once.

| Modifier | Ingredient | Effect |
|---|---|---|
| Reinforced | Iron Nugget | Increases health |
| Swift | Sugar | Increases speed and reduces attack cooldown |
| Fierce | Flint | Increases damage |
| Longshot | Amethyst Shard | Increases attack range |
| Explosive | Fire Charge | Adds splash damage |
| Venom | Spider Eye | Adds bonus venom damage |
| Lifesteal | Ghast Tear | Heals the attacker |

Examples:

```text
Clay Soldier Doll + Iron Nugget = Reinforced Clay Soldier Doll
Clay Soldier Doll + Sugar = Swift Clay Soldier Doll
Clay Soldier Doll + Flint = Fierce Clay Soldier Doll
Clay Soldier Doll + Amethyst Shard = Longshot Clay Soldier Doll
Clay Soldier Doll + Fire Charge = Explosive Clay Soldier Doll
Clay Soldier Doll + Spider Eye = Venom Clay Soldier Doll
Clay Soldier Doll + Ghast Tear = Lifesteal Clay Soldier Doll
```

Modifiers can be stacked over multiple crafting steps.

Example:

```text
Red Guard Doll + Iron Nugget = Reinforced Red Guard Doll
Reinforced Red Guard Doll + Sugar = Reinforced Swift Red Guard Doll
```

---

# Combat

Clay Soldiers automatically look for enemy soldiers nearby.

A soldier will not target:

- Itself
- Dead soldiers
- Soldiers on the same team
- Soldiers in another world

When an enemy is found, the soldier will move toward it, attack it, flank it, hold formation, or use its role-specific combat behavior.

---

## Attack Types

Clay Soldiers can use several attack types:

| Attack | Description |
|---|---|
| Quick | Basic close-range attack |
| Poke | Spearman reach attack |
| Leap | Jumping attack used at distance |
| Sweep | Area attack used against grouped enemies |
| Shield Bash | Guard attack that pushes enemies |
| Sling | Ranged attack used by Slingers |

---

## Dodging

Dodging is enabled by default.

When attacked by another soldier, a soldier may dodge based on its role.

Skirmishers have the highest dodge chance.

A successful dodge moves the soldier away from the attack and plays visual/sound effects.

---

## Flanking

Flanking is enabled by default.

Some soldiers, especially Skirmishers, may attempt to move around enemies instead of charging directly forward.

Flanking helps units attack from the sides or back of enemy groups.

---

## Death and Drops

Soldiers have health and can be killed by:

- Enemy soldiers
- Players
- Fire
- Lava
- Magma or hot blocks
- Other configured damage sources

By default, soldiers can drop their doll item when killed.

Creative players can instantly kill soldiers if that config option is enabled.

---

# Formations

Clay Soldiers can enter formations when enough allied soldiers of the same type are nearby.

By default, tactical formations require at least:

```text
4 same-team, same-role soldiers nearby
```

Formations can improve combat behavior and create more organized battles.

Formation behavior includes:

- Front-line positioning
- Shield walls
- Spear walls
- Support positioning
- Flank support
- Defensive clustering

When a formation activates, soldiers may display a formation indicator in their nameplate.

---

# Nameplates

Clay Soldiers can display custom nameplates.

Nameplates can show:

- Team
- Role
- Health
- Formation status

The default formation indicator is:

```text
[F]
```

Health can be shown as text and/or a small bar depending on config settings.

---

# Movement

Clay Soldiers use custom simulated movement.

They are not normal mobs, so their movement is handled by the plugin.

Movement features include:

- Direct movement
- Pathfinding fallback
- One-block step support
- Stair support
- Slab support
- Diagonal movement
- Hazard avoidance
- Route refreshing
- Path smoothing
- Maximum drop height checks

For best results, battles should take place on mostly clear terrain.

---

# Spawn Limits

Spawn limits are enabled by default.

Default limits:

```text
Max soldiers: 32
Radius: 100 blocks
```

If too many soldiers already exist nearby, new soldiers may fail to spawn.

Server owners can adjust this in the config.

---

# Commands

The main command is:

```text
/claysoldiers
```

Aliases:

```text
/csoldiers
```

Permission:

```text
claysoldiers.claysoldiers
```

By default, this permission is available to operators.

---

## Give Soldiers

Gives Clay Soldier Dolls to a player.

```text
/claysoldiers give <team> [amount] [player]
/claysoldiers give <team> [role] [amount] [player]
```

Examples:

```text
/claysoldiers give red
/claysoldiers give blue 16
/claysoldiers give green guard 32 Steve
/claysoldiers give pumpkin slinger 8
```

---

## Spawn Soldiers

Spawns active soldiers directly into the world.

```text
/claysoldiers spawn <team> [amount] [role]
```

Examples:

```text
/claysoldiers spawn red
/claysoldiers spawn blue 16
/claysoldiers spawn green 12 spearman
/claysoldiers spawn black skirmisher 8
```

This command must be run by a player.

---

## Clear Soldiers

Removes nearby Clay Soldiers.

```text
/claysoldiers clear [radius]
```

Examples:

```text
/claysoldiers clear
/claysoldiers clear 50
```

---

## List Teams

Shows all available teams.

```text
/claysoldiers teams
```

---

## List Roles

Shows all available roles.

```text
/claysoldiers roles
```

---

## Count Soldiers

Shows the number of active Clay Soldiers.

```text
/claysoldiers count
```

---

# Suggested Army Compositions

## Basic Balanced Army

```text
8 Guards
8 Spearmen
4 Slingers
4 Skirmishers
```

Guards hold the front.  
Spearmen attack from behind.  
Slingers provide ranged support.  
Skirmishers flank enemies.

---

## Cheap Swarm

```text
Mostly Warriors
A few Fierce or Swift upgrades
```

This is a simple early-game army that uses easy-to-produce soldiers.

---

## Defensive Wall

```text
Guards + Spearmen
```

This army focuses on shield walls and spear support.

---

## Ranged Support Army

```text
Guards in front
Slingers behind
Longshot on Slingers
```

This setup protects ranged units while they attack from behind the front line.

---

## Chaos Army

```text
Explosive Slingers
Venom Skirmishers
Lifesteal Guards
```

This army focuses on splash damage, damage-over-time pressure, and survivability.

---

# Recommended Modifier Pairings

| Role | Recommended Modifiers |
|---|---|
| Guard | Reinforced, Lifesteal |
| Spearman | Longshot, Fierce |
| Skirmisher | Swift, Venom |
| Slinger | Longshot, Explosive |
| Warrior | Fierce, Reinforced |

---

# Arena Tips

Clay Soldiers work best in open or semi-open spaces.

Recommended arena features:

- Flat ground
- Simple obstacles
- Enough room for flanking
- Clear team starting areas
- Limited lava/fire unless intentionally used
- Avoid overly complex terrain

Suggested sizes:

```text
Small battle: 20x20 blocks
Larger battle: 40x40 blocks
```

---

# Configuration

Most gameplay values are configurable, including:

- Crafting recipes
- Spawn limits
- Soldier health
- Soldier damage
- Movement speed
- Attack range
- Role stats
- Modifier effects
- Dodge behavior
- Flanking behavior
- Formation behavior
- Nameplate display
- Drops
- Player damage
- Pathfinding behavior

Server owners should review `config.yml` before running large public battles.

---

# Development Status

ClaySoldiers focuses only on survival-friendly Clay Soldier battles.

---

# License

No license information is included here by default. Add a license section if/when the project chooses one.
