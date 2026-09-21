package fr.sparta.grappin;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GrappinListener implements Listener {

    // Plafond de vitesse : au-dela, le deplacement devient incontrolable
    // et l'anti-triche de certains serveurs le signale.
    private static final double MAX_SPEED = 3.5;
    // En dessous, le joueur est deja sur place.
    private static final double MIN_DISTANCE = 1.5;

    private final GrappinPlugin plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Map<UUID, Long> fallProtectedUntil = new HashMap<>();

    public GrappinListener(GrappinPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        // IN_GROUND : le joueur ramene la ligne alors que le bouchon est plante dans un bloc.
        // C'est aussi ce cas qui coute 2 points de durabilite a la canne en vanilla.
        if (event.getState() != PlayerFishEvent.State.IN_GROUND) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.items().isGrappin(rodInUse(player)) || !player.hasPermission("grappin.use")) {
            return;
        }

        Settings settings = plugin.settings();
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (!player.hasPermission("grappin.bypass.cooldown")) {
            long cooldownMillis = (long) (settings.cooldownSeconds() * 1000);
            Long last = lastUse.get(id);
            if (last != null && now - last < cooldownMillis) {
                double left = (cooldownMillis - (now - last)) / 1000.0;
                plugin.actionBar(player, "cooldown",
                        Placeholder.unparsed("time", String.format(Locale.FRANCE, "%.1f", left)));
                return;
            }
        }

        FishHook hook = event.getHook();
        Location from = player.getLocation();
        Location to = hook.getLocation();
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }

        Vector delta = to.toVector().subtract(from.toVector());
        double distance = delta.length();

        if (distance > settings.range()) {
            plugin.actionBar(player, "too-far",
                    Placeholder.unparsed("range", String.valueOf((int) settings.range())));
            return;
        }
        if (distance < MIN_DISTANCE) {
            return;
        }

        // Trajectoire en cloche : la composante horizontale croit avec la distance pour
        // compenser le frottement de l'air, et un surplus vertical proportionnel a la
        // distance donne l'arc qui fait retomber le joueur pres du point d'accroche.
        Vector velocity = new Vector(
                (1.0 + 0.08 * distance) * delta.getX() / distance,
                (1.0 + 0.03 * distance) * delta.getY() / distance + 0.04 * distance,
                (1.0 + 0.08 * distance) * delta.getZ() / distance
        ).multiply(settings.strength());

        if (velocity.length() > MAX_SPEED) {
            velocity.normalize().multiply(MAX_SPEED);
        }

        player.setVelocity(velocity);
        lastUse.put(id, now);
        fallProtectedUntil.put(id, now + settings.fallProtectionMillis());

        player.getWorld().playSound(from, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.6f);
        player.getWorld().spawnParticle(Particle.CRIT, to, 12, 0.2, 0.2, 0.2, 0.1);
    }

    /**
     * Annule la premiere chute qui suit un grappin, si elle survient dans le delai.
     * L'entree est consommee a la premiere chute, protegee ou non : une entree expiree
     * ne peut donc jamais proteger une chute bien plus tardive.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) {
            return;
        }

        Long until = fallProtectedUntil.remove(player.getUniqueId());
        if (until != null && System.currentTimeMillis() <= until) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastUse.remove(id);
        fallProtectedUntil.remove(id);
    }

    /** Meme regle que le jeu : la canne en main principale a priorite, sinon celle de la main secondaire. */
    private static ItemStack rodInUse(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack main = inventory.getItemInMainHand();
        return main.getType() == Material.FISHING_ROD ? main : inventory.getItemInOffHand();
    }
}
