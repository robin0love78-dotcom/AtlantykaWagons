package fr.atlantyka.wagons;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

public final class AtlantykaWagons extends JavaPlugin implements Listener, TabExecutor {
    private NamespacedKey itemKey, entityKey;
    private final Map<UUID, Wagon> wagons = new HashMap<>();
    private final Map<UUID, Wagon> seatOwners = new HashMap<>();
    private BukkitTask ticker;
    private long tickCounter;

    private static final class Wagon {
        final Minecart cart;
        final String model;
        final List<BlockDisplay> displays = new ArrayList<>();
        final List<ArmorStand> seats = new ArrayList<>();
        Wagon(Minecart cart, String model) { this.cart = cart; this.model = model; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "wagon_model");
        entityKey = new NamespacedKey(this, "wagon_entity");
        PluginCommand command = Objects.requireNonNull(getCommand("wagon"));
        command.setExecutor(this);
        command.setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tickWagons, 1L, 1L);
        getLogger().info("AtlantykaWagons 1.1.0 active.");
    }

    @Override public void onDisable() {
        if (ticker != null) ticker.cancel();
        for (Wagon wagon : new ArrayList<>(wagons.values())) destroyWagon(wagon);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("atlantykawagons.admin")) {
            sender.sendMessage(color("&cTu n'as pas la permission."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(color("&b&lAtlantykaWagons &7- commandes"));
            sender.sendMessage(color("&f/wagon give <joueur> <modele>"));
            sender.sendMessage(color("&f/wagon list"));
            sender.sendMessage(color("&f/wagon remove"));
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(color("&bModèles disponibles : &f" + String.join(", ", modelNames())));
            return true;
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length != 3) {
                sender.sendMessage(color("&cUtilisation : /wagon give <joueur> <modele>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(color("&cJoueur introuvable ou hors ligne."));
                return true;
            }
            String model = args[2].toLowerCase(Locale.ROOT);
            if (!isModel(model)) {
                sender.sendMessage(color("&cModèle inconnu. Utilise /wagon list."));
                return true;
            }
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(createWagonItem(model));
            leftovers.values().forEach(it -> target.getWorld().dropItemNaturally(target.getLocation(), it));
            sender.sendMessage(color("&aTu as donné &f" + model + " &aà &f" + target.getName() + "&a."));
            target.sendMessage(color("&aTu as reçu un wagon &f" + model + "&a."));
            return true;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Cette commande doit être utilisée en jeu.");
                return true;
            }
            Wagon nearest = wagons.values().stream()
                    .filter(w -> w.cart.getWorld().equals(player.getWorld()))
                    .filter(w -> w.cart.getLocation().distanceSquared(player.getLocation()) <= 36)
                    .min(Comparator.comparingDouble(w -> w.cart.getLocation().distanceSquared(player.getLocation())))
                    .orElse(null);
            if (nearest == null) {
                player.sendMessage(color("&cAucun wagon Atlantyka à moins de 6 blocs."));
                return true;
            }
            destroyWagon(nearest);
            player.sendMessage(color("&aWagon supprimé."));
            return true;
        }
        sender.sendMessage(color("&cCommande inconnue. Fais /wagon help."));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return startsWith(List.of("give", "list", "remove", "help"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give"))
            return startsWith(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return startsWith(modelNames(), args[2]);
        return Collections.emptyList();
    }

    private List<String> modelNames() {
        if (getConfig().getConfigurationSection("models") == null) return List.of();
        return new ArrayList<>(getConfig().getConfigurationSection("models").getKeys(false));
    }
    private List<String> startsWith(List<String> values, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
    private boolean isModel(String model) { return getConfig().isConfigurationSection("models." + model); }

    private ItemStack createWagonItem(String model) {
        ItemStack item = new ItemStack(Material.MINECART);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("models." + model + ".display-name", "&6" + model)));
        meta.setLore(List.of(color("&7Wagon d'attraction Atlantyka"),
                color("&eClic droit sur un rail pour le poser"), color("&8Modèle : " + model)));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, model);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = event.getItem();
        if (held == null || held.getType() != Material.MINECART || !held.hasItemMeta()) return;
        String model = held.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (model == null || !isModel(model)) return;
        Block rail = event.getClickedBlock();
        if (rail == null || !rail.getType().name().contains("RAIL")) {
            event.getPlayer().sendMessage(color("&cClique directement sur un rail pour poser le wagon."));
            return;
        }
        event.setCancelled(true);
        Location spawn = rail.getLocation().add(0.5, 0.12, 0.5);
        Minecart cart = rail.getWorld().spawn(spawn, Minecart.class, c -> {
            c.setCustomName(color(getConfig().getString("models." + model + ".display-name", model)));
            c.setCustomNameVisible(false); c.setSlowWhenEmpty(false); c.setMaxSpeed(0.45);
            c.setInvulnerable(true); c.setPersistent(true); c.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        });
        Wagon wagon = new Wagon(cart, model);
        wagons.put(cart.getUniqueId(), wagon);
        buildModel(wagon); buildSeats(wagon); consumeOne(held);
        event.getPlayer().sendMessage(color("&aWagon &f" + model + " &apositionné ! Clic droit sur un siège pour monter."));
        playConfiguredSound(cart.getLocation(), "models." + model + ".place-sound", "BLOCK_WOOD_PLACE", 0.8f, 1.0f);
    }

    private void consumeOne(ItemStack item) {
        if (item.getAmount() <= 1) item.setAmount(0); else item.setAmount(item.getAmount() - 1);
    }

    private void buildModel(Wagon wagon) {
        String path = "models." + wagon.model + ".";
        Material base = getMaterial(path + "base", Material.SPRUCE_PLANKS);
        Material seat = getMaterial(path + "seat", Material.SPRUCE_STAIRS);
        Material back = getMaterial(path + "back", Material.STRIPPED_SPRUCE_LOG);
        Material fence = getMaterial(path + "fence", Material.SPRUCE_FENCE);
        Material bumper = getMaterial(path + "bumper", Material.DARK_OAK_PLANKS);
        addBlock(wagon, base, 0, .15, 0, 1.35f, .22f, 1.75f);
        addBlock(wagon, seat, -.30, .34, -.18, .52f, .38f, .62f);
        addBlock(wagon, seat, .30, .34, -.18, .52f, .38f, .62f);
        addBlock(wagon, back, -.30, .57, -.49, .48f, .55f, .16f);
        addBlock(wagon, back, .30, .57, -.49, .48f, .55f, .16f);
        addBlock(wagon, fence, -.64, .43, .25, .16f, .68f, 1.12f);
        addBlock(wagon, fence, .64, .43, .25, .16f, .68f, 1.12f);
        addBlock(wagon, bumper, 0, .35, .82, 1.20f, .25f, .16f);
        addBlock(wagon, bumper, 0, .35, -.82, 1.20f, .25f, .16f);
        addBlock(wagon, bumper, -.53, .28, .68, .18f, .22f, .18f);
        addBlock(wagon, bumper, .53, .28, .68, .18f, .22f, .18f);
    }

    private void addBlock(Wagon wagon, Material material, double x, double y, double z, float sx, float sy, float sz) {
        Location loc = rotated(wagon.cart.getLocation(), x, y, z, wagon.cart.getYaw());
        BlockDisplay display = loc.getWorld().spawn(loc, BlockDisplay.class, entity -> {
            entity.setBlock(Bukkit.createBlockData(material));
            entity.setInvulnerable(true); entity.setPersistent(false); entity.setShadowRadius(0);
            entity.setTransformation(new Transformation(new Vector3f(0, 0, 0),
                    new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf()));
        });
        wagon.displays.add(display);
    }

    private void buildSeats(Wagon wagon) {
        int count = Math.max(1, Math.min(2, getConfig().getInt("settings.seat-count", 2)));
        for (int i = 0; i < count; i++) {
            double x = count == 1 ? 0 : (i == 0 ? -.30 : .30);
            Location seatLoc = rotated(wagon.cart.getLocation(), x, .78, -.10, wagon.cart.getYaw());
            ArmorStand seat = seatLoc.getWorld().spawn(seatLoc, ArmorStand.class, stand -> {
                stand.setVisible(false); stand.setInvisible(true); stand.setMarker(false);
                stand.setGravity(false); stand.setArms(false); stand.setBasePlate(false);
                stand.setCollidable(false); stand.setInvulnerable(true); stand.setCanPickupItems(false);
                stand.setPersistent(false);
                stand.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, "seat");
            });
            wagon.seats.add(seat); seatOwners.put(seat.getUniqueId(), wagon);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSeatInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        Wagon wagon = seatOwners.get(stand.getUniqueId());
        if (wagon == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isInsideVehicle()) { player.sendMessage(color("&cDescends d'abord de ton véhicule actuel.")); return; }
        if (!stand.getPassengers().isEmpty()) { player.sendMessage(color("&cCette place est déjà occupée.")); return; }
        stand.addPassenger(player);
        player.sendMessage(color("&aTu es installé dans le wagon ! &7Maj pour descendre."));
        playConfiguredSound(stand.getLocation(), "models." + wagon.model + ".board-sound",
                "BLOCK_NOTE_BLOCK_BELL", .7f, 1.25f);
    }

    @EventHandler public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Wagon wagon = seatOwners.get(event.getDismounted().getUniqueId());
        if (wagon != null && player.isOnline()) playSound(player.getLocation(), "ENTITY_ITEM_PICKUP", .45f, .8f);
    }

    @EventHandler public void protectWagonEntities(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (wagons.containsKey(entity.getUniqueId()) || seatOwners.containsKey(entity.getUniqueId())) {
            event.setCancelled(true); return;
        }
        if (entity instanceof BlockDisplay) {
            for (Wagon wagon : wagons.values()) if (wagon.displays.contains(entity)) {
                event.setCancelled(true); return;
            }
        }
    }

    private void tickWagons() {
        tickCounter++;
        Iterator<Wagon> iterator = wagons.values().iterator();
        while (iterator.hasNext()) {
            Wagon wagon = iterator.next();
            if (!wagon.cart.isValid() || wagon.cart.isDead()) {
                removeEntities(wagon); iterator.remove(); continue;
            }
            Location origin = wagon.cart.getLocation();
            float yaw = wagon.cart.getYaw();
            double[][] parts = {{0,.15,0},{-.30,.34,-.18},{.30,.34,-.18},
                    {-.30,.57,-.49},{.30,.57,-.49},{-.64,.43,.25},{.64,.43,.25},
                    {0,.35,.82},{0,.35,-.82},{-.53,.28,.68},{.53,.28,.68}};
            for (int i = 0; i < wagon.displays.size() && i < parts.length; i++) {
                double[] p = parts[i];
                wagon.displays.get(i).teleport(rotated(origin, p[0], p[1], p[2], yaw));
            }
            int count = wagon.seats.size();
            for (int i = 0; i < count; i++) {
                double x = count == 1 ? 0 : (i == 0 ? -.30 : .30);
                wagon.seats.get(i).teleport(rotated(origin, x, .78, -.10, yaw));
            }
            if (getConfig().getBoolean("settings.movement-sound", true)
                    && tickCounter % Math.max(20, getConfig().getLong("settings.movement-sound-interval-ticks", 50)) == 0
                    && wagon.cart.getVelocity().lengthSquared() > .015) {
                playSound(origin, "ENTITY_MINECART_RIDING",
                        (float)getConfig().getDouble("settings.movement-sound-volume", .22),
                        (float)getConfig().getDouble("settings.movement-sound-pitch", .85));
            }
        }
    }

    private void destroyWagon(Wagon wagon) {
        wagons.remove(wagon.cart.getUniqueId()); removeEntities(wagon);
        if (wagon.cart.isValid()) wagon.cart.remove();
    }
    private void removeEntities(Wagon wagon) {
        for (ArmorStand seat : wagon.seats) {
            seatOwners.remove(seat.getUniqueId());
            if (seat.isValid()) {
                for (Entity passenger : new ArrayList<>(seat.getPassengers())) seat.removePassenger(passenger);
                seat.remove();
            }
        }
        for (BlockDisplay display : wagon.displays) if (display.isValid()) display.remove();
    }
    private Location rotated(Location origin, double x, double y, double z, float yaw) {
        double radians = Math.toRadians(yaw);
        double rx = x * Math.cos(radians) - z * Math.sin(radians);
        double rz = x * Math.sin(radians) + z * Math.cos(radians);
        return origin.clone().add(rx, y, rz);
    }
    private Material getMaterial(String path, Material fallback) {
        Material material = Material.matchMaterial(getConfig().getString(path, fallback.name()));
        return material == null ? fallback : material;
    }
    private void playConfiguredSound(Location location, String configPath, String fallback, float volume, float pitch) {
        playSound(location, getConfig().getString(configPath, fallback), volume, pitch);
    }
    private void playSound(Location location, String name, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (IllegalArgumentException | NullPointerException ex) {
            getLogger().warning("Son inconnu dans config.yml : " + name);
        }
    }
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}