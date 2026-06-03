package com.humankindgames.claysoldiers;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum ClaySoldierModifier {
    REINFORCED("reinforced", "Reinforced", Material.IRON_NUGGET, NamedTextColor.GRAY),
    SWIFT("swift", "Swift", Material.SUGAR, NamedTextColor.AQUA),
    FIERCE("fierce", "Fierce", Material.FLINT, NamedTextColor.RED),
    LONGSHOT("longshot", "Longshot", Material.AMETHYST_SHARD, NamedTextColor.YELLOW),
    EXPLOSIVE("explosive", "Explosive", Material.FIRE_CHARGE, NamedTextColor.GOLD),
    VENOM("venom", "Venom", Material.SPIDER_EYE, NamedTextColor.DARK_GREEN),
    LIFESTEAL("lifesteal", "Lifesteal", Material.GHAST_TEAR, NamedTextColor.LIGHT_PURPLE);

    private final String key;
    private final String displayName;
    private final Material defaultIngredient;
    private final NamedTextColor textColor;

    ClaySoldierModifier(String key, String displayName, Material defaultIngredient, NamedTextColor textColor) {
        this.key = key;
        this.displayName = displayName;
        this.defaultIngredient = defaultIngredient;
        this.textColor = textColor;
    }

    public String key() {
        return this.key;
    }

    public String displayName() {
        return this.displayName;
    }

    public Material defaultIngredient() {
        return this.defaultIngredient;
    }

    public NamedTextColor textColor() {
        return this.textColor;
    }

    public static Optional<ClaySoldierModifier> fromKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return Arrays.stream(values())
                .filter(modifier -> modifier.key.replace("_", "").equals(normalized))
                .findFirst();
    }
}
