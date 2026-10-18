package fr.sparta.grappin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creation et reconnaissance des grappins.
 *
 * Un grappin est une canne a peche portant un marqueur dans ses donnees persistantes.
 * On ne s'appuie jamais sur le nom : il peut etre renomme a l'enclume sans perdre
 * son effet, et deux objets au nom identique ne sont pas confondus.
 */
public final class GrappinItem {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GrappinPlugin plugin;
    // Registre d'usure : la plus forte usure jamais constatee sur ce grappin.
    private final NamespacedKey wearKey;
    // Choix fait a la creation : 1 = reparable, 0 = irreparable. Absent = valeur de la config.
    private final NamespacedKey repairableKey;
    // Meme convention : 1 = lancers illimites, 0 = limites, absent = config.
    private final NamespacedKey unlimitedKey;
    // Niveau de recharge : "court", "moyen" ou "long". Absent = niveau par defaut de la config.
    private final NamespacedKey cooldownKey;
    // Niveau de durabilite : "normale", "bonne" ou "excellente". Absent = niveau par defaut.
    private final NamespacedKey durabilityKey;
    // Option "sans cooldown" de la 1.4.0, remplacee par les niveaux : effacee a la mise a jour.
    private final NamespacedKey obsoleteNoCooldownKey;

    public GrappinItem(GrappinPlugin plugin) {
        this.plugin = plugin;
        this.wearKey = new NamespacedKey(plugin, "recorded_wear");
        this.repairableKey = new NamespacedKey(plugin, "repairable");
        this.unlimitedKey = new NamespacedKey(plugin, "unlimited");
        this.cooldownKey = new NamespacedKey(plugin, "cooldown");
        this.durabilityKey = new NamespacedKey(plugin, "durability");
        this.obsoleteNoCooldownKey = new NamespacedKey(plugin, "no_cooldown");
    }

    /** @param options chaque option imposee, ou null pour la valeur de la config */
    public ItemStack create(GrappinOptions options) {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        apply(rod, options);
        return rod;
    }

    /**
     * Transforme une canne existante en grappin, ou rafraichit un grappin, en conservant
     * ses enchantements et son usure.
     *
     * @param options chaque option imposee ; null pour garder celle deja inscrite sur
     *                l'objet, ou a defaut la valeur de la config
     */
    public void apply(ItemStack rod, GrappinOptions options) {
        ItemMeta meta = rod.getItemMeta();
        Settings settings = plugin.settings();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        store(data, repairableKey, options.repairable());
        store(data, unlimitedKey, options.unlimited());
        storeTier(data, cooldownKey, options.cooldown());
        storeTier(data, durabilityKey, options.durability());
        data.remove(obsoleteNoCooldownKey);

        String durability = resolveDurability(data);
        meta.displayName(MM.deserialize(settings.itemName()));
        List<String> lore = new ArrayList<>(settings.itemLore());
        boolean unlimited = resolve(data, unlimitedKey, settings.unlimited());
        // Un grappin illimite ne s'use pas : les mentions d'usure et de durabilite seraient fausses.
        addLine(lore, !unlimited, settings.wearLine());
        addLine(lore, !unlimited, settings.durabilityLines().get(durability));
        addLine(lore, !resolveRepairable(data), settings.unrepairableLine());
        addLine(lore, unlimited, settings.unlimitedLine());
        addLine(lore, true, settings.cooldownLines().get(resolveCooldown(data)));
        meta.lore(lore.stream().map(MM::deserialize).toList());

        int maxDurability = settings.durabilities().get(durability);
        if (maxDurability > 0 && meta instanceof Damageable damageable) {
            damageable.setMaxDamage(maxDurability);
            // Une canne deja usee ne doit pas depasser la nouvelle limite et se briser d'un coup.
            if (damageable.getDamage() >= maxDurability) {
                damageable.setDamage(maxDurability - 1);
            }
        }

        data.set(plugin.grappinKey(), PersistentDataType.BYTE, (byte) 1);
        if (meta instanceof Damageable damageable) {
            // Une mise a jour ne doit jamais effacer l'usure deja enregistree.
            int recorded = Math.max(damageable.getDamage(), data.getOrDefault(wearKey, PersistentDataType.INTEGER, 0));
            int ceiling = (damageable.hasMaxDamage() ? damageable.getMaxDamage() : rod.getType().getMaxDurability()) - 1;
            data.set(wearKey, PersistentDataType.INTEGER, Math.min(recorded, ceiling));
        }
        rod.setItemMeta(meta);
    }

    private static void store(PersistentDataContainer data, NamespacedKey key, Boolean value) {
        if (value != null) {
            data.set(key, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
        }
    }

    private static void storeTier(PersistentDataContainer data, NamespacedKey key, String tier) {
        if (tier != null) {
            data.set(key, PersistentDataType.STRING, tier);
        }
    }

    private static void addLine(List<String> lore, boolean active, String line) {
        if (active && !line.isEmpty()) {
            lore.add(line);
        }
    }

    /**
     * Garantit qu'un grappin irreparable ne regagne jamais de durabilite.
     *
     * Chaque grappin garde la plus forte usure jamais constatee. Si sa durabilite remonte
     * au-dessus - commande de reparation, plugin tiers, n'importe quelle source - elle est
     * remise au niveau enregistre. Quand ce grappin est reparable, le
     * registre suit simplement l'usure reelle, pour qu'une reactivation ulterieure du
     * blocage parte de l'etat du moment et non d'une vieille valeur.
     *
     * Modifie l'objet sur place ; l'appelant doit le reecrire dans l'inventaire.
     *
     * @return true si une reparation vient d'etre annulee
     */
    public boolean enforceWear(ItemStack item) {
        if (!isGrappin(item) || !(item.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }
        PersistentDataContainer data = damageable.getPersistentDataContainer();
        Integer recorded = data.get(wearKey, PersistentDataType.INTEGER);
        int current = damageable.getDamage();

        if (recorded == null || resolveRepairable(data) || current > recorded) {
            if (recorded == null || recorded != current) {
                data.set(wearKey, PersistentDataType.INTEGER, current);
                item.setItemMeta(damageable);
            }
            return false;
        }
        if (current < recorded) {
            damageable.setDamage(recorded);
            item.setItemMeta(damageable);
            return true;
        }
        return false;
    }

    /**
     * Retire les enchantements d'un grappin quand ils sont interdits (item.enchantable: false),
     * pour ceux enchantes avant l'interdiction. Modifie l'objet sur place.
     *
     * @return true si des enchantements ont ete retires
     */
    public boolean stripEnchantments(ItemStack item) {
        if (plugin.settings().enchantable() || !isGrappin(item) || item.getEnchantments().isEmpty()) {
            return false;
        }
        item.getEnchantments().keySet().forEach(item::removeEnchantment);
        return true;
    }

    /**
     * Applique enforceWear et stripEnchantments a tout l'inventaire du joueur, et le previent
     * si des enchantements ont ete retires.
     *
     * @return true si une reparation a ete annulee
     */
    public boolean enforceInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean reverted = false;
        boolean stripped = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != Material.FISHING_ROD) {
                continue;
            }
            ItemStack before = item.clone();
            stripped |= stripEnchantments(item);
            reverted |= enforceWear(item);
            if (!item.equals(before)) {
                inventory.setItem(slot, item);
            }
        }
        if (stripped) {
            plugin.actionBar(player, "enchant-removed");
        }
        return reverted;
    }

    /** Un objet qui n'est pas un grappin n'est pas concerne : il est considere reparable. */
    public boolean isRepairable(ItemStack item) {
        return !isGrappin(item) || resolveRepairable(item.getItemMeta().getPersistentDataContainer());
    }

    /** Un grappin illimite ne perd aucune durabilite en s'accrochant. */
    public boolean isUnlimited(ItemStack item) {
        return isGrappin(item) && resolve(item.getItemMeta().getPersistentDataContainer(), unlimitedKey, plugin.settings().unlimited());
    }

    /** Niveau de recharge du grappin : "court", "moyen" ou "long". */
    public String cooldown(ItemStack item) {
        return isGrappin(item) ? resolveCooldown(item.getItemMeta().getPersistentDataContainer()) : plugin.settings().defaultCooldown();
    }

    /** Niveau de durabilite du grappin : "normale", "bonne" ou "excellente". */
    public String durability(ItemStack item) {
        return isGrappin(item) ? resolveDurability(item.getItemMeta().getPersistentDataContainer()) : plugin.settings().defaultDurability();
    }

    private String resolveCooldown(PersistentDataContainer data) {
        return resolveTier(data, cooldownKey, plugin.settings().cooldowns(), plugin.settings().defaultCooldown());
    }

    private String resolveDurability(PersistentDataContainer data) {
        return resolveTier(data, durabilityKey, plugin.settings().durabilities(), plugin.settings().defaultDurability());
    }

    /** Niveau inscrit sur le grappin ; a defaut, ou s'il est inconnu, celui de la config. */
    private static String resolveTier(PersistentDataContainer data, NamespacedKey key, Map<String, ?> tiers, String fallback) {
        String tier = data.get(key, PersistentDataType.STRING);
        return tier != null && tiers.containsKey(tier) ? tier : fallback;
    }

    private boolean resolveRepairable(PersistentDataContainer data) {
        return resolve(data, repairableKey, plugin.settings().repairable());
    }

    /** Choix inscrit sur le grappin ; a defaut (anciens grappins), la valeur de la config. */
    private static boolean resolve(PersistentDataContainer data, NamespacedKey key, boolean fallback) {
        Byte choice = data.get(key, PersistentDataType.BYTE);
        return choice != null ? choice == 1 : fallback;
    }

    public boolean isGrappin(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(plugin.grappinKey(), PersistentDataType.BYTE);
    }
}
