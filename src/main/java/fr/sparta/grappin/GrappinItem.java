package fr.sparta.grappin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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

    public GrappinItem(GrappinPlugin plugin) {
        this.plugin = plugin;
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
        meta.lore(settings.itemLore().stream().map(MM::deserialize).toList());

        if (settings.maxDurability() > 0 && meta instanceof Damageable damageable) {
            damageable.setMaxDamage(settings.maxDurability());
            // Une canne deja usee ne doit pas depasser la nouvelle limite et se briser d'un coup.
            if (damageable.getDamage() >= settings.maxDurability()) {
                damageable.setDamage(settings.maxDurability() - 1);
            }
        }

        meta.getPersistentDataContainer().set(plugin.grappinKey(), PersistentDataType.BYTE, (byte) 1);
        rod.setItemMeta(meta);
    }

    public boolean isGrappin(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(plugin.grappinKey(), PersistentDataType.BYTE);
    }
}
