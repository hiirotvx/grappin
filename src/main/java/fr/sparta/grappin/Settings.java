package fr.sparta.grappin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Instantane immuable de config.yml, reconstruit a chaque rechargement. */
public record Settings(
        double range,
        double cooldownSeconds,
        double strength,
        long fallProtectionMillis,
        String itemName,
        List<String> itemLore,
        int maxDurability,
        Map<String, String> messages
) {

    public static Settings from(FileConfiguration config) {
        Map<String, String> messages = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                messages.put(key, section.getString(key, ""));
            }
        }

        return new Settings(
                config.getDouble("grappin.range", 30),
                config.getDouble("grappin.cooldown", 2),
                config.getDouble("grappin.strength", 1.0),
                (long) (config.getDouble("grappin.fall-protection-seconds", 5) * 1000),
                config.getString("item.name", "<!italic><#d9a441><b>Grappin</b>"),
                config.getStringList("item.lore"),
                config.getInt("item.max-durability", 0),
                Map.copyOf(messages)
        );
    }

    public String message(String key) {
        return messages.getOrDefault(key, "<red>Message manquant dans config.yml : " + key);
    }
}
