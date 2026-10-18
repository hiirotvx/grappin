package fr.sparta.grappin;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Instantane immuable de config.yml, reconstruit a chaque rechargement. */
public record Settings(
        double range,
        Map<String, Double> cooldowns,
        double strength,
        long fallProtectionMillis,
        int durabilityCost,
        Map<String, Integer> durabilities,
        String itemName,
        List<String> itemLore,
        String wearLine,
        boolean repairable,
        String unrepairableLine,
        boolean unlimited,
        String unlimitedLine,
        String defaultCooldown,
        Map<String, String> cooldownLines,
        String defaultDurability,
        Map<String, String> durabilityLines,
        boolean enchantable,
        Map<String, String> messages
) {

    /** Durabilite vanilla d'une canne a peche. */
    private static final int VANILLA_DURABILITY = 64;

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

        // Une config d'avant les niveaux n'a qu'un "grappin.cooldown" : il devient le niveau moyen.
        Map<String, Double> cooldowns = Map.of(
                "court", config.getDouble("grappin.cooldowns.court", 1),
                "moyen", config.getDouble("grappin.cooldowns.moyen", config.getDouble("grappin.cooldown", 2)),
                "long", config.getDouble("grappin.cooldowns.long", 4)
        );
        // De meme, l'ancien "item.max-durability" devient le niveau normal (0 = vanilla).
        int legacyMax = config.getInt("item.max-durability", 0);
        Map<String, Integer> durabilities = Map.of(
                "normale", config.getInt("grappin.durabilities.normale", legacyMax > 0 ? legacyMax : VANILLA_DURABILITY),
                "bonne", config.getInt("grappin.durabilities.bonne", 128),
                "excellente", config.getInt("grappin.durabilities.excellente", 256)
        );

        return new Settings(
                config.getDouble("grappin.range", 30),
                cooldowns,
                config.getDouble("grappin.strength", 1.0),
                (long) (config.getDouble("grappin.fall-protection-seconds", 5) * 1000),
                config.getInt("grappin.durability-cost", 1),
                durabilities,
                config.getString("item.name", "<!italic><#d9a441><b>Grappin</b>"),
                config.getStringList("item.lore"),
                // Vide par defaut : une ancienne config a encore cette phrase dans item.lore.
                config.getString("item.wear-line", ""),
                config.getBoolean("item.repairable", false),
                config.getString("item.unrepairable-line", ""),
                config.getBoolean("item.unlimited", false),
                config.getString("item.unlimited-line", "<!italic><#d9a441>✦ <#a89f91>Lancers illimités"),
                tier(config.getString("item.cooldown", "moyen"), cooldowns, "moyen"),
                Map.of(
                        "court", config.getString("item.cooldown-lines.court", "<!italic><#d9a441>✦ <#a89f91>Recharge courte"),
                        "moyen", config.getString("item.cooldown-lines.moyen", "<!italic><#d9a441>✦ <#a89f91>Recharge moyenne"),
                        "long", config.getString("item.cooldown-lines.long", "<!italic><#d9a441>✦ <#a89f91>Recharge longue")
                ),
                tier(config.getString("item.durability", "normale"), durabilities, "normale"),
                Map.of(
                        "normale", config.getString("item.durability-lines.normale", "<!italic><#d9a441>✦ <#a89f91>Durabilité normale"),
                        "bonne", config.getString("item.durability-lines.bonne", "<!italic><#d9a441>✦ <#a89f91>Bonne durabilité"),
                        "excellente", config.getString("item.durability-lines.excellente", "<!italic><#d9a441>✦ <#a89f91>Excellente durabilité")
                ),
                config.getBoolean("item.enchantable", false),
                Map.copyOf(messages)
        );
    }

    /** Niveau par defaut lu dans la config, ou le repli s'il est inconnu. */
    private static String tier(String raw, Map<String, ?> tiers, String fallback) {
        String value = raw.toLowerCase(Locale.ROOT);
        return tiers.containsKey(value) ? value : fallback;
    }

    /** Duree de recharge d'un niveau, en millisecondes. */
    public long cooldownMillis(String tier) {
        return (long) (cooldowns.getOrDefault(tier, cooldowns.get(defaultCooldown)) * 1000);
    }

    public String message(String key) {
        return messages.getOrDefault(key, "<red>Message manquant dans config.yml : " + key);
    }
}
