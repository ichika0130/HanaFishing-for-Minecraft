package xyz.hanamae.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
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
    void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        int hour = LocalTime.now().getHour();
        String greeting = (hour >= 5 && hour < 11) ? "早上好！" : (hour >= 11 && hour < 14) ? "中午好！" : (hour >= 14 && hour < 18) ? "下午好！" : (hour >= 18 && hour < 23) ? "晚上好！" : "夜深了，注意休息哦！";
        player.sendTitle("§6" + greeting, "§f欢迎回到服务器，" + player.getName(), 10, 70, 20);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();

        // 将状态改为 BITE (鱼咬钩，浮标下沉时)
        if (event.getState() == PlayerFishEvent.State.BITE) {
            double chance = 0.2; // 基础概率

            // 检查鱼竿加成
            ItemStack rod = player.getInventory().getItemInMainHand();
            if (rod == null || rod.getType() != Material.FISHING_ROD) {
                rod = player.getInventory().getItemInOffHand();
            }

            if (rod != null && rod.getType() == Material.FISHING_ROD) {
                int luckLevel = rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
                chance = Math.min(1.0, chance + (luckLevel * 0.05));
            }

            // 判定是否触发史诗鱼
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                // 重要：取消收杆动作，防止玩家点一下右键就把浮标收了
                // 注意：在 BITE 状态取消事件，浮标通常会留在水里或消失，取决于具体版本
                // 我们通过启动小游戏来接管玩家的操作

                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1, 1);

                // 启动小游戏
                FishingTask task = new FishingTask(player, createEpicFish());
                task.runTaskTimer(plugin, 0L, 1L);
                activeGames.put(player.getUniqueId(), task);

                // 提醒玩家：鱼已经咬钩了！
                player.sendTitle("§c§l咬钩了！", "§e立刻按住 [SHIFT] 开始角力", 0, 40, 10);
            }
        }

        // 防止玩家在小游戏进行中通过正常的钓鱼逻辑收杆
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH || event.getState() == PlayerFishEvent.State.IN_GROUND) {
            if (activeGames.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
            }
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
        private int barPos = 0;
        private boolean forward = true;
        private int tickCounter = 0;
        private int gracePeriod = 80;

        // 新增：折返次数计数
        private int directionSwitches = 0;
        private final int maxSwitches = 2; // 走一个来回（左->右，右->左）就结束

        private final int barLength = 35;
        private final Set<Integer> targetIndices = new HashSet<>();

        public FishingTask(Player player, ItemStack fish) {
            this.player = player;
            this.fish = fish;
            // 随机生成 3 段实心判定区
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

            // 1. 宽限期
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

            // 2. 潜行检查
            if (!player.isSneaking()) {
                player.sendTitle("§c§l× 脱钩了 ×", "§7你松开了按键", 0, 20, 5);
                cleanup(); return;
            }

            // 3. 移动逻辑与折返检测
            tickCounter++;
            if (tickCounter % 2 == 0) {
                if (forward) {
                    barPos++;
                    if (barPos >= barLength) {
                        forward = false;
                        directionSwitches++;
                    }
                } else {
                    barPos--;
                    if (barPos <= 0) {
                        forward = true;
                        directionSwitches++;
                    }
                }

                // 检查是否超过最大折返次数
                if (directionSwitches >= maxSwitches) {
                    player.sendTitle("§c§l× 鱼逃跑了 ×", "§7你犹豫太久了！", 5, 20, 5);
                    cleanup();
                    return;
                }
            }

            // 4. 渲染界面 (实心黄区样式)
            StringBuilder bar = new StringBuilder("§8");
            for (int i = 0; i <= barLength; i++) {
                if (i == barPos) {
                    bar.append("§c§l⚓"); // 玩家红色鱼钩
                } else if (targetIndices.contains(i)) {
                    // 使用加粗和特定字符模拟实心 |·| 效果
                    // §n 是下划线，配合 §l 加粗可以填满空间
                    if (i % 2 == 0) bar.append("§e§l§n|");
                    else bar.append("§e§l§n·");
                } else {
                    bar.append("§8|");
                }
            }

            // 在副标题显示剩余机会（进度提示）
            String progress = (directionSwitches == 0) ? "§a>>> 正在拉线" : "§6<<< 最后机会！";
            player.sendTitle(bar.toString(), progress, 0, 5, 0);
        }

        public void checkHit() {
            if (gracePeriod > 20) {
                player.sendTitle("§c§l太心急了！", "§f鱼还没咬稳", 5, 20, 5);
                cleanup(); return;
            }

            if (targetIndices.contains(barPos)) {
                player.getInventory().addItem(fish);
                player.sendTitle("§6§l完美捕获！", "§f获得: " + fish.getItemMeta().getDisplayName(), 5, 40, 5);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 2);
            } else {
                player.sendTitle("§c§l未命中！", "§f鱼挣脱了钩子", 5, 20, 5);
            }
            cleanup();
        }

        private void cleanup() {
            cancel();
            activeGames.remove(player.getUniqueId());
        }
    }
}