package fr.sparta.grappin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class GrappinPlugin extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private NamespacedKey grappinKey;
    private Settings settings;
    private GrappinItem items;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        grappinKey = new NamespacedKey(this, "grappling_hook");
        reloadSettings();
        items = new GrappinItem(this);

        getServer().getPluginManager().registerEvents(new GrappinListener(this), this);

        PluginCommand command = getCommand("grappin");
        if (command != null) {
            GrappinCommand executor = new GrappinCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig());
    }

    public NamespacedKey grappinKey() {
        return grappinKey;
    }

    public Settings settings() {
        return settings;
    }

    public GrappinItem items() {
        return items;
    }

    /** Message de chat, precede du prefixe. */
    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(MM.deserialize(settings.message("prefix") + settings.message(key), resolvers));
    }

    /** Message court au-dessus de la barre d'inventaire, sans prefixe. */
    public void actionBar(Player player, String key, TagResolver... resolvers) {
        player.sendActionBar(MM.deserialize(settings.message(key), resolvers));
    }
}
