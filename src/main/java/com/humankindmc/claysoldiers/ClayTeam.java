package com.humankindmc.claysoldiers;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;

public enum ClayTeam {
    CLAY("clay", "Clay", "CA383B5D-DDAF-4523-9001-5A5685EF5FBA", Color.fromRGB(0x8E8E86), NamedTextColor.GRAY, Material.CLAY_BALL),
    RED("red", "Red", "77BFA9D5-2E49-4AD3-B6E6-DED0EE1AAD2D", Color.fromRGB(0xA22823), NamedTextColor.RED, Material.RED_DYE),
    YELLOW("yellow", "Yellow", "BFDC0FF6-BBE2-4C54-ADF6-9599E16D157A", Color.fromRGB(0xFCD030), NamedTextColor.YELLOW, Material.YELLOW_DYE),
    GREEN("green", "Green", "2C521F69-846F-4294-95DD-2E9C76C19589", Color.fromRGB(0x56701B), NamedTextColor.GREEN, Material.GREEN_DYE),
    BLUE("blue", "Blue", "E8BB8A2C-3DEA-4193-AAC9-84E052A02A48", Color.fromRGB(0x373CA1), NamedTextColor.BLUE, Material.BLUE_DYE),
    ORANGE("orange", "Orange", "F0A0E637-BB71-44BD-AC73-6886503C6FD6", Color.fromRGB(0xEE7110), NamedTextColor.GOLD, Material.ORANGE_DYE),
    MAGENTA("magenta", "Magenta", "7EB78104-728C-4D36-85A8-98A6B5E2184C", Color.fromRGB(0xC64EBD), NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_DYE),
    LIGHT_BLUE("lightblue", "Light Blue", "025A1385-278D-41B7-981B-087141F99120", Color.fromRGB(0x41B7DE), NamedTextColor.AQUA, Material.LIGHT_BLUE_DYE),
    LIME("lime", "Lime", "E9FE47F6-EA6E-4467-99A7-70E59A60835B", Color.fromRGB(0x77BF1A), NamedTextColor.GREEN, Material.LIME_DYE),
    PINK("pink", "Pink", "7A11B9A2-87A7-45F5-939A-D0C121C32D84", Color.fromRGB(0xEF95B2), NamedTextColor.LIGHT_PURPLE, Material.PINK_DYE),
    CYAN("cyan", "Cyan", "A717B83F-B0EF-4F8F-9829-3CFF6EDF7CEC", Color.fromRGB(0x159095), NamedTextColor.DARK_AQUA, Material.CYAN_DYE),
    PURPLE("purple", "Purple", "6EE60BC8-70C9-4941-9EB8-200A0E7AD867", Color.fromRGB(0x7D2BAD), NamedTextColor.DARK_PURPLE, Material.PURPLE_DYE),
    BROWN("brown", "Brown", "75B2C91A-BE22-4492-8AC7-CFF4767E37F1", Color.fromRGB(0x784C2C), NamedTextColor.GOLD, Material.BROWN_DYE),
    BLACK("black", "Black", "BC6913E6-859F-4714-9E90-28CE4283E9CC", Color.fromRGB(0x19191D), NamedTextColor.DARK_GRAY, Material.BLACK_DYE),
    GRAY("gray", "Gray", "77C678AB-ED0D-4E3D-9C8D-B1F8C9600CD3", Color.fromRGB(0x545B5E), NamedTextColor.GRAY, Material.GRAY_DYE),
    WHITE("white", "White", "7ECD63AF-21A5-42C7-AD32-8988014DA398", Color.fromRGB(0xEAEDED), NamedTextColor.WHITE, Material.WHITE_DYE),
    MELON("melon", "Melon", "400BEDA7-3463-46E9-A01B-16D874ADF728", Color.fromRGB(0x9FCC5C), NamedTextColor.GREEN, Material.MELON_SLICE),
    PUMPKIN("pumpkin", "Pumpkin", "81227ECB-F129-4D2E-80C7-07CEC076B53D", Color.fromRGB(0xD47D20), NamedTextColor.GOLD, Material.PUMPKIN),
    REDSTONE("redstone", "Redstone", "0FF36671-62A0-4C41-9567-16A8071FD4AF", Color.fromRGB(0xC40000), NamedTextColor.RED, Material.REDSTONE),
    COAL("coal", "Coal", "F7D936D6-BFCD-48AA-88E5-1C6B12641943", Color.fromRGB(0x202020), NamedTextColor.DARK_GRAY, Material.COAL),
    CARROT("carrot", "Carrot", "8E7B0ADF-756C-4B25-B352-E9ED21219024", Color.fromRGB(0xEA8C20), NamedTextColor.GOLD, Material.CARROT),
    POTATO("potato", "Potato", "C52FD8FB-AD68-4F8F-8BBD-EA9D399A735C", Color.fromRGB(0xC6A65B), NamedTextColor.YELLOW, Material.POTATO),
    BEETROOT("beetroot", "Beetroot", "F2F99E13-D874-4C23-B454-B3383861E996", Color.fromRGB(0x7E1233), NamedTextColor.DARK_RED, Material.BEETROOT);

    private final String key;
    private final String displayName;
    private final UUID sourceId;
    private final Color armorColor;
    private final NamedTextColor textColor;
    private final Material iconMaterial;

    ClayTeam(String key, String displayName, String sourceId, Color armorColor, NamedTextColor textColor, Material iconMaterial) {
        this.key = key;
        this.displayName = displayName;
        this.sourceId = UUID.fromString(sourceId);
        this.armorColor = armorColor;
        this.textColor = textColor;
        this.iconMaterial = iconMaterial;
    }

    public String key() {
        return this.key;
    }

    public String displayName() {
        return this.displayName;
    }

    public UUID sourceId() {
        return this.sourceId;
    }

    public Color armorColor() {
        return this.armorColor;
    }

    public NamedTextColor textColor() {
        return this.textColor;
    }

    public Material iconMaterial() {
        return this.iconMaterial;
    }

    public static Optional<ClayTeam> fromKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return Arrays.stream(values())
                .filter(team -> team.key.replace("_", "").equals(normalized))
                .findFirst();
    }
}
