package com.humankindgames.claysoldiers;

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
        boolean dropDollOnDeath
) {
    public static ClaySoldierSettings from(FileConfiguration config) {
        String base = "clay-soldiers.";
        return new ClaySoldierSettings(
                config.getDouble(base + "max-health", 20.0D),
                config.getDouble(base + "attack-damage", 1.0D),
                config.getDouble(base + "follow-range", 16.0D),
                config.getDouble(base + "move-step", 0.22D),
                config.getDouble(base + "attack-range", 0.75D),
                config.getInt(base + "attack-cooldown-ticks", 20),
                Math.max(1, config.getInt(base + "tick-period-ticks", 5)),
                clamp(config.getInt(base + "doll-stack-size", 16), 1, 64),
                clamp(config.getInt(base + "max-spawn-per-use", 64), 1, 256),
                config.getBoolean(base + "drop-doll-on-death", true)
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
