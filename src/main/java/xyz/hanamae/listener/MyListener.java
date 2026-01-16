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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.hanamae.bait.BaitType;
import xyz.hanamae.bait.BaitManager;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MyListener implements Listener {

    private final BaitManager baitManager;
    private final JavaPlugin plugin;
    private final NamespacedKey weightKey;
    private final NamespacedKey baitIdKey;    // 鱼饵 ID
    private final NamespacedKey baitBonusKey; // 存储在钩子上的动态加成
    private final Map<UUID, FishingTask> activeGames = new HashMap<>();

    public MyListener(JavaPlugin plugin, BaitManager baitManager) {
        this.baitManager = baitManager;
        this.plugin = plugin;
        this.weightKey = new NamespacedKey(plugin, "fish_weight");
        this.baitIdKey = new NamespacedKey(plugin, "bait_id");
        this.baitBonusKey = new NamespacedKey(plugin, "bait_bonus");
    }

    // --- 史诗鱼池配置 ---
    private static class FishType {
        String name; Material material; String color; double minWeight; double maxWeight;
        FishType(String name, Material material, String color, double min, double max) {
            this.name = name; this.material = material; this.color = color;
            this.minWeight = min; this.maxWeight = max;
        }
    }

    private final List<FishType> epicFishPool = List.of(
            new FishType("神秘的巨大热带鱼", Material.TROPICAL_FISH, "§6§l", 20.0, 100.0),
            new FishType("铜晶鱼", Material.RAW_COPPER, "§e§l", 5.0, 15.0),
            new FishType("水滴鱼", Material.GHAST_TEAR, "§d§l", 0.5, 2.0),
            new FishType("下界合金碎片鱼", Material.NETHERITE_SCRAP, "§4§l", 50.0, 150.0)
    );

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        FishHook hook = event.getHook();

        // 1. 抛竿阶段
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            handleBaitApplication(player, hook);
        }

        // 2. 咬钩阶段
        if (event.getState() == PlayerFishEvent.State.BITE) {
            handleBiteEvent(event, player, hook);
        }

        // 3. 收钩判定
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            // 如果是正在进行史诗鱼小游戏，拦截原版逻辑
            if (activeGames.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            // 普通鱼收竿提示
            if (event.getCaught() instanceof Item itemEntity) {
                ItemStack caughtItem = itemEntity.getItemStack();
                String name = caughtItem.hasItemMeta() && caughtItem.getItemMeta().hasDisplayName() ?
                        caughtItem.getItemMeta().getDisplayName() : caughtItem.getType().name();

                player.sendTitle("§a收杆成功", "§7你钓到了: §f" + name, 10, 40, 10);
            }
        }

        if (event.getState() == PlayerFishEvent.State.IN_GROUND && activeGames.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void handleBaitApplication(Player player, FishHook hook) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand == null || !offHand.hasItemMeta()) return;

        // 从 PDC 获取鱼饵 ID（兼容所有在 BaitType 注册的鱼饵）
        String id = offHand.getItemMeta().getPersistentDataContainer().get(baitIdKey, PersistentDataType.STRING);
        BaitType type = BaitType.fromId(id);

        if (type != null) {
            offHand.setAmount(offHand.getAmount() - 1);
            // 将该鱼饵的具体加成值注入钩子
            hook.getPersistentDataContainer().set(baitBonusKey, PersistentDataType.DOUBLE, type.getBonus());

            String message = "§7已挂上 " + type.getDisplayName() + " §8(史诗鱼概率 +" + (int)(type.getBonus()*100) + "%)";
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(message));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
        }
    }

    private void handleBiteEvent(PlayerFishEvent event, Player player, FishHook hook) {
        double chance = 0.15; // 基础概率

        // 海之眷顾加成
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod != null && rod.getType() == Material.FISHING_ROD) {
            chance += rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA) * 0.05;
        }

        // 鱼饵动态加成
        Double bonus = hook.getPersistentDataContainer().get(baitBonusKey, PersistentDataType.DOUBLE);
        if (bonus != null) chance += bonus;

        // 触发概率判定
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            // 启动小游戏
            FishingTask task = new FishingTask(player, createEpicFish(), hook);
            task.runTaskTimer(plugin, 0L, 1L);
            activeGames.put(player.getUniqueId(), task);

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1, 1);
            player.sendTitle("§c§l咬钩了！", "§e按住 [SHIFT] 开始角力", 0, 40, 10);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // 只有正在游戏且是右键点击时才触发 hit 判定
        if (activeGames.containsKey(player.getUniqueId()) && event.getAction().name().contains("RIGHT")) {
            event.setCancelled(true);
            activeGames.get(player.getUniqueId()).checkHit();
        }
    }

    private ItemStack createEpicFish() {
        FishType config = epicFishPool.get(ThreadLocalRandom.current().nextInt(epicFishPool.size()));
        ItemStack fish = new ItemStack(config.material);
        ItemMeta meta = fish.getItemMeta();

        if (meta != null) {
            double weight = ThreadLocalRandom.current().nextDouble(config.minWeight, config.maxWeight);
            meta.getPersistentDataContainer().set(weightKey, PersistentDataType.DOUBLE, weight);

            // 1. 设置基础信息
            meta.setDisplayName(config.color + config.name);
            meta.setLore(Arrays.asList(
                    "§7稀有度: §6§l史诗",
                    "§7重量: §e" + String.format("%.2f", weight) + "kg",
                    "",
                    "§c# 极其罕见的变异品种"
            ));

            // 2. 核心：添加附魔光效
            // 使用一个不冲突的附魔（比如持久/MENDING 或 力量/POWER）
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);

            // 3. 核心：隐藏附魔文字（只保留紫光）
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

            fish.setItemMeta(meta);
        }
        return fish;
    }

    // --- 内部任务类：处理角力逻辑 ---
    private class FishingTask extends BukkitRunnable {
        private final Player player;
        private final ItemStack fish;
        private final FishHook hook;
        private int barPos = 0;
        private boolean forward = true;
        private int tickCounter = 0;
        private int gracePeriod = 80;
        private int directionSwitches = 0;
        private final int maxSwitches = 2; // 折返限制
        private final int barLength = 35;
        private final Set<Integer> targetIndices = new HashSet<>();

        public FishingTask(Player player, ItemStack fish, FishHook hook) {
            this.player = player;
            this.fish = fish;
            this.hook = hook;
            // 随机生成判定区
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

            // 预备阶段
            if (gracePeriod > 0) {
                if (!player.isSneaking()) {
                    gracePeriod--;
                    player.sendTitle("§c§l请按住 [SHIFT]", "§e准备角力... " + String.format("%.1f", gracePeriod/20.0) + "s", 0, 5, 0);
                } else {
                    gracePeriod -= 2;
                    player.sendTitle("§a§l稳住鱼竿...", "§f目标出现", 0, 5, 0);
                }
                return;
            }

            // 角力阶段：必须按住 SHIFT
            if (!player.isSneaking()) {
                endGame("§c§l× 脱钩了 ×", "§7你松开了按键");
                return;
            }

            // 指针移动逻辑
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
                    endGame("§c§l× 鱼逃跑了 ×", "§7你犹豫太久了！");
                    return;
                }
            }

            // 渲染 UI
            renderUI();
        }

        private void renderUI() {
            StringBuilder bar = new StringBuilder("§8");
            for (int i = 0; i <= barLength; i++) {
                if (i == barPos) bar.append("§c§l▶");
                else if (targetIndices.contains(i)) bar.append("§e§l§n·");
                else bar.append("§8|");
            }
            String sub = (directionSwitches == 0) ? "§a[右键收杆]" : "§6[最后机会!]";
            player.sendTitle(bar.toString(), sub, 0, 5, 0);
        }

        public void checkHit() {
            if (gracePeriod > 20) {
                endGame("§c§l太心急了！", "§f鱼还没咬稳");
                return;
            }

            if (targetIndices.contains(barPos)) {
                player.getInventory().addItem(fish);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 2);
                endGame("§6§l完美捕获！", "§f获得: " + fish.getItemMeta().getDisplayName());
            } else {
                endGame("§c§l未命中！", "§f鱼挣脱了钩子");
            }
        }

        private void endGame(String title, String subtitle) {
            player.sendTitle(title, subtitle, 5, 25, 5);
            if (hook != null && !hook.isDead()) {
                player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1f);
                hook.remove();
            }
            cleanup();
        }

        private void cleanup() {
            cancel();
            activeGames.remove(player.getUniqueId());
        }
    }
}