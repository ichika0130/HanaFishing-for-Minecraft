package xyz.hanamae.listener;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MyListener implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey weightKey;
    private final Map<UUID, FishingTask> activeGames = new HashMap<>();

    public MyListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.weightKey = new NamespacedKey(plugin, "fish_weight");
    }

    private static class FishType {
        String name; Material material; String color; double minWeight; double maxWeight;
        FishType(String name, Material material, String color, double min, double max) {
            this.name = name; this.material = material; this.color = color;
            this.minWeight = min; this.maxWeight = max;
        }
    }

    private final List<FishType> epicFishPool = List.of(
            new FishType("神秘的巨大热带鱼", Material.TROPICAL_FISH, "§6§l", 20.0, 100.0),
            new FishType("深海远古肺鱼", Material.RAW_COPPER, "§e§l", 5.0, 15.0),
            new FishType("水滴说是鱼", Material.GHAST_TEAR, "§d§l", 0.5, 2.0),
            new FishType("下界合金碎片鱼", Material.NETHERITE_SCRAP, "§4§l", 50.0, 150.0)
    );

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();

        // 1. 咬钩判定
        if (event.getState() == PlayerFishEvent.State.BITE) {
            double chance = 0.2;
            ItemStack rod = player.getInventory().getItemInMainHand();
            if (rod == null || rod.getType() != Material.FISHING_ROD) {
                rod = player.getInventory().getItemInOffHand();
            }

            if (rod != null && rod.getType() == Material.FISHING_ROD) {
                int luckLevel = rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
                chance = Math.min(1.0, chance + (luckLevel * 0.05));
            }

            if (ThreadLocalRandom.current().nextDouble() < chance) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1, 1);
                // 修正：在这里传入 event.getHook()
                FishingTask task = new FishingTask(player, createEpicFish(), event.getHook());
                task.runTaskTimer(plugin, 0L, 1L);
                activeGames.put(player.getUniqueId(), task);
                player.sendTitle("§c§l咬钩了！", "§e立刻按住 [SHIFT] 开始角力", 0, 40, 10);
            }
        }

        // 2. 收钩判定
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            if (activeGames.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            if (event.getCaught() instanceof Item itemEntity) {
                player.sendTitle("§a收杆成功", "§7你钓到了: §f" + getItemName(itemEntity.getItemStack()), 10, 40, 10);
            }
        }

        if (event.getState() == PlayerFishEvent.State.IN_GROUND && activeGames.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (activeGames.containsKey(player.getUniqueId()) && event.getAction().name().contains("RIGHT")) {
            event.setCancelled(true);
            activeGames.get(player.getUniqueId()).checkHit();
        }
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) return item.getItemMeta().getDisplayName();
        return item.getType().name().replace("_", " ").toLowerCase();
    }

    private ItemStack createEpicFish() {
        FishType config = epicFishPool.get(ThreadLocalRandom.current().nextInt(epicFishPool.size()));
        ItemStack fish = new ItemStack(config.material);
        ItemMeta meta = fish.getItemMeta();
        if (meta != null) {
            double weight = ThreadLocalRandom.current().nextDouble(config.minWeight, config.maxWeight);
            meta.getPersistentDataContainer().set(weightKey, PersistentDataType.DOUBLE, weight);
            meta.setDisplayName(config.color + config.name);
            meta.setLore(Arrays.asList("§7稀有度: §6§l史诗", "§7重量: §e" + String.format("%.2f", weight) + "kg", "", "§c# 极其罕见的变异品种"));
            fish.setItemMeta(meta);
        }
        return fish;
    }

    private class FishingTask extends BukkitRunnable {
        private final Player player;
        private final ItemStack fish;
        private final FishHook hook; // 变量定义
        private int barPos = 0;
        private boolean forward = true;
        private int tickCounter = 0;
        private int gracePeriod = 80;
        private int directionSwitches = 0;
        private final int maxSwitches = 2;
        private final int barLength = 35;
        private final Set<Integer> targetIndices = new HashSet<>();

        // 修正：构造函数现在正确接收 FishHook 参数
        public FishingTask(Player player, ItemStack fish, FishHook hook) {
            this.player = player;
            this.fish = fish;
            this.hook = hook; // 现在可以正确初始化了
            for (int i = 0; i < 3; i++) {
                int seed = ThreadLocalRandom.current().nextInt(5, barLength - 5);
                targetIndices.add(seed);
                targetIndices.add(seed + 1);
                targetIndices.add(seed - 1);
            }
        }

        @Override
        public void run() {
            if (!player.isOnline()) { cleanup(); return; }

            if (gracePeriod > 0) {
                if (!player.isSneaking()) {
                    gracePeriod--;
                    player.sendTitle("§c§l请按住 [SHIFT]", "§e等待咬钩... " + String.format("%.1f", gracePeriod/20.0) + "s", 0, 5, 0);
                } else {
                    gracePeriod -= 2;
                    player.sendTitle("§a§l稳住鱼竿...", "§f即将开始角力", 0, 5, 0);
                }
                return;
            }

            if (!player.isSneaking()) {
                player.sendTitle("§c§l× 脱钩了 ×", "§7你松开了按键", 0, 20, 5);
                removeHook(); // 失败也要收钩
                cleanup(); return;
            }

            tickCounter++;
            if (tickCounter % 2 == 0) {
                if (forward) {
                    barPos++;
                    if (barPos >= barLength) { forward = false; directionSwitches++; }
                } else {
                    barPos--;
                    if (barPos <= 0) { forward = true; directionSwitches++; }
                }

                if (directionSwitches >= maxSwitches) {
                    player.sendTitle("§c§l× 鱼逃跑了 ×", "§7你犹豫太久了！", 5, 20, 5);
                    removeHook(); // 超时也要收钩
                    cleanup(); return;
                }
            }

            StringBuilder bar = new StringBuilder("§8");
            for (int i = 0; i <= barLength; i++) {
                if (i == barPos) {
                    bar.append("§c§l⚓");
                } else if (targetIndices.contains(i)) {
                    if (i % 2 == 0) bar.append("§e§l§n|");
                    else bar.append("§e§l§n·");
                } else {
                    bar.append("§8|");
                }
            }
            String progress = (directionSwitches == 0) ? "§a>>> 正在拉线" : "§6<<< 最后机会！";
            player.sendTitle(bar.toString(), progress, 0, 5, 0);
        }

        public void checkHit() {
            if (gracePeriod > 20) {
                player.sendTitle("§c§l太心急了！", "§f鱼还没咬稳", 5, 20, 5);
                removeHook();
                cleanup();
                return;
            }

            if (targetIndices.contains(barPos)) {
                player.getInventory().addItem(fish);
                player.sendTitle("§6§l完美捕获！", "§f获得: " + fish.getItemMeta().getDisplayName(), 5, 40, 5);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 2);
            } else {
                player.sendTitle("§c§l未命中！", "§f鱼挣脱了钩子", 5, 20, 5);
            }

            removeHook(); // 无论中没中，最后都收钩
            cleanup();
        }

        // 封装一个收钩方法，增加真实感
        private void removeHook() {
            if (hook != null && !hook.isDead()) {
                player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
                hook.remove();
            }
        }

        private void cleanup() {
            cancel();
            activeGames.remove(player.getUniqueId());
        }
    }
}