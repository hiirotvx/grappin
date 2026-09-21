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

    public GrappinItem(GrappinPlugin plugin) {
        this.plugin = plugin;
        this.wearKey = new NamespacedKey(plugin, "recorded_wear");
    }

    public ItemStack create() {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        apply(rod);
        return rod;
    }

    /** Transforme une canne existante en grappin, en conservant ses enchantements et son usure. */
    public void apply(ItemStack rod) {
        ItemMeta meta = rod.getItemMeta();
        Settings settings = plugin.settings();

        meta.displayName(MM.deserialize(settings.itemName()));
        List<String> lore = new ArrayList<>(settings.itemLore());
        if (!settings.repairable() && !settings.unrepairableLine().isEmpty()) {
            lore.add(settings.unrepairableLine());
        }
        meta.lore(lore.stream().map(MM::deserialize).toList());

        if (settings.maxDurability() > 0 && meta instanceof Damageable damageable) {
            damageable.setMaxDamage(settings.maxDurability());
            // Une canne deja usee ne doit pas depasser la nouvelle limite et se briser d'un coup.
            if (damageable.getDamage() >= settings.maxDurability()) {
                damageable.setDamage(settings.maxDurability() - 1);
            }
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(plugin.grappinKey(), PersistentDataType.BYTE, (byte) 1);
        if (meta instanceof Damageable damageable) {
            // Une mise a jour ne doit jamais effacer l'usure deja enregistree.
            int recorded = Math.max(damageable.getDamage(), data.getOrDefault(wearKey, PersistentDataType.INTEGER, 0));
            int ceiling = (damageable.hasMaxDamage() ? damageable.getMaxDamage() : rod.getType().getMaxDurability()) - 1;
            data.set(wearKey, PersistentDataType.INTEGER, Math.min(recorded, ceiling));
        }
        rod.setItemMeta(meta);
    }

    /**
     * Garantit qu'un grappin irreparable ne regagne jamais de durabilite.
     *
     * Chaque grappin garde la plus forte usure jamais constatee. Si sa durabilite remonte
     * au-dessus - commande de reparation, plugin tiers, n'importe quelle source - elle est
     * remise au niveau enregistre. Quand la reparation est autorisee dans la config, le
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

        if (recorded == null || plugin.settings().repairable() || current > recorded) {
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

    public boolean isGrappin(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(plugin.grappinKey(), PersistentDataType.BYTE);
    }
}
