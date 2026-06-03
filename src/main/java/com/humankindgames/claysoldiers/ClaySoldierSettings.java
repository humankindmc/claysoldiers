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
        boolean useNameplates,
        boolean nameplateShowTeam,
        boolean nameplateShowRole,
        boolean nameplateShowHealthText,
        int nameplateHealthBarSegments,
        double nameplateHealthyThreshold,
        double nameplateLowThreshold,
        boolean useFormations,
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
                config.getBoolean(base + "nameplates.enabled", true),
                config.getBoolean(base + "nameplates.show-team", true),
                config.getBoolean(base + "nameplates.show-role", true),
                config.getBoolean(base + "nameplates.show-health-text", true),
                clamp(config.getInt(base + "nameplates.health-bar-segments", 10), 1, 40),
                config.getDouble(base + "nameplates.healthy-threshold", 0.60D),
                config.getDouble(base + "nameplates.low-threshold", 0.30D),
                config.getBoolean(base + "formations.enabled", true),
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
                Map.copyOf(attacks)
        );
    }

    public RoleTuning role(ClaySoldierRole role) {
        return this.roles.getOrDefault(role, RoleTuning.fromRole(role));
    }

    public AttackTuning attack(String key) {
        return this.attacks.getOrDefault(key.toLowerCase(Locale.ROOT), defaultAttacks().getOrDefault(key, AttackTuning.quick()));
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
                    parseMaterial(config.getString(path + "main-hand"), defaults.mainHand),
                    parseMaterial(config.getString(path + "off-hand"), defaults.offHand)
            );
        }

        private static RoleTuning fromRole(ClaySoldierRole role) {
            return new RoleTuning(true, role.healthMultiplier(), role.damageMultiplier(), role.speedMultiplier(), role.rangeMultiplier(),
                    role.dodgeChance(), role.flankChance(), role.formationDepth(), role.mainHand(), role.offHand());
        }

        private static Material parseMaterial(String configured, Material fallback) {
            if( configured == null || configured.isBlank() || configured.equalsIgnoreCase("none") ) {
                return configured != null && configured.equalsIgnoreCase("none") ? null : fallback;
            }

            Material material = Material.matchMaterial(configured);
            return material == null ? fallback : material;
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
