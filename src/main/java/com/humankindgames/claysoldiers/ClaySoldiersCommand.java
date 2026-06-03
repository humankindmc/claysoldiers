package com.humankindgames.claysoldiers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private static final String PERMISSION = "humankindgames.claysoldiers";
    private static final String SPAWN_FAILURE_MESSAGE = "Clay soldier spawn failed. Check the server console for the stack trace.";

    private final ClaySoldierItems items;
    private final ClaySoldierService soldiers;

    public ClaySoldiersCommand(ClaySoldierItems items, ClaySoldierService soldiers) {
        this.items = items;
        this.soldiers = soldiers;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if( !sender.hasPermission(PERMISSION) ) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
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
            case "count" -> sender.sendMessage(Component.text("Active clay soldiers: " + this.soldiers.activeCount(), NamedTextColor.YELLOW));
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
            sender.sendMessage(Component.text("Usage: /claysoldiers give <team> [amount] [player]", NamedTextColor.RED));
            return;
        }

        Optional<ClayTeam> team = ClayTeam.fromKey(args[1]);
        if( team.isEmpty() ) {
            sender.sendMessage(Component.text("Unknown clay soldier team: " + args[1], NamedTextColor.RED));
            return;
        }

        ParsedOptions options = parseOptions(sender, args, 2, 16);
        if( options.error() != null ) {
            sender.sendMessage(Component.text(options.error(), NamedTextColor.RED));
            return;
        }

        int amount = options.amount();
        Player target = options.target();
        if( target == null ) {
            sender.sendMessage(Component.text("Specify a target player when running from console.", NamedTextColor.RED));
            return;
        }

        int remaining = amount;
        while( remaining > 0 ) {
            int stackAmount = Math.min(this.items.dollStackSize(), remaining);
            ItemStack doll = this.items.createSoldierDoll(team.get(), options.role(), stackAmount);
            target.getInventory().addItem(doll).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            remaining -= stackAmount;
        }

        sender.sendMessage(Component.text("Gave " + amount + " " + team.get().displayName() + " " + options.role().displayName()
                + " doll(s) to " + target.getName() + ".", NamedTextColor.GREEN));
    }

    private void spawn(CommandSender sender, String[] args) {
        if( !(sender instanceof Player player) ) {
            sender.sendMessage(Component.text("Only players can use /claysoldiers spawn.", NamedTextColor.RED));
            return;
        }

        if( args.length < 2 ) {
            sender.sendMessage(Component.text("Usage: /claysoldiers spawn <team> [amount]", NamedTextColor.RED));
            return;
        }

        Optional<ClayTeam> team = ClayTeam.fromKey(args[1]);
        if( team.isEmpty() ) {
            sender.sendMessage(Component.text("Unknown clay soldier team: " + args[1], NamedTextColor.RED));
            return;
        }

        ParsedOptions options = parseOptions(sender, args, 2, 1);
        if( options.error() != null ) {
            sender.sendMessage(Component.text(options.error(), NamedTextColor.RED));
            return;
        }

        int amount = options.amount();
        Location location = player.getLocation().clone().add(horizontalDirection(player).multiply(1.8D));
        location.setY(player.getLocation().getY());

        try {
            this.soldiers.spawnSoldiers(team.get(), options.role(), location, amount);
        } catch( RuntimeException ex ) {
            sender.sendMessage(Component.text(SPAWN_FAILURE_MESSAGE, NamedTextColor.RED));
            this.soldiers.plugin().getLogger().log(Level.SEVERE, "Failed to spawn clay soldier from command", ex);
            return;
        }

        sender.sendMessage(Component.text("Spawned " + amount + " " + team.get().displayName() + " " + options.role().displayName()
                + " soldier(s).", NamedTextColor.GREEN));
    }

    private void clear(CommandSender sender, String[] args) {
        if( !(sender instanceof Player player) ) {
            sender.sendMessage(Component.text("Only players can clear nearby clay soldiers.", NamedTextColor.RED));
            return;
        }

        int radius = parseInt(args, 1, 32, 1, 512);
        int removed = this.soldiers.clearSoldiers(player.getLocation(), radius);
        sender.sendMessage(Component.text("Removed " + removed + " clay soldier(s) within " + radius + " blocks.", NamedTextColor.YELLOW));
    }

    private void teams(CommandSender sender) {
        sender.sendMessage(Component.text("Clay soldier teams:", NamedTextColor.YELLOW));
        for( ClayTeam team : ClayTeam.values() ) {
            sender.sendMessage(Component.text(" - " + team.key() + " (" + team.displayName() + ")", team.textColor()));
        }
    }

    private void roles(CommandSender sender) {
        sender.sendMessage(Component.text("Clay soldier roles:", NamedTextColor.YELLOW));
        for( ClaySoldierRole role : ClaySoldierRole.values() ) {
            sender.sendMessage(Component.text(" - " + role.key() + " (" + role.displayName() + "): " + role.description(), role.textColor()));
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

            return new ParsedOptions(amount, role, target, "Unknown option: " + value);
        }

        return new ParsedOptions(amount, role, target, null);
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
        sender.sendMessage(Component.text("HumankindGames Clay Soldiers", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " give <team> [amount] [player]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " give <team> [role] [amount] [player]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " spawn <team> [amount] [role]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " clear [radius]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " teams", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " roles", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " count", NamedTextColor.GRAY));
    }

    private record ParsedOptions(int amount, ClaySoldierRole role, Player target, String error) {
    }
}
