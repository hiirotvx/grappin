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
    // Memes conventions : 1 = lancers illimites / sans cooldown, 0 = non, absent = config.
    private final NamespacedKey unlimitedKey;
    private final NamespacedKey noCooldownKey;

    public GrappinItem(GrappinPlugin plugin) {
        this.plugin = plugin;
        this.wearKey = new NamespacedKey(plugin, "recorded_wear");
        this.repairableKey = new NamespacedKey(plugin, "repairable");
        this.unlimitedKey = new NamespacedKey(plugin, "unlimited");
        this.noCooldownKey = new NamespacedKey(plugin, "no_cooldown");
    }

    /** @param options chaque option a true / false pour imposer le choix, null pour la valeur de la config */
    public ItemStack create(GrappinOptions options) {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        apply(rod, options);
        return rod;
    }

    /**
     * Transforme une canne existante en grappin, ou rafraichit un grappin, en conservant
     * ses enchantements et son usure.
     *
     * @param options chaque option a true / false pour imposer le choix ; null pour garder
     *                celui deja inscrit sur l'objet, ou a defaut la valeur de la config
     */
    public void apply(ItemStack rod, GrappinOptions options) {
        ItemMeta meta = rod.getItemMeta();
        Settings settings = plugin.settings();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        store(data, repairableKey, options.repairable());
        store(data, unlimitedKey, options.unlimited());
        store(data, noCooldownKey, options.noCooldown());

        meta.displayName(MM.deserialize(settings.itemName()));
        List<String> lore = new ArrayList<>(settings.itemLore());
        boolean unlimited = resolve(data, unlimitedKey, settings.unlimited());
        // Un grappin illimite ne s'use pas : la mention d'usure serait fausse.
        addLine(lore, !unlimited, settings.wearLine());
        addLine(lore, !resolveRepairable(data), settings.unrepairableLine());
        addLine(lore, unlimited, settings.unlimitedLine());
        addLine(lore, resolve(data, noCooldownKey, settings.noCooldown()), settings.noCooldownLine());
        meta.lore(lore.stream().map(MM::deserialize).toList());

        if (settings.maxDurability() > 0 && meta instanceof Damageable damageable) {
            damageable.setMaxDamage(settings.maxDurability());
            // Une canne deja usee ne doit pas depasser la nouvelle limite et se briser d'un coup.
            if (damageable.getDamage() >= settings.maxDurability()) {
                damageable.setDamage(settings.maxDurability() - 1);
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

    /** Applique enforceWear a tout l'inventaire du joueur. @return true si une reparation a ete annulee */
    public boolean enforceInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean reverted = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != Material.FISHING_ROD) {
                continue;
            }
            ItemStack before = item.clone();
            reverted |= enforceWear(item);
            if (!item.equals(before)) {
                inventory.setItem(slot, item);
            }
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

    /** Un grappin sans cooldown peut etre relance sans attendre le temps de recharge. */
    public boolean hasNoCooldown(ItemStack item) {
        return isGrappin(item) && resolve(item.getItemMeta().getPersistentDataContainer(), noCooldownKey, plugin.settings().noCooldown());
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
