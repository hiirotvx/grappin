package fr.sparta.grappin;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GrappinCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("give", "set", "reload");
    private static final List<String> OPTIONS = List.of("reparable", "irreparable");
    private static final Set<String> REPAIRABLE = Set.of("reparable", "réparable", "repairable");
    private static final Set<String> UNREPAIRABLE = Set.of("irreparable", "irréparable", "unrepairable");

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
            case "set" -> set(sender, args);
            case "reload" -> {
                plugin.reloadSettings();
                plugin.send(sender, "reloaded");
            }
            default -> plugin.send(sender, "usage");
        }
        return true;
    }

    /**
     * /grappin give [joueur] [reparable|irreparable]
     * Les deux arguments sont facultatifs et peuvent etre donnes dans n'importe quel ordre.
     */
    private void give(CommandSender sender, String[] args) {
        Player target = null;
        Boolean repairable = null;

        for (int i = 1; i < args.length; i++) {
            Boolean option = parseOption(args[i]);
            if (option != null) {
                repairable = option;
            } else if (target == null) {
                target = Bukkit.getPlayerExact(args[i]);
                if (target == null) {
                    plugin.send(sender, "player-not-found", Placeholder.unparsed("player", args[i]));
                    return;
                }
            } else {
                plugin.send(sender, "usage");
                return;
            }
        }

        if (target == null) {
            if (!(sender instanceof Player player)) {
                plugin.send(sender, "usage");
                return;
            }
            target = player;
        }

        ItemStack grappin = plugin.items().create(repairable);
        Player receiver = target;
        // Inventaire plein : l'objet tombe aux pieds du joueur plutot que d'etre perdu.
        receiver.getInventory().addItem(grappin).values()
                .forEach(left -> receiver.getWorld().dropItemNaturally(receiver.getLocation(), left));

        TagResolver state = state(plugin.items().isRepairable(grappin));
        plugin.send(sender, "given", Placeholder.unparsed("player", receiver.getName()), state);
        if (!receiver.equals(sender)) {
            plugin.send(receiver, "received", state);
        }
    }

    /**
     * /grappin set [reparable|irreparable]
     * Sans option : une canne neuve prend la valeur de la config, un grappin garde son choix.
     */
    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only");
            return;
        }

        Boolean repairable = null;
        if (args.length >= 2) {
            repairable = parseOption(args[1]);
            if (repairable == null || args.length > 2) {
                plugin.send(player, "usage");
                return;
            }
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.FISHING_ROD) {
            plugin.send(player, "not-a-rod");
            return;
        }

        // Deja un grappin : on le remet a jour (nom, description) sans toucher a son usure.
        boolean update = plugin.items().isGrappin(held);
        plugin.items().apply(held, repairable);
        player.getInventory().setItemInMainHand(held);
        plugin.send(player, update ? "updated" : "converted", state(plugin.items().isRepairable(held)));
    }

    private TagResolver state(boolean repairable) {
        return Placeholder.parsed("state",
                plugin.settings().message(repairable ? "state-repairable" : "state-unrepairable"));
    }

    /** Accepte les variantes avec et sans accent, et l'anglais. */
    private static Boolean parseOption(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (REPAIRABLE.contains(value)) {
            return Boolean.TRUE;
        }
        if (UNREPAIRABLE.contains(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("grappin.admin")) {
            return List.of();
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.addAll(SUBCOMMANDS);
        } else if (args[0].equalsIgnoreCase("give") && args.length <= 3) {
            boolean optionGiven = false;
            boolean playerGiven = false;
            for (int i = 1; i < args.length - 1; i++) {
                if (parseOption(args[i]) != null) optionGiven = true; else playerGiven = true;
            }
            if (!optionGiven) suggestions.addAll(OPTIONS);
            if (!playerGiven) Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
        } else if (args[0].equalsIgnoreCase("set") && args.length == 2) {
            suggestions.addAll(OPTIONS);
        }

        return suggestions.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(typed))
                .toList();
    }
}
