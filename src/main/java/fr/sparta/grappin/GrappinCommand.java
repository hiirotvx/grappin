package fr.sparta.grappin;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public final class GrappinCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("give", "set", "reload");

    private final GrappinPlugin plugin;

    public GrappinCommand(GrappinPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("grappin.admin")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.send(sender, "usage");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "set" -> set(sender);
            case "reload" -> {
                plugin.reloadSettings();
                plugin.send(sender, "reloaded");
            }
            default -> plugin.send(sender, "usage");
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.send(sender, "player-not-found", Placeholder.unparsed("player", args[1]));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.send(sender, "usage");
            return;
        }

        // Inventaire plein : l'objet tombe aux pieds du joueur plutot que d'etre perdu.
        target.getInventory().addItem(plugin.items().create()).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

        plugin.send(sender, "given", Placeholder.unparsed("player", target.getName()));
        if (!target.equals(sender)) {
            plugin.send(target, "received");
        }
    }

    private void set(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.FISHING_ROD) {
            plugin.send(player, "not-a-rod");
            return;
        }
        if (plugin.items().isGrappin(held)) {
            plugin.send(player, "already-grappin");
            return;
        }

        plugin.items().apply(held);
        plugin.send(player, "converted");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("grappin.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
