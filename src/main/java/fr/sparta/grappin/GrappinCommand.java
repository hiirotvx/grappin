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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
     * /grappin give [joueur] [reparable|irreparable] [illimite|limite] [court|moyen|long] [normale|bonne|excellente]
     * Tous les arguments sont facultatifs et peuvent etre donnes dans n'importe quel ordre.
     */
    private void give(CommandSender sender, String[] args) {
        Player target = null;
        GrappinOptions options = GrappinOptions.NONE;

        for (int i = 1; i < args.length; i++) {
            GrappinOptions withOption = options.with(args[i]);
            if (withOption != null) {
                options = withOption;
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

        ItemStack grappin = plugin.items().create(options);
        Player receiver = target;
        // Inventaire plein : l'objet tombe aux pieds du joueur plutot que d'etre perdu.
        receiver.getInventory().addItem(grappin).values()
                .forEach(left -> receiver.getWorld().dropItemNaturally(receiver.getLocation(), left));

        TagResolver state = state(grappin);
        plugin.send(sender, "given", Placeholder.unparsed("player", receiver.getName()), state);
        if (!receiver.equals(sender)) {
            plugin.send(receiver, "received", state);
        }
    }

    /**
     * /grappin set [options...]
     * Option non precisee : une canne neuve prend la valeur de la config, un grappin garde son choix.
     */
    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only");
            return;
        }

        GrappinOptions options = GrappinOptions.NONE;
        for (int i = 1; i < args.length; i++) {
            options = options.with(args[i]);
            if (options == null) {
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
        plugin.items().apply(held, options);
        player.getInventory().setItemInMainHand(held);
        plugin.send(player, update ? "updated" : "converted", state(held));
    }

    /** Resume des options du grappin pour <state> : "irreparable, bonne durabilite, recharge courte". */
    private TagResolver state(ItemStack grappin) {
        Settings settings = plugin.settings();
        GrappinItem items = plugin.items();
        List<String> parts = new ArrayList<>();
        parts.add(settings.message(items.isRepairable(grappin) ? "state-repairable" : "state-unrepairable"));
        // Un grappin illimite ne s'use pas : son niveau de durabilite n'a pas d'interet.
        if (items.isUnlimited(grappin)) {
            parts.add(settings.message("state-unlimited"));
        } else {
            parts.add(settings.message("state-durability-" + items.durability(grappin)));
        }
        parts.add(settings.message("state-cooldown-" + items.cooldown(grappin)));
        return Placeholder.parsed("state", String.join(settings.message("state-separator"), parts));
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
        } else if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set")) {
            // On ne propose que les categories d'options pas encore choisies.
            Set<GrappinOptions.Kind> given = EnumSet.noneOf(GrappinOptions.Kind.class);
            boolean playerGiven = false;
            for (int i = 1; i < args.length - 1; i++) {
                GrappinOptions.Kind kind = GrappinOptions.kindOf(args[i]);
                if (kind != null) given.add(kind); else playerGiven = true;
            }
            for (GrappinOptions.Kind kind : GrappinOptions.Kind.values()) {
                if (!given.contains(kind)) suggestions.addAll(GrappinOptions.SUGGESTIONS.get(kind));
            }
            if (args[0].equalsIgnoreCase("give") && !playerGiven) {
                Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
            }
        }

        return suggestions.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(typed))
                .toList();
    }
}
