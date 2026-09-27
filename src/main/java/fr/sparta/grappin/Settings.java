package fr.sparta.grappin;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Instantane immuable de config.yml, reconstruit a chaque rechargement. */
public record Settings(
        double range,
        double cooldownSeconds,
        double strength,
        long fallProtectionMillis,
        int durabilityCost,
        String itemName,
        List<String> itemLore,
        String wearLine,
        int maxDurability,
        boolean repairable,
        String unrepairableLine,
        boolean unlimited,
        String unlimitedLine,
        boolean noCooldown,
        String noCooldownLine,
        Map<String, String> messages
) {

    public static Settings from(FileConfiguration config) {
        // Une config generee par une version precedente n'a pas les nouveaux messages :
        // on complete avec ceux embarques dans le JAR plutot que d'afficher une erreur.
        Set<String> keys = new HashSet<>();
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section != null) {
            keys.addAll(section.getKeys(false));
        }
        Configuration defaults = config.getDefaults();
        if (defaults != null && defaults.getConfigurationSection("messages") != null) {
            keys.addAll(defaults.getConfigurationSection("messages").getKeys(false));
        }
        Map<String, String> messages = new HashMap<>();
        for (String key : keys) {
            // Sans valeur par defaut explicite : getString(path, def) ignorerait celles du JAR.
            String value = config.getString("messages." + key);
            messages.put(key, value != null ? value : "");
        }

        return new Settings(
                config.getDouble("grappin.range", 30),
                config.getDouble("grappin.cooldown", 2),
                config.getDouble("grappin.strength", 1.0),
                (long) (config.getDouble("grappin.fall-protection-seconds", 5) * 1000),
                config.getInt("grappin.durability-cost", 1),
                config.getString("item.name", "<!italic><#d9a441><b>Grappin</b>"),
                config.getStringList("item.lore"),
                // Vide par defaut : une ancienne config a encore cette phrase dans item.lore.
                config.getString("item.wear-line", ""),
                config.getInt("item.max-durability", 0),
                config.getBoolean("item.repairable", false),
                config.getString("item.unrepairable-line", ""),
                config.getBoolean("item.unlimited", false),
                config.getString("item.unlimited-line", "<!italic><#d9a441>✦ <#a89f91>Lancers illimités"),
                config.getBoolean("item.no-cooldown", false),
                config.getString("item.no-cooldown-line", "<!italic><#d9a441>✦ <#a89f91>Sans recharge"),
                Map.copyOf(messages)
        );
    }

    public String message(String key) {
        return messages.getOrDefault(key, "<red>Message manquant dans config.yml : " + key);
    }
}
