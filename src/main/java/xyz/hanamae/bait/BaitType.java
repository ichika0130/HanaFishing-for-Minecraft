package xyz.hanamae.bait;

import org.bukkit.Material;

public enum BaitType {
    // ID, 概率加成, 显示名称, 材质, CustomModelData
    EARTHWORM("earthworm", 0.10, "§6蚯蚓", Material.COD, 50001),
    BREAD_CRUMB("bread_crumb", 0.05, "§f面包碎块", Material.COD, 50002);

    private final String id;
    private final double bonus;
    private final String displayName;
    private final Material material;
    private final int customModelData;

    // 修正构造函数：增加 int customModelData 参数
    BaitType(String id, double bonus, String displayName, Material material, int customModelData) {
        this.id = id;
        this.bonus = bonus;
        this.displayName = displayName;
        this.material = material;
        this.customModelData = customModelData;
    }

    public String getId() { return id; }
    public double getBonus() { return bonus; }
    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public int getCustomModelData() { return customModelData; } // 增加 Getter

    public static BaitType fromId(String id) {
        for (BaitType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }
}