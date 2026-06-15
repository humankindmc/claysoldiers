package com.claysoldiers.claysoldiers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaySoldierMessages {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ClaySoldierMessages(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(this.plugin.getDataFolder(), "messages.yml");
        if( !file.exists() ) {
            this.plugin.saveResource("messages.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public Component component(String path) {
        return component(path, Map.of());
    }

    public Component component(String path, Map<String, String> placeholders) {
        return LEGACY.deserialize(applyPlaceholders(this.config.getString(path, path), placeholders))
                .decoration(TextDecoration.ITALIC, false);
    }

    public String plain(String path) {
        return plain(path, Map.of());
    }

    public String plain(String path, Map<String, String> placeholders) {
        return applyPlaceholders(this.config.getString(path, path), placeholders);
    }

    public List<Component> componentList(String path, Map<String, String> placeholders) {
        List<String> strings = this.config.getStringList(path);
        List<Component> components = new ArrayList<>();
        for( String line : strings ) {
            components.add(LEGACY.deserialize(applyPlaceholders(line, placeholders))
                    .decoration(TextDecoration.ITALIC, false));
        }

        return components;
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String output = input == null ? "" : input;
        for( Map.Entry<String, String> entry : placeholders.entrySet() ) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return output;
    }

    public static String colorCode(NamedTextColor color) {
        if( color == NamedTextColor.BLACK ) {
            return "&0";
        }
        if( color == NamedTextColor.DARK_BLUE ) {
            return "&1";
        }
        if( color == NamedTextColor.DARK_GREEN ) {
            return "&2";
        }
        if( color == NamedTextColor.DARK_AQUA ) {
            return "&3";
        }
        if( color == NamedTextColor.DARK_RED ) {
            return "&4";
        }
        if( color == NamedTextColor.DARK_PURPLE ) {
            return "&5";
        }
        if( color == NamedTextColor.GOLD ) {
            return "&6";
        }
        if( color == NamedTextColor.GRAY ) {
            return "&7";
        }
        if( color == NamedTextColor.DARK_GRAY ) {
            return "&8";
        }
        if( color == NamedTextColor.BLUE ) {
            return "&9";
        }
        if( color == NamedTextColor.GREEN ) {
            return "&a";
        }
        if( color == NamedTextColor.AQUA ) {
            return "&b";
        }
        if( color == NamedTextColor.RED ) {
            return "&c";
        }
        if( color == NamedTextColor.LIGHT_PURPLE ) {
            return "&d";
        }
        if( color == NamedTextColor.YELLOW ) {
            return "&e";
        }

        return "&f";
    }
}
