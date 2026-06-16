package com.humankindmc.claysoldiers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClaySoldiersCommand
        implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "claysoldiers.claysoldiers";

    private final ClaySoldierItems items;
    private final ClaySoldierService soldiers;
    private final ClaySoldierMessages messages;

    public ClaySoldiersCommand(ClaySoldierItems items, ClaySoldierService soldiers, ClaySoldierMessages messages) {
        this.items = items;
        this.soldiers = soldiers;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if( !sender.hasPermission(PERMISSION) ) {
            sender.sendMessage(this.messages.component("commands.no-permission"));
            return true;
        }

        if( args.length == 0 ) {
            sendHelp(sender, label);
            return true;
        }

        switch( args[0].toLowerCase(Locale.ROOT) ) {
            case "give" -> give(sender, args);
            case "spawn" -> spawn(sender, args);
            case "clear" -> clear(sender, args);
            case "teams" -> teams(sender);
            case "roles" -> roles(sender);
            case "count" -> sender.sendMessage(this.messages.component("commands.active-count", Map.of("count", Integer.toString(this.soldiers.activeCount()))));
            default -> sendHelp(sender, label);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if( !sender.hasPermission(PERMISSION) ) {
            return Collections.emptyList();
        }

        if( args.length == 1 ) {
            return complete(args[0], Stream.of("give", "spawn", "clear", "teams", "roles", "count"));
        }

        if( args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("spawn")) ) {
            return complete(args[1], Arrays.stream(ClayTeam.values()).map(ClayTeam::key));
        }

        if( args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("spawn") ) {
            return complete(args[args.length - 1], Stream.concat(
                    Arrays.stream(ClaySoldierRole.values()).map(ClaySoldierRole::key),
                    Bukkit.getOnlinePlayers().stream().map(Player::getName)
            ));
        }

        return Collections.emptyList();
    }

    private void give(CommandSender sender, String[] args) {
        if( args.length < 2 ) {
            sender.sendMessage(this.messages.component("commands.usage-give"));
            return;
        }

        Optional<ClayTeam> team = ClayTeam.fromKey(args[1]);
        if( team.isEmpty() ) {
            sender.sendMessage(this.messages.component("commands.unknown-team", Map.of("team", args[1])));
            return;
        }

        ParsedOptions options = parseOptions(sender, args, 2, 16);
        if( options.error() != null ) {
            sender.sendMessage(this.messages.component(options.error(), options.errorPlaceholders()));
            return;
        }

        int amount = options.amount();
        Player target = options.target();
        if( target == null ) {
            sender.sendMessage(this.messages.component("commands.console-target-required"));
            return;
        }

        int remaining = amount;
        while( remaining > 0 ) {
            int stackAmount = Math.min(this.items.dollStackSize(), remaining);
            ItemStack doll = this.items.createSoldierDoll(team.get(), options.role(), stackAmount);
            target.getInventory().addItem(doll).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            remaining -= stackAmount;
        }

        sender.sendMessage(this.messages.component("commands.give-success", Map.of(
                "amount", Integer.toString(amount),
                "team", team.get().displayName(),
                "role", options.role().displayName(),
                "player", target.getName()
        )));
    }

    private void spawn(CommandSender sender, String[] args) {
        if( !(sender instanceof Player player) ) {
            sender.sendMessage(this.messages.component("commands.console-spawn-only"));
            return;
        }

        if( args.length < 2 ) {
            sender.sendMessage(this.messages.component("commands.usage-spawn"));
            return;
        }

        Optional<ClayTeam> team = ClayTeam.fromKey(args[1]);
        if( team.isEmpty() ) {
            sender.sendMessage(this.messages.component("commands.unknown-team", Map.of("team", args[1])));
            return;
        }

        ParsedOptions options = parseOptions(sender, args, 2, 1);
        if( options.error() != null ) {
            sender.sendMessage(this.messages.component(options.error(), options.errorPlaceholders()));
            return;
        }

        int amount = options.amount();
        Location location = player.getLocation().clone().add(horizontalDirection(player).multiply(1.8D));
        location.setY(player.getLocation().getY());
        if( !this.soldiers.canSpawnSoldiers(location, amount) ) {
            sender.sendMessage(this.messages.component("commands.spawn-limit-reached", Map.of(
                    "limit", Integer.toString(this.soldiers.spawnLimitMaxSoldiers()),
                    "radius", formatNumber(this.soldiers.spawnLimitRadius()),
                    "current", Integer.toString(this.soldiers.nearbySoldierCount(location))
            )));
            return;
        }

        try {
            this.soldiers.spawnSoldiers(team.get(), options.role(), location, amount);
        } catch( RuntimeException ex ) {
            sender.sendMessage(this.messages.component("commands.spawn-failure-player"));
            this.soldiers.plugin().getLogger().log(Level.SEVERE, this.messages.plain("commands.spawn-failure-log-command"), ex);
            return;
        }

        sender.sendMessage(this.messages.component("commands.spawn-success", Map.of(
                "amount", Integer.toString(amount),
                "team", team.get().displayName(),
                "role", options.role().displayName()
        )));
    }

    private void clear(CommandSender sender, String[] args) {
        if( !(sender instanceof Player player) ) {
            sender.sendMessage(this.messages.component("commands.console-clear-only"));
            return;
        }

        int radius = parseInt(args, 1, 32, 1, 512);
        int removed = this.soldiers.clearSoldiers(player.getLocation(), radius);
        sender.sendMessage(this.messages.component("commands.clear-success", Map.of(
                "amount", Integer.toString(removed),
                "radius", Integer.toString(radius)
        )));
    }

    private void teams(CommandSender sender) {
        sender.sendMessage(this.messages.component("commands.teams-header"));
        for( ClayTeam team : ClayTeam.values() ) {
            sender.sendMessage(this.messages.component("commands.team-line", Map.of(
                    "key", team.key(),
                    "team", team.displayName(),
                    "team_color", ClaySoldierMessages.colorCode(team.textColor())
            )));
        }
    }

    private void roles(CommandSender sender) {
        sender.sendMessage(this.messages.component("commands.roles-header"));
        for( ClaySoldierRole role : ClaySoldierRole.values() ) {
            sender.sendMessage(this.messages.component("commands.role-line", Map.of(
                    "key", role.key(),
                    "role", role.displayName(),
                    "role_color", ClaySoldierMessages.colorCode(role.textColor()),
                    "description", role.description()
            )));
        }
    }

    private Player resolveTarget(CommandSender sender, String[] args, int index) {
        if( args.length > index ) {
            return Bukkit.getPlayerExact(args[index]);
        }

        return sender instanceof Player player ? player : null;
    }

    private int parseInt(String[] args, int index, int fallback, int min, int max) {
        if( args.length <= index ) {
            return fallback;
        }

        try {
            return Math.max(min, Math.min(max, Integer.parseInt(args[index])));
        } catch( NumberFormatException ignored ) {
            return fallback;
        }
    }

    private ParsedOptions parseOptions(CommandSender sender, String[] args, int startIndex, int fallbackAmount) {
        int amount = fallbackAmount;
        ClaySoldierRole role = ClaySoldierRole.WARRIOR;
        Player target = sender instanceof Player player ? player : null;

        for( int i = startIndex; i < args.length; i++ ) {
            String value = args[i];

            try {
                amount = Math.max(1, Math.min(256, Integer.parseInt(value)));
                continue;
            } catch( NumberFormatException ignored ) {
                // Not an amount; try role and player below.
            }

            Optional<ClaySoldierRole> parsedRole = ClaySoldierRole.fromKey(value);
            if( parsedRole.isPresent() ) {
                role = parsedRole.get();
                continue;
            }

            Player parsedTarget = Bukkit.getPlayerExact(value);
            if( parsedTarget != null ) {
                target = parsedTarget;
                continue;
            }

            return new ParsedOptions(amount, role, target, "commands.unknown-option", Map.of("option", value));
        }

        return new ParsedOptions(amount, role, target, null, Map.of());
    }

    private List<String> complete(String input, Stream<String> options) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return options.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private Vector horizontalDirection(Player player) {
        Vector direction = player.getLocation().getDirection();
        direction.setY(0.0D);
        if( direction.lengthSquared() < 0.0001D ) {
            direction = player.getFacing().getDirection();
            direction.setY(0.0D);
        }

        return direction.normalize();
    }

    private void sendHelp(CommandSender sender, String label) {
        Map<String, String> placeholders = Map.of("label", label);
        sender.sendMessage(this.messages.component("commands.help-header", placeholders));
        sender.sendMessage(this.messages.component("commands.help-give", placeholders));
        sender.sendMessage(this.messages.component("commands.help-give-role", placeholders));
        sender.sendMessage(this.messages.component("commands.help-spawn", placeholders));
        sender.sendMessage(this.messages.component("commands.help-clear", placeholders));
        sender.sendMessage(this.messages.component("commands.help-teams", placeholders));
        sender.sendMessage(this.messages.component("commands.help-roles", placeholders));
        sender.sendMessage(this.messages.component("commands.help-count", placeholders));
    }

    private String formatNumber(double value) {
        return Math.rint(value) == value ? Long.toString(Math.round(value)) : Double.toString(value);
    }

    private record ParsedOptions(int amount, ClaySoldierRole role, Player target, String error, Map<String, String> errorPlaceholders) {
    }
}
