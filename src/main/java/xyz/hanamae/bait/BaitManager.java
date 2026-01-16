package xyz.hanamae.bait;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class BaitManager {
    private final NamespacedKey baitKey;
    private final JavaPlugin plugin; // 增加引用以便注册配方

    public BaitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.baitKey = new NamespacedKey(plugin, "bait_id");
    }

    public ItemStack createBait(BaitType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(type.getDisplayName());

            // 注入 CustomModelData，确保材质包生效
            meta.setCustomModelData(type.getCustomModelData());

            List<String> lore = new ArrayList<>();
            lore.add("§7稀有鱼上钩率: §a+" + (int)(type.getBonus() * 100) + "%");
            lore.add("");
            lore.add("§8[Project Tidal Whispers]");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(baitKey, PersistentDataType.STRING, type.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 注册鱼饵合成表
     */
    public void registerRecipes() {
        // 1. 生成面包碎物品实例
        ItemStack breadCrumb = createBait(BaitType.BREAD_CRUMB);
        breadCrumb.setAmount(4); // 硬核设定：1个面包拆成4份

        // 2. 创建配方 (使用唯一 Key 防止冲突)
        NamespacedKey recipeKey = new NamespacedKey(plugin, "bread_to_crumb");
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, breadCrumb);

        // 3. 只需要一个原版面包
        recipe.addIngredient(Material.BREAD);

        // 4. 注册
        plugin.getServer().addRecipe(recipe);
    }

    public BaitType getBaitType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(baitKey, PersistentDataType.STRING);
        return id != null ? BaitType.fromId(id) : null;
    }
}