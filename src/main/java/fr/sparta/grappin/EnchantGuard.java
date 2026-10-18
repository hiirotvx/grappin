package fr.sparta.grappin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Empeche les joueurs d'enchanter un grappin (item.enchantable: false).
 *
 * Solidite allongerait la duree de vie au-dela du niveau de durabilite choisi,
 * et les autres enchantements n'ont pas d'usage sur un grappin.
 * Les grappins enchantes avant l'interdiction sont nettoyes par GrappinItem#stripEnchantments.
 */
public final class EnchantGuard implements Listener {

    private final GrappinPlugin plugin;

    public EnchantGuard(GrappinPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean blocked(ItemStack item) {
        return !plugin.settings().enchantable() && plugin.items().isGrappin(item);
    }

    /** Table d'enchantement : aucune offre n'est proposee pour un grappin. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (blocked(event.getItem())) {
            event.setCancelled(true);
            plugin.actionBar(event.getEnchanter(), "enchant-blocked");
        }
    }

    /** Filet de securite si un autre plugin propose quand meme des offres. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (blocked(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Enclume : tout resultat dont les enchantements different de ceux du grappin
     * d'origine est refuse (livre enchante, fusion avec une canne enchantee).
     * Renommer reste possible.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack base = event.getInventory().getItem(0);
        ItemStack result = event.getResult();
        if (!blocked(base) || result == null) {
            return;
        }
        if (!result.getEnchantments().equals(base.getEnchantments())) {
            event.setResult(null);
            if (event.getView().getPlayer() instanceof Player player) {
                plugin.actionBar(player, "enchant-blocked");
            }
        }
    }
}
