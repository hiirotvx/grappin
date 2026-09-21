package fr.sparta.grappin;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Grappin d'un seul clic droit : le plugin vise le bloc regarde, sans jamais lancer
 * de bouchon. Rien n'est consomme si aucun bloc n'est a portee.
 */
public final class GrappinListener implements Listener {

    // Plafond de vitesse : au-dela, le deplacement devient incontrolable
    // et l'anti-triche de certains serveurs le signale.
    private static final double MAX_SPEED = 3.5;
    // En dessous, le joueur est deja sur place.
    private static final double MIN_DISTANCE = 1.5;
    // Un clic sur un bloc peut etre suivi d'un second evenement "clic dans le vide"
    // envoye par le client. Ce delai ignore ce doublon sans afficher de message.
    private static final long DOUBLE_EVENT_MILLIS = 250;
    // On se pose legerement devant la face touchee plutot que dans le bloc.
    private static final double FACE_OFFSET = 0.6;

    private static final Particle.DustOptions ROPE = new Particle.DustOptions(Color.fromRGB(140, 98, 57), 0.7f);

    private final GrappinPlugin plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Map<UUID, Long> fallProtectedUntil = new HashMap<>();

    public GrappinListener(GrappinPlugin plugin) {
        this.plugin = plugin;
    }

    // Pas de ignoreCancelled : Bukkit marque les clics dans le vide comme deja annules
    // par defaut, on les perdrait tous.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        ItemStack item = event.getItem();
        if (hand == null || !plugin.items().isGrappin(item)) {
            return;
        }

        Player player = event.getPlayer();

        // Porte, coffre, levier... : sans s'accroupir, on laisse le jeu ouvrir le bloc.
        Block clicked = event.getClickedBlock();
        if (action == Action.RIGHT_CLICK_BLOCK && !player.isSneaking()
                && clicked != null && clicked.getType().isInteractable()) {
            return;
        }

        // Le bouchon n'est jamais lance : c'est ce qui rendait les clics dans le vide couteux.
        event.setUseItemInHand(Event.Result.DENY);

        if (!player.hasPermission("grappin.use")) {
            return;
        }

        Settings settings = plugin.settings();
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastUse.get(id);

        if (last != null && now - last < DOUBLE_EVENT_MILLIS) {
            return;
        }
        if (!player.hasPermission("grappin.bypass.cooldown")) {
            long cooldownMillis = (long) (settings.cooldownSeconds() * 1000);
            if (last != null && now - last < cooldownMillis) {
                double left = (cooldownMillis - (now - last)) / 1000.0;
                plugin.actionBar(player, "cooldown",
                        Placeholder.unparsed("time", String.format(Locale.FRANCE, "%.1f", left)));
                return;
            }
        }

        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        // Les blocs traversables (herbes, fleurs) ne retiennent pas le grappin.
        RayTraceResult hit = world.rayTraceBlocks(eye, eye.getDirection(), settings.range(),
                FluidCollisionMode.NEVER, true);

        if (hit == null || hit.getHitBlock() == null) {
            // Rien a portee : aucune durabilite ni recharge consommee.
            plugin.actionBar(player, "nothing-to-grab",
                    Placeholder.unparsed("range", String.valueOf((int) settings.range())));
            return;
        }

        Vector anchor = hit.getHitPosition();
        Vector target = anchor.clone();
        if (hit.getHitBlockFace() != null) {
            target.add(hit.getHitBlockFace().getDirection().multiply(FACE_OFFSET));
        }

        Vector delta = target.clone().subtract(player.getLocation().toVector());
        double distance = delta.length();
        if (distance < MIN_DISTANCE) {
            return;
        }

        // Trajectoire en cloche : la composante horizontale croit avec la distance pour
        // compenser le frottement de l'air, et un surplus vertical proportionnel a la
        // distance donne l'arc qui fait arriver le joueur pres du point d'accroche.
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

        drawRope(world, eye.toVector(), anchor);
        world.playSound(eye, Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 0.8f);
        world.playSound(eye, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.6f);

        consumeDurability(player, hand, settings);
    }

    /**
     * Durabilite de la canne elle-meme : passe par le calcul du jeu, donc Solidite
     * reduit l'usure, Raccommodage repare avec l'experience, et la canne se brise
     * normalement a zero.
     */
    private void consumeDurability(Player player, EquipmentSlot hand, Settings settings) {
        int cost = settings.durabilityCost();
        if (cost <= 0 || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        player.damageItemStack(hand, cost);

        ItemStack after = player.getInventory().getItem(hand);
        if (!plugin.items().isGrappin(after) || !(after.getItemMeta() instanceof Damageable damageable)) {
            return; // brisee : le jeu joue deja le son de casse
        }
        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : after.getType().getMaxDurability();
        int usesLeft = Math.max(0, (max - damageable.getDamage()) / cost);
        plugin.actionBar(player, "uses-left", Placeholder.unparsed("uses", String.valueOf(usesLeft)));
    }

    private static void drawRope(World world, Vector from, Vector to) {
        Vector step = to.clone().subtract(from);
        double length = step.length();
        if (length <= 0) {
            return;
        }
        step.normalize().multiply(0.5);
        Vector point = from.clone();
        for (double travelled = 0; travelled < length; travelled += 0.5) {
            world.spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0, ROPE);
            point.add(step);
        }
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
}
