package fr.sparta.grappin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Ferme une a une les voies de reparation connues, pour les grappins irreparables.
 *
 * Ces blocages rendent l'experience propre : aucun resultat repare n'apparait, aucune
 * experience de Raccommodage n'est gaspillee. Mais ils ne sont pas la garantie : une
 * commande /repair modifie directement la durabilite sans passer par aucun evenement.
 * La garantie, c'est le registre d'usure de GrappinItem#enforceWear, verifie avant chaque
 * tir et periodiquement sur tous les joueurs connectes.
 */
public final class RepairGuard implements Listener {

    private final GrappinPlugin plugin;

    public RepairGuard(GrappinPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean blocked(ItemStack item) {
        return plugin.items().isGrappin(item) && !plugin.items().isRepairable(item);
    }

    /** Raccommodage : l'experience recoltee ne repare pas le grappin et revient au joueur. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent event) {
        if (blocked(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Enclume : seul un resultat moins use que le grappin d'origine est refuse.
     * Renommer ou ajouter un enchantement reste donc possible.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack base = event.getInventory().getItem(0);
        ItemStack result = event.getResult();
        if (!blocked(base) || result == null) {
            return;
        }
        if (base.getItemMeta() instanceof Damageable before
                && result.getItemMeta() instanceof Damageable after
                && after.getDamage() < before.getDamage()) {
            event.setResult(null);
        }
    }

    /**
     * Meule : fusionner deux cannes additionne leur durabilite, c'est une reparation.
     * Un grappin seul peut toujours y etre desenchante, ce qui ne touche pas a son usure.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inventory = event.getInventory();
        ItemStack upper = inventory.getUpperItem();
        ItemStack lower = inventory.getLowerItem();
        if (upper != null && lower != null && (blocked(upper) || blocked(lower))) {
            event.setResult(null);
        }
    }

    /** Etabli : poser deux cannes usees dans la grille les fusionne, c'est une reparation. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        if (!event.isRepair()) {
            return;
        }
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (blocked(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** A la connexion : initialise le registre des grappins et annule une reparation faite hors ligne. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.items().enforceInventory(player)) {
            plugin.actionBar(player, "repair-blocked");
        }
    }
}
