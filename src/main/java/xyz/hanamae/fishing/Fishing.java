package xyz.hanamae.fishing;

import org.bukkit.plugin.java.JavaPlugin;
import xyz.hanamae.bait.BaitManager;
import xyz.hanamae.listener.BaitAcquisitionListener;
import xyz.hanamae.listener.MyListener;

public final class Fishing extends JavaPlugin {

    @Override
    public void onEnable() {
        // 1. 初始化管理类 (它是所有数据的源头)
        BaitManager baitManager = new BaitManager(this);

        // 2. 注册配方 (利用刚才创建的 baitManager)
        baitManager.registerRecipes();

        // 3. 注册监听器 (只注册一次，并统一传入 baitManager 实例)
        // 这样 MyListener 和 BaitAcquisitionListener 才能共享同一个 Key 识别系统
        getServer().getPluginManager().registerEvents(new MyListener(this, baitManager), this);
        getServer().getPluginManager().registerEvents(new BaitAcquisitionListener(baitManager), this);

        getLogger().info("§a[HanaFishing] 插件已成功启动，鱼饵与史诗鱼系统就绪。");
    }

    @Override
    public void onDisable() {
        // 卸载逻辑
    }
}