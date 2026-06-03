package com.humankindgames.claysoldiers;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum ClaySoldierRole {
    WARRIOR("warrior", "Warrior", "Balanced front-line fighter", Material.STICK, null, NamedTextColor.YELLOW,
            1.00D, 1.00D, 1.00D, 1.00D, 0.12D, 0.22D, 0.00D),
    GUARD("guard", "Guard", "Slow shield unit that holds the front", Material.STICK, Material.SHIELD, NamedTextColor.BLUE,
            1.40D, 0.82D, 0.72D, 1.18D, 0.05D, 0.08D, -0.20D),
    SPEARMAN("spearman", "Spearman", "Long reach unit that fights from the second line", Material.BAMBOO, null, NamedTextColor.GREEN,
            0.90D, 0.88D, 0.86D, 1.55D, 0.10D, 0.18D, 0.45D),
    SKIRMISHER("skirmisher", "Skirmisher", "Fast flanker with high dodge chance", Material.FEATHER, null, NamedTextColor.AQUA,
            0.72D, 0.74D, 1.38D, 0.88D, 0.42D, 0.62D, 0.15D),
    SLINGER("slinger", "Slinger", "Ranged support that throws clay pellets", Material.SNOWBALL, null, NamedTextColor.WHITE,
            0.80D, 0.62D, 1.05D, 5.50D, 0.22D, 0.35D, 1.20D);

    private final String key;
    private final String displayName;
    private final String description;
    private final Material mainHand;
    private final Material offHand;
    private final NamedTextColor textColor;
    private final double healthMultiplier;
    private final double damageMultiplier;
    private final double speedMultiplier;
    private final double rangeMultiplier;
    private final double dodgeChance;
    private final double flankChance;
    private final double formationDepth;

    ClaySoldierRole(String key, String displayName, String description, Material mainHand, Material offHand, NamedTextColor textColor,
                    double healthMultiplier, double damageMultiplier, double speedMultiplier, double rangeMultiplier,
                    double dodgeChance, double flankChance, double formationDepth) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.mainHand = mainHand;
        this.offHand = offHand;
        this.textColor = textColor;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.rangeMultiplier = rangeMultiplier;
        this.dodgeChance = dodgeChance;
        this.flankChance = flankChance;
        this.formationDepth = formationDepth;
    }

    public String key() {
        return this.key;
    }

    public String displayName() {
        return this.displayName;
    }

    public String description() {
        return this.description;
    }

    public Material mainHand() {
        return this.mainHand;
    }

    public Material offHand() {
        return this.offHand;
    }

    public NamedTextColor textColor() {
        return this.textColor;
    }

    public double healthMultiplier() {
        return this.healthMultiplier;
    }

    public double damageMultiplier() {
        return this.damageMultiplier;
    }

    public double speedMultiplier() {
        return this.speedMultiplier;
    }

    public double rangeMultiplier() {
        return this.rangeMultiplier;
    }

    public double dodgeChance() {
        return this.dodgeChance;
    }

    public double flankChance() {
        return this.flankChance;
    }

    public double formationDepth() {
        return this.formationDepth;
    }

    public static Optional<ClaySoldierRole> fromKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return Arrays.stream(values())
                .filter(role -> role.key.replace("_", "").equals(normalized))
                .findFirst();
    }
}
