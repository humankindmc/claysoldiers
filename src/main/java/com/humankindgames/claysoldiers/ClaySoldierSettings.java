package com.humankindgames.claysoldiers;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record ClaySoldierSettings(
        double maxHealth,
        double attackDamage,
        double followRange,
        double moveStep,
        double attackRange,
        int attackCooldownTicks,
        int tickPeriodTicks,
        int dollStackSize,
        int maxSpawnPerUse,
        boolean dropDollOnDeath,
        boolean spawnLimitEnabled,
        int spawnLimitMaxSoldiers,
        double spawnLimitRadius,
        boolean useNameplates,
        boolean nameplateShowTeam,
        boolean nameplateShowRole,
        boolean nameplateShowHealthText,
        String nameplateFormationIndicator,
        int nameplateHealthBarSegments,
        double nameplateHealthyThreshold,
        double nameplateLowThreshold,
        String nameplateBarPrefix,
        String nameplateBarSuffix,
        String nameplateBarFilledSymbol,
        String nameplateBarLostSymbol,
        String nameplateBarEmptySymbol,
        String nameplateBarHealthyColor,
        String nameplateBarInjuredColor,
        String nameplateBarLowColor,
        String nameplateBarLostColor,
        String nameplateBarBracketColor,
        boolean craftingEnabled,
        boolean teamRecoloringEnabled,
        Map<ClaySoldierRole, Material> roleUpgradeIngredients,
        boolean useFormations,
        boolean useTacticalFormations,
        boolean formationSoundEnabled,
        int tacticalFormationMinSoldiers,
        double tacticalFormationScanRange,
        double tacticalFormationSpeedMultiplier,
        double shieldWallSpeedMultiplier,
        double tacticalFormationDamageMultiplier,
        double specialFormationDamageMultiplier,
        double shieldWallDistance,
        double shieldWallLateralSpacing,
        double shieldWallRowSpacing,
        double shieldWallHoldRadius,
        double spearWallDistance,
        double supportBehindWallDistance,
        double flankProtectionWidth,
        double combinedWallChance,
        int formationColumns,
        double formationLateralSpacing,
        double formationRowSpacing,
        double formationBaseDistanceMultiplier,
        double formationScanRangeMultiplier,
        double skirmisherFlankWidth,
        double skirmisherFlankColumnSpacing,
        double skirmisherFlankRowSpacing,
        boolean useFlanking,
        double flankChanceScale,
        int flankDurationMinTicks,
        int flankDurationMaxTicks,
        double flankSideOffset,
        double flankSideJitter,
        double flankBackOffset,
        boolean useDodging,
        int dodgeCooldownMinTicks,
        int dodgeCooldownMaxTicks,
        double dodgeSideDistanceMin,
        double dodgeSideDistanceMax,
        double dodgeBackDistance,
        boolean useJumping,
        double idleJumpChance,
        double chaseJumpChance,
        double attackLeapChance,
        double skirmisherLeapChance,
        double jumpForwardDistance,
        double jumpHeight,
        double jumpVelocityY,
        int normalWanderRoll,
        int skirmisherWanderRoll,
        double wanderStepMultiplier,
        double closeCombatStepMultiplier,
        boolean pathfindingEnabled,
        double pathfindingMaxStepHeight,
        double pathfindingSideStepMultiplier,
        double pathfindingBackStepMultiplier,
        double targetSearchExtraRange,
        int sweepMinEnemies,
        double sweepClusterRadius,
        double sweepChance,
        double guardShieldBashChance,
        double warriorLeapChance,
        double leapMinDistance,
        double shieldBashPushDistance,
        double sweepSplashDamageMultiplier,
        double playerDamage,
        boolean creativePlayerInstantKill,
        double fireDamage,
        double lavaDamage,
        double hotFloorDamage,
        boolean useSounds,
        float soundVolume,
        float soundPitchJitter,
        boolean useParticles,
        double particleScale,
        boolean useAnimations,
        double animationSpeed,
        double runArmSwing,
        double runLegSwing,
        double runBodySway,
        Map<ClaySoldierRole, RoleTuning> roles,
        Map<ClaySoldierModifier, ModifierTuning> modifiers,
        Map<String, AttackTuning> attacks
) {
    public static ClaySoldierSettings from(FileConfiguration config) {
        String base = "clay-soldiers.";
        Map<ClaySoldierRole, RoleTuning> roles = new HashMap<>();
        for( ClaySoldierRole role : ClaySoldierRole.values() ) {
            roles.put(role, RoleTuning.from(config, base + "roles." + role.key() + ".", role));
        }

        Map<String, AttackTuning> attacks = new HashMap<>();
        defaultAttacks().forEach((key, tuning) -> attacks.put(key, AttackTuning.from(config, base + "attacks." + key + ".", tuning)));

        Map<ClaySoldierModifier, ModifierTuning> modifiers = new HashMap<>();
        for( ClaySoldierModifier modifier : ClaySoldierModifier.values() ) {
            modifiers.put(modifier, ModifierTuning.from(config, base + "modifiers." + modifier.key() + ".", modifier));
        }

        Map<ClaySoldierRole, Material> roleUpgradeIngredients = new HashMap<>();
        putMaterial(roleUpgradeIngredients, ClaySoldierRole.GUARD, parseMaterial(config.getString(base + "crafting.role-upgrades.guard"), Material.IRON_INGOT));
        putMaterial(roleUpgradeIngredients, ClaySoldierRole.SPEARMAN, parseMaterial(config.getString(base + "crafting.role-upgrades.spearman"), Material.BAMBOO));
        putMaterial(roleUpgradeIngredients, ClaySoldierRole.SKIRMISHER, parseMaterial(config.getString(base + "crafting.role-upgrades.skirmisher"), Material.FEATHER));
        putMaterial(roleUpgradeIngredients, ClaySoldierRole.SLINGER, parseMaterial(config.getString(base + "crafting.role-upgrades.slinger"), Material.SNOWBALL));

        return new ClaySoldierSettings(
                config.getDouble(base + "max-health", 20.0D),
                config.getDouble(base + "attack-damage", 1.0D),
                config.getDouble(base + "follow-range", 16.0D),
                config.getDouble(base + "move-step", 0.30D),
                config.getDouble(base + "attack-range", 0.75D),
                config.getInt(base + "attack-cooldown-ticks", 16),
                Math.max(1, config.getInt(base + "tick-period-ticks", 4)),
                clamp(config.getInt(base + "doll-stack-size", 16), 1, 64),
                clamp(config.getInt(base + "max-spawn-per-use", 64), 1, 256),
                config.getBoolean(base + "drop-doll-on-death", true),
                config.getBoolean(base + "spawn-limits.enabled", true),
                clamp(config.getInt(base + "spawn-limits.max-soldiers", 32), 1, 10_000),
                config.getDouble(base + "spawn-limits.radius", 100.0D),
                config.getBoolean(base + "nameplates.enabled", true),
                config.getBoolean(base + "nameplates.show-team", true),
                config.getBoolean(base + "nameplates.show-role", true),
                config.getBoolean(base + "nameplates.show-health-text", true),
                config.getString(base + "nameplates.formation-indicator", "[F]"),
                clamp(config.getInt(base + "nameplates.health-bar-segments", 10), 1, 40),
                config.getDouble(base + "nameplates.healthy-threshold", 0.60D),
                config.getDouble(base + "nameplates.low-threshold", 0.30D),
                config.getString(base + "nameplates.health-bar.prefix", "["),
                config.getString(base + "nameplates.health-bar.suffix", "]"),
                config.getString(base + "nameplates.health-bar.filled-symbol", "|"),
                config.getString(base + "nameplates.health-bar.lost-symbol", "|"),
                config.getString(base + "nameplates.health-bar.empty-symbol", "-"),
                config.getString(base + "nameplates.health-bar.healthy-color", "green"),
                config.getString(base + "nameplates.health-bar.injured-color", "yellow"),
                config.getString(base + "nameplates.health-bar.low-color", "red"),
                config.getString(base + "nameplates.health-bar.lost-color", "red"),
                config.getString(base + "nameplates.health-bar.bracket-color", "dark_gray"),
                config.getBoolean(base + "crafting.enabled", true),
                config.getBoolean(base + "crafting.team-recoloring-enabled", true),
                Map.copyOf(roleUpgradeIngredients),
                config.getBoolean(base + "formations.enabled", true),
                config.getBoolean(base + "formations.tactical-enabled", true),
                config.getBoolean(base + "formations.sound-enabled", true),
                clamp(config.getInt(base + "formations.tactical-min-soldiers", 4), 2, 256),
                config.getDouble(base + "formations.tactical-scan-range", 8.0D),
                config.getDouble(base + "formations.tactical-speed-multiplier", 0.82D),
                config.getDouble(base + "formations.shield-wall-speed-multiplier", 0.45D),
                config.getDouble(base + "formations.tactical-damage-multiplier", 1.14D),
                config.getDouble(base + "formations.special-attack-damage-multiplier", 1.35D),
                config.getDouble(base + "formations.shield-wall-distance", 0.70D),
                config.getDouble(base + "formations.shield-wall-lateral-spacing", 0.34D),
                config.getDouble(base + "formations.shield-wall-row-spacing", 0.08D),
                config.getDouble(base + "formations.shield-wall-hold-radius", 0.55D),
                config.getDouble(base + "formations.spear-wall-distance", 1.05D),
                config.getDouble(base + "formations.support-behind-wall-distance", 1.65D),
                config.getDouble(base + "formations.flank-protection-width", 1.85D),
                config.getDouble(base + "formations.combined-wall-chance", 0.65D),
                clamp(config.getInt(base + "formations.columns", 5), 1, 15),
                config.getDouble(base + "formations.lateral-spacing", 0.50D),
                config.getDouble(base + "formations.row-spacing", 0.34D),
                config.getDouble(base + "formations.base-distance-multiplier", 0.82D),
                config.getDouble(base + "formations.scan-range-multiplier", 1.0D),
                config.getDouble(base + "formations.skirmisher-flank-width", 1.25D),
                config.getDouble(base + "formations.skirmisher-flank-column-spacing", 0.25D),
                config.getDouble(base + "formations.skirmisher-flank-row-spacing", 0.20D),
                config.getBoolean(base + "flanking.enabled", true),
                config.getDouble(base + "flanking.chance-scale", 0.16D),
                clamp(config.getInt(base + "flanking.duration-min-ticks", 24), 1, 20_000),
                clamp(config.getInt(base + "flanking.duration-max-ticks", 60), 1, 20_000),
                config.getDouble(base + "flanking.side-offset", 1.35D),
                config.getDouble(base + "flanking.side-jitter", 0.35D),
                config.getDouble(base + "flanking.back-offset", 0.38D),
                config.getBoolean(base + "dodging.enabled", true),
                clamp(config.getInt(base + "dodging.cooldown-min-ticks", 18), 0, 20_000),
                clamp(config.getInt(base + "dodging.cooldown-max-ticks", 34), 0, 20_000),
                config.getDouble(base + "dodging.side-distance-min", 0.56D),
                config.getDouble(base + "dodging.side-distance-max", 0.86D),
                config.getDouble(base + "dodging.back-distance", 0.24D),
                config.getBoolean(base + "jumping.enabled", true),
                config.getDouble(base + "jumping.idle-jump-chance", 0.05D),
                config.getDouble(base + "jumping.chase-jump-chance", 0.09D),
                config.getDouble(base + "jumping.attack-leap-chance", 0.32D),
                config.getDouble(base + "jumping.skirmisher-leap-chance", 0.56D),
                config.getDouble(base + "jumping.forward-distance", 0.52D),
                config.getDouble(base + "jumping.height", 0.28D),
                config.getDouble(base + "jumping.velocity-y", 0.12D),
                clamp(config.getInt(base + "movement.normal-wander-roll", 4), 1, 1000),
                clamp(config.getInt(base + "movement.skirmisher-wander-roll", 2), 1, 1000),
                config.getDouble(base + "movement.wander-step-multiplier", 0.65D),
                config.getDouble(base + "movement.close-combat-step-multiplier", 0.78D),
                config.getBoolean(base + "movement.pathfinding-enabled", true),
                config.getDouble(base + "movement.pathfinding-max-step-height", 1.0D),
                config.getDouble(base + "movement.pathfinding-side-step-multiplier", 0.90D),
                config.getDouble(base + "movement.pathfinding-back-step-multiplier", 0.60D),
                config.getDouble(base + "target-search-extra-range", 3.0D),
                clamp(config.getInt(base + "combat.sweep-min-enemies", 2), 1, 100),
                config.getDouble(base + "combat.sweep-cluster-radius", 1.15D),
                config.getDouble(base + "combat.sweep-chance", 0.35D),
                config.getDouble(base + "combat.guard-shield-bash-chance", 0.48D),
                config.getDouble(base + "combat.warrior-leap-chance", 0.28D),
                config.getDouble(base + "combat.leap-min-distance", 0.45D),
                config.getDouble(base + "combat.shield-bash-push-distance", 0.42D),
                config.getDouble(base + "combat.sweep-splash-damage-multiplier", 0.65D),
                config.getDouble(base + "combat.player-damage", 4.0D),
                config.getBoolean(base + "combat.creative-player-instant-kill", true),
                config.getDouble(base + "combat.fire-damage", 6.0D),
                config.getDouble(base + "combat.lava-damage", 12.0D),
                config.getDouble(base + "combat.hot-floor-damage", 4.0D),
                config.getBoolean(base + "sounds.enabled", true),
                (float) config.getDouble(base + "sounds.volume", 1.0D),
                (float) config.getDouble(base + "sounds.pitch-jitter", 0.25D),
                config.getBoolean(base + "particles.enabled", true),
                config.getDouble(base + "particles.scale", 1.0D),
                config.getBoolean(base + "animations.enabled", true),
                config.getDouble(base + "animations.speed", 0.34D),
                config.getDouble(base + "animations.run-arm-swing", 0.65D),
                config.getDouble(base + "animations.run-leg-swing", 0.45D),
                config.getDouble(base + "animations.run-body-sway", 0.06D),
                Map.copyOf(roles),
                Map.copyOf(modifiers),
                Map.copyOf(attacks)
        );
    }

    public RoleTuning role(ClaySoldierRole role) {
        return this.roles.getOrDefault(role, RoleTuning.fromRole(role));
    }

    public AttackTuning attack(String key) {
        return this.attacks.getOrDefault(key.toLowerCase(Locale.ROOT), defaultAttacks().getOrDefault(key, AttackTuning.quick()));
    }

    public ModifierTuning modifier(ClaySoldierModifier modifier) {
        return this.modifiers.getOrDefault(modifier, ModifierTuning.fromModifier(modifier));
    }

    public Material roleUpgradeIngredient(ClaySoldierRole role) {
        return this.roleUpgradeIngredients.get(role);
    }

    public int flankDurationRange() {
        return Math.max(0, this.flankDurationMaxTicks - this.flankDurationMinTicks);
    }

    public int dodgeCooldownRange() {
        return Math.max(0, this.dodgeCooldownMaxTicks - this.dodgeCooldownMinTicks);
    }

    private static Map<String, AttackTuning> defaultAttacks() {
        return Map.of(
                "quick", new AttackTuning(true, 5, 0, 1.00D, 0.00D),
                "poke", new AttackTuning(true, 7, 5, 0.92D, 0.25D),
                "leap", new AttackTuning(true, 5, 9, 1.25D, 0.15D),
                "sweep", new AttackTuning(true, 8, 12, 0.88D, 0.10D),
                "shield-bash", new AttackTuning(true, 7, 16, 0.78D, 0.05D),
                "sling", new AttackTuning(true, 10, 14, 1.00D, 0.00D)
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Material parseMaterial(String configured, Material fallback) {
        if( configured == null || configured.isBlank() || configured.equalsIgnoreCase("none") ) {
            return configured != null && configured.equalsIgnoreCase("none") ? null : fallback;
        }

        Material material = Material.matchMaterial(configured);
        return material == null ? fallback : material;
    }

    private static void putMaterial(Map<ClaySoldierRole, Material> materials, ClaySoldierRole role, Material material) {
        if( material != null ) {
            materials.put(role, material);
        }
    }

    public record RoleTuning(
            boolean enabled,
            double healthMultiplier,
            double damageMultiplier,
            double speedMultiplier,
            double rangeMultiplier,
            double dodgeChance,
            double flankChance,
            double formationDepth,
            Material mainHand,
            Material offHand
    ) {
        private static RoleTuning from(FileConfiguration config, String path, ClaySoldierRole role) {
            RoleTuning defaults = fromRole(role);
            ConfigurationSection section = config.getConfigurationSection(path.substring(0, path.length() - 1));
            if( section == null ) {
                return defaults;
            }

            return new RoleTuning(
                    config.getBoolean(path + "enabled", defaults.enabled),
                    config.getDouble(path + "health-multiplier", defaults.healthMultiplier),
                    config.getDouble(path + "damage-multiplier", defaults.damageMultiplier),
                    config.getDouble(path + "speed-multiplier", defaults.speedMultiplier),
                    config.getDouble(path + "range-multiplier", defaults.rangeMultiplier),
                    config.getDouble(path + "dodge-chance", defaults.dodgeChance),
                    config.getDouble(path + "flank-chance", defaults.flankChance),
                    config.getDouble(path + "formation-depth", defaults.formationDepth),
                    ClaySoldierSettings.parseMaterial(config.getString(path + "main-hand"), defaults.mainHand),
                    ClaySoldierSettings.parseMaterial(config.getString(path + "off-hand"), defaults.offHand)
            );
        }

        private static RoleTuning fromRole(ClaySoldierRole role) {
            return new RoleTuning(true, role.healthMultiplier(), role.damageMultiplier(), role.speedMultiplier(), role.rangeMultiplier(),
                    role.dodgeChance(), role.flankChance(), role.formationDepth(), role.mainHand(), role.offHand());
        }

    }

    public record ModifierTuning(
            boolean enabled,
            Material ingredient,
            double healthMultiplierBonus,
            double damageMultiplierBonus,
            double speedMultiplierBonus,
            double rangeMultiplierBonus,
            double cooldownMultiplier,
            double splashRadius,
            double splashDamageMultiplier,
            double venomDamage,
            double lifestealAmount
    ) {
        private static ModifierTuning from(FileConfiguration config, String path, ClaySoldierModifier modifier) {
            ModifierTuning defaults = fromModifier(modifier);
            ConfigurationSection section = config.getConfigurationSection(path.substring(0, path.length() - 1));
            if( section == null ) {
                return defaults;
            }

            return new ModifierTuning(
                    config.getBoolean(path + "enabled", defaults.enabled),
                    ClaySoldierSettings.parseMaterial(config.getString(path + "ingredient"), defaults.ingredient),
                    config.getDouble(path + "health-multiplier-bonus", defaults.healthMultiplierBonus),
                    config.getDouble(path + "damage-multiplier-bonus", defaults.damageMultiplierBonus),
                    config.getDouble(path + "speed-multiplier-bonus", defaults.speedMultiplierBonus),
                    config.getDouble(path + "range-multiplier-bonus", defaults.rangeMultiplierBonus),
                    config.getDouble(path + "cooldown-multiplier", defaults.cooldownMultiplier),
                    config.getDouble(path + "splash-radius", defaults.splashRadius),
                    config.getDouble(path + "splash-damage-multiplier", defaults.splashDamageMultiplier),
                    config.getDouble(path + "venom-damage", defaults.venomDamage),
                    config.getDouble(path + "lifesteal-amount", defaults.lifestealAmount)
            );
        }

        private static ModifierTuning fromModifier(ClaySoldierModifier modifier) {
            return switch( modifier ) {
                case REINFORCED -> new ModifierTuning(true, modifier.defaultIngredient(), 0.25D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D, 0.0D, 0.0D);
                case SWIFT -> new ModifierTuning(true, modifier.defaultIngredient(), 0.0D, 0.0D, 0.25D, 0.0D, 0.86D, 0.0D, 0.0D, 0.0D, 0.0D);
                case FIERCE -> new ModifierTuning(true, modifier.defaultIngredient(), 0.0D, 0.25D, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D, 0.0D, 0.0D);
                case LONGSHOT -> new ModifierTuning(true, modifier.defaultIngredient(), 0.0D, 0.0D, 0.0D, 0.35D, 1.0D, 0.0D, 0.0D, 0.0D, 0.0D);
                case EXPLOSIVE -> new ModifierTuning(true, modifier.defaultIngredient(), 0.0D, 0.0D, 0.0D, 0.0D, 1.12D, 1.15D, 0.35D, 0.0D, 0.0D);
                case VENOM -> new ModifierTuning(true, modifier.defaultIngredient(), 0.0D, 0.12D, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D, 0.35D, 0.0D);
                case LIFESTEAL -> new ModifierTuning(true, modifier.defaultIngredient(), 0.0D, 0.0D, 0.0D, 0.0D, 1.08D, 0.0D, 0.0D, 0.0D, 0.65D);
            };
        }
    }

    public record AttackTuning(
            boolean enabled,
            int windupTicks,
            int extraCooldownTicks,
            double damageMultiplier,
            double rangeBonus
    ) {
        private static AttackTuning from(FileConfiguration config, String path, AttackTuning defaults) {
            return new AttackTuning(
                    config.getBoolean(path + "enabled", defaults.enabled),
                    clamp(config.getInt(path + "windup-ticks", defaults.windupTicks), 0, 20_000),
                    clamp(config.getInt(path + "extra-cooldown-ticks", defaults.extraCooldownTicks), 0, 20_000),
                    config.getDouble(path + "damage-multiplier", defaults.damageMultiplier),
                    config.getDouble(path + "range-bonus", defaults.rangeBonus)
            );
        }

        private static AttackTuning quick() {
            return new AttackTuning(true, 5, 0, 1.0D, 0.0D);
        }
    }
}
